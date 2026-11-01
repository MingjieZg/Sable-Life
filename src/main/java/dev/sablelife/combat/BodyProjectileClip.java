package dev.sablelife.combat;
import dev.ryanhcode.sable.Sable;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Optional mods' custom terrain traces do not visit Sable plots. Merge only an earlier real body voxel. */
public final class BodyProjectileClip {
    public static BlockHitResult merge(Level level,ClipContext context,BlockHitResult original){
        var start=SableBodyProbe.toWorld(level,context.getFrom());var end=SableBodyProbe.toWorld(level,context.getTo());var cursor=start;var ray=end.subtract(start).normalize();
        double stop=original!=null&&original.getType()==HitResult.Type.BLOCK?SableBodyProbe.toWorld(level,original.getLocation()).distanceToSqr(start):Double.POSITIVE_INFINITY;
        for(int step=0;step<128;step++){
            var hit=level.clip(step==0?context:new ClipContext(cursor,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
            if(hit.getType()!=HitResult.Type.BLOCK)return original;
            var point=SableBodyProbe.toWorld(level,hit.getLocation());if(point.distanceToSqr(start)>=stop-1e-8)return original;
            if(Sable.HELPER.getContaining(level,hit.getBlockPos())!=null)
                // TaCZ uses the point for distance, entity sorting, particles and explosions; block identity stays in the plot.
                return new BlockHitResult(point,hit.getDirection(),hit.getBlockPos(),hit.isInside());
            // Any earlier terrain collider absent from the mod's hit was deliberately ignored by that mod.
            var next=dev.sablelife.sense.SensorGeometry.leaveVoxel(point,hit.getBlockPos().getCenter(),ray,new org.joml.Quaterniond());
            if(next.subtract(cursor).dot(ray)<.001||end.subtract(next).dot(ray)<=0)return original;cursor=next;
        }
        return original;
    }
    private BodyProjectileClip(){}
}
