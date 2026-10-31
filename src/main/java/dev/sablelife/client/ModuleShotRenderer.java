package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModuleShotRenderer extends EntityRenderer<ModuleShot> {
    private final ItemRenderer items;
    private final net.minecraft.client.model.ShulkerBulletModel<ModuleShot> shulker;
    private static final ResourceLocation SPARK=ResourceLocation.withDefaultNamespace("textures/entity/shulker/spark.png");
    public ModuleShotRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); shulker = new net.minecraft.client.model.ShulkerBulletModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHULKER_BULLET)); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SableLife.LEVEL_REWARD_ORB.get(),net.minecraft.client.renderer.entity.ExperienceOrbRenderer::new);
        event.registerEntityRenderer(SableLife.MODULE_SHOT.get(), ModuleShotRenderer::new);
    }
    @Override public void render(ModuleShot shot, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if(shot.defaultHomingAppearance()) {
            pose.pushPose(); float age=shot.tickCount+partial;
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(net.minecraft.util.Mth.sin(age*.1F)*180));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(net.minecraft.util.Mth.cos(age*.1F)*180));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(net.minecraft.util.Mth.sin(age*.15F)*360));
            float scale=shot.projectileDiameter()*2; pose.scale(-scale,-scale,scale);
            shulker.setupAnim(shot,0,0,0,0,0);
            shulker.renderToBuffer(pose,buffers.getBuffer(shulker.renderType(SPARK)),15728880,OverlayTexture.NO_OVERLAY);
            pose.scale(1.5F,1.5F,1.5F);
            shulker.renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(SPARK)),15728880,OverlayTexture.NO_OVERLAY,0x26FFFFFF);
            pose.popPose(); super.render(shot,yaw,partial,pose,buffers,light); return;
        }
        var item = shot.getItem();
        pose.pushPose();
        if(shot.fixedFallingItem()) pose.mulPose(shot.fallingItemRotation());
        else if(shot.homingProjectile()) {
            var velocity=shot.getDeltaMovement();
            float heading=velocity.lengthSqr()>.000001 ? (float)Math.toDegrees(Math.atan2(velocity.x,velocity.z)) : shot.getYRot();
            float pitch=velocity.lengthSqr()>.000001 ? (float)Math.toDegrees(Math.atan2(velocity.y,Math.hypot(velocity.x,velocity.z))) : shot.getXRot();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(heading)); pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90)); pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45));
        } else pose.mulPose(entityRenderDispatcher.cameraOrientation());
        ProjectileItemRenderer.render(items,item,shot.level(),shot.getId(),shot.fallingProjectile()?shot.fallingItemSize():shot.projectileDiameter(),pose,buffers,15728880);
        pose.popPose(); super.render(shot, yaw, partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(ModuleShot shot) { return TextureAtlas.LOCATION_BLOCKS; }
}
