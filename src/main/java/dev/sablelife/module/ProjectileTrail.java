package dev.sablelife.module;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
/** Sample the swept segment so fast projectiles leave a continuous, bounded trail. */
public final class ProjectileTrail {
    public static void draw(ServerLevel world, Vec3 start, Vec3 end, List<ModuleParticles.Entry> particles) {
        if (particles.isEmpty()) return;
        int steps = Math.clamp((int) Math.ceil(start.distanceTo(end) * 4), 1, 32);
        for (int i = 0; i < steps; i++) {
            var point = start.lerp(end, (i + .5) / steps);
            var particle = particles.get((i + (int) (world.getGameTime() % particles.size())) % particles.size());
            if (particle != null) dev.sablelife.module.ModuleParticles.send(world, particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }
}
