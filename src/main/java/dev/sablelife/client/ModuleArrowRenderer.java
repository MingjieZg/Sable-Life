package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Native arrow fallback; copied weapons use their complete item model along the flight axis. */
@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModuleArrowRenderer extends ArrowRenderer<ModuleArrow> {
    private final ItemRenderer items;
    public ModuleArrowRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SableLife.MODULE_ARROW.get(), ModuleArrowRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(ModuleArrow arrow) {
        return ResourceLocation.withDefaultNamespace(arrow.getColor() > 0 ? "textures/entity/projectiles/tipped_arrow.png" : "textures/entity/projectiles/arrow.png");
    }
    @Override public void render(ModuleArrow arrow, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var item = arrow.appearance();
        if (item.isEmpty()) {
            pose.pushPose(); pose.scale(arrow.visualSize(), arrow.visualSize(), arrow.visualSize());
            super.render(arrow, yaw, partial, pose, buffers, light); pose.popPose(); return;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, arrow.yRotO, arrow.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, arrow.xRotO, arrow.getXRot())));
        pose.mulPose(Axis.YP.rotationDegrees(-90)); pose.mulPose(Axis.ZP.rotationDegrees(-45));
        ProjectileItemRenderer.render(items,item,arrow.level(),arrow.getId(),arrow.visualSize(),pose,buffers,light);
        pose.popPose();
    }
}
