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
 private static Setup setup(GameTestHelper h,boolean large){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));var positions=new ArrayList<BlockPos>();int length=large?260:3;
  // Keep physical spawn dimensions bounded while requiring multiple incremental steps.
  for(int i=0;i<length;i++){var b=p.offset(i%16,0,i/16);positions.add(b);l.setBlockAndUpdate(b,i==0?SableLife.HEART.get().defaultBlockState():i==1?Blocks.CHEST.defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
  ((ChestBlockEntity)l.getBlockEntity(p.east())).setItem(0,new ItemStack(Items.DIAMOND,7));
  var body=SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+(large?15:2),p.getY(),p.getZ()+(large?16:0)));
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
}
