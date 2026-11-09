package dev.sablelife.combat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.sablelife.heart.*;
import dev.sablelife.module.CollisionWorkBudget;
import dev.sablelife.ownership.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import java.util.*;
/** Exact closest points on owned collision boxes; enclosing bounds only select candidates. */
public final class BodySphereQuery {
 public record Hit(HeartBlockEntity core,BlockPos block,Vec3 point,double distance){}
 public static List<Hit> query(ServerLevel level,Vec3 center,Vec3 eye,double radius){
  if(level==null||center==null||eye==null||!Double.isFinite(center.lengthSqr())||!Double.isFinite(eye.lengthSqr())||!Double.isFinite(radius)||radius<=0||radius>256||center.distanceToSqr(eye)>256*256||Math.abs(center.x)>30000000||Math.abs(center.y)>30000000||Math.abs(center.z)>30000000)return List.of();
  var result=new ArrayList<Hit>();var budget=CollisionWorkBudget.begin(level);
  for(var core:LoadedCoreIndex.query(level,new AABB(center,center).inflate(radius))){
   if(core.isControlCore()||core.isRemoved()||core.health().state()!=HeartState.ALIVE)continue;
   var hit=nearest(level,core,center,eye,radius,budget,null,null);if(hit!=null)result.add(hit);
   if(result.size()>=64||CollisionWorkBudget.exhausted(level))break;
  }
  result.sort(Comparator.comparingDouble(Hit::distance));return List.copyOf(result);
 }
 /** Exact union of at most 16 attack boxes; body enclosing bounds only choose candidates. */
 public static List<Hit> queryVolumes(ServerLevel level,Vec3 eye,java.util.List<dev.sablelife.attack.AttackVolume> volumes){
  return queryVolumes(level,null,eye,volumes);
 }
 public static List<Hit> queryVolumes(ServerLevel level,UUID attacker,Vec3 eye,java.util.List<dev.sablelife.attack.AttackVolume> volumes){
  if(level==null||eye==null||!Double.isFinite(eye.lengthSqr())||volumes==null||volumes.isEmpty()||volumes.size()>16)return List.of();
  AABB bounds=null;for(var v:volumes){if(v==null||!v.valid())return List.of();bounds=bounds==null?v.bounds():bounds.minmax(v.bounds());}
  var center=bounds.getCenter();double radius=Math.sqrt(bounds.getXsize()*bounds.getXsize()+bounds.getYsize()*bounds.getYsize()+bounds.getZsize()*bounds.getZsize())/2;
  if(!Double.isFinite(radius)||radius<=0||radius>256||center.distanceToSqr(eye)>256*256||Math.abs(center.x)>30000000||Math.abs(center.y)>30000000||Math.abs(center.z)>30000000)return List.of();
  var hits=new ArrayList<Hit>();var budget=CollisionWorkBudget.begin(level);
  for(var core:LoadedCoreIndex.query(level,bounds)){
   if(core.isControlCore()||core.isRemoved()||core.health().state()!=HeartState.ALIVE||attacker!=null&&!core.acceptsLifeAttack(attacker))continue;
   var hit=nearest(level,core,center,eye,radius,budget,volumes,null);if(hit!=null)hits.add(hit);
   if(hits.size()>=64||CollisionWorkBudget.exhausted(level))break;
  }hits.sort(Comparator.comparingDouble(Hit::distance));return List.copyOf(hits);
 }
 /** Intersection of a circular area with its native vertical/box limits. */
 public static List<Hit> queryRegion(ServerLevel level,Vec3 center,Vec3 eye,double radius,dev.sablelife.attack.AttackVolume limit){
  if(level==null||center==null||eye==null||limit==null||!limit.valid()||!Double.isFinite(center.lengthSqr())||!Double.isFinite(eye.lengthSqr())||!Double.isFinite(radius)||radius<=0||radius>256||center.distanceToSqr(eye)>256*256||Math.abs(center.x)>30000000||Math.abs(center.y)>30000000||Math.abs(center.z)>30000000)return List.of();
  var hits=new ArrayList<Hit>();var budget=CollisionWorkBudget.begin(level);
  for(var core:LoadedCoreIndex.query(level,limit.bounds())){
   if(core.isControlCore()||core.isRemoved()||core.health().state()!=HeartState.ALIVE)continue;
   var hit=nearest(level,core,center,eye,radius,budget,List.of(limit),null);if(hit!=null)hits.add(hit);
   if(hits.size()>=64||CollisionWorkBudget.exhausted(level))break;
  }hits.sort(Comparator.comparingDouble(Hit::distance));return List.copyOf(hits);
 }
 private record Sweep(Vec3 start,Vec3 end,double radius){}
 /** Swept circular flame front against actual owned voxels; terrain occludes contact. */
 public static List<Hit> querySweep(ServerLevel level,UUID attacker,Vec3 start,Vec3 end,double radius){
  if(level==null||start==null||end==null||!Double.isFinite(start.lengthSqr()+end.lengthSqr()+radius)||radius<=0||radius>4||start.distanceToSqr(end)>16.01)return List.of();
  var center=start.lerp(end,.5);double enclosing=start.distanceTo(end)/2+radius;
  var hits=new ArrayList<Hit>();var budget=CollisionWorkBudget.begin(level);var sweep=new Sweep(start,end,radius);
  for(var core:LoadedCoreIndex.query(level,new AABB(start,end).inflate(radius))){
   if(core.isControlCore()||core.isRemoved()||core.health().state()!=HeartState.ALIVE||!core.acceptsLifeAttack(attacker))continue;
   var hit=nearest(level,core,center,start,enclosing,budget,null,sweep);if(hit!=null)hits.add(hit);
   if(hits.size()>=64||CollisionWorkBudget.exhausted(level))break;
  }return List.copyOf(hits);
 }
 private static Hit nearest(ServerLevel level,HeartBlockEntity core,Vec3 center,Vec3 eye,double radius,CollisionWorkBudget.Budget budget,java.util.List<dev.sablelife.attack.AttackVolume> volumes,Sweep sweep){
  var root=Sable.HELPER.getContaining(level,core.getBlockPos());if(root==null)return null;var ownership=BodyOwnership.get(level);Hit best=null;int remaining=32768;
  for(var body:SubLevelHelper.getConnectedChain(root)){
   if(body.isRemoved())continue;var bounds=body.getPlot().getBoundingBox();if(bounds==null)continue;var pivot=body.getPlot().getCenterBlock().getCenter();var pose=body.logicalPose();var world=pose.transformPosition(pivot);
   var xAxis=pose.transformPosition(pivot.add(1,0,0)).subtract(world);var yAxis=pose.transformPosition(pivot.add(0,1,0)).subtract(world);var zAxis=pose.transformPosition(pivot.add(0,0,1)).subtract(world);
   double sx=xAxis.length(),sy=yAxis.length(),sz=zAxis.length();if(!Double.isFinite(sx+sy+sz)||Math.min(sx,Math.min(sy,sz))<1e-8)continue;
   var delta=center.subtract(world);var local=pivot.add(delta.dot(xAxis)/(sx*sx),delta.dot(yAxis)/(sy*sy),delta.dot(zAxis)/(sz*sz));
   int x0=(int)Math.max(bounds.minX(),Math.floor(local.x-radius/sx)),x1=(int)Math.min(bounds.maxX(),Math.floor(local.x+radius/sx));
   int y0=(int)Math.max(bounds.minY(),Math.floor(local.y-radius/sy)),y1=(int)Math.min(bounds.maxY(),Math.floor(local.y+radius/sy));
   int z0=(int)Math.max(bounds.minZ(),Math.floor(local.z-radius/sz)),z1=(int)Math.min(bounds.maxZ(),Math.floor(local.z+radius/sz));
   for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){
    if(--remaining<0||!budget.spend(1))return best;var pos=new BlockPos(x,y,z);if(!core.creatureId().equals(ownership.owner(pos))||ownership.part(pos)!=null||!level.hasChunkAt(pos))continue;
    var state=level.getBlockState(pos);if(state.isAir())continue;
    for(var box:state.getCollisionShape(level,pos).toAabbs()){
     if(!budget.spend(1))return best;
     if(volumes!=null){
      var voxel=new dev.sablelife.attack.AttackVolume(pose.transformPosition(box.getCenter().add(x,y,z)),xAxis.scale(box.getXsize()/2),yAxis.scale(box.getYsize()/2),zAxis.scale(box.getZsize()/2));
      boolean intersects=false;for(var volume:volumes){if(!budget.spend(1))return best;if(volume.intersects(voxel)){intersects=true;break;}}if(!intersects)continue;
     }
     var localPoint=new Vec3(Math.clamp(local.x,x+box.minX,x+box.maxX),Math.clamp(local.y,y+box.minY,y+box.maxY),Math.clamp(local.z,z+box.minZ,z+box.maxZ));
     if(sweep!=null){
      if(!budget.spend(8))return best;
      var from=sweep.start.subtract(world);var to=sweep.end.subtract(world);
      var localStart=pivot.add(from.dot(xAxis)/(sx*sx),from.dot(yAxis)/(sy*sy),from.dot(zAxis)/(sz*sz));
      var localEnd=pivot.add(to.dot(xAxis)/(sx*sx),to.dot(yAxis)/(sy*sy),to.dot(zAxis)/(sz*sz));
      var contact=SegmentBoxContact.closest(localStart,localEnd,box.move(x,y,z),new Vec3(sx*sx,sy*sy,sz*sz));
      localPoint=contact.point();
      if(pose.transformPosition(localPoint).distanceToSqr(sweep.start.lerp(sweep.end,contact.fraction()))>sweep.radius*sweep.radius+1e-8)continue;
     }
     var point=pose.transformPosition(localPoint);double distance=point.distanceTo(center);if(distance>radius||best!=null&&distance>=best.distance)continue;
     var inside=pose.transformPosition(localPoint.lerp(box.getCenter().add(x,y,z),.001));
     if(!loadedPath(level,eye,inside,budget))continue;
     var ray=level.clip(new ClipContext(eye,inside,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
     if(ray.getType()==HitResult.Type.MISS||ray.getBlockPos().equals(pos)||core.creatureId().equals(ownership.owner(ray.getBlockPos()))){
      best=new Hit(core,pos,point,distance);
     }
    }
   }
  }return best;
 }
 /** Bound the exact chunk path before a native clip, so the bridge never requests unloaded terrain. */
 private static boolean loadedPath(ServerLevel level,Vec3 from,Vec3 to,CollisionWorkBudget.Budget budget){
  int x=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(from.x)),z=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(from.z));
  int endX=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(to.x)),endZ=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(to.z));
  double dx=to.x-from.x,dz=to.z-from.z;int stepX=Double.compare(dx,0),stepZ=Double.compare(dz,0);
  double nextX=stepX==0?Double.POSITIVE_INFINITY:(((stepX>0?x+1:x)*16.-from.x)/dx);
  double nextZ=stepZ==0?Double.POSITIVE_INFINITY:(((stepZ>0?z+1:z)*16.-from.z)/dz);
  double deltaX=stepX==0?Double.POSITIVE_INFINITY:16/Math.abs(dx),deltaZ=stepZ==0?Double.POSITIVE_INFINITY:16/Math.abs(dz);
  for(int visits=0;visits<128;visits++){
   if(!budget.spend(1)||!level.hasChunk(x,z))return false;if(x==endX&&z==endZ)return true;
   if(nextX<nextZ){x+=stepX;nextX+=deltaX;}else if(nextZ<nextX){z+=stepZ;nextZ+=deltaZ;}
   else {if(!budget.spend(2)||!level.hasChunk(x+stepX,z)||!level.hasChunk(x,z+stepZ))return false;x+=stepX;z+=stepZ;nextX+=deltaX;nextZ+=deltaZ;}
  }return false;
 }
 private BodySphereQuery(){}
}
