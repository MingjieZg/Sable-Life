package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import java.util.*;
public final class ArenaRespawnScenarios {
 private record Setup(HeartBlockEntity core,VirtualModule arena,net.neoforged.neoforge.common.util.FakePlayer actor,UUID mark,UUID body,String name){}
 private static Setup setup(GameTestHelper h,boolean large){return setup(h,large,false);}
 private static Setup setup(GameTestHelper h,boolean large,boolean sparse){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));var positions=new ArrayList<BlockPos>();int length=sparse?100:large?260:3;
  // Keep physical spawn dimensions bounded while requiring multiple incremental steps.
  for(int i=0;i<length;i++){var b=sparse?(i<34?p.offset(i,0,0):i<67?p.offset(33,i-33,0):p.offset(33,33,i-66)):p.offset(i%16,0,i/16);positions.add(b);l.setBlockAndUpdate(b,i==0?SableLife.HEART.get().defaultBlockState():i==1?Blocks.CHEST.defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
  ((ChestBlockEntity)l.getBlockEntity(p.east())).setItem(0,new ItemStack(Items.DIAMOND,7));
  var body=SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+(sparse?33:large?15:2),p.getY()+(sparse?33:0),p.getZ()+(sparse?33:large?16:0)));
  var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(100,0);var arena=c.modules().add("arena");arena.arenaCenter(ModuleArena.corePosition(c),l.dimension().location().toString());arena.edit("arenaRespawnEnabled",1,"");arena.edit("arenaRespawnDelay",20,"");
  var mark=c.modules().add("melee");mark.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(2),Direction.EAST)));mark.edit("damage",17,"");String caseName="R"+c.creatureId();mark.edit("name",0,caseName);
  // Vanilla FakePlayer deliberately denies command-block permissions; use an explicit admin test role.
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArenaAdmin")){
   @Override public boolean canUseGameMasterBlocks(){return isCreative();}
  };actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);actor.setPos(ModuleArena.corePosition(c).add(0,-actor.getEyeHeight(),2));
  return new Setup(c,arena,actor,mark.id(),body.getUniqueId(),caseName);
 }
 public static void cycle(GameTestHelper h,boolean resume){var s=setup(h,resume);final UUID original=s.core.creatureId();{
  h.assertTrue(ArenaRespawns.available(),"Photomancy capability absent in respawn suite");h.assertTrue(s.actor.canUseGameMasterBlocks(),"Test actor lacks real creative OP permission");dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(s.core,s.arena.arenaCenter());h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Native structure capture failed");var records=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10);var own=records.stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(original)).findFirst().orElseThrow();if(resume)h.assertTrue(dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(own.getCompound("Template")).blockCount()>=260,"Fixture did not capture the whole large structure");s.core.tryActivate(s.actor);s.core.forceDeath();
  var restarted=new boolean[]{false};var checked=new boolean[]{false};
  h.onEachTick(()->{if(checked[0])return;var data=ArenaRespawns.get(h.getLevel().getServer());var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());
   if(resume&&!restarted[0])for(var value:saved.getList("Entries",10)){var row=(CompoundTag)value;if(row.getString("Phase").equals("building")&&!row.getList("NewBodies",10).isEmpty()){
    var replacement=ArenaRespawns.load(saved.copy(),h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_arena_respawns",replacement);restarted[0]=true;break;
   }}
   var candidates=LoadedCoreIndex.all(h.getLevel()).stream().filter(c->!c.creatureId().equals(original)&&c.modules().all().stream().anyMatch(m->m.type().equals("melee")&&m.name().equals(s.name))).toList();
   var alive=candidates.stream().filter(c->c.health().state()==HeartState.ALIVE).toList();if(alive.isEmpty())return;
   h.assertTrue(alive.size()==1,"Respawn duplicated active cores");var c=alive.getFirst();h.assertTrue(c.health().current()==100,"Respawn did not restore full health");h.assertTrue(ModuleArena.corePosition(c).distanceTo(s.arena.arenaCenter())<1,"Respawn world position changed: actual="+ModuleArena.corePosition(c)+" expected="+s.arena.arenaCenter());
   var melee=c.modules().all().stream().filter(m->m.type().equals("melee")).findFirst().orElseThrow();h.assertTrue(!melee.id().equals(s.mark)&&!melee.anchors().getFirst().bodyId().equals(s.body)&&melee.anchors().getFirst().status(c).equals("valid"),"Module/mark UUIDs did not migrate");
   h.assertTrue(h.getLevel().getBlockEntity(c.getBlockPos().east()) instanceof ChestBlockEntity chest&&chest.getItem(0).getCount()==7&&chest.getItem(0).is(Items.DIAMOND),"Full captured inventory did not restore");
   h.assertTrue(!resume||restarted[0],"Save/reload fixture did not interrupt actual partial placement");checked[0]=true;
   h.runAfterDelay(15,()->{h.assertTrue(LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v.modules().all().stream().anyMatch(m->m.type().equals("melee")&&m.name().equals(s.name))&&v.health().state()==HeartState.ALIVE).count()==1,"Finished job repeated respawn");ArenaRespawns.clear(s.actor,c,c.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow());h.succeed();});
  });
 }}
 public static void permissions(GameTestHelper h){var s=setup(h,false);var noOp=TestActors.creative(h.getLevel());noOp.setPos(s.actor.position());h.assertTrue(!ArenaRespawns.record(noOp,s.core,s.arena),"Non-operator recorded a full respawn template");s.actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!ArenaRespawns.record(s.actor,s.core,s.arena),"Survival operator recorded a template");s.actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(!ArenaRespawns.record(s.actor,s.core,s.core.modules().all().stream().filter(m->m.type().equals("melee")).findFirst().orElseThrow()),"Wrong module type recorded");h.succeed();}
 public static void unloaded(GameTestHelper h){var s=setup(h,false);final UUID original=s.core.creatureId();{h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();var data=ArenaRespawns.get(h.getLevel().getServer());var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(original)).findFirst().orElseThrow();row.putDouble("X",1000000);row.putDouble("Z",1000000);row.putInt("Remaining",20);var restored=ArenaRespawns.load(saved,h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_arena_respawns",restored);
  h.runAfterDelay(30,()->{var current=restored.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(original)).findFirst().orElseThrow();h.assertTrue(current.getInt("Remaining")==20&&!h.getLevel().hasChunk(1000000>>4,1000000>>4),"Unloaded countdown advanced or loaded terrain");s.actor.setPos(1000000,40,1000000); // cleanup through common cancellation command
   h.getLevel().getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");h.succeed();});
 }}
 public static void repeats(GameTestHelper h){var s=setup(h,false);final UUID original=s.core.creatureId();h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();var generation=new UUID[]{original};var cycles=new int[]{0};var finished=new boolean[]{false};h.onEachTick(()->{if(finished[0])return;var found=LoadedCoreIndex.all(h.getLevel()).stream().filter(c->c.health().state()==HeartState.ALIVE&&c.modules().all().stream().anyMatch(m->m.name().equals(s.name))).toList();if(found.isEmpty())return;h.assertTrue(found.size()==1,"Several alive generations exist");var core=found.getFirst();if(core.creatureId().equals(generation[0]))return;generation[0]=core.creatureId();cycles[0]++;if(cycles[0]<2){core.forceDeath();return;}finished[0]=true;ArenaRespawns.clear(s.actor,core,core.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow());h.succeed();});}
 public static void cancelPartial(GameTestHelper h){var s=setup(h,true);final UUID original=s.core.creatureId();h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();var stopped=new boolean[]{false};h.onEachTick(()->{if(stopped[0])return;var data=ArenaRespawns.get(h.getLevel().getServer());var rows=data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10);for(var item:rows){var row=(CompoundTag)item;if(!row.getUUID("Creature").equals(original)||!row.getString("Phase").equals("building")||row.getList("NewBodies",10).isEmpty())continue;stopped[0]=true;var ids=row.getList("NewBodies",10).stream().map(v->((CompoundTag)v).getUUID("Id")).toList();s.actor.setPos(s.arena.arenaCenter());h.getLevel().getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");h.assertTrue(ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10).stream().noneMatch(v->((CompoundTag)v).getUUID("Creature").equals(original)),"Cancelled job remained");var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());for(var id:ids)h.assertTrue(container.getSubLevel(id)==null||container.getSubLevel(id).isRemoved(),"Cancelled partial body remained live");h.succeed();}});}
 public static void sparseCapture(GameTestHelper h){
  var s=setup(h,false,true);
  var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(s.core.getBlockPos().east());chest.getPersistentData().putByteArray("RespawnLargeDataTest",new byte[2*1024*1024]);
  var template=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(s.core,s.arena.arenaCenter());
  h.assertTrue(template.sizeInBytes()>1048576,"Fixture did not exceed the old template limit");
  h.assertTrue(dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template).blockCount()==100,"Sparse capture lost actual blocks or captured unrelated structures");
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Sparse 34 cubed bounding box or 2 MiB block-entity data was rejected");
  var saved=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());var restored=ArenaRespawns.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());
  var row=restored.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();
  h.assertTrue(row.getCompound("Template").sizeInBytes()==template.sizeInBytes(),"Large recorded template disappeared after save/load");ArenaRespawns.clear(s.actor,s.core,s.arena);h.succeed();
 }
 public static void savedBudget(GameTestHelper h){
  var source=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
  for(int i=0;i<5;i++){
   var row=new CompoundTag();row.putUUID("Id",UUID.randomUUID());row.putUUID("Creature",UUID.randomUUID());row.putUUID("Module",UUID.randomUUID());row.putUUID("TemplateModule",UUID.randomUUID());row.putString("Dimension",h.getLevel().dimension().location().toString());row.putString("Phase","ready");row.putInt("Delay",20);
   var template=new CompoundTag();template.putByteArray("Fixture",new byte[7*1024*1024]);row.put("Template",template);entries.add(row);
  }
  source.put("Entries",entries);h.assertTrue(ArenaRespawns.load(source,h.getLevel().registryAccess()).count()==4,"World template budget did not reject the fifth 7 MiB template");
  var oversized=((CompoundTag)entries.get(0)).copy();var template=new CompoundTag();template.putByteArray("Fixture",new byte[ArenaRespawnLimits.TEMPLATE_BYTES]);oversized.put("Template",template);var only=new net.minecraft.nbt.ListTag();only.add(oversized);source.put("Entries",only);
  h.assertTrue(ArenaRespawns.load(source,h.getLevel().registryAccess()).count()==0,"Per-template byte limit was not enforced during world load");h.succeed();
 }

 public static void multiplePartsSecondDeath(GameTestHelper h){
  var s=setup(h,false);var l=h.getLevel();var p=h.absolutePos(new BlockPos(10,40,10));l.setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());
  var limb=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
  var outside=p.east(5);l.setBlockAndUpdate(outside,Blocks.GOLD_BLOCK.defaultBlockState());var sentinel=SubLevelAssemblyHelper.assembleBlocks(l,outside,List.of(outside),new BoundingBox3i(outside.getX(),outside.getY(),outside.getZ(),outside.getX(),outside.getY(),outside.getZ()));
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");
  // Simulate a recorded multi-part assembly whose native connection actor is not ready on rebuild.
  // Export both real bodies; leave the unrelated third body outside the template.
  var template=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(l,s.arena.arenaCenter(),List.of(s.body,limb.getUniqueId())).save();
  var saved=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess());var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();row.put("Template",template);
  l.getServer().overworld().getDataStorage().set("sablelife_arena_respawns",ArenaRespawns.load(saved,l.registryAccess()));
  s.core.tryActivate(s.actor);BodyOwnership.get(l).bind(limb.getPlot().getCenterBlock(),s.core.creatureId());s.core.forceDeath();
  var second=new UUID[]{null};var parts=new HashSet<UUID>();var stopped=new boolean[]{false};
  h.onEachTick(()->{
   if(stopped[0])return;
   if(second[0]==null){
    var core=LoadedCoreIndex.all(l).stream().filter(c->c.health().state()==HeartState.ALIVE&&!c.creatureId().equals(s.core.creatureId())&&c.modules().all().stream().anyMatch(m->m.name().equals(s.name))).findFirst().orElse(null);if(core==null)return;
    second[0]=core.creatureId();parts.addAll(BodyOwnership.get(l).bodyIds(l,second[0]));h.assertTrue(parts.size()==2,"Respawn only registered the core part");
    core.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow().edit("arenaRespawnDelay",72000,"");
    l.getDataStorage().set("sablelife_bodies",BodyOwnership.load(BodyOwnership.get(l).save(new CompoundTag(),l.registryAccess()),l.registryAccess()));core.forceDeath();
    var deathRow=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess()).getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(second[0])).findFirst().orElseThrow();
    h.assertTrue(deathRow.getList("OldBodies",10).stream().map(v->((CompoundTag)v).getUUID("Id")).collect(java.util.stream.Collectors.toSet()).equals(parts),"Next generation cleanup wait omitted a reconstructed limb");
    return;
   }
   if(!LifeLifecycle.get(l.getServer()).finished(second[0]))return;
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l);
   for(var id:parts)h.assertTrue(container.getSubLevel(id)==null||container.getSubLevel(id).isRemoved(),"Second death left a reconstructed limb");
   h.assertTrue(!BodyOwnership.get(l).hasOwned(second[0]),"Second death retained body ownership");h.assertTrue(container.getSubLevel(sentinel.getUniqueId())!=null&&!sentinel.isRemoved()&&l.getBlockState(sentinel.getPlot().getCenterBlock()).is(Blocks.GOLD_BLOCK),"Unrelated structure was removed");
   s.actor.setPos(s.arena.arenaCenter());l.getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");stopped[0]=true;h.succeed();
  });
 }

}
