package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.ownership.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.*;

/** Private user blueprint stays outside source control; inject only a test core into its largest body. */
@GameTestHolder("sablelife_dragon_replay_tests") @PrefixGameTestTemplate(false)
public final class DragonBlueprintReplayGameTests {
 @GameTest(template="combat",timeoutTicks=1000) public static void FixedCannonEntitiesInUserBlueprintDieWithBody(GameTestHelper h)throws Exception{
  var path=System.getProperty("sablelife.dragonReplayTemplate");h.assertTrue(path!=null,"Requires private dragon blueprint profile");
  var template=NbtIo.readCompressed(java.nio.file.Path.of(path),NbtAccounter.unlimitedHeap());
  var parts=template.getList("sub_levels",10);var root=parts.stream().map(t->(CompoundTag)t).max(Comparator.comparingInt(p->p.getList("blocks",10).size())).orElseThrow();
  var palette=root.getList("block_palette",10);var blocks=root.getList("blocks",10);
  var block=blocks.stream().map(t->(CompoundTag)t).filter(b->palette.getCompound(b.getInt("palette_id")).getString("Name").startsWith("copycats:")).findFirst().orElseThrow();
  var heartState=new CompoundTag();heartState.putString("Name",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(SableLife.HEART.get()).toString());var paletteId=palette.size();palette.add(heartState);block.putInt("palette_id",paletteId);block.remove("block_entity_data_id");
  // The complete user vehicle extends beyond the tiny GameTest's usual loaded columns.
  var loaded=new HashSet<net.minecraft.world.level.ChunkPos>();for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){var chunk=new net.minecraft.world.level.ChunkPos(x,z);h.getLevel().setChunkForced(x,z,true);loaded.add(chunk);}
  var before=new HashSet<UUID>();for(var entity:h.getLevel().getAllEntities())before.add(entity.getUUID());
  var decoded=dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.loadWithDiagnostics(template);
  decoded.diagnostics().logSummary(com.mojang.logging.LogUtils.getLogger(),"Dragon original blueprint decode");
  var result=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer.place(h.getLevel(),decoded.blueprint(),Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(10,110,10))));
  result.diagnostics().logSummary(com.mojang.logging.LogUtils.getLogger(),"Dragon native tool placement");
  var bodyIds=new HashSet<>(result.subLevelUuidMap().values());
  dev.sablelife.heart.HeartBlockEntity found=null;var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());
  for(var id:bodyIds){if(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body&&!body.isRemoved())for(var holder:body.getPlot().getLoadedChunks())for(var be:holder.getChunk().getBlockEntities().values())if(be instanceof dev.sablelife.heart.HeartBlockEntity heart)found=heart;}
  final var core=found;h.assertTrue(core!=null,"Test core missing");
  var mounted=new ArrayList<AbstractContraptionEntity>();for(var entity:h.getLevel().getAllEntities())if(entity instanceof AbstractContraptionEntity contraption&&!before.contains(entity.getUUID()))mounted.add(contraption);
  h.assertTrue(mounted.stream().filter(e->e.getType().builtInRegistryHolder().key().location().getNamespace().equals("createbigcannons")).count()>=7,"Original cannon entities were not restored");
  com.mojang.logging.LogUtils.getLogger().info("Dragon initial core={} state={} removed={} bodies={}",core.getBlockPos(),core.health().state(),core.isRemoved(),bodyIds.size());
  for(var entity:mounted)if(entity.getType().builtInRegistryHolder().key().location().getNamespace().equals("createbigcannons")){var raw=entity.saveWithoutId(new CompoundTag());com.mojang.logging.LogUtils.getLogger().info("Dragon cannon pos={} controllerRelative={}",entity.position(),raw.get("ControllerRelative"));}
  var initialized=new boolean[]{false};
  h.runAfterDelay(2,()->{
   if(initialized[0])return;initialized[0]=true;
   int fixed=0,attached=0;
   for(var id:bodyIds){var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)container.getSubLevel(id);if(body==null||body.isRemoved())continue;for(var holder:body.getPlot().getLoadedChunks())for(var be:holder.getChunk().getBlockEntities().values())if(be.getClass().getName().contains("FixedCannonMount")){fixed++;try{if(be.getClass().getMethod("getContraption").invoke(be)!=null)attached++;}catch(Exception e){throw new RuntimeException(e);}}}
   com.mojang.logging.LogUtils.getLogger().info("Dragon replay fixed controllers={} attached={} mounted entities={} coreRemoved={} coreState={} root={}",fixed,attached,mounted.size(),core.isRemoved(),core.health().state(),dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos()));
   h.assertTrue(core.configure(100,40),"Could not configure test death delay");
   h.assertTrue(core.activateForRespawn(),"User blueprint test core could not activate");BodyOwnership.get(h.getLevel()).capture(core);var owned=BodyOwnership.get(h.getLevel()).bodyIds(h.getLevel(),core.creatureId());var checked=new boolean[]{false};h.onEachTick(()->{if(checked[0])return;long gone=owned.stream().filter(id->container.getSubLevel(id)==null||container.getSubLevel(id).isRemoved()).count();h.assertTrue(gone==0||gone==owned.size(),"User vehicle cleared only some owned parts on a tick");if(gone>0)checked[0]=true;});core.forceDeath();
   for(var id:bodyIds){var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)container.getSubLevel(id);if(body==null||body.isRemoved())continue;for(var holder:body.getPlot().getLoadedChunks())for(var be:List.copyOf(holder.getChunk().getBlockEntities().values()))if(be.getClass().getName().contains("FixedCannonMount"))try{be.getClass().getMethod("onRedstoneUpdate",boolean.class,boolean.class,boolean.class,boolean.class,int.class).invoke(be,false,true,false,false,0);}catch(Exception failure){throw new RuntimeException(failure);}}
   for(var entity:mounted)if(entity.getType().builtInRegistryHolder().key().location().getNamespace().equals("createbigcannons"))h.assertTrue(!entity.getContraption().disassembled,"Cannon restored unowned blocks when power ended during death countdown");
   h.succeedWhen(()->{
    h.assertTrue(LifeLifecycle.get(h.getLevel().getServer()).finished(core.creatureId()),"Waiting for user blueprint body cleanup");
    for(var entity:mounted)h.assertTrue(entity.isRemoved(),"Mounted entity survived body death: "+entity.getType().builtInRegistryHolder().key().location()+" "+entity.getContraption().getClass().getName());
    for(var chunk:loaded)h.getLevel().setChunkForced(chunk.x,chunk.z,false);
   });
  });
 }
}
