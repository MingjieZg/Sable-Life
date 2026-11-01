package dev.sablelife.ownership;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.heart.DeathPresentation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Prepare all dimensions without mutations; remove one fully verified life in one server tick. */
final class WholeLifeDeletion {
 enum Outcome { DONE, WAITING, FALLBACK }
 // A bounded synchronous path, matching the largest supported single-record block count.
 private static final int MAX_RECORDS=1_048_576;
 private record Plan(ServerLevel level,BodyOwnership ownership,List<ServerSubLevel> bodies,List<BlockPos> airRecords,Map<UUID,List<BlockPos>> bodyRecords,List<Vec3> effects) {}

 static Outcome attempt(MinecraftServer server,UUID id,boolean part,DeathPresentation effect){
  long total=0;for(var level:server.getAllLevels())total+=BodyOwnership.get(level).cleanupPositions(id,part).size();
  if(total>MAX_RECORDS)return Outcome.FALLBACK;
  var plans=new ArrayList<Plan>();boolean waiting=false;
  for(var level:server.getAllLevels()){
   var ownership=BodyOwnership.get(level);var airRecords=new ArrayList<BlockPos>();var bodyRecords=new HashMap<UUID,List<BlockPos>>();var bodies=new LinkedHashMap<UUID,ServerSubLevel>();var solid=new HashMap<UUID,Long>();var effects=new ArrayList<Vec3>();
   for(var key:ownership.cleanupPositions(id,part)){
    var pos=BlockPos.of(key);
    if(!level.hasChunkAt(pos)){waiting=true;continue;}
    if(level.getBlockState(pos).isAir()){
     var containing=Sable.HELPER.getContaining(level,pos);if(containing instanceof ServerSubLevel body&&!body.isRemoved()){bodies.put(body.getUniqueId(),body);bodyRecords.computeIfAbsent(body.getUniqueId(),k->new ArrayList<>()).add(pos);}else airRecords.add(pos);continue;
    }
    if(!(Sable.HELPER.getContaining(level,pos) instanceof ServerSubLevel body)||body.isRemoved())return Outcome.FALLBACK;
    bodyRecords.computeIfAbsent(body.getUniqueId(),k->new ArrayList<>()).add(pos);bodies.put(body.getUniqueId(),body);solid.merge(body.getUniqueId(),1L,Long::sum);
   }
   for(var body:bodies.values()){
    var chunks=body.getPlot().getLoadedChunks();if(chunks.isEmpty()){waiting=true;continue;}
    long[] count={0};int emitted=0;
    for(var holder:chunks){
     var chunk=holder.getChunk();if(chunk==null||!level.hasChunk(chunk.getPos().x,chunk.getPos().z)){waiting=true;continue;}
     for(var section:chunk.getSections())if(!section.hasOnlyAir())section.getStates().count((state,n)->{if(!state.isAir())count[0]+=n;});
     // Every non-air voxel must belong to this exact cleanup group, including mixed limb plots.
     for(var key:ownership.chunkRecords(chunk.getPos().toLong())){
      var pos=BlockPos.of(key);if(Sable.HELPER.getContaining(level,pos)!=body||level.getBlockState(pos).isAir())continue;
      if(!ownership.matchesCleanup(pos,id,part))return Outcome.FALLBACK;
      if(emitted++<16)effects.add(body.logicalPose().transformPosition(pos.getCenter()));
     }
    }
    if(count[0]!=solid.getOrDefault(body.getUniqueId(),0L)){
     if(!waiting)return Outcome.FALLBACK;
    }
   }
   plans.add(new Plan(level,ownership,List.copyOf(bodies.values()),airRecords,bodyRecords,effects));
  }
  if(waiting)return Outcome.WAITING;
  BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()+1);
  try{
   for(var plan:plans)if(!plan.bodies.isEmpty())StructureRemovalApi.removeAll(plan.level,plan.bodies);
  }catch(RuntimeException failure){
   com.mojang.logging.LogUtils.getLogger().error("Whole-life cleanup {} failed; remaining ownership will use budgeted cleanup",id,failure);
   return Outcome.FALLBACK;
  }finally{
   BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()-1);
   // Retire only actual removals, even if a native callback failed halfway through the batch.
   for(var plan:plans){
    for(var pos:plan.airRecords)if(plan.ownership.matchesCleanup(pos,id,part)&&plan.level.hasChunkAt(pos)&&plan.level.getBlockState(pos).isAir())plan.ownership.forget(pos);
    var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(plan.level);
    for(var body:plan.bodies)if(body.isRemoved()||container==null||container.getSubLevel(body.getUniqueId())==null)
     for(var pos:plan.bodyRecords.getOrDefault(body.getUniqueId(),List.of()))if(plan.ownership.matchesCleanup(pos,id,part))plan.ownership.forget(pos);
    dev.sablelife.compat.sable.SableBodyProbe.invalidate(plan.level);LoadedCoreIndex.geometryChanged(plan.level);
    plan.ownership.finishCleanup(id,part);
   }
  }
  for(var plan:plans)for(var point:plan.effects){if(effect!=null)effect.emit(plan.level,point);else dev.sablelife.module.ModuleParticles.send(plan.level,net.minecraft.core.particles.ParticleTypes.POOF,point.x,point.y,point.z,6,.35,.35,.35,.04);}
  for(var plan:plans)if(!plan.ownership.cleanupPositions(id,part).isEmpty())return Outcome.FALLBACK;
  return Outcome.DONE;
 }
 private WholeLifeDeletion(){}
}
