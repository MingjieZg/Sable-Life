package dev.sablelife.compat.littletiles.mixin;

import dev.sablelife.compat.littletiles.LittleTilesMixinContract;
import dev.sablelife.compat.littletiles.LittleTilesRenderRecovery;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.littletiles.client.render.cache.buffer.*;
import team.creative.littletiles.common.block.entity.BETiles;
import java.util.function.Function;

@Pseudo
@Mixin(targets="team.creative.littletiles.client.render.cache.pipeline.LittleRenderPipelineType", remap=false)
public abstract class PipelineRecoveryMixin {
    @Inject(method=LittleTilesMixinContract.PIPELINE_COMPILE, at=@At("TAIL"))
    private static void sablelife$afterCompile(long section, BETiles be,
            Function<RenderType, ChunkBufferUploader> uploader, Function<RenderType, BufferCollection> collection, CallbackInfo ci) {
        LittleTilesRenderRecovery.afterCompile(be, section);
    }
}
