package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_plot_cache_tests") @PrefixGameTestTemplate(false)
public final class PlotChunkCacheGameTests {
 @GameTest(template="combat",batch="plot_cache",timeoutTicks=100)
 public static void ReloadedTemplateAndManualBlueprintSurviveReusedPlot(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,40,5));
  level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
  var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
  var heart=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var center=dev.sablelife.module.ModuleArena.corePosition(heart);
  var template=PhotomancyRespawnBridge.capture(heart,center);var chunkPos=new ChunkPos(heart.getBlockPos());
  StructureRemovalApi.remove(level,body);
  // A loaded world may already have cached an empty plot read before the allocator reuses it.
  level.getChunk(chunkPos.x,chunkPos.z);
  var decoded=dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template.copy()).save();
  var build=PhotomancyRespawnBridge.create(decoded,new CompoundTag(),center.add(0,8,0));
  h.assertTrue(build.step(level).equals("DONE")&&build.heart(level)!=null,"Cached empty chunk caused incomplete respawn");
  var rebuilt=build.heart(level);var reused=(dev.ryanhcode.sable.sublevel.ServerSubLevel)Sable.HELPER.getContaining(level,rebuilt.getBlockPos());
  h.assertTrue(new ChunkPos(rebuilt.getBlockPos()).equals(chunkPos),"Allocator did not reuse deleted plot");
  h.assertTrue(level.getChunk(chunkPos.x,chunkPos.z)==reused.getPlot().getChunk(reused.getPlot().toLocal(chunkPos)),"Level read differs from authoritative plot chunk");
  StructureRemovalApi.remove(level,reused);level.getChunk(chunkPos.x,chunkPos.z);
  var manual=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer.place(level,dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template),center.add(0,16,0));
  h.assertTrue(manual.diagnostics().isEmpty()&&manual.placedSubLevels()==1,"Ordinary Photomancy placement failed after cancellation-style removal");
  var restored=(dev.ryanhcode.sable.sublevel.ServerSubLevel)Sable.HELPER.getContaining(level,body.getPlot().getCenterBlock());
  h.assertTrue(restored!=null&&level.getBlockState(restored.getPlot().getCenterBlock()).is(SableLife.HEART.get()),"Manual blueprint silently placed into a cached empty chunk");
  StructureRemovalApi.remove(level,restored);h.succeed();
 }
 @GameTest(template="combat",batch="blueprint_failure_detail")
 public static void NativeToolFailureShowsSpecificReasonToOperator(GameTestHelper h){
  var messages=new ArrayList<String>();
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BlueprintDiagnosticAdmin")){
   @Override public boolean hasPermissions(int permission){return permission<=2;}
   @Override public void displayClientMessage(net.minecraft.network.chat.Component message,boolean overlay){messages.add(message.getString());}
  };
  actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems.BLUEPRINT_TOOL.get()));
  actor.setPos(h.absolutePos(new BlockPos(4,40,4)).getCenter());
  var data=new CompoundTag();data.putString("name","invalid-diagnostic-fixture");data.putByteArray("data",new byte[]{1,2,3});
  var origin=new CompoundTag();origin.putDouble("x",actor.getX());origin.putDouble("y",actor.getY());origin.putDouble("z",actor.getZ());data.put("origin",origin);
  dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions.registerDefaults();
  dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions.handle(dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions.LOAD_BLUEPRINT,actor,data);
  h.assertTrue(messages.stream().anyMatch(m->m.startsWith("Failed to load Sable blueprint:")&&m.length()>35),"Native generic failure hid the concrete reason");h.succeed();
 }

}
