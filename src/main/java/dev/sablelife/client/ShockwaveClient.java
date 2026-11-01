package dev.sablelife.client;
import dev.sablelife.SableLife;
import dev.sablelife.module.ShockwavePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class ShockwaveClient {
 public static final int MAX_WAVES=8;
 public record Wave(ShockwavePacket packet,long born){}
 private static final VisualFilterState filters=new VisualFilterState();
 private static final ShockwaveZoomState zooms=new ShockwaveZoomState();
 private static final List<Wave> waves=new ArrayList<>();private static net.minecraft.client.multiplayer.ClientLevel previous;
 public static void receive(ShockwavePacket p){var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(p.dimension())||!p.valid())return;if(previous!=level){waves.clear();zooms.clear();filters.clear();previous=level;}if(waves.size()>=MAX_WAVES)waves.removeFirst();waves.add(new Wave(p,level.getGameTime()));}
 public static void receive(dev.sablelife.module.ShockwaveZoomPacket p){var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(p.dimension())||!p.valid())return;if(previous!=level){waves.clear();zooms.clear();filters.clear();previous=level;}zooms.accept(p,level.getGameTime());}
 public static void receive(dev.sablelife.module.VisualFilterPacket p){var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(p.dimension())||!p.valid())return;if(previous!=level){waves.clear();zooms.clear();filters.clear();previous=level;}filters.accept(p,level.getGameTime());}
 private static double now(double partial){var level=Minecraft.getInstance().level;return level==null?0:level.getGameTime()+partial;}
 public static List<Wave> waves(){return List.copyOf(waves);}
 public static void clear(){filters.clear();zooms.clear();waves.clear();previous=null;ShockwaveRenderer.release();}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=previous){clear();return;}filters.tick(mc.level.getGameTime());zooms.tick(mc.level.getGameTime());waves.removeIf(w->mc.level.getGameTime()-w.born()>w.packet().lifetime()+2);}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;var mc=Minecraft.getInstance();float scale=mc.options.screenEffectScale().get().floatValue();if(scale<=0)return;double time=now(event.getPartialTick().getGameTimeDeltaPartialTick(false));var tint=filters.tint(time);if(waves.isEmpty()&&tint.alpha()<=0)return;ShockwaveRenderer.render(mc.getMainRenderTarget(),waves,event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix(),time,scale,tint);}
 @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){var mc=Minecraft.getInstance();if(mc.level==null||waves.isEmpty())return;double ageTime=now(event.getPartialTick()),yaw=0,pitch=0,roll=0,scale=mc.options.screenEffectScale().get();for(var w:waves){var p=w.packet();double age=ageTime-w.born(),amp=3*p.shake()*ShockwaveMath.envelope(age,p.shakeTicks())*ShockwaveMath.attenuation(event.getCamera().getPosition().distanceTo(p.point()),p.range())*scale;double seed=w.born()*.17; yaw+=Math.sin(age*2.7+seed)*amp;pitch+=Math.sin(age*3.9+seed)*amp;roll+=Math.sin(age*2.1+seed)*amp*.6;}event.setYaw(event.getYaw()+(float)Math.clamp(yaw,-3,3));event.setPitch(event.getPitch()+(float)Math.clamp(pitch,-3,3));event.setRoll(event.getRoll()+(float)Math.clamp(roll,-2,2));}
 @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){var mc=Minecraft.getInstance();if(!event.usedConfiguredFov()||mc.level==null||mc.level!=previous)return;double t=now(event.getPartialTick()),zoom=zooms.zoom(t,event.getCamera().getPosition());for(var w:waves){var p=w.packet();zoom+=p.zoom()*ShockwaveMath.envelope(t-w.born(),p.zoomTicks())*ShockwaveMath.attenuation(event.getCamera().getPosition().distanceTo(p.point()),p.range());}zoom=Math.clamp(zoom*mc.options.screenEffectScale().get(),-100,30);event.setFOV(Math.clamp(event.getFOV()*(1-zoom/100),1,179));}
}
