package dev.sablelife.sense;

import dev.sablelife.attack.ContactBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import dev.sablelife.compat.sable.SableBodyProbe;

public final class SensorGeometry {
    /** Leave the complete rotated voxel, including diagonal rays, before checking occlusion. */
    public static Vec3 sightStart(Vec3 center, Vec3 delta, Quaterniond orientation) {
        var ray = delta.normalize();
        var local = ContactBox.rotate(new Quaterniond(orientation).conjugate(), ray);
        double largest = Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z)));
        return largest < 1e-8 ? center : center.add(ray.scale(Math.min(delta.length(), .501 / largest)));
    }
    /** Continue along the same ray beyond a voxel, instead of recasting from its center. */
    public static Vec3 leaveVoxel(Vec3 hit, Vec3 center, Vec3 ray, Quaterniond orientation) {
        var inverse = new Quaterniond(orientation).conjugate();
        var point = ContactBox.rotate(inverse, hit.subtract(center)); var direction = ContactBox.rotate(inverse, ray);
        double distance = Double.POSITIVE_INFINITY;
        double[] p = {point.x, point.y, point.z}, d = {direction.x, direction.y, direction.z};
        for (int axis = 0; axis < 3; axis++) if (Math.abs(d[axis]) > 1e-8) {
            double exit = (Math.copySign(.5, d[axis]) - p[axis]) / d[axis];
            if (exit >= -.001) distance = Math.min(distance, Math.max(0, exit));
        }
        return Double.isFinite(distance) ? hit.add(ray.scale(distance + .01)) : hit.add(ray.scale(.01));
    }
    /** Ignore only this creature's captured body; terrain and other bodies still block sight. */
    public static boolean visible(ServerLevel level, java.util.UUID creature, Vec3 start, Vec3 point) {
        return visible(level,creature,start,point,null);
    }
    public static boolean visible(ServerLevel level, java.util.UUID creature, Vec3 start, Vec3 point, java.util.UUID target) {
        return visible(level,creature,start,point,target,java.util.Set.of());
    }
    /** A non-living control core has no captured ownership; its live connected bodies can still be ignored. */
    public static boolean visible(ServerLevel level, java.util.UUID creature, Vec3 start, Vec3 point, java.util.UUID target, java.util.Set<java.util.UUID> ownBodies) {
        var ray = point.subtract(start).normalize();
        for (int step = 0; step < 128; step++) {
            if (point.subtract(start).dot(ray) <= .001) return true;
            var hit = level.clip(new ClipContext(start, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (hit.getType() == HitResult.Type.MISS) return true;
            var hitPoint = SableBodyProbe.toWorld(level, hit.getLocation());
            if (hitPoint.distanceToSqr(point) < .0001 || target != null && target.equals(dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos()))) return true;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, hit.getBlockPos());
            if ((creature == null || !creature.equals(dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos())))
                    && (body==null||!ownBodies.contains(body.getUniqueId()))) return false;
            var rotation = body == null ? new Quaterniond() : new Quaterniond(body.logicalPose().orientation());
            var next = leaveVoxel(hitPoint, SableBodyProbe.toWorld(level, hit.getBlockPos().getCenter()), ray, rotation);
            if (next.subtract(start).dot(ray) < .001) return false;
            start = next;
        }
        return false;
    }
    /** Beam collision endpoint, skipping only the emitting creature's captured voxels. */
    public static Vec3 beamEnd(ServerLevel level, java.util.UUID creature, Vec3 start, Vec3 end) {
        var ray = end.subtract(start).normalize();
        for (int step = 0; step < 128; step++) {
            if (end.subtract(start).dot(ray) <= .001) return end;
            var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (hit.getType() == HitResult.Type.MISS) return end;
            var point = SableBodyProbe.toWorld(level, hit.getLocation());
            if (creature == null || !creature.equals(dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos()))) return point;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, hit.getBlockPos());
            var rotation = body == null ? new Quaterniond() : new Quaterniond(body.logicalPose().orientation());
            var next = leaveVoxel(point, SableBodyProbe.toWorld(level, hit.getBlockPos().getCenter()), ray, rotation);
            if (next.subtract(start).dot(ray) < .001) return point;
            start = next;
        }
        return start; // Fail closed rather than firing through an unresolvable collider.
    }
    private SensorGeometry() {}
}
