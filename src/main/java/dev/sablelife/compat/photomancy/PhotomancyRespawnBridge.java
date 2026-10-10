package dev.sablelife.compat.photomancy;

import dev.rew1nd.sableschematicapi.blueprint.*;
import dev.rew1nd.sableschematicapi.survival.*;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.ArenaRespawns;
import dev.sablelife.ownership.ArenaRespawnLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.*;

/** Optional API types stay here. A trusted server capture is the only source of respawn templates. */
public final class PhotomancyRespawnBridge {
 private static final ThreadLocal<SableBlueprint> TRUSTED=new ThreadLocal<>();
 public static boolean trusted(SableBlueprint blueprint){return TRUSTED.get()==blueprint;}
 public static CompoundTag capture(HeartBlockEntity heart,Vec3 center){
  var level=(ServerLevel)heart.getLevel();var root=Sable.HELPER.getContaining(level,heart.getBlockPos());if(root==null||root.isRemoved())throw new IllegalArgumentException("unassembled");
  var ids=new LinkedHashSet<UUID>();long visits=0;
  for(var body:SubLevelHelper.getConnectedChain(root)){
   var b=body.getPlot().getBoundingBox();if(body.isRemoved()||b==null)throw new IllegalArgumentException("invalid_structure");
   visits+=((long)b.maxX()-b.minX()+1)*((long)b.maxY()-b.minY()+1)*((long)b.maxZ()-b.minZ()+1);
   ArenaRespawnLimits.check("scan",visits,ArenaRespawnLimits.SCAN_VOXELS);
   ArenaRespawnLimits.check("parts",ids.size()+1,ArenaRespawnLimits.PARTS);
   for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)if(!level.hasChunk(x,z))throw new IllegalArgumentException("unloaded");
   ids.add(body.getUniqueId());
  }
  var blueprint=SableBlueprintExporter.exportSelected(level,center,ids);
  if(blueprint.isEmpty())throw new IllegalArgumentException("invalid_structure");
  ArenaRespawnLimits.check("blocks",blueprint.blockCount(),ArenaRespawnLimits.BLOCKS);
  ArenaRespawnLimits.check("block_entities",blueprint.blockEntityCount(),ArenaRespawnLimits.BLOCK_ENTITIES);
  ArenaRespawnLimits.check("entities",blueprint.entityCount(),ArenaRespawnLimits.ENTITIES);
  int hearts=0;for(var part:blueprint.subLevels())for(var block:part.blocks())if(part.blockPalette().get(block.paletteId()).is(SableLife.HEART.get()))hearts++;
  if(hearts!=1)throw new IllegalArgumentException("one_heart");
  var tag=blueprint.save();ArenaRespawnLimits.check("template",tag.sizeInBytes(),ArenaRespawnLimits.TEMPLATE_BYTES);return tag;
 }
 public static ArenaRespawns.Build create(CompoundTag template,CompoundTag saved,Vec3 center){return new Build(template,saved,center);}
 private static final class Build implements ArenaRespawns.Build {
  final SableBlueprint blueprint;final BlueprintBuildProgress progress;final BlueprintPlacementPlan plan;
  Build(CompoundTag template,CompoundTag saved,Vec3 center){blueprint=SableBlueprint.load(template);progress=saved.isEmpty()?new BlueprintBuildProgress():BlueprintBuildProgress.load(saved);plan=BlueprintPlacementPlan.legacy(blueprint,center);}
  @Override public AABB bounds(){var b=blueprint.hasCanonicalBounds()?blueprint.canonicalBounds():blueprint.rootBounds();return new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()).move(plan.originVec3());}
  @Override public String step(ServerLevel level){
   var previous=TRUSTED.get();TRUSTED.set(blueprint);
   stabilize(level);
   try{var status=SableBlueprintIncrementalPlacer.step(level,blueprint,plan,progress,new BlueprintBuildTickBudget(128,16,8,2000000),BlueprintBuildMaterialBudget.UNLIMITED).status().name();stabilize(level);return status;}
   finally{if(previous==null)TRUSTED.remove();else TRUSTED.set(previous);}
  }
  private void stabilize(ServerLevel level){
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);if(container==null||physics==null)return;
   for(var part:blueprint.subLevels()){var placed=progress.placedSubLevel(part.id());if(placed==null)continue;if(container.getSubLevel(placed.placedUuid()) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body){var pose=plan.pose(part);physics.getPipeline().resetVelocity(body);var pivot=body.logicalPose().rotationPoint();var origin=placed.blockOrigin();var point=pose.transformPosition(new Vec3(pivot.x()-origin.getX(),pivot.y()-origin.getY(),pivot.z()-origin.getZ()));physics.getPipeline().teleport(body,new org.joml.Vector3d(point.x,point.y,point.z),new org.joml.Quaterniond(pose.orientation()));}}
  }
  @Override public void captureOwnership(HeartBlockEntity heart){
   var level=(ServerLevel)heart.getLevel();var ownership=dev.sablelife.ownership.BodyOwnership.get(level);
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
   for(var part:blueprint.subLevels()){
    var placed=progress.placedSubLevel(part.id());
    if(placed==null||container==null||container.getSubLevel(placed.placedUuid())==null||container.getSubLevel(placed.placedUuid()).isRemoved())throw new IllegalStateException("Completed respawn is missing a placed body");
    for(var block:part.blocks()){
     var pos=placed.blockOrigin().offset(block.localPos());var body=Sable.HELPER.getContaining(level,pos);
     if(body==null||!body.getUniqueId().equals(placed.placedUuid()))throw new IllegalStateException("Respawn block is outside its allocated body");
     if(level.getBlockState(pos).isAir())continue;
     var previous=ownership.owner(pos);if(previous!=null&&!previous.equals(heart.creatureId()))throw new IllegalStateException("Respawn block belongs to another creature");
     ownership.bind(pos,heart.creatureId());
    }
   }
  }
  @Override public Set<UUID> bodies(){var ids=new HashSet<UUID>();for(var p:progress.placedSubLevels().values())ids.add(p.placedUuid());return ids;}
  @Override public void restoreApprovals(HeartBlockEntity heart){
   for(var part:blueprint.subLevels())for(var raw:part.blockEntities())for(var item:raw.getCompound("VirtualModules").getList("Modules",10)){
    var tag=(CompoundTag)item;if(tag.getString("Type").equals("command")&&tag.hasUUID("Id")&&!tag.getBoolean("CommandApprovalRequired")){var id=module(tag.getUUID("Id"));var m=id==null?null:heart.modules().find(id);if(m!=null&&m.type().equals("command"))m.approveCommands();}
   }
  }
  @Override public CompoundTag save(){return progress.save();}
  @Override public float fraction(){int done=0;for(int i=0;i<Math.min(progress.currentSubLevelIndex(),blueprint.subLevels().size());i++)done+=blueprint.subLevels().get(i).blocks().size();done+=progress.currentBlockIndex();return Math.min(.99F,done/(float)Math.max(1,blueprint.blockCount()));}
  @Override public HeartBlockEntity heart(ServerLevel level){
   for(var part:blueprint.subLevels()){
    var placed=progress.placedSubLevel(part.id());if(placed==null)continue;
    for(var block:part.blocks())if(part.blockPalette().get(block.paletteId()).is(SableLife.HEART.get())){
     var pos=placed.blockOrigin().offset(block.localPos());if(level.getBlockEntity(pos) instanceof HeartBlockEntity h)return h;
    }
   }return null;
  }
  @Override public UUID module(UUID original){return progress.allocatedUuidMap().get(original);}
 }
 private PhotomancyRespawnBridge(){}
}
