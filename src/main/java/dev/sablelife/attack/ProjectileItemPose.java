package dev.sablelife.attack;

import dev.sablelife.module.VirtualModule;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cosmetic local model rotation, independent of projectile motion and collision. */
public final class ProjectileItemPose {
    public static Vector3f from(VirtualModule module) {
        return new Vector3f(angle(module.value("projectileRotationX")),angle(module.value("projectileRotationY")),angle(module.value("projectileRotationZ")));
    }
    private static float angle(double value){return Double.isFinite(value)?(float)Math.clamp(value,-180,180):0;}
    public static Vector3f read(CompoundTag tag){return new Vector3f(angle(tag.getFloat("X")),angle(tag.getFloat("Y")),angle(tag.getFloat("Z")));}
    public static CompoundTag save(Vector3f angles){var tag=new CompoundTag();tag.putFloat("X",angles.x);tag.putFloat("Y",angles.y);tag.putFloat("Z",angles.z);return tag;}
    public static Quaternionf local(Vector3f angles){return new Quaternionf().rotationXYZ((float)Math.toRadians(angles.x),(float)Math.toRadians(angles.y),(float)Math.toRadians(angles.z));}
    public static Quaternionf flight(float yaw,float pitch,Vector3f angles){
        return new Quaternionf().rotationY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(-pitch))
                .rotateY((float)Math.toRadians(-90)).rotateZ((float)Math.toRadians(-45)).mul(local(angles));
    }
    private ProjectileItemPose(){}
}
