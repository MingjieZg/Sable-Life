package dev.sablelife.module;

import dev.sablelife.attack.AttackVolume;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Planar fan with an expanding vertical half-thickness and a circular outer edge. */
public record FlameFanGeometry(Vec3 origin, Vec3 forward, Vec3 side, Vec3 up,
                               double range, double endRadius, double halfAngle) {
    private static final double NOZZLE_RADIUS = .12;

    public static FlameFanGeometry create(Vec3 origin, Vec3 direction, double range, double endRadius, double degrees) {
        var forward = direction.normalize();
        var side = forward.cross(Math.abs(forward.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        return new FlameFanGeometry(origin, forward, side, side.cross(forward).normalize(), range, endRadius,
                Math.toRadians(degrees) / 2);
    }

    public static double angle(VirtualModule module) {
        return module.value("flameShape") == 1 ? module.value("flameFanAngle") : 0;
    }

    public Vec3 ray(double angle) { return forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle))); }

    public double radius(double distance) {
        return NOZZLE_RADIUS + (endRadius - NOZZLE_RADIUS) * Math.clamp(distance / range, 0, 1);
    }

    public Vec3 point(double distance, double angle, double heightFraction) {
        return origin.add(ray(angle).scale(distance)).add(up.scale(radius(distance) * heightFraction));
    }

    /** Narrow oriented strips approximate the swept arc for the shared block and entity collision queries. */
    public List<AttackVolume> sweep(double from, double to) {
        int strips = Math.clamp((int) Math.ceil(halfAngle * 2 / Math.toRadians(12)), 1, 15);
        double halfStrip = halfAngle / strips;
        double start = from * Math.cos(halfStrip);
        double middle = (start + to) / 2;
        double radialHalf = (to - start) / 2;
        double lateralHalf = to * Math.sin(halfStrip);
        var boxes = new ArrayList<AttackVolume>(strips);
        for (int i = 0; i < strips; i++) {
            double angle = -halfAngle + (2 * i + 1) * halfStrip;
            var ray = ray(angle);
            var tangent = side.scale(Math.cos(angle)).subtract(forward.scale(Math.sin(angle)));
            boxes.add(new AttackVolume(origin.add(ray.scale(middle)), ray.scale(radialHalf),
                    tangent.scale(lateralHalf), up.scale(radius(to))));
        }
        return boxes;
    }
}
