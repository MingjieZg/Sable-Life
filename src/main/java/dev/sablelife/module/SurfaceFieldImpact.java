package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Shared accepted-impact adapter. It never manufactures contacts at maximum range. */
public final class SurfaceFieldImpact {
    public static void projectile(Entity projectile,VirtualModule cast,HitResult hit,Vec3 incoming){
        if(cast==null||cast.impactFieldId()==null||!(projectile.level() instanceof ServerLevel level))return;
        var tag=projectile.getPersistentData();if(!tag.hasUUID("SableLifeAttackOwner"))return;
        var heart=dev.sablelife.ownership.BodyOwnership.get(level).loadedHeart(level,tag.getUUID("SableLifeAttackOwner"));
        if(heart==null)return;
        if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)block(heart,cast,block,incoming);
        else if(hit instanceof EntityHitResult entity)entity(heart,cast,SableBodyProbe.toWorld(level,entity.getLocation()),incoming,entity.getEntity().getUUID());
    }
    public static void block(HeartBlockEntity heart,VirtualModule cast,BlockHitResult hit,Vec3 incoming){
        if(cast.impactFieldId()==null||!(heart.getLevel() instanceof ServerLevel level)||!level.hasChunkAt(hit.getBlockPos()))return;
        var point=SableBodyProbe.toWorld(level,hit.getLocation());
        if(dev.ryanhcode.sable.Sable.HELPER.getContaining(level,hit.getBlockPos())!=null){
            var owner=dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos());
            if(owner!=null&&!owner.equals(heart.creatureId()))entity(heart,cast,point,incoming,owner);
            return;
        }
        heart.modules().surfaceFields.impact(heart,cast,point,Vec3.atLowerCornerOf(hit.getDirection().getNormal()),incoming);
    }
    public static void entity(HeartBlockEntity heart,VirtualModule cast,Vec3 point,Vec3 incoming,java.util.UUID target){
        if(cast.impactFieldId()==null||!(heart.getLevel() instanceof ServerLevel level))return;
        // Start at the contact, not four blocks above it: a low roof is not the target's floor.
        var floor=ModuleGroundStrike.floor(level,heart,point.add(0,-3.9,0),target);
        if(floor!=null)heart.modules().surfaceFields.impact(heart,cast,floor,new Vec3(0,1,0),incoming);
    }
    /** Resolve the face only at the actual stopped endpoint, including beams that ignore own voxels. */
    public static void terrainEnd(HeartBlockEntity heart,VirtualModule cast,Vec3 endpoint,Vec3 incoming){
        if(cast.impactFieldId()==null||incoming.lengthSqr()<1e-8||!(heart.getLevel() instanceof ServerLevel level))return;
        var delta=incoming.normalize().scale(.2);var start=endpoint.subtract(delta);var end=endpoint.add(delta);
        if(!level.hasChunkAt(BlockPos.containing(start))||!level.hasChunkAt(BlockPos.containing(end)))return;
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null));
        if(hit.getType()==HitResult.Type.BLOCK)block(heart,cast,hit,incoming);
    }
    private SurfaceFieldImpact(){}
}
