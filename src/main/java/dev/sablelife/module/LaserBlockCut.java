package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.*;
import dev.sablelife.sense.SensorGeometry;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Centerline removal; each beam has its own progress, and all beams share a bounded world budget. */
public final class LaserBlockCut {
    public static final class Front {private boolean started;private double distance;private long previous;}
    private static final class Budget {long tick=Long.MIN_VALUE;int left;}
    private static final Map<ServerLevel,Budget> budgets=new WeakHashMap<>();
    private static Vec3 loadedEnd(ServerLevel level,Vec3 start,Vec3 end){
        var direction=end.subtract(start).normalize();
        return BlockGetter.traverseBlocks(start,end,(Void)null,(ignored,pos)->{
            if(level.hasChunkAt(pos)&&!level.isOutsideBuildHeight(pos))return null;
            var boundary=new AABB(pos).clip(start,end).orElse(start);
            return boundary.subtract(start).dot(direction)<.001?start:boundary.subtract(direction.scale(.001));
        },ignored->end);
    }
    private static Vec3 firstObstacle(ServerLevel level,HeartBlockEntity core,Set<UUID> ownBodies,Vec3 start,Vec3 end){
        var ray=end.subtract(start).normalize();var cursor=start;
        for(int i=0;i<512;i++){
            if(end.subtract(cursor).dot(ray)<.00001)return end;
            var hit=level.clip(new ClipContext(cursor,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));if(hit.getType()==HitResult.Type.MISS)return end;
            var point=SableBodyProbe.toWorld(level,hit.getLocation());var body=Sable.HELPER.getContaining(level,hit.getBlockPos());
            if(!core.creatureId().equals(BodyOwnership.get(level).owner(hit.getBlockPos()))&&(body==null||!ownBodies.contains(body.getUniqueId())))return point;
            var next=SensorGeometry.leaveVoxel(point,SableBodyProbe.toWorld(level,hit.getBlockPos().getCenter()),ray,body==null?new org.joml.Quaterniond():new org.joml.Quaterniond(body.logicalPose().orientation()));if(next.subtract(cursor).dot(ray)<.00001)return point;cursor=next;
        }return cursor;
    }
    public static Vec3 cut(ServerLevel level,HeartBlockEntity core,VirtualModule m,Vec3 start,Vec3 desired,Front front,long now){
        var end=loadedEnd(level,start,desired);var ray=end.subtract(start);double length=ray.length();if(length<.00001)return end;ray=ray.scale(1/length);
        var ownBodies=new HashSet<>(SableBodyProbe.inspect(level,core.getBlockPos()).connectedBodies());
        if(m.value("laserBreakMode")==1){
            if(!front.started){var first=firstObstacle(level,core,ownBodies,start,end);if(first.distanceTo(end)<.001)return end;front.started=true;front.distance=Math.max(0,first.subtract(start).dot(ray))+.0001;front.previous=now;}
            else {front.distance+=Math.max(0,now-front.previous)*m.value("laserBreakSpeed")/20;front.previous=now;}
            end=start.add(ray.scale(Math.min(length,front.distance)));
        }
        var budget=budgets.computeIfAbsent(level,l->new Budget());if(budget.tick!=now){budget.tick=now;budget.left=1024;}
        var cursor=start;
        for(int attempt=0;attempt<512;attempt++){
            if(end.subtract(cursor).dot(ray)<.00001)return end;
            var hit=level.clip(new ClipContext(cursor,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));if(hit.getType()==HitResult.Type.MISS)return end;
            var point=SableBodyProbe.toWorld(level,hit.getLocation());var pos=hit.getBlockPos();if(!level.hasChunkAt(pos))return point;
            var body=Sable.HELPER.getContaining(level,pos);
            if(core.creatureId().equals(BodyOwnership.get(level).owner(pos))||body!=null&&ownBodies.contains(body.getUniqueId())){
                var rotation=body==null?new org.joml.Quaterniond():new org.joml.Quaterniond(body.logicalPose().orientation());var next=SensorGeometry.leaveVoxel(point,SableBodyProbe.toWorld(level,pos.getCenter()),ray,rotation);if(next.subtract(cursor).dot(ray)<.00001)return point;cursor=next;continue;
            }
            if(level.getBlockState(pos).getDestroySpeed(level,pos)<0||BodyProtection.locked(level,pos)||SableBodyProbe.findConnectedHearts(level,pos).stream().anyMatch(target->!target.acceptsLifeAttack(core.creatureId())))return point;
            if(budget.left<=0)return point;budget.left--;
            if(!level.destroyBlock(pos,false))return point;
            // Recast from the same point: destruction callbacks can leave another obstruction here.
            cursor=point.subtract(ray.scale(.0001));
        }
        return SensorGeometry.beamEnd(level,core.creatureId(),cursor,end);
    }
    private LaserBlockCut(){}
}
