package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.TerrainBlockShapes;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.Map;
import java.util.WeakHashMap;

/** Optional terrain-only part of melee; entity hits and their cadence remain in ModuleCombat. */
final class MeleeBlockBreaker {
    static final int WORK_PER_CORE = 8192, WORK_PER_WORLD = 65536, BREAKS_PER_CORE = 64, CELLS_PER_MODULE = 1024;
    static final class Cursor { long next; }
    private static final class WorldBudget { long tick = Long.MIN_VALUE; int remaining; }
    private static final Map<ServerLevel, WorldBudget> worlds = new WeakHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int remaining, broken;

    private boolean spend(ServerLevel level) {
        var world = worlds.computeIfAbsent(level, ignored -> new WorldBudget());
        if (world.tick != level.getGameTime()) { world.tick = level.getGameTime(); world.remaining = WORK_PER_WORLD; }
        if (remaining <= 0 || world.remaining <= 0) return false;
        remaining--; world.remaining--; return true;
    }

    void clear() { tick = Long.MIN_VALUE; }

    void sweep(ServerLevel level, HeartBlockEntity core, VirtualModule module, MeleeGeometry.Frame previous, MeleeGeometry.Frame frame,
               Cursor cursor, long energyCost) {
        if (!core.operational() || core.isControlCore()) return;
        if (tick != level.getGameTime()) { tick = level.getGameTime(); remaining = WORK_PER_CORE; broken = 0; }
        if (remaining <= 0 || broken >= BREAKS_PER_CORE) return;
        boolean moving = previous.center().distanceToSqr(frame.center()) > 1e-10 || MeleeGeometry.angular(previous, frame) > 1e-6;
        int steps = moving ? MeleeGeometry.samples(previous, frame) : 0, visited = 0;
        var ownership = BodyOwnership.get(level);
        var protection=module.meleeProtection(core);
        // Visit the current box first, then the actual sampled arc. A single swept AABB wastes
        // its budget on empty corners (especially the vertical sag of a horizontal rotation).
        for (int pass = 0; pass <= steps && visited < CELLS_PER_MODULE && broken < BREAKS_PER_CORE; pass++) {
            var sample = pass == 0 ? new MeleeGeometry.Sample(frame.center(), frame.rotation())
                    : MeleeGeometry.sample(previous, frame, (pass - 1) / (double) steps);
            var bounds = bounds(sample.center(), frame.half(), sample.rotation());
            if (!Double.isFinite(bounds.getSize() + bounds.getCenter().lengthSqr())) continue;
            int x0 = (int) Math.floor(bounds.minX + 1e-7), x1 = (int) Math.floor(bounds.maxX - 1e-7);
            int y0 = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY + 1e-7));
            int y1 = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(bounds.maxY - 1e-7));
            int z0 = (int) Math.floor(bounds.minZ + 1e-7), z1 = (int) Math.floor(bounds.maxZ - 1e-7);
            long nx = (long) x1 - x0 + 1, ny = (long) y1 - y0 + 1, nz = (long) z1 - z0 + 1;
            if (nx <= 0 || ny <= 0 || nz <= 0 || nx > 4096 || ny > 4096 || nz > 4096) continue;
            long total = nx * ny * nz;
            if (total > 16_777_216) continue;
            long start = pass == 0 ? Math.floorMod(cursor.next, total) : 0;
            int scanned = 0;
            for (int i = 0; i < total && visited < CELLS_PER_MODULE && broken < BREAKS_PER_CORE; i++) {
                if (!spend(level)) return;
                long at = (start + i) % total; visited++; scanned++;
                if (pass == 0) cursor.next = (start + scanned) % total;
                var pos = new BlockPos(x0 + (int) (at % nx), y0 + (int) (at / nx % ny), z0 + (int) (at / (nx * ny)));
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || ownership.owner(pos) != null || Sable.HELPER.getContaining(level, pos) != null) continue;
                var state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || protection.protects(state)) continue;
                var shapes = TerrainBlockShapes.contact(level, pos, state).toAabbs();
                boolean hit = false;
                for (var shape : shapes) {
                    if (!spend(level)) return;
                    if (ContactBox.intersects(sample.center(), frame.half(), sample.rotation(), shape.move(pos))) { hit = true; break; }
                }
                if (!hit || !level.getBlockState(pos).equals(state)) continue;
                if (!core.spendAttackTokens(energyCost)) return;
                boolean removed = false;
                try { removed = level.destroyBlock(pos, module.value("meleeDrops")==1); }
                finally { if (!removed) core.refundAttackTokens(energyCost); }
                if (removed) {
                    broken++;
                    if(module.value("meleeDebris")==1){
                        var direction=pos.getCenter().subtract(sample.center());
                        if(direction.lengthSqr()<1e-8)direction=new Vec3(0,1,0);
                        var packet=new TerrainDebrisPacket(pos.getCenter(),direction.normalize().scale(.25),net.minecraft.world.level.block.Block.getId(state));
                        for(var player:level.players())if(player.position().distanceToSqr(pos.getCenter())<80*80&&VisualBudget.allow(player,8))
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
                    }
                }
            }
        }
    }

    private static AABB bounds(Vec3 center, Vec3 half, Quaterniond rotation) {
        var a = ContactBox.rotate(rotation, new Vec3(half.x, 0, 0));
        var b = ContactBox.rotate(rotation, new Vec3(0, half.y, 0));
        var c = ContactBox.rotate(rotation, new Vec3(0, 0, half.z));
        var extent = new Vec3(Math.abs(a.x) + Math.abs(b.x) + Math.abs(c.x),
                Math.abs(a.y) + Math.abs(b.y) + Math.abs(c.y), Math.abs(a.z) + Math.abs(b.z) + Math.abs(c.z));
        return new AABB(center.subtract(extent), center.add(extent));
    }
}
