package dev.sablelife.mixin;

import dev.sablelife.client.RespawnLabelClient;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class RespawnLabelRendererMixin {
    @Inject(method="renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",at=@At("HEAD"),cancellable=true)
    private void sablelife$afterShaderLabel(Display.TextDisplay text,Display.TextDisplay.TextRenderState state,PoseStack pose,MultiBufferSource buffers,int light,float partial,CallbackInfo ci) {
        if(RespawnLabelClient.replaces(text))ci.cancel();
    }
}
