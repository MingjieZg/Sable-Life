package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_plot_read_tests") @PrefixGameTestTemplate(false)
public final class PlotReadConsistencyGameTests {
 private static void stale(net.minecraft.server.level.ServerLevel level,ChunkPos pos,net.minecraft.world.level.chunk.ChunkAccess chunk)throws Exception{
  var cache=level.getChunkSource();var type=net.minecraft.server.level.ServerChunkCache.class;
  var key=type.getDeclaredField("lastChunkPos");key.setAccessible(true);((long[])key.get(cache))[0]=pos.toLong();
  var status=type.getDeclaredField("lastChunkStatus");status.setAccessible(true);((ChunkStatus[])status.get(cache))[0]=ChunkStatus.FULL;
  var value=type.getDeclaredField("lastChunk");value.setAccessible(true);((net.minecraft.world.level.chunk.ChunkAccess[])value.get(cache))[0]=chunk;
 }
 @GameTest(template="combat",batch="plot_authoritative_read")
 public static void PlotReadsIgnoreCacheRepopulatedAfterChunkCreation(GameTestHelper h)throws Exception{
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
  var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
  var center=body.getPlot().getCenterBlock();var chunkPos=new ChunkPos(center);var actual=body.getPlot().getChunk(body.getPlot().toLocal(chunkPos));
  var empty=new net.minecraft.world.level.chunk.EmptyLevelChunk(level,chunkPos,level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
  stale(level,chunkPos,empty);
  h.assertTrue(level.getChunk(chunkPos.x,chunkPos.z)==actual&&level.getBlockState(center).is(SableLife.HEART.get()),"Stale empty cache overrides a loaded plot chunk");
  dev.sablelife.compat.StructureRemovalApi.remove(level,body);stale(level,chunkPos,actual);
  h.assertTrue(level.getBlockState(center).isAir(),"Retired cached chunk remains visible after plot removal");h.succeed();
 }
}
