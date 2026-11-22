package dev.sablelife.compat.littletiles.mixin;

import dev.sablelife.compat.littletiles.*;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import team.creative.creativecore.common.util.type.map.ChunkLayerMap;
import team.creative.littletiles.client.render.cache.buffer.*;
import team.creative.littletiles.client.render.cache.pipeline.LittleRenderPipelineType;

@Pseudo
@Mixin(targets="team.creative.littletiles.client.render.cache.BlockBufferCache", remap=false)
public abstract class BufferLossMixin implements LostMesh {
    @Shadow private ChunkLayerMap<BufferCache> uploaded;
    @Unique private final MeshRecoveryState sablelife$recovery = new MeshRecoveryState();

    @Inject(method=LittleTilesMixinContract.CACHE_GET_UPLOADED, at=@At("HEAD"))
    private void sablelife$beforeDiscard(RenderType layer, CallbackInfoReturnable<BufferCache> ci) {
        var holder = uploaded.get(layer);
        // CPU-buffer release after upload is normal; only explicit invalidation records a loss.
        if (holder != null && holder.isInvalid() && !holder.isEmpty()) sablelife$lostMesh();
    }

    @Redirect(method=LittleTilesMixinContract.CACHE_UPLOAD,
            at=@At(value="INVOKE", target=LittleTilesMixinContract.PIPELINE_UPLOAD_INVOCATION))
    private BufferCache sablelife$rememberFailedUpload(ChunkBufferUploader uploader, BufferCollection collection, BufferCache cache) {
        var result = LittleRenderPipelineType.upload(uploader, collection, cache);
        if (result == null && cache != null && !cache.isEmpty()) sablelife$lostMesh();
        return result;
    }

    @Inject(method=LittleTilesMixinContract.CACHE_SET_EMPTY, at=@At("TAIL"))
    private void sablelife$normalUnload(CallbackInfo ci) { sablelife$builtMesh(); }

    @Inject(method=LittleTilesMixinContract.CACHE_SET_BUFFERS, at=@At("TAIL"))
    private void sablelife$newBuild(ChunkLayerMap<BufferCache> buffers, CallbackInfo ci) { sablelife$builtMesh(); }

    @Override public void sablelife$lostMesh() { sablelife$recovery.lost(); }
    @Override public void sablelife$builtMesh() { sablelife$recovery.built(); }
    @Override public boolean sablelife$hasLoss() { return sablelife$recovery.hasLoss(); }
    @Override public boolean sablelife$takeRecovery(long now) { return sablelife$recovery.take(now); }
}
