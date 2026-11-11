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
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));var positions=new ArrayList<BlockPos>();int length=sparse?244:large?260:3;
  // Keep physical spawn dimensions bounded while exceeding the former per-tick placement budget.
  for(int i=0;i<length;i++){var b=sparse?(i<82?p.offset(i,0,0):i<163?p.offset(81,i-81,0):p.offset(81,81,i-162)):p.offset(i%16,0,i/16);positions.add(b);l.setBlockAndUpdate(b,i==0?SableLife.HEART.get().defaultBlockState():i==1?Blocks.CHEST.defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
  ((ChestBlockEntity)l.getBlockEntity(p.east())).setItem(0,new ItemStack(Items.DIAMOND,7));
  var body=SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+(sparse?81:large?15:2),p.getY()+(sparse?81:0),p.getZ()+(sparse?81:large?16:0)));
  var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(100,0);var arena=c.modules().add("arena");arena.arenaCenter(ModuleArena.corePosition(c),l.dimension().location().toString());arena.edit("arenaRespawnEnabled",1,"");arena.edit("arenaRespawnDelay",100,"");
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
   if(resume&&!restarted[0])for(var value:saved.getList("Entries",10)){var row=(CompoundTag)value;if(row.getUUID("Creature").equals(original)&&row.getString("Phase").equals("countdown")&&row.getInt("Remaining")<50){
    seedLegacyPartial(h,row);
    var replacement=ArenaRespawns.load(saved.copy(),h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_arena_respawns",replacement);restarted[0]=true;break;
   }}
   var candidates=LoadedCoreIndex.all(h.getLevel()).stream().filter(c->!c.creatureId().equals(original)&&c.modules().all().stream().anyMatch(m->m.type().equals("melee")&&m.name().equals(s.name))).toList();
   var alive=candidates.stream().filter(c->c.health().state()==HeartState.ALIVE).toList();if(alive.isEmpty())return;
   h.assertTrue(alive.size()==1,"Respawn duplicated active cores");var c=alive.getFirst();h.assertTrue(c.health().current()==100,"Respawn did not restore full health");if(!resume){var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());var position=root.logicalPose().position();h.assertTrue(new Vec3(position.x(),position.y(),position.z()).distanceTo(s.arena.arenaCenter())<1,"Native template basis did not reach Arena anchor");}
   var melee=c.modules().all().stream().filter(m->m.type().equals("melee")).findFirst().orElseThrow();h.assertTrue(!melee.id().equals(s.mark)&&!melee.anchors().getFirst().bodyId().equals(s.body)&&melee.anchors().getFirst().status(c).equals("valid"),"Module/mark UUIDs did not migrate");
   h.assertTrue(h.getLevel().getBlockEntity(c.getBlockPos().east()) instanceof ChestBlockEntity chest&&chest.getItem(0).getCount()==7&&chest.getItem(0).is(Items.DIAMOND),"Full captured inventory did not restore");
   h.assertTrue(!resume||restarted[0],"Save/reload fixture did not interrupt actual partial placement");checked[0]=true;
   h.runAfterDelay(15,()->{h.assertTrue(LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v.modules().all().stream().anyMatch(m->m.type().equals("melee")&&m.name().equals(s.name))&&v.health().state()==HeartState.ALIVE).count()==1,"Finished job repeated respawn");ArenaRespawns.clear(s.actor,c,c.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow());h.succeed();});
  });
 }}
 public static void enableAfterRecord(GameTestHelper h){
  var s=setup(h,false);var original=s.core.creatureId();
  s.arena.edit("arenaRespawnEnabled",0,"");
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Recording with automatic respawn disabled failed");
  s.arena.edit("arenaRespawnEnabled",1,"");s.core.tryActivate(s.actor);s.core.forceDeath();
  var checked=new boolean[]{false};
  h.onEachTick(()->{
   if(checked[0])return;
   var candidates=LoadedCoreIndex.all(h.getLevel()).stream().filter(c->c.health().state()==HeartState.ALIVE&&!c.creatureId().equals(original)&&c.modules().all().stream().anyMatch(m->m.name().equals(s.name))).toList();
   if(candidates.isEmpty())return;
   h.assertTrue(candidates.size()==1,"First respawn duplicated the life");
   var next=candidates.getFirst();var arena=next.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow();
   h.assertTrue(arena.value("arenaRespawnEnabled")==0,"Recorded disabled setting was unexpectedly replaced");
   checked[0]=true;next.forceDeath();
   h.runAfterDelay(5,()->{
    var records=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10);
    h.assertTrue(records.stream().map(t->(CompoundTag)t).filter(t->t.getUUID("Creature").equals(next.creatureId())).noneMatch(t->!t.getString("Phase").equals("ready")),"Disabled restored Arena unexpectedly started another countdown");
    System.out.println("ARENA_RECORD_ORDER_REPRO: recorded OFF, enabled afterward -> first respawn restores OFF -> second death has no countdown");
    ArenaRespawns.clear(s.actor,next,arena);h.succeed();
   });
  });
 }
 public static void permissions(GameTestHelper h){var s=setup(h,false);var noOp=TestActors.creative(h.getLevel());noOp.setPos(s.actor.position());h.assertTrue(!ArenaRespawns.record(noOp,s.core,s.arena),"Non-operator recorded a full respawn template");s.actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!ArenaRespawns.record(s.actor,s.core,s.arena),"Survival operator recorded a template");s.actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(!ArenaRespawns.record(s.actor,s.core,s.core.modules().all().stream().filter(m->m.type().equals("melee")).findFirst().orElseThrow()),"Wrong module type recorded");h.succeed();}
 public static void unloaded(GameTestHelper h){var s=setup(h,false);final UUID original=s.core.creatureId();{h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();var data=ArenaRespawns.get(h.getLevel().getServer());var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(original)).findFirst().orElseThrow();row.putDouble("X",1000000);row.putDouble("Z",1000000);row.putInt("Remaining",100);var restored=ArenaRespawns.load(saved,h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_arena_respawns",restored);
  h.runAfterDelay(30,()->{var current=restored.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(original)).findFirst().orElseThrow();h.assertTrue(current.getInt("Remaining")==100&&!h.getLevel().hasChunk(1000000>>4,1000000>>4),"Unloaded countdown advanced or loaded terrain");s.actor.setPos(1000000,40,1000000); // cleanup through common cancellation command
   h.getLevel().getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");h.succeed();});
 }}
 public static void repeats(GameTestHelper h){var s=setup(h,false);final UUID original=s.core.creatureId();h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();var generation=new UUID[]{original};var cycles=new int[]{0};var finished=new boolean[]{false};h.onEachTick(()->{if(finished[0])return;var found=LoadedCoreIndex.all(h.getLevel()).stream().filter(c->c.health().state()==HeartState.ALIVE&&c.modules().all().stream().anyMatch(m->m.name().equals(s.name))).toList();if(found.isEmpty())return;h.assertTrue(found.size()==1,"Several alive generations exist");var core=found.getFirst();if(core.creatureId().equals(generation[0]))return;generation[0]=core.creatureId();cycles[0]++;if(cycles[0]<2){core.forceDeath();return;}finished[0]=true;ArenaRespawns.clear(s.actor,core,core.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow());h.succeed();});}
 private static void seedLegacyPartial(GameTestHelper h,CompoundTag row){
  var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(row.getCompound("Template"));
  var progress=new dev.rew1nd.sableschematicapi.survival.BlueprintBuildProgress();
  var center=new Vec3(row.getDouble("X"),row.getDouble("Y"),row.getDouble("Z"));
  var result=dev.rew1nd.sableschematicapi.survival.SableBlueprintIncrementalPlacer.step(h.getLevel(),blueprint,dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan.legacy(blueprint,center),progress,new dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildTickBudget(128,16,8,2000000),dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildMaterialBudget.UNLIMITED);
  h.assertTrue(!result.status().name().equals("DONE")&&!progress.placedSubLevels().isEmpty(),"Legacy fixture did not produce partial placement");
  row.put("Progress",progress.save());var ids=new net.minecraft.nbt.ListTag();for(var placed:progress.placedSubLevels().values()){var tag=new CompoundTag();tag.putUUID("Id",placed.placedUuid());ids.add(tag);}row.put("NewBodies",ids);row.putString("Phase","building");
 }
 public static void cancelPartial(GameTestHelper h){
  var s=setup(h,true);h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();
  var saved=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());
  var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();seedLegacyPartial(h,row);row.putString("Phase","failed");
  h.getLevel().getServer().overworld().getDataStorage().set("sablelife_arena_respawns",ArenaRespawns.load(saved,h.getLevel().registryAccess()));
  var ids=row.getList("NewBodies",10).stream().map(v->((CompoundTag)v).getUUID("Id")).toList();s.actor.setPos(s.arena.arenaCenter());lCancel(h,s);
  h.assertTrue(ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10).stream().noneMatch(v->((CompoundTag)v).getUUID("Creature").equals(s.core.creatureId())),"Cancelled job remained");
  var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());for(var id:ids)h.assertTrue(container.getSubLevel(id)==null||container.getSubLevel(id).isRemoved(),"Cancelled partial body remained live");
  // Exercise the real operator cancel path, then the ordinary native blueprint tool path.
  var result=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer.place(h.getLevel(),dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(row.getCompound("Template")),s.arena.arenaCenter().add(0,20,0));
  h.assertTrue(result.diagnostics().isEmpty()&&result.placedSubLevels()>0,"Operator cancel broke ordinary Photomancy placement");
  for(var id:result.subLevelUuidMap().values())if(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)dev.sablelife.compat.StructureRemovalApi.remove(h.getLevel(),body);
  h.succeed();
 }
 public static void directPlacement(GameTestHelper h){
  var s=setup(h,true);h.getLevel().setBlockAndUpdate(s.core.getBlockPos().east(2),com.simibubi.create.AllBlocks.COPYCAT_PANEL.getDefaultState());
  var originalCopycat=(com.simibubi.create.content.decoration.copycat.CopycatBlockEntity)h.getLevel().getBlockEntity(s.core.getBlockPos().east(2));originalCopycat.setMaterial(Blocks.DIAMOND_BLOCK.defaultBlockState());originalCopycat.setConsumedItem(new ItemStack(Items.DIAMOND_BLOCK));
  var template=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(s.core,s.arena.arenaCenter());
  var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.create(template,new CompoundTag(),s.arena.arenaCenter().add(0,10,0));
  h.assertTrue(build.step(h.getLevel()).equals("DONE"),"New respawn did not complete in one native placement call");
  var core=build.heart(h.getLevel());h.assertTrue(core!=null&&core.health().state()==HeartState.DORMANT,"Placement activated an incomplete core");
  h.assertTrue(h.getLevel().getBlockEntity(core.getBlockPos().east()) instanceof ChestBlockEntity chest&&chest.getItem(0).getCount()==7,"Direct placement lost inventory");
  h.assertTrue(h.getLevel().getBlockEntity(core.getBlockPos().east(2)) instanceof com.simibubi.create.content.decoration.copycat.CopycatBlockEntity copycat&&copycat.getMaterial().is(Blocks.DIAMOND_BLOCK),"Direct placement lost Copycat camouflage");
  h.assertTrue(build.module(s.arena.id())!=null&&!build.module(s.arena.id()).equals(s.arena.id()),"Direct placement lost module identity mapping");
  var ids=build.bodies();var restored=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.create(template,build.save(),s.arena.arenaCenter().add(0,10,0));h.assertTrue(restored.step(h.getLevel()).equals("DONE")&&restored.bodies().equals(ids),"Saved completed placement duplicated bodies");
  for(var id:ids){var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel()).getSubLevel(id);dev.sablelife.compat.StructureRemovalApi.remove(h.getLevel(),body);}h.succeed();
 }
 public static void directFailure(GameTestHelper h){
  var s=setup(h,false);var template=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(s.core,s.arena.arenaCenter());
  var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template);var flag=new CompoundTag();flag.putBoolean("Fail",true);template.getCompound("global_extra_data").put("sablelife:test_failed_restore",flag);
  dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry.register(new dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent(){
   @Override public net.minecraft.resources.ResourceLocation id(){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","test_failed_restore");}
   @Override public void onPlaceAfterBlockEntities(dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession session,CompoundTag data){if(data.getBoolean("Fail"))throw new IllegalStateException("Deliberate restoration callback failure");}
  });
  var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.create(template,new CompoundTag(),s.arena.arenaCenter().add(0,10,0));boolean rejected=false;try{build.step(h.getLevel());}catch(IllegalStateException expected){rejected=true;}
  h.assertTrue(rejected&&build.heart(h.getLevel())!=null&&build.heart(h.getLevel()).health().state()==HeartState.DORMANT,"Native partial restoration was silently activated");
  var bodies=build.bodies();h.assertTrue(!bodies.isEmpty(),"Failed direct build lost cancellation identities");
  var restored=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.create(template,build.save(),s.arena.arenaCenter().add(0,10,0));rejected=false;try{restored.step(h.getLevel());}catch(IllegalStateException expected){rejected=true;}h.assertTrue(rejected&&restored.bodies().equals(bodies),"Failed saved direct build generated duplicate bodies");
  for(var id:bodies){var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel()).getSubLevel(id);if(body!=null)dev.sablelife.compat.StructureRemovalApi.remove(h.getLevel(),body);}h.succeed();
 }
 public static void sparseCapture(GameTestHelper h){
  var s=setup(h,false,true);
  var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),s.core.getBlockPos());
  var plot=body.getPlot();var b=plot.getBoundingBox();var hole=new net.minecraft.world.level.ChunkPos((b.maxX()+64)>>4,(b.maxZ()+64)>>4);
  h.assertTrue(plot.getChunkHolder(plot.toLocal(hole))==null&&!h.getLevel().hasChunk(hole.x,hole.z),"Fixture lacks an actual unallocated plot hole");
  // Loose cached bounds include air holes, as do hollow structures; no new chunks are allocated.
  plot.setBoundingBox(new BoundingBox3i(b.minX(),b.minY(),b.minZ(),b.maxX()+96,b.maxY(),b.maxZ()+96));var originalChunks=plot.getLoadedChunks().size();
  var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(s.core.getBlockPos().east());chest.getPersistentData().putByteArray("RespawnLargeDataTest",new byte[2*1024*1024]);
  var template=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(s.core,s.arena.arenaCenter());
  h.assertTrue(template.sizeInBytes()>1048576,"Fixture did not exceed the old template limit");
  h.assertTrue(dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template).blockCount()==244,"Sparse capture lost actual blocks or captured unrelated structures");
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Sparse plot with unallocated holes or 2 MiB block-entity data was rejected");
  h.assertTrue(plot.getLoadedChunks().size()==originalChunks&&plot.getChunkHolder(plot.toLocal(hole))==null&&!h.getLevel().hasChunk(hole.x,hole.z),"Native capture allocated or loaded the plot hole");
  var saved=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());var restored=ArenaRespawns.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());
  var row=restored.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();
  h.assertTrue(row.getCompound("Template").sizeInBytes()==template.sizeInBytes(),"Large recorded template disappeared after save/load");ArenaRespawns.clear(s.actor,s.core,s.arena);h.succeed();
 }
 public static void savedBudget(GameTestHelper h){
  var source=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
  for(int i=0;i<5;i++){
   var row=new CompoundTag();row.putUUID("Id",UUID.randomUUID());row.putUUID("Creature",UUID.randomUUID());row.putUUID("Module",UUID.randomUUID());row.putUUID("TemplateModule",UUID.randomUUID());row.putString("Dimension",h.getLevel().dimension().location().toString());row.putString("Phase","ready");row.putInt("Delay",20);
   var template=new CompoundTag();template.putByteArray("Fixture",new byte[31*1024*1024]);row.put("Template",template);entries.add(row);
  }
  source.put("Entries",entries);h.assertTrue(ArenaRespawns.load(source,h.getLevel().registryAccess()).count()==4,"World template budget did not reject the fifth 31 MiB template");
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

 public static void cancelOrphanDisplay(GameTestHelper h){
  var l=h.getLevel();var id=UUID.randomUUID();var p=net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(5,6,5)));var ghosts=new ArrayList<net.minecraft.world.entity.Display.TextDisplay>();
  for(int i=0;i<2;i++){var text=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(l);text.setPos(p.add(i,0,0));h.assertTrue(l.addFreshEntity(text),"Display fixture failed");text.getPersistentData().putUUID("SableLifeArenaRecord",id);text.addTag("sablelife_arena_respawn");ghosts.add(text);}
  var unrelated=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(l);unrelated.setPos(p);l.addFreshEntity(unrelated);var actor=TestActors.creative(l);actor.setPos(p);
  l.getServer().getCommands().performPrefixedCommand(actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");
  h.assertTrue(ghosts.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),"Orphan duplicate text was not cancelled");h.assertTrue(!unrelated.isRemoved(),"Unrelated display was removed");unrelated.discard();h.succeed();
 }

 public static void buildTickIsolation(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,15,5));var bodies=new ArrayList<dev.ryanhcode.sable.sublevel.ServerSubLevel>();var furnaces=new ArrayList<net.minecraft.world.level.block.entity.FurnaceBlockEntity>();
  for(int i=0;i<2;i++){var pos=p.east(i*3);l.setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());furnace.setItem(0,new ItemStack(Items.IRON_ORE));furnace.setItem(1,new ItemStack(Items.COAL));bodies.add(body);furnaces.add(furnace);}
  ArenaBuildTicks.hold(l,UUID.randomUUID(),Set.of(bodies.getFirst().getUniqueId()));
  h.runAfterDelay(5,()->{
   h.assertTrue(furnaces.getFirst().getItem(1).getCount()==1&&!l.getBlockState(furnaces.getFirst().getBlockPos()).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Partial blueprint block entity ticked before commit");
   h.assertTrue(furnaces.get(1).getItem(1).isEmpty()&&l.getBlockState(furnaces.get(1).getBlockPos()).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Unrelated body tick was blocked");
   ArenaBuildTicks.release(l,Set.of(bodies.getFirst().getUniqueId()));
   h.runAfterDelay(5,()->{h.assertTrue(furnaces.getFirst().getItem(1).isEmpty()&&l.getBlockState(furnaces.getFirst().getBlockPos()).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Completed blueprint did not resume normal ticks");for(var b:bodies)dev.sablelife.compat.StructureRemovalApi.remove(l,b);h.succeed();});
  });
 }

 public static void respawnMinimum(GameTestHelper h){
  var s=setup(h,false);s.arena.edit("arenaRespawnDelay",20,"");h.assertTrue(s.arena.value("arenaRespawnDelay")==100,"Module editor permits less than 5 seconds");
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();
  var saved=ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();h.assertTrue(row.getInt("Delay")==100&&row.getInt("Remaining")==100,"Server death countdown is shorter than minimum");
  row.putInt("Delay",20);var old=ArenaRespawns.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());var migrated=old.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();h.assertTrue(migrated.getInt("Delay")==100,"Saved delay below minimum was not migrated");
  s.actor.setPos(s.arena.arenaCenter());h.runAfterDelay(30,()->{h.assertTrue(LoadedCoreIndex.all(h.getLevel()).stream().noneMatch(c->!c.creatureId().equals(s.core.creatureId())&&c.modules().all().stream().anyMatch(m->m.name().equals(s.name))),"Respawn occurred before five seconds");lCancel(h,s);h.succeed();});
 }
 private static void lCancel(GameTestHelper h,Setup s){h.getLevel().getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");}

 public static void sixtyFourThousandBlocks(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));var positions=new ArrayList<BlockPos>(64000);
  for(int x=0;x<40;x++)for(int y=0;y<40;y++)for(int z=0;z<40;z++){var pos=p.offset(x,y,z);positions.add(pos);l.setBlockAndUpdate(pos,x==0&&y==0&&z==0?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
  var body=SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+39,p.getY()+39,p.getZ()+39));var core=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());var arena=core.modules().add("arena");arena.arenaCenter(ModuleArena.corePosition(core),l.dimension().location().toString());
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GiantAdmin")){@Override public boolean canUseGameMasterBlocks(){return isCreative();}};actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);actor.setPos(ModuleArena.corePosition(core).add(0,-actor.getEyeHeight(),2));
  h.assertTrue(ArenaRespawns.record(actor,core,arena),"Actual 64000-block native template rejected");var saved=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess());var data=ArenaRespawns.load(saved,l.registryAccess()).save(new CompoundTag(),l.registryAccess());var row=data.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(core.creatureId())).findFirst().orElseThrow();h.assertTrue(dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(row.getCompound("Template")).blockCount()==64000,"Large native template lost blocks on save/load");var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.create(row.getCompound("Template"),new CompoundTag(),arena.arenaCenter().add(0,60,0));h.assertTrue(build.step(l).equals("DONE")&&build.heart(l)!=null,"Actual 64000-block template did not restore in one native call");h.assertTrue(build.bodies().size()==1,"Giant direct placement duplicated parts");for(var id:build.bodies()){var restoredBody=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l).getSubLevel(id);long[] count={0};for(var holder:restoredBody.getPlot().getLoadedChunks())for(var section:holder.getChunk().getSections())if(!section.hasOnlyAir())section.getStates().count((state,n)->{if(!state.isAir())count[0]+=n;});h.assertTrue(count[0]==64000,"Giant native restoration lost blocks");dev.sablelife.compat.StructureRemovalApi.remove(l,restoredBody);}ArenaRespawns.clear(actor,core,arena);dev.sablelife.compat.StructureRemovalApi.remove(l,body);h.succeed();
 }

 public static void namedTimer(GameTestHelper h,boolean legacyUnnamed){
  var s=setup(h,false);var l=h.getLevel();
  h.assertTrue(s.core.editCore(s.actor,"name",0,"Old recorded name"),"Initial name edit failed");
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");
  String raw=legacyUnnamed?"":"&a&lArena Guardian";
  h.assertTrue(s.core.editCore(s.actor,"name",0,raw),"Latest creature name edit failed");s.core.tryActivate(s.actor);s.core.forceDeath();
  var saved=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess());
  var row=saved.getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();
  h.assertTrue(row.getString("CreatureName").equals(raw),"Countdown did not capture the latest name at death");
  if(legacyUnnamed)row.remove("CreatureName");
  l.getServer().overworld().getDataStorage().set("sablelife_arena_respawns",ArenaRespawns.load(saved,l.registryAccess()));
  h.runAfterDelay(3,()->{
   var current=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess()).getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();
   h.assertTrue(current.getString("CreatureName").equals(raw),"Reload lost the creature name");
   var text=(net.minecraft.world.entity.Display.TextDisplay)l.getEntity(current.getUUID("Display"));
   h.assertTrue(text!=null,"Countdown display missing after reload");
   var label=((dev.sablelife.mixin.RespawnTextDisplayAccess)(Object)text).sablelife$getText();
   h.assertTrue(label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("arena_respawn.sablelife.named"),"Name wrapper broke arena shader label identification");
   var args=((net.minecraft.network.chat.contents.TranslatableContents)label.getContents()).getArgs();var name=(net.minecraft.network.chat.Component)args[0];var countdown=(net.minecraft.network.chat.Component)args[1];
   h.assertTrue(name.getString().equals(legacyUnnamed?net.minecraft.network.chat.Component.translatable("entity.sablelife.creature").getString():"Arena Guardian"),"Name did not appear before countdown");
   h.assertTrue(countdown.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("arena_respawn.sablelife.countdown")&&countdown.getSiblings().size()==2,"Countdown/progress content changed");
   if(!legacyUnnamed){var formatted=new boolean[]{false};name.visit((style,value)->{if(value.contains("Arena Guardian"))formatted[0]=style.isBold()&&style.getColor()!=null&&style.getColor().getValue()==0x55FF55;return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);h.assertTrue(formatted[0]&&countdown.getStyle().getColor()==null,"Name formatting was lost or leaked to countdown");}
   s.actor.setPos(text.position());l.getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");
   h.assertTrue(text.isRemoved(),"Named countdown was not cleaned on cancellation");h.succeed();
  });
 }
 public static void groundedTimer(GameTestHelper h){
  var s=setup(h,false);var l=h.getLevel();var center=s.arena.arenaCenter().add(0,150,0);s.arena.arenaCenter(center,l.dimension().location().toString());
  var ground=new BlockPos((int)Math.floor(center.x),h.absolutePos(new BlockPos(0,5,0)).getY(),(int)Math.floor(center.z));l.setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState());
  // Completed GameTests can leave beacon/marker terrain above this column; establish a unique known surface.
  for(int y=ground.getY()+1;y<l.getMaxBuildHeight();y++)l.setBlockAndUpdate(new BlockPos(ground.getX(),y,ground.getZ()),Blocks.AIR.defaultBlockState());
  h.assertTrue(ArenaRespawns.record(s.actor,s.core,s.arena),"Capture failed");s.core.tryActivate(s.actor);s.core.forceDeath();
  h.runAfterDelay(3,()->{
   var row=ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess()).getList("Entries",10).stream().map(v->(CompoundTag)v).filter(v->v.getUUID("Creature").equals(s.core.creatureId())).findFirst().orElseThrow();h.assertTrue(row.hasUUID("Display"),"Countdown display missing");var text=l.getEntity(row.getUUID("Display"));
   h.assertTrue(text!=null&&text.getY()==ground.getY()+3.5&&text.getX()==center.x&&text.getZ()==center.z,"Actual countdown="+(text==null?"missing":text.position())+" ground="+ground+" center="+center);h.assertTrue(row.getDouble("Y")==center.y&&text.position().distanceTo(center)>64,"Grounded timer changed spawn anchor or fixture not high enough");
   s.actor.setPos(text.position());l.getServer().getCommands().performPrefixedCommand(s.actor.createCommandSourceStack().withPermission(4),"sablelife_respawn cancel");h.assertTrue(text.isRemoved()&&ArenaRespawns.get(l.getServer()).save(new CompoundTag(),l.registryAccess()).getList("Entries",10).stream().noneMatch(v->((CompoundTag)v).getUUID("Creature").equals(s.core.creatureId())),"Cannot cancel high-anchor job from its ground display");h.succeed();
  });
 }

}
