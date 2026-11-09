package dev.sablelife.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Set;

/** Receiver-volume geometry for existing mob AI; attack reach and cooldown stay native. */
public final class HitboxMobGeometry {
    public static Vec3 closest(EntityHitbox receiver, Vec3 point) {
        var box=receiver.getBoundingBox();
        return new Vec3(Math.clamp(point.x,box.minX,box.maxX),Math.clamp(point.y,box.minY,box.maxY),Math.clamp(point.z,box.minZ,box.maxZ));
    }

    public static boolean visible(Mob observer, EntityHitbox receiver) {
        if(observer.level()!=receiver.level())return false;
        var eye=observer.getEyePosition();var point=closest(receiver,eye);
        return eye.distanceToSqr(point)<=128*128
                &&observer.level().clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer)).getType()==HitResult.Type.MISS;
    }

    public static Set<BlockPos> pathTargets(Mob mob, Set<BlockPos> targets) {
        if(!(mob.getTarget() instanceof EntityHitbox receiver)||targets.size()!=1)return targets;
        var target=targets.iterator().next();var center=receiver.blockPosition();
        // Ground navigation may already have moved the target Y down to its floor.
        // Only redirect a chase of this receiver, never unrelated waypoints.
        if(target.getX()!=center.getX()||target.getZ()!=center.getZ())return targets;
        return Set.of(BlockPos.containing(closest(receiver,mob.position())));
    }

    private HitboxMobGeometry() {}
}
