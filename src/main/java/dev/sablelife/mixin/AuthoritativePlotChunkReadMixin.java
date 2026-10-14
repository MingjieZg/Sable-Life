package dev.sablelife.mixin;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Plot chunks are owned by Sable, not by vanilla's recent terrain lookup cache. */
@Mixin(value=ServerChunkCache.class,priority=2000)
public abstract class AuthoritativePlotChunkReadMixin {
 @Shadow @Final private ServerLevel level;
 @Unique private EmptyLevelChunk sablelife$emptyPlotChunk;
 @Inject(method="getChunk",at=@At("HEAD"),cancellable=true)
 private void sablelife$currentPlot(int x,int z,ChunkStatus status,boolean create,CallbackInfoReturnable<ChunkAccess> ci){
  if(status!=ChunkStatus.FULL||!level.getServer().isSameThread())return;
  var container=SubLevelContainer.getContainer(level);if(container==null||!container.inBounds(x,z))return;
  var chunk=container.getChunk(new ChunkPos(x,z));
  if(chunk!=null){ci.setReturnValue(chunk);return;}
  // Match Sable's native hole semantics: no plot allocation, terrain request, or forced chunk load.
  if(sablelife$emptyPlotChunk==null)sablelife$emptyPlotChunk=new EmptyLevelChunk(level,new ChunkPos(0,0),level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
  ci.setReturnValue(sablelife$emptyPlotChunk);
 }
}
