package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.DistortionTrailPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class DistortionTrailClient {
 private static final DistortionTrailState state=new DistortionTrailState();private static net.minecraft.client.multiplayer.ClientLevel previous;
 public static void receive(DistortionTrailPacket p){var level=Minecraft.getInstance().level;if(level==null||!p.valid()||!level.dimension().location().equals(p.dimension()))return;if(previous!=level){clear();previous=level;}state.accept(p,level.getGameTime());}
 public static void clear(){state.clear();previous=null;DistortionTrailRenderer.release();}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var level=Minecraft.getInstance().level;if(level==null||level!=previous){clear();return;}state.tick(level.getGameTime());}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||previous==null||state.births().isEmpty())return;var mc=Minecraft.getInstance();if(mc.level!=previous)return;DistortionTrailRenderer.render(mc.getMainRenderTarget(),state.births(),event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix(),previous.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false),mc.options.screenEffectScale().get().floatValue());}
}
