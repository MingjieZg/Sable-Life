package dev.sablelife.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.ownership.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Native removal kicks untagged entities into world space; owned hanging decorations must be discarded first. */
public final class BodyDecorationCleanup {
 private static boolean dyingSupport(ServerLevel level,BlockPos support){
  if(BodyProtection.CLEANING.get()==0)return false;
  var data=BodyOwnership.get(level);var owner=data.owner(support);if(owner==null)return false;
  var life=LifeLifecycle.get(level.getServer());var part=data.part(support);
  return life.isDying(owner)||part!=null&&life.isDying(part);
 }
 private static BlockPos support(HangingEntity entity){return entity.getPos().relative(entity.getDirection().getOpposite());}
 public static void discardForBody(ServerLevel level,ServerSubLevel body){
  if(BodyProtection.CLEANING.get()==0)return;
  var seen=new HashSet<UUID>();var bounds=body.getPlot().getBoundingBox();if(bounds==null)return;
  for(var holder:body.getPlot().getLoadedChunks()){
   var chunk=holder.getChunk();var pos=chunk.getPos();
   var area=new AABB(pos.getMinBlockX()-16,bounds.minY()-16,pos.getMinBlockZ()-16,pos.getMaxBlockX()+17,bounds.maxY()+17,pos.getMaxBlockZ()+17);
   for(var entity:level.getEntitiesOfClass(HangingEntity.class,area)){
    var support=support(entity);
    if(seen.add(entity.getUUID())&&Sable.HELPER.getContaining(level,support)==body&&dyingSupport(level,support))entity.discard();
   }
  }
 }
 public static void discardForBlock(ServerLevel level,BlockPos pos){
  if(!dyingSupport(level,pos))return;
  for(var entity:level.getEntitiesOfClass(HangingEntity.class,new AABB(pos).inflate(16)))if(support(entity).equals(pos))entity.discard();
 }
 private BodyDecorationCleanup(){}
}
