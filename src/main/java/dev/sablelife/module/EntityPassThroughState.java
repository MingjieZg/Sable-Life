package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.combat.EntityHitbox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned runtime state. The client receives only the bodies relevant to its own player. */
@EventBusSubscriber(modid = SableLife.MOD_ID)
public final class EntityPassThroughState {
    private static final int EXIT_GRACE = 20;
    private static final int EXIT_RETRY = 10;
    private static final int EXIT_CHECKS_PER_TICK = 256;
    private static final Map<ServerLevel, WorldState> SERVER = new IdentityHashMap<>();
    private static final Set<UUID> CLIENT_BODIES = new HashSet<>();
    private static WeakReference<Player> clientPlayer = new WeakReference<>(null);
    private static WeakReference<Level> clientLevel = new WeakReference<>(null);

    private static final class WorldState {
        final Map<ServerSubLevel, BodyState> bodies = new IdentityHashMap<>();
        final LinkedHashSet<Exit> pending = new LinkedHashSet<>();
    }

    private static final class Exit {
        final LivingEntity entity;
        final ServerSubLevel body;
        long retryAt;

        Exit(LivingEntity entity, ServerSubLevel body, long retryAt) {
            this.entity = entity;
            this.body = body;
            this.retryAt = retryAt;
        }
    }

    private static final class BodyState {
        int claims;
        boolean releasing;
        final Map<LivingEntity, Exit> exiting = new IdentityHashMap<>();
        final Set<ServerPlayer> informed = Collections.newSetFromMap(new IdentityHashMap<>());

        boolean active() {
            return claims > 0 || releasing;
        }
    }

    static void replace(ServerLevel level, Set<ServerSubLevel> previous, Set<ServerSubLevel> next) {
        var world = SERVER.computeIfAbsent(level, ignored -> new WorldState());
        var bodies = world.bodies;
        for (var body : previous) {
            if (next.contains(body)) continue;
            var state = bodies.get(body);
            if (state != null && --state.claims == 0) state.releasing = true;
        }
        for (var body : next) {
            if (previous.contains(body)) continue;
            var state = bodies.computeIfAbsent(body, ignored -> new BodyState());
            if (state.claims++ == 0) {
                state.releasing = false;
                discardExits(world, state);
            }
        }
    }

    private static void discardExits(WorldState world, BodyState state) {
        for (var exit : state.exiting.values()) world.pending.remove(exit);
        state.exiting.clear();
    }

    public static boolean mayIgnore(Entity entity) {
        Level level = entity.level();
        if (level.isClientSide()) {
            return entity == clientPlayer.get() && level == clientLevel.get() && !CLIENT_BODIES.isEmpty();
        }
        return SERVER.containsKey(level) && eligible(entity);
    }

    public static boolean ignores(Entity entity, SubLevel body) {
        Level level = entity.level();
        if (body.getLevel() != level || body.isRemoved()) return false;
        if (level.isClientSide()) {
            return entity == clientPlayer.get() && level == clientLevel.get() && CLIENT_BODIES.contains(body.getUniqueId());
        }
        var world = SERVER.get(level);
        var state = world == null ? null : world.bodies.get(body);
        return state != null && eligible(entity) && (state.active() || state.exiting.containsKey(entity));
    }

    private static boolean eligible(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof EntityHitbox) && entity.isAlive()
                && (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer || canSync(player));
    }

    private static boolean canSync(ServerPlayer player) {
        return player.connection != null && NetworkRegistry.hasChannel(player.connection, EntityPassThroughPacket.TYPE.id());
    }

    static void receive(Player player, EntityPassThroughPacket packet) {
        Level level = player.level();
        if (!level.isClientSide() || !level.dimension().location().equals(packet.dimension())) return;
        if (clientPlayer.get() != player || clientLevel.get() != level) {
            CLIENT_BODIES.clear();
            clientPlayer = new WeakReference<>(player);
            clientLevel = new WeakReference<>(level);
        }
        for (var change : packet.changes()) {
            if (change.enabled()) CLIENT_BODIES.add(change.bodyId());
            else CLIENT_BODIES.remove(change.bodyId());
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var levels = SERVER.entrySet().iterator();
        while (levels.hasNext()) {
            var entry = levels.next();
            var level = entry.getKey();
            if (level.getServer() != event.getServer()) continue;
            var world = entry.getValue();
            for (var part : world.bodies.entrySet()) {
                var body = part.getKey();
                var state = part.getValue();
                if (body.isRemoved()) {
                    state.claims = 0;
                    state.releasing = false;
                    discardExits(world, state);
                } else if (state.releasing || !state.exiting.isEmpty()) {
                    body.updateBoundingBox();
                    if (state.releasing) beginRelease(level, world, body, state);
                }
            }
            checkExits(level, world);

            var changes = new IdentityHashMap<ServerPlayer, Set<UUID>>();
            var bodies = world.bodies.entrySet().iterator();
            while (bodies.hasNext()) {
                var part = bodies.next();
                var state = part.getValue();
                track(level, part.getKey(), state, changes);
                if (state.claims == 0 && state.exiting.isEmpty()) bodies.remove();
            }
            send(level, world, changes);
            if (world.bodies.isEmpty()) levels.remove();
        }
    }

    private static void beginRelease(ServerLevel level, WorldState world, ServerSubLevel body, BodyState state) {
        state.releasing = false;
        var box = body.boundingBox();
        var bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        long deadline = level.getGameTime() + EXIT_GRACE;
        // Only entities already inside the bounds at shutoff can receive an exit allowance.
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, bounds, EntityPassThroughState::eligible)) {
            var exit = new Exit(entity, body, deadline);
            state.exiting.put(entity, exit);
            world.pending.add(exit);
        }
    }

    private static void checkExits(ServerLevel level, WorldState world) {
        if (world.pending.isEmpty()) return;
        long now = level.getGameTime();
        var budget = CollisionWorkBudget.isolated(8192);
        int checks = Math.min(world.pending.size(), EXIT_CHECKS_PER_TICK);
        // Process once per round so a complicated overlap cannot starve later bodies.
        for (int i = 0; i < checks && !budget.isExhausted(); i++) {
            var exit = world.pending.removeFirst();
            var state = world.bodies.get(exit.body);
            if (state == null || state.exiting.get(exit.entity) != exit) continue;
            var entity = exit.entity;
            if (entity.level() != level || !eligible(entity)) {
                state.exiting.remove(entity);
                continue;
            }
            var shape = bodyShape(level, exit.body, entity.getBoundingBox(), budget);
            if (shape == LinearMovementGeometry.ShapeCheck.CLEAR) {
                state.exiting.remove(entity);
                continue;
            }
            if (shape == LinearMovementGeometry.ShapeCheck.OVERLAP && now >= exit.retryAt) {
                if (tryExit(level, exit.body, entity, budget)) {
                    state.exiting.remove(entity);
                    continue;
                }
                exit.retryAt = now + EXIT_RETRY;
            }
            world.pending.add(exit);
        }
    }

    private static LinearMovementGeometry.ShapeCheck bodyShape(ServerLevel level, SubLevel body, AABB box,
                                                              CollisionWorkBudget.Budget budget) {
        return LinearMovementGeometry.bodyShapeCheck(level, body, box.deflate(.001), budget, new int[1]);
    }

    private static boolean tryExit(ServerLevel level, SubLevel body, LivingEntity entity, CollisionWorkBudget.Budget budget) {
        if (entity.isPassenger() || entity.isVehicle()) return false; // The vehicle keeps its usual physics.
        var box = entity.getBoundingBox();
        for (double distance = .5; distance <= 2; distance += .5) {
            for (var face : Direction.values()) {
                var offset = Vec3.atLowerCornerOf(face.getNormal()).scale(distance);
                var destination = box.move(offset);
                if (safeExit(level, body, entity, box, destination, offset, budget)) {
                    var point = entity.position().add(offset);
                    entity.teleportTo(point.x, point.y, point.z);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean safeExit(ServerLevel level, SubLevel body, LivingEntity entity, AABB source, AABB destination,
                                    Vec3 offset, CollisionWorkBudget.Budget budget) {
        var path = source.expandTowards(offset).deflate(.001);
        double scanVolume = (Math.ceil(path.getXsize()) + 1) * (Math.ceil(path.getYsize()) + 1)
                * (Math.ceil(path.getZsize()) + 1);
        if (!Double.isFinite(scanVolume) || scanVolume > 8192 || !budget.spend((int) scanVolume)
                || !level.getWorldBorder().isWithinBounds(path)
                || path.minY < level.getMinBuildHeight() || path.maxY > level.getMaxBuildHeight()
                || !level.hasChunksAt(BlockPos.containing(path.minX, path.minY, path.minZ),
                        BlockPos.containing(path.maxX, path.maxY, path.maxZ))
                || !level.noCollision(entity, destination.deflate(.001))
                || level.getBlockCollisions(entity, path).iterator().hasNext()
                || bodyShape(level, body, destination, budget) != LinearMovementGeometry.ShapeCheck.CLEAR) return false;

        var inside = destination.deflate(.001);
        for (var block : BlockPos.betweenClosed(BlockPos.containing(inside.minX, inside.minY, inside.minZ),
                BlockPos.containing(inside.maxX, inside.maxY, inside.maxZ))) {
            if (!budget.spend(1) || level.getFluidState(block).is(FluidTags.LAVA)) return false;
        }

        var bounds = new BoundingBox3d(path.minX, path.minY, path.minZ, path.maxX, path.maxY, path.maxZ);
        for (var other : Sable.HELPER.getAllIntersecting(level, bounds)) {
            if (other != body && !other.isRemoved()
                    && bodyShape(level, other, path, budget) != LinearMovementGeometry.ShapeCheck.CLEAR) return false;
        }
        return true;
    }

    private static void track(ServerLevel level, ServerSubLevel body, BodyState state,
                              Map<ServerPlayer, Set<UUID>> changes) {
        if (state.active()) {
            for (var id : body.getTrackingPlayers()) {
                var player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null && player.serverLevel() == level && canSync(player) && state.informed.add(player))
                    change(changes, player, body);
            }
        } else {
            for (var entity : state.exiting.keySet()) {
                if (entity instanceof ServerPlayer player && canSync(player) && state.informed.add(player))
                    change(changes, player, body);
            }
        }

        var informed = state.informed.iterator();
        while (informed.hasNext()) {
            var player = informed.next();
            if (player.serverLevel() != level || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                    || player.hasDisconnected() || !canSync(player)) {
                informed.remove();
            } else if (state.active() ? !body.getTrackingPlayers().contains(player.getUUID()) : !state.exiting.containsKey(player)) {
                informed.remove();
                change(changes, player, body);
            }
        }
    }

    private static void change(Map<ServerPlayer, Set<UUID>> changes, ServerPlayer player, SubLevel body) {
        changes.computeIfAbsent(player, ignored -> new HashSet<>()).add(body.getUniqueId());
    }

    private static void send(ServerLevel level, WorldState world, Map<ServerPlayer, Set<UUID>> changes) {
        if (changes.isEmpty()) return;
        var container = SubLevelContainer.getContainer(level);
        for (var entry : changes.entrySet()) {
            var player = entry.getKey();
            var batch = new ArrayList<EntityPassThroughPacket.Change>(EntityPassThroughPacket.LIMIT);
            for (var id : entry.getValue()) {
                // A replacement body can reuse its saved UUID in the same tick as the old body unloads.
                var body = container == null ? null : container.getSubLevel(id);
                var state = body == null ? null : world.bodies.get(body);
                batch.add(new EntityPassThroughPacket.Change(id, state != null && state.informed.contains(player)));
                if (batch.size() == EntityPassThroughPacket.LIMIT) {
                    PacketDistributor.sendToPlayer(player, new EntityPassThroughPacket(level.dimension().location(), batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                PacketDistributor.sendToPlayer(player, new EntityPassThroughPacket(level.dimension().location(), batch));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SERVER.keySet().removeIf(level -> level.getServer() == event.getServer());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) SERVER.remove(server);
        if (event.getLevel() == clientLevel.get()) {
            CLIENT_BODIES.clear();
            clientPlayer.clear();
            clientLevel.clear();
        }
    }

    private EntityPassThroughState() {}
}
