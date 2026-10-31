package dev.sablelife.module;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The same actual-volume and loaded-terrain guard for player and mob attachments. */
public final class RestraintPath {
    public static boolean safe(LivingEntity entity,Vec3 from,Vec3 to){
        if(!(entity.level() instanceof ServerLevel level)||!Double.isFinite(to.x+to.y+to.z)||from.distanceToSqr(to)>64)return false;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)*4));var box=entity.getBoundingBox();
        for(int i=1;i<=steps;i++){var point=from.lerp(to,i/(double)steps);var moved=box.move(point.subtract(entity.position()));
            if(!level.hasChunksAt(net.minecraft.core.BlockPos.containing(moved.minX,moved.minY,moved.minZ),net.minecraft.core.BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ))||!level.getWorldBorder().isWithinBounds(moved)||!level.noCollision(entity,moved.deflate(.001)))return false;
        }
        return true;
    }
    private RestraintPath(){}
}
