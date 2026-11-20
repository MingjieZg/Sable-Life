package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.attack.AttackVolume;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.heart.CoreRelations;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Read-only spatial queries. Never creates entities, loads chunks, or uses the movement work budget. */
public final class ContactSensorQuery {
    public record Result(boolean touching, Set<UUID> projectiles, boolean complete) {}
    private static final int MAX_WORK = 32768, MAX_ENTITIES = 256, MAX_BODIES = 64;
    private static final Map<ServerLevel, Work> WORK = new WeakHashMap<>();
    private static final class Work {
        long tick = Long.MIN_VALUE;
        int remaining;
        boolean spend() { if (remaining <= 0) return false; remaining--; return true; }
    }
    private static Work work(ServerLevel level) {
        var work = WORK.computeIfAbsent(level, ignored -> new Work());
        if (work.tick != level.getGameTime()) { work.tick = level.getGameTime(); work.remaining = MAX_WORK; }
        return work;
    }
    public static boolean checkbox(String key) { return key.startsWith("contactDetect") || key.equals("contactTargetsOnly"); }
    public static boolean visible(VirtualModule m, String key) {
        if (key.equals("contactTargetsOnly")) return m.value("contactDetectStructures") == 1 || m.value("contactDetectPlayers") == 1
                || m.value("contactDetectMobs") == 1 || m.value("contactDetectProjectiles") == 1;
        return !Set.of("contactPulse", "contactCooldown").contains(key) || m.value("contactOutputMode") == 1;
    }

    public static Result query(ServerLevel level, HeartBlockEntity core, VirtualModule module) {
        var frame = ContactSensorGeometry.frame(core, module);
        var volume = frame.volume();
        var bounds = volume.bounds();
        var work = work(level);
        boolean touching = false, complete = true;
        var projectiles = new HashSet<UUID>();
        if (module.value("contactDetectTerrain") == 1) {
            int found = blocks(level, volume, null, work);
            touching = found == 1; complete = found >= 0;
        }
        if (module.value("contactDetectStructures") == 1 && !touching) {
            var root = Sable.HELPER.getContaining(level, core.getBlockPos());
            var own = new HashSet<UUID>();
            if (root != null) for (var body : SubLevelHelper.getConnectedChain(root)) own.add(body.getUniqueId());
            // Walker attachments are independently simulated, but remain part of this creature.
            for (var body : dev.sablelife.compat.letitwalk.WalkerLegDamage.intersecting(level, bounds).getOrDefault(core, Set.of()))
                own.add(body.getUniqueId());
            var container = SubLevelContainer.getContainer(level);
            int count = 0;
            if (container != null) for (var body : container.queryIntersecting(new BoundingBox3d(bounds))) {
                if (++count > MAX_BODIES || !work.spend()) { complete = false; break; }
                if (body.isRemoved() || own.contains(body.getUniqueId())) continue;
                var owner = BodyOwnership.get(level).owner(body.getPlot().getCenterBlock());
                if (core.creatureId().equals(owner)) continue;
                var other = owner == null ? null : LoadedCoreIndex.find(level, owner);
                if (module.value("contactTargetsOnly") == 1 && other != null && CoreRelations.allies(core, other)) continue;
                int found = blocks(level, volume, body, work);
                complete &= found >= 0;
                if (found == 1) { touching = true; break; }
                if (found < 0) break;
            }
        }
        if (module.value("contactDetectPlayers") == 1 || module.value("contactDetectMobs") == 1
                || module.value("contactDetectProjectiles") == 1) {
            var candidates = new ArrayList<Entity>();
            // A small margin also catches ordinary arrows crossing a thin box between two samples.
            var search = module.value("contactDetectProjectiles") == 1 ? bounds.inflate(4) : bounds;
            if (module.value("contactDetectPlayers") == 1 || module.value("contactDetectMobs") == 1)
                level.getEntities(EntityTypeTest.forClass(LivingEntity.class), bounds,
                        entity -> !(entity instanceof ArmorStand) && (entity instanceof Player
                                ? module.value("contactDetectPlayers") == 1 : module.value("contactDetectMobs") == 1), candidates, MAX_ENTITIES + 1);
            if (module.value("contactDetectProjectiles") == 1 && candidates.size() <= MAX_ENTITIES)
                level.getEntities(EntityTypeTest.forClass(Projectile.class), search, entity -> true, candidates, MAX_ENTITIES + 1);
            if (module.value("contactDetectProjectiles") == 1 && candidates.size() <= MAX_ENTITIES)
                level.getEntities(EntityTypeTest.forClass(dev.sablelife.attack.ModuleShot.class), search, entity -> true, candidates, MAX_ENTITIES + 1);
            if (candidates.size() > MAX_ENTITIES) complete = false;
            int count = 0;
            for (var entity : candidates) {
                if (++count > MAX_ENTITIES || !work.spend()) { complete = false; break; }
                if (entity.isRemoved() || entity.isSpectator() || entity instanceof EntityHitbox || entity instanceof ArmorStand) continue;
                if (projectile(entity)) {
                    if (module.value("contactDetectProjectiles") != 1 || !projectileAllowed(level, core, module, entity)) continue;
                    var box = entity.getBoundingBox();
                    boolean intersects = intersects(volume, box);
                    var previous = new Vec3(entity.xo, entity.yo, entity.zo);
                    if (!intersects && previous.distanceToSqr(entity.position()) <= 16 && previous.distanceToSqr(entity.position()) > 1e-10) {
                        intersects = swept(frame, box, previous.subtract(entity.position()));
                    }
                    if (intersects) projectiles.add(entity.getUUID());
                } else if (entity instanceof LivingEntity living && living.isAlive()) {
                    boolean selected = living instanceof Player player ? module.value("contactDetectPlayers") == 1 && !player.isCreative()
                            : module.value("contactDetectMobs") == 1;
                    if (selected && (module.value("contactTargetsOnly") == 0 || CoreRelations.targetAllowed(level, core.creatureId(), living))
                            && intersects(volume, entity.getBoundingBox())) touching = true;
                }
            }
        }
        return new Result(touching, Set.copyOf(projectiles), complete);
    }

    private static boolean projectile(Entity entity) {
        return entity instanceof Projectile || entity instanceof dev.sablelife.attack.ModuleShot;
    }
    private static boolean projectileAllowed(ServerLevel level, HeartBlockEntity core, VirtualModule module, Entity projectile) {
        var tag = projectile.getPersistentData();
        UUID owner = tag.hasUUID("SableLifeAttackOwner") ? tag.getUUID("SableLifeAttackOwner") : null;
        if (core.creatureId().equals(owner)) return false;
        var shooter = projectile instanceof Projectile p ? p.getOwner() : null;
        if (shooter instanceof EntityHitbox box && box.core() != null) {
            owner = box.core().creatureId();
            if (core.creatureId().equals(owner)) return false;
        }
        if (module.value("contactTargetsOnly") == 0) return true;
        var source = owner == null ? null : LoadedCoreIndex.find(level, owner);
        if (source != null && CoreRelations.allies(core, source)) return false;
        return !(shooter instanceof LivingEntity living) || CoreRelations.targetAllowed(level, core.creatureId(), living);
    }

    private static boolean intersects(AttackVolume volume, AABB box) {
        return volume.intersects(AttackVolume.box(box.getCenter(), new Vec3(box.getXsize(), box.getYsize(), box.getZsize()).scale(.5), new org.joml.Quaterniond()));
    }
    /** Translating projectile AABB against a fixed rotated box; intersect all separating-axis time intervals. */
    private static boolean swept(ContactSensorGeometry.Frame frame, AABB end, Vec3 previousOffset) {
        var world = List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        var local = world.stream().map(axis -> dev.sablelife.attack.ContactBox.rotate(frame.rotation(),axis)).toList();
        var axes = new ArrayList<Vec3>(15); axes.addAll(world); axes.addAll(local);
        for(var a:world) for(var b:local) axes.add(a.cross(b));
        var start = end.getCenter().add(previousOffset).subtract(frame.center());
        var motion = previousOffset.scale(-1); var half = frame.half();
        double from = 0, to = 1;
        for(var axis:axes) {
            if(axis.lengthSqr()<1e-12)continue;
            double extent = Math.abs(axis.dot(local.get(0)))*half.x + Math.abs(axis.dot(local.get(1)))*half.y + Math.abs(axis.dot(local.get(2)))*half.z
                    + (Math.abs(axis.x)*end.getXsize()+Math.abs(axis.y)*end.getYsize()+Math.abs(axis.z)*end.getZsize())/2 + 1e-7;
            double position = axis.dot(start), speed = axis.dot(motion);
            if(Math.abs(speed)<1e-12) { if(Math.abs(position)>extent)return false; continue; }
            double a=(-extent-position)/speed, b=(extent-position)/speed;
            from=Math.max(from,Math.min(a,b));to=Math.min(to,Math.max(a,b));
            if(from>to)return false;
        }
        return true;
    }
    /** -1 means incomplete, 0 clear, 1 actual collision shape contact. */
    private static int blocks(ServerLevel level, AttackVolume volume, SubLevel body, Work work) {
        var bounds = volume.bounds();
        if (body != null) {
            AABB local = null;
            for (int c = 0; c < 8; c++) {
                var p = body.logicalPose().transformPositionInverse(new Vec3((c & 1) == 0 ? bounds.minX : bounds.maxX,
                        (c & 2) == 0 ? bounds.minY : bounds.maxY, (c & 4) == 0 ? bounds.minZ : bounds.maxZ));
                local = local == null ? new AABB(p, p) : local.minmax(new AABB(p, p));
            }
            bounds = local;
            var plot = body.getPlot().getBoundingBox();
            if (plot == null) return 0;
            if (!bounds.intersects(new AABB(plot.minX(), plot.minY(), plot.minZ(), plot.maxX() + 1., plot.maxY() + 1., plot.maxZ() + 1.))) return 0;
            bounds = bounds.intersect(new AABB(plot.minX(), plot.minY(), plot.minZ(), plot.maxX() + 1., plot.maxY() + 1., plot.maxZ() + 1.));
        }
        if (!Double.isFinite(bounds.getSize()) || bounds.getSize() > 64) return -1;
        int x0 = (int)Math.floor(bounds.minX), y0 = (int)Math.floor(bounds.minY), z0 = (int)Math.floor(bounds.minZ);
        int x1 = (int)Math.floor(bounds.maxX), y1 = (int)Math.floor(bounds.maxY), z1 = (int)Math.floor(bounds.maxZ);
        for (var pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (!work.spend()) return -1;
            if (!level.hasChunkAt(pos)) return -1;
            if (body == null && (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight())) continue;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            for (var shape : state.getCollisionShape(level, pos).toAabbs()) {
                if (!work.spend()) return -1;
                var b = shape.move(pos);
                if (body == null) { if (intersects(volume, b)) return 1; }
                else {
                    var pose = body.logicalPose(); var point = b.getCenter(); var center = pose.transformPosition(point);
                    var voxel = new AttackVolume(center,
                            pose.transformPosition(point.add(b.getXsize()/2, 0, 0)).subtract(center),
                            pose.transformPosition(point.add(0, b.getYsize()/2, 0)).subtract(center),
                            pose.transformPosition(point.add(0, 0, b.getZsize()/2)).subtract(center));
                    if (volume.intersects(voxel)) return 1;
                }
            }
        }
        return 0;
    }
    private ContactSensorQuery() {}
}
