package dev.sablelife.compat.letitwalk;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.sablelife.compat.ModVersion;
import dev.sablelife.compat.OptionalMod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Optional adapter that validates the Walker attachment fields before traversing them. */
public final class WalkerLegDamage {
    private static final String MOD = "let_it_walk";
    private static final Map<ServerLevel, Snapshot> snapshots = new WeakHashMap<>();
    private static boolean initialized;
    private static Access access;

    private record Joint(UUID parent, UUID child, BlockPos anchor) {}
    private record Leg(UUID hull, BlockPos walker, WeakReference<Object> runtime, List<Joint> joints) {}
    private record Snapshot(long tick, Map<UUID, Leg> legs) {}

    private record Access(Class<?> walker, Class<?> hinge, Field tracked, Field legs, Field built,
                          Field segments, Field joints, Field parent, Field child, Field anchor, Method hingeChild) {
        static Access open() throws ReflectiveOperationException {
            var walker = Class.forName("dev.letitwalk.content.WalkerCoreBlockEntity", false, WalkerLegDamage.class.getClassLoader());
            var hinge = Class.forName("dev.letitwalk.content.LegJointBlockEntity", false, walker.getClassLoader());
            var tracker = Class.forName("dev.letitwalk.walker.WalkerTracker", false, walker.getClassLoader());
            var runtime = Class.forName("dev.letitwalk.walker.LegRuntime", false, walker.getClassLoader());
            var built = runtime.getField("built");
            var joint = Class.forName("dev.letitwalk.walker.BuiltLeg$Joint", false, walker.getClassLoader());
            var tracked = tracker.getDeclaredField("CORES");
            var hingeChild = hinge.getMethod("child");
            if (tracked.getType() != Map.class || !java.lang.reflect.Modifier.isStatic(tracked.getModifiers())
                    || !tracked.trySetAccessible() || hingeChild.getReturnType() != UUID.class
                    || java.lang.reflect.Modifier.isStatic(hingeChild.getModifiers())
                    || java.lang.reflect.Modifier.isStatic(built.getModifiers())
                    || !built.getType().getName().equals("dev.letitwalk.walker.BuiltLeg")
                    || !BlockEntity.class.isAssignableFrom(walker) || !BlockEntity.class.isAssignableFrom(hinge)) {
                throw new NoSuchFieldException("Let it Walk tracker or hinge");
            }
            return new Access(walker, hinge, tracked, field(walker, "legs", List.class), built,
                    field(built.getType(), "segments", List.class), field(built.getType(), "joints", List.class),
                    field(joint, "parent", UUID.class), field(joint, "child", UUID.class),
                    field(joint, "parentPlot", Vec3.class), hingeChild);
        }

        private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
            var field = owner.getField(name);
            if (field.getType() != type || java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                throw new NoSuchFieldException(owner.getName() + "." + name);
            return field;
        }
    }

    private static Access access() {
        if (!initialized) {
            initialized = true;
            if (ModVersion.atLeast(OptionalMod.version(MOD), "0.1.5")) {
                try {
                    access = Access.open();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    disable(failure);
                }
            }
        }
        return access;
    }

    private static void disable(Throwable failure) {
        access = null;
        snapshots.clear();
        LogUtils.getLogger().warn("Disabling optional Let it Walk leg damage compatibility", failure);
    }

    public static @Nullable HeartBlockEntity owner(ServerLevel level, BlockPos hit) {
        var api = access();
        if (api == null) return null;
        var body = Sable.HELPER.getContaining(level, hit);
        if (body == null || !level.hasChunkAt(hit) || level.getBlockState(hit).isAir()) return null;
        try {
            var heart = owner(level, body, snapshot(level, api), api);
            return heart != null && acceptsBlock(heart, hit) ? heart : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disable(failure);
            return null;
        }
    }

    public static Map<HeartBlockEntity, Set<SubLevel>> intersecting(ServerLevel level, AABB bounds) {
        var api = access();
        if (api == null) return Map.of();
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Map.of();
        try {
            var index = snapshot(level, api);
            if (index.legs.isEmpty()) return Map.of();
            var result = new LinkedHashMap<HeartBlockEntity, Set<SubLevel>>();
            for (var body : container.queryIntersecting(new BoundingBox3d(bounds))) {
                var heart = owner(level, body, index, api);
                if (heart != null) result.computeIfAbsent(heart, ignored -> new LinkedHashSet<>()).add(body);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disable(failure);
            return Map.of();
        }
    }

    public static boolean acceptsBlock(HeartBlockEntity heart, BlockPos hit) {
        var ownership = BodyOwnership.get((ServerLevel) heart.getLevel());
        var owner = ownership.owner(hit);
        return ownership.part(hit) == null && (owner == null || heart.creatureId().equals(owner));
    }

    /** Death-only snapshot; never extends marking, activation or ordinary block ownership. */
    static Set<SubLevel> deathAttachments(HeartBlockEntity heart) {
        var api = access();
        if (api == null || !(heart.getLevel() instanceof ServerLevel level)) return Set.of();
        try {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) return Set.of();
            var index = snapshot(level, api);
            var result = new LinkedHashSet<SubLevel>();
            for (var id : index.legs.keySet()) {
                var body = container.getSubLevel(id);
                // BuiltLeg retains its exact segment IDs after a hinge or a cored segment is destroyed.
                // Death cleanup includes those remnants; combat still requires every live hinge below.
                if (body != null && attachedOwner(level, body, index, api, false, false) == heart) result.add(body);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disable(failure);
            return Set.of();
        }
    }

    private static Snapshot snapshot(ServerLevel level, Access api) throws ReflectiveOperationException {
        long tick = level.getGameTime();
        var previous = snapshots.get(level);
        if (previous != null && previous.tick == tick) return previous;
        var container = SubLevelContainer.getContainer(level);
        var legs = new HashMap<UUID, Leg>();
        var ambiguous = new HashSet<UUID>();
        if (container != null) for (var entry : ((Map<?, ?>) api.tracked.get(null)).entrySet()) {
            if (!(entry.getKey() instanceof UUID hull) || !(entry.getValue() instanceof BlockPos pos)) continue;
            var body = container.getSubLevel(hull);
            if (body == null || body.isRemoved() || Sable.HELPER.getContaining(level, pos) != body) continue;
            var walker = loadedBlockEntity(level, pos);
            if (!api.walker.isInstance(walker)) continue;
            for (var leg : (List<?>) api.legs.get(walker)) {
                var built = api.built.get(leg);
                if (built == null) continue;
                var segments = new HashSet<>((List<?>) api.segments.get(built));
                var children = new HashMap<UUID, List<Joint>>();
                for (var joint : (List<?>) api.joints.get(built)) {
                    var parent = (UUID) api.parent.get(joint);
                    var child = (UUID) api.child.get(joint);
                    var anchor = (Vec3) api.anchor.get(joint);
                    if (parent != null && child != null && !child.equals(hull) && segments.contains(child)
                            && anchor != null && Double.isFinite(anchor.lengthSqr())) {
                        children.computeIfAbsent(parent, ignored -> new ArrayList<>())
                                .add(new Joint(parent, child, BlockPos.containing(anchor)));
                    }
                }
                var reachable = new LinkedHashMap<UUID, List<Joint>>();
                var remaining = new ArrayDeque<UUID>();
                reachable.put(hull, List.of());
                remaining.add(hull);
                while (!remaining.isEmpty()) {
                    UUID parent = remaining.remove();
                    for (var joint : children.getOrDefault(parent, List.of())) {
                        if (reachable.containsKey(joint.child)) continue;
                        var path = new ArrayList<>(reachable.get(parent));
                        path.add(joint);
                        reachable.put(joint.child, List.copyOf(path));
                        remaining.add(joint.child);
                    }
                }
                reachable.remove(hull);
                var runtime = new WeakReference<>(leg);
                reachable.forEach((segment, path) -> {
                    var candidate = new Leg(hull, pos, runtime, path);
                    if (ambiguous.contains(segment)) return;
                    var old = legs.putIfAbsent(segment, candidate);
                    if (old != null && (!old.hull.equals(hull) || !old.joints.equals(path))) {
                        ambiguous.add(segment);
                        legs.remove(segment);
                    }
                });
            }
        }
        var result = new Snapshot(tick, legs);
        snapshots.put(level, result);
        return result;
    }

    private static @Nullable HeartBlockEntity owner(ServerLevel level, SubLevel body, Snapshot index, Access api)
            throws ReflectiveOperationException {
        return attachedOwner(level, body, index, api, true, true);
    }

    private static @Nullable HeartBlockEntity attachedOwner(ServerLevel level, SubLevel body, Snapshot index, Access api, boolean requireAlive, boolean checkJoints)
            throws ReflectiveOperationException {
        if (body.isRemoved()) return null;
        var candidate = index.legs.get(body.getUniqueId());
        if (candidate == null) return null;
        var runtime = candidate.runtime.get();
        if (runtime == null) return null;
        var walker = loadedBlockEntity(level, candidate.walker);
        var hull = Sable.HELPER.getContaining(level, candidate.walker);
        if (!api.walker.isInstance(walker) || hull == null || hull.isRemoved()
                || !candidate.hull.equals(hull.getUniqueId()) || !((List<?>) api.legs.get(walker)).contains(runtime)) return null;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        if (checkJoints) for (var joint : candidate.joints) {
            var parent = container.getSubLevel(joint.parent);
            var child = container.getSubLevel(joint.child);
            if (parent == null || child == null || parent.isRemoved() || child.isRemoved()
                    || Sable.HELPER.getContaining(level, joint.anchor) != parent) return null;
            var hinge = loadedBlockEntity(level, joint.anchor);
            if (!api.hinge.isInstance(hinge) || !joint.child.equals(api.hingeChild.invoke(hinge))) return null;
        }
        var hearts = SableBodyProbe.findConnectedHearts(level, candidate.walker);
        if (hearts.size() != 1) return null;
        var heart = hearts.getFirst();
        return !heart.isControlCore() && (!requireAlive || heart.health().state() == HeartState.ALIVE)
                && heart.creatureId().equals(BodyOwnership.get(level).owner(heart.getBlockPos())) ? heart : null;
    }

    private static @Nullable BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
    }

    private WalkerLegDamage() {}
}
