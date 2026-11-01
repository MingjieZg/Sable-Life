package dev.sablelife.combat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.sablelife.attack.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.CollisionWorkBudget;
import dev.sablelife.ownership.*;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

/** Indexed broad phase followed by collision shapes of owned, currently connected body voxels. */
public final class BodyAttackVolumes {
    public record Hit(HeartBlockEntity heart,BlockPos block,Vec3 point,double distance) {}
    public static List<Hit> query(ServerLevel level,Vec3 eye,List<AttackVolume> samples) {
        if(samples.isEmpty())return List.of();
        var bounds=samples.getFirst().bounds();for(var volume:samples)bounds=bounds.minmax(volume.bounds());
        var hits=new ArrayList<Hit>();var budget=CollisionWorkBudget.begin(level);
        for(var core:LoadedCoreIndex.query(level,bounds)) {
            if(core.isControlCore()||core.isRemoved())continue;
            var root=Sable.HELPER.getContaining(level,core.getBlockPos());if(root==null)continue;
            Hit closest=null;int visits=0;
            for(var body:SubLevelHelper.getConnectedChain(root)) {
                if(body.isRemoved())continue;
                var plot=body.getPlot().getBoundingBox();if(plot==null)continue;
                var rotation=new Quaterniond(body.logicalPose().orientation());var inverse=new Quaterniond(rotation).conjugate();
                var pivot=body.getPlot().getCenterBlock().getCenter();var worldPivot=body.logicalPose().transformPosition(pivot);
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(int corner=0;corner<8;corner++) {
                    var world=new Vec3((corner&1)==0?bounds.minX:bounds.maxX,(corner&2)==0?bounds.minY:bounds.maxY,(corner&4)==0?bounds.minZ:bounds.maxZ);
                    var local=pivot.add(ContactBox.rotate(inverse,world.subtract(worldPivot)));
                    minX=Math.min(minX,local.x);minY=Math.min(minY,local.y);minZ=Math.min(minZ,local.z);
                    maxX=Math.max(maxX,local.x);maxY=Math.max(maxY,local.y);maxZ=Math.max(maxZ,local.z);
                }
                int x0=(int)Math.max(plot.minX(),Math.floor(minX)),x1=(int)Math.min(plot.maxX(),Math.floor(maxX));
                int y0=(int)Math.max(plot.minY(),Math.floor(minY)),y1=(int)Math.min(plot.maxY(),Math.floor(maxY));
                int z0=(int)Math.max(plot.minZ(),Math.floor(minZ)),z1=(int)Math.min(plot.maxZ(),Math.floor(maxZ));
                scan:for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
                    if(++visits>32768||!budget.spend(1))break scan;
                    var pos=new BlockPos(x,y,z);var ownership=BodyOwnership.get(level);
                    if(!core.creatureId().equals(ownership.owner(pos))||!level.hasChunkAt(pos))continue;
                    var state=level.getBlockState(pos);if(state.isAir())continue;
                    for(var box:state.getCollisionShape(level,pos).toAabbs()) {
                        var center=body.logicalPose().transformPosition(box.getCenter().add(x,y,z));
                        var voxel=AttackVolume.box(center,new Vec3(box.getXsize(),box.getYsize(),box.getZsize()).scale(.5),rotation);
                        boolean touched=false;
                        for(var volume:samples){if(!budget.spend(1))break;if(volume.intersects(voxel)){touched=true;break;}}
                        double distance=center.distanceToSqr(eye);
                        if(touched&&(closest==null||distance<closest.distance)&&SensorGeometry.visible(level,null,eye,center,core.creatureId()))
                            closest=new Hit(core,pos,center,distance);
                    }
                }
                if(visits>32768||CollisionWorkBudget.exhausted(level))break;
            }
            if(closest!=null)hits.add(closest);
            if(hits.size()>=64||CollisionWorkBudget.exhausted(level))break;
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));return hits;
    }
    private BodyAttackVolumes() {}
}
