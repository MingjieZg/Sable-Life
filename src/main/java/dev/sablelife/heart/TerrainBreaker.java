package dev.sablelife.heart;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.module.LinearMovementGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

/** Main-thread, shape-aware contact clearing. Never uses a whole body bound as proof of a hit. */
public final class TerrainBreaker {
 public static final int BREAKS_PER_TICK=128,WORK_PER_TICK=32768;
 private record Pending(BlockState state,long start,long seen){}
 private final Map<BlockPos,Pending> pending=new HashMap<>();private final Map<UUID,Long> cursors=new HashMap<>();
 private long tick=Long.MIN_VALUE;private int work,broken;private boolean waiting;
 private List<LinearMovementGeometry.Body> supportBodies=List.of();
 public boolean brokeThisTick(long now){return tick==now&&broken>0;}
 private static final class WorldBudget {long tick=Long.MIN_VALUE;int remaining;}
 private static final Map<ServerLevel,WorldBudget> budgets=new WeakHashMap<>();
 public void clear(){pending.clear();cursors.clear();supportBodies=List.of();tick=Long.MIN_VALUE;}
 private boolean spend(ServerLevel level){var budget=budgets.computeIfAbsent(level,l->new WorldBudget());if(budget.tick!=level.getGameTime()){budget.tick=level.getGameTime();budget.remaining=131072;}if(work<=0||budget.remaining<=0)return false;work--;budget.remaining--;return true;}
 public void tick(HeartBlockEntity heart){if(!ready(heart)){clear();return;}var bodies=LinearMovementGeometry.capture(heart);for(var body:bodies){var velocity=body.body().latestLinearVelocity;var step=new Vec3(velocity.x,velocity.y,velocity.z).scale(.05);if(step.length()>2)step=step.normalize().scale(2);advance(heart,List.of(body),step);}}
 private boolean ready(HeartBlockEntity heart){return heart.getLevel() instanceof ServerLevel&&!heart.isControlCore()&&heart.operational()&&heart.terrainSettings().enabled();}
 /** True means a known breakable contact is waiting: Travel should not climb that obstacle yet. */
 public boolean advance(HeartBlockEntity heart,List<LinearMovementGeometry.Body> bodies,Vec3 requested){
  if(!ready(heart))return false;var level=(ServerLevel)heart.getLevel();long now=level.getGameTime();
  if(tick!=now){tick=now;work=WORK_PER_TICK;broken=0;waiting=false;supportBodies=LinearMovementGeometry.capture(heart);var kept=new HashSet<UUID>();supportBodies.forEach(b->kept.add(b.body().getUniqueId()));cursors.keySet().retainAll(kept);pending.entrySet().removeIf(e->now-e.getValue().seen>10);}
  if(!Double.isFinite(requested.lengthSqr())||requested.length()>8)return waiting;
  for(var body:bodies){if(body.body().isRemoved()||work<=0)break;var area=body.box().expandTowards(requested).inflate(.09);if(!Double.isFinite(area.getCenter().lengthSqr()+area.getSize()))continue;int minX=(int)Math.floor(area.minX),maxX=(int)Math.floor(area.maxX),minY=Math.max(level.getMinBuildHeight(),(int)Math.floor(area.minY)),maxY=Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(area.maxY)),minZ=(int)Math.floor(area.minZ),maxZ=(int)Math.floor(area.maxZ);
   long nx=(long)maxX-minX+1,ny=(long)maxY-minY+1,nz=(long)maxZ-minZ+1,total=nx*ny*nz;if(nx<=0||ny<=0||nz<=0||nx>4096||ny>4096||nz>4096||total>16777216)continue;
   long cursor=cursors.getOrDefault(body.body().getUniqueId(),0L)%total;int scans=(int)Math.min(total,4096);int axis=Math.abs(requested.x)>=Math.abs(requested.z)?0:2;boolean positive=axis==0?requested.x>=0:requested.z>=0;
   int visited=0;for(int i=0;i<scans&&work>0;i++){visited++;
    long at=(cursor+i)%total;int x,y,z;if(axis==0){long layer=at/(ny*nz);x=positive?maxX-(int)layer:minX+(int)layer;y=minY+(int)(at%ny);z=minZ+(int)(at/ny%nz);}else{long layer=at/(nx*ny);z=positive?maxZ-(int)layer:minZ+(int)layer;x=minX+(int)(at%nx);y=minY+(int)(at/nx%ny);}
    if(!spend(level))break;var pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||Sable.HELPER.getContaining(level,pos)!=null)continue;var state=level.getBlockState(pos);
    if(state.isAir()||state.getDestroySpeed(level,pos)<0||heart.terrainSettings().protects(state))continue;
    var shapes=state.getCollisionShape(level,pos).toAabbs();if(shapes.isEmpty())continue;
    boolean hit=false,support=false;Vec3 point=pos.getCenter();
    for(var shape:shapes){var box=shape.move(pos);var contact=contact(level,body,box,requested);if(contact!=null){hit=true;support|=contact.support;point=contact.point;}}
    if(hit&&heart.terrainSettings().floor()&&!support)for(var other:supportBodies)if(other.body()!=body.body()&&other.box().inflate(.1).intersects(new AABB(pos))){for(var shape:shapes){var floorContact=contact(level,other,shape.move(pos),Vec3.ZERO);if(floorContact!=null&&floorContact.support)support=true;}}
    if(!hit||work<=0||heart.terrainSettings().floor()&&support)continue;
    var old=pending.get(pos);if(old==null||old.state!=state||now-old.seen>10)old=new Pending(state,now,now);
    if(pending.size()<512||pending.containsKey(pos))pending.put(pos,new Pending(state,old.start,now));else continue;
    if(now-old.start<heart.terrainSettings().delay()||broken>=BREAKS_PER_TICK){waiting=true;continue;}
    if(!level.getBlockState(pos).equals(state))continue;
    var linear=body.body().latestLinearVelocity;var angular=motion(body,point).subtract(linear.x,linear.y,linear.z).scale(.05);Vec3 direction=requested.lengthSqr()>1e-8?requested.add(angular):motion(body,point).scale(.05);if(direction.lengthSqr()<1e-8)direction=pos.getCenter().subtract(body.box().getCenter());if(direction.lengthSqr()<1e-8)direction=new Vec3(0,1,0);
    if(level.destroyBlock(pos,heart.terrainSettings().drops())){broken++;pending.remove(pos);if(heart.terrainSettings().debris())debris(level,pos,state,direction);}
   }
   cursors.put(body.body().getUniqueId(),(cursor+visited)%total);
  }return waiting;
 }
 private record Contact(Vec3 point,boolean support){}
 private Contact contact(ServerLevel level,LinearMovementGeometry.Body body,AABB target,Vec3 step){
  var bounds=body.body().getPlot().getBoundingBox();if(bounds==null)return null;var rotation=new Quaterniond(body.rotation());int samples=Math.clamp((int)Math.ceil(step.length()/.25),1,32);boolean hit=false,support=false;Vec3 point=target.getCenter();
  for(int n=0;n<=samples;n++){if(!spend(level))return null;var shift=step.scale(n/(double)samples);double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
   for(int i=0;i<8;i++){var p=new Vec3((i&1)==0?target.minX:target.maxX,(i&2)==0?target.minY:target.maxY,(i&4)==0?target.minZ:target.maxZ).subtract(shift);p=body.body().logicalPose().transformPositionInverse(p);x0=Math.min(x0,p.x);y0=Math.min(y0,p.y);z0=Math.min(z0,p.z);x1=Math.max(x1,p.x);y1=Math.max(y1,p.y);z1=Math.max(z1,p.z);}
   int minX=Math.max(bounds.minX(),(int)Math.floor(x0-.04)),maxX=Math.min(bounds.maxX(),(int)Math.floor(x1+.04)),minY=Math.max(bounds.minY(),(int)Math.floor(y0-.04)),maxY=Math.min(bounds.maxY(),(int)Math.floor(y1+.04)),minZ=Math.max(bounds.minZ(),(int)Math.floor(z0-.04)),maxZ=Math.min(bounds.maxZ(),(int)Math.floor(z1+.04));
   if(minX>maxX||minY>maxY||minZ>maxZ)continue;
   if((long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)>512)continue;
   for(var local:BlockPos.betweenClosed(minX,minY,minZ,maxX,maxY,maxZ)){if(!spend(level))return null;if(!level.hasChunkAt(local))continue;var state=level.getBlockState(local);if(state.isAir())continue;
    for(var shape:state.getCollisionShape(level,local).toAabbs()){var center=body.body().logicalPose().transformPosition(shape.getCenter().add(local.getX(),local.getY(),local.getZ())).add(shift);var half=new Vec3(shape.getXsize()/2,shape.getYsize()/2,shape.getZsize()/2);
     if(!ContactBox.intersects(center,half.add(.025,.025,.025),rotation,target))continue;hit=true;point=center;var extent=extent(rotation,half);var world=new AABB(center.subtract(extent),center.add(extent));support|=n==0&&support(world,target);
    }
   }
  }return hit?new Contact(point,support):null;
 }
 public static boolean support(AABB body,AABB block){double x=Math.min(body.maxX,block.maxX)-Math.max(body.minX,block.minX),y=Math.min(body.maxY,block.maxY)-Math.max(body.minY,block.minY),z=Math.min(body.maxZ,block.maxZ)-Math.max(body.minZ,block.minZ);return block.maxY<=body.getCenter().y+.03&&x>.04&&z>.04&&y<=Math.min(x,z)+.08;}
 private static Vec3 extent(Quaterniond q,Vec3 half){var a=ContactBox.rotate(q,new Vec3(1,0,0));var b=ContactBox.rotate(q,new Vec3(0,1,0));var c=ContactBox.rotate(q,new Vec3(0,0,1));return new Vec3(Math.abs(a.x)*half.x+Math.abs(b.x)*half.y+Math.abs(c.x)*half.z,Math.abs(a.y)*half.x+Math.abs(b.y)*half.y+Math.abs(c.y)*half.z,Math.abs(a.z)*half.x+Math.abs(b.z)*half.y+Math.abs(c.z)*half.z);}
 private static Vec3 motion(LinearMovementGeometry.Body body,Vec3 point){var p=body.body().logicalPose().position();var r=point.subtract(p.x(),p.y(),p.z());var v=body.body().latestLinearVelocity;var a=body.body().latestAngularVelocity;return new Vec3(v.x,v.y,v.z).add(new Vec3(a.x,a.y,a.z).cross(r));}
 private static void debris(ServerLevel level,BlockPos pos,BlockState state,Vec3 direction){double speed=Math.clamp(direction.length(),.15,1);var packet=new dev.sablelife.module.TerrainDebrisPacket(pos.getCenter(),direction.normalize().scale(speed),Block.getId(state));for(var p:level.players())if(p.position().distanceToSqr(pos.getCenter())<80*80&&dev.sablelife.module.VisualBudget.allow(p,8))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,packet);}
}
