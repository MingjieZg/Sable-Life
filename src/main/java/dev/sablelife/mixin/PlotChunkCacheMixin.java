package dev.sablelife.mixin;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Invalidate cached empty/retired chunks only when plot holders actually change. */
@Mixin(value=LevelPlot.class,remap=false)
public abstract class PlotChunkCacheMixin {
    @Inject(method="addChunkHolder",at=@At("RETURN"))
    private void sablelife$added(ChunkPos local,PlotChunkHolder holder,boolean lighting,CallbackInfo ci){
        var chunk=holder.getChunk();
        if(chunk!=null&&chunk.getLevel() instanceof ServerLevel level)
            ((ServerChunkCacheAccess)(Object)level.getChunkSource()).sablelife$clearCache();
    }
    @Inject(method="onRemove",at=@At("RETURN"))
    private void sablelife$removed(CallbackInfo ci){
        var plot=(LevelPlot)(Object)this;
        if(plot.getSubLevel().getLevel() instanceof ServerLevel level)
            ((ServerChunkCacheAccess)(Object)level.getChunkSource()).sablelife$clearCache();
    }
}
