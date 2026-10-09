package dev.sablelife.attack;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Separating-axis test for a rotated body voxel against a living entity's axis-aligned hitbox. */
public final class ContactBox {
    private ContactBox() {}
    public static Vec3 rotate(Quaterniond rotation, Vec3 v) {
        var result = rotation.transform(new Vector3d(v.x, v.y, v.z));
        return new Vec3(result.x, result.y, result.z);
    }
    public static boolean intersects(Vec3 center, Vec3 half, Quaterniond rotation, AABB target) {
        Vec3[] axes = {rotate(rotation, new Vec3(1, 0, 0)), rotate(rotation, new Vec3(0, 1, 0)), rotate(rotation, new Vec3(0, 0, 1))};
        Vec3[] world = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        Vec3 delta = target.getCenter().subtract(center);
        Vec3 extent = new Vec3(target.getXsize(), target.getYsize(), target.getZsize()).scale(0.5);
        for (var axis : axes) if (separated(axis, delta, axes, half, extent)) return false;
        for (var axis : world) if (separated(axis, delta, axes, half, extent)) return false;
        for (var a : axes) for (var b : world) if (separated(a.cross(b), delta, axes, half, extent)) return false;
        return true;
    }
    private static boolean separated(Vec3 axis, Vec3 delta, Vec3[] axes, Vec3 half, Vec3 target) {
        if (axis.lengthSqr() < 1e-12) return false;
        double bodyProjection = Math.abs(axis.dot(axes[0])) * half.x + Math.abs(axis.dot(axes[1])) * half.y + Math.abs(axis.dot(axes[2])) * half.z;
        double targetProjection = Math.abs(axis.x) * target.x + Math.abs(axis.y) * target.y + Math.abs(axis.z) * target.z;
        return Math.abs(axis.dot(delta)) > bodyProjection + targetProjection + 1e-7;
    }
}
