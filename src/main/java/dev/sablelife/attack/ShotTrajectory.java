package dev.sablelife.attack;

import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

public final class ShotTrajectory {
    public static final double GRAVITY = .05;
    public record Flight(List<Vec3> points, Vec3 landing) {}
    /** Sample target once per shot, then solve the same discrete gravity used by ModuleShot. */
    public static Vec3 firingVelocity(ServerLevel world, HeartBlockEntity heart, VirtualModule module) {
        var velocities = firingVelocities(world, heart, module);
        return velocities.isEmpty() ? null : velocities.getFirst();
    }
    /** One shell per eligible, reachable target; single mode retains nearest feasible targeting. */
    public static List<Vec3> firingVelocities(ServerLevel world, HeartBlockEntity heart, VirtualModule module) {
        return firingVelocities(world, heart, module, true);
    }

    public static List<Vec3> previewVelocities(ServerLevel world, HeartBlockEntity heart, VirtualModule module) {
        return firingVelocities(world, heart, module, false);
    }

    private static List<Vec3> firingVelocities(ServerLevel world, HeartBlockEntity heart, VirtualModule module, boolean remember) {
        if (module.value("aimMode") == 0) return List.of(velocity(heart, module));
        var start = ModuleCombat.nozzle(heart, module);
        double range = module.value("targetRange"), speed = module.value("speed");
        boolean multiple = module.value("targetMultiple") == 1;
        var bounds = new AABB(start, start).inflate(range);
        var aim = ModuleTargets.attackAim(heart, module);
        java.util.function.ToDoubleFunction<net.minecraft.world.entity.LivingEntity> distance = target ->
                ModuleTargets.attackFeet(world, heart, module, target, aim).distanceToSqr(start);
        java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> eligible =
                e -> ModuleTargets.matches(e, module) && distance.applyAsDouble(e) <= range * range;
        var order = ModuleTargets.comparator(heart, distance);
        java.util.function.Function<net.minecraft.world.entity.LivingEntity, Vec3> velocity = target -> {
            if (!ModuleTargets.attackVisible(world, heart, module, start, target, aim)) return null;
            var delta = ModuleTargets.attackFeet(world, heart, module, target, aim).subtract(start);
            return delta.lengthSqr() <= range * range ? ballisticVelocity(delta.add(0, .05, 0), speed) : null;
        };
        var results = ModuleTargets.attackResults(world, heart, module, bounds, eligible, order, velocity, aim, multiple, remember);
        return results.limit(multiple ? 64 : 1).toList();
    }
    private static Vec3 ballisticVelocity(Vec3 delta, double speed) {
        Vec3 solution = null;
        // Last feasible duration gives the high arc. Integer duration lands exactly at the snapshot.
        for (int ticks = 1; ticks <= 600; ticks++) {
            var velocity = new Vec3(delta.x / ticks, delta.y / ticks + GRAVITY * (ticks - 1) / 2, delta.z / ticks);
            if (velocity.y > 0 && velocity.lengthSqr() <= speed * speed) solution = velocity;
        }
        return solution;
    }
    public static Vec3 velocity(HeartBlockEntity heart, VirtualModule module) {
        var forward = ModuleCombat.direction(heart, module).multiply(1, 0, 1);
        if (forward.lengthSqr() < .001) forward = new Vec3(0, 0, -1);
        double angle = Math.toRadians(module.value("launchAngle")), speed = module.value("speed");
        return forward.normalize().scale(Math.cos(angle) * speed).add(0, Math.sin(angle) * speed, 0);
    }
    public static Flight trace(ServerLevel world, Vec3 start, Vec3 velocity) {
        var points = new ArrayList<Vec3>(); points.add(start);
        for (int i = 0; i < 600; i++) {
            var end = start.add(velocity);
            if (!world.hasChunkAt(BlockPos.containing(end)) || end.y < world.getMinBuildHeight() - 16) break;
            var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (hit.getType() != HitResult.Type.MISS) {
                var landing = SableBodyProbe.toWorld(world, hit.getLocation()); points.add(landing); return new Flight(points, landing);
            }
            points.add(end); start = end; velocity = velocity.add(0, -GRAVITY, 0);
        }
        return new Flight(points, null);
    }
    public static void ring(ServerLevel world, ServerPlayer player, Vec3 point, double radius) {
        ring(world, player, point, radius, 0);
    }
    public static DustParticleOptions warningColor(int color) {
        return new DustParticleOptions(ModuleColors.packedVector(color),1.3F);
    }
    public static void ring(ServerLevel world, ServerPlayer player, Vec3 point, double radius, int color) {
        if (point == null) return;
        var dust = warningColor(color);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48; var p = point.add(Math.cos(a) * radius, .06, Math.sin(a) * radius);
            if (player == null) dev.sablelife.module.ModuleParticles.send(world, dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            else world.sendParticles(player, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
