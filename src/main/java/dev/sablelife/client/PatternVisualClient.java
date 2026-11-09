package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class PatternVisualClient {
    public record Birth(PatternVisual packet,long born){}
    private static final Map<UUID,Birth> effects=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    public static void receive(PatternVisual packet){
        var level=Minecraft.getInstance().level;
        if(level!=world){effects.clear();world=level;}
        if(level==null||!packet.valid()||!level.dimension().location().equals(packet.dimension()))return;
        effects.put(packet.id(),new Birth(packet,level.getGameTime()));
        while(effects.size()>PatternVisual.MAX_EFFECTS)effects.remove(effects.keySet().iterator().next());
    }
    public static void clear(){effects.clear();world=null;PatternVisualRenderer.release();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(level==null||level!=world){if(world!=null)clear();return;}
        effects.values().removeIf(effect->level.getGameTime()-effect.born()>=effect.packet().duration());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        // Iris text programs must participate in the entity pass. Photon discards later
        // custom text-material geometry, even though ordinary font glyphs remain visible.
        var stage=ShaderParticleCompat.active()?RenderLevelStageEvent.Stage.AFTER_ENTITIES:RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if(event.getStage()!=stage)return;
        var mc=Minecraft.getInstance();if(world==null||world!=mc.level||effects.isEmpty())return;
        var camera=event.getCamera().getPosition();double partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now=world.getGameTime()+partial;int strokes=0;
        for(var effect:List.copyOf(effects.values())){
            var packet=effect.packet();boolean symbol=packet.pattern()==6;
            double age=now-effect.born();if(age<0||age>=packet.duration())continue;
            Vec3 point=packet.point(),normal=packet.normal(),up=packet.up();
            if(packet.follow()){
                var container=SubLevelContainer.getContainer(world);
                var body=container==null?null:container.getSubLevel(packet.anchor().bodyId());
                if(!(body instanceof ClientSubLevel client)||body.isRemoved()
                        ||world.getBlockState(packet.anchor().position(body)).isAir()){effects.remove(packet.id());continue;}
                var local=ModuleHealthBar.localPoint(packet.anchor(),body,packet.startDistance());
                var render=client.renderPose((float)partial);point=render.transformPosition(local);
                var localNormal=Vec3.atLowerCornerOf(packet.anchor().face().getNormal());
                normal=render.transformPosition(local.add(localNormal)).subtract(point).normalize();
                var right=localNormal.cross(Math.abs(localNormal.y)>.9?new Vec3(0,0,1):new Vec3(0,1,0)).normalize();
                up=render.transformPosition(local.add(localNormal.cross(right))).subtract(point).normalize();
            }
            if(point.distanceToSqr(camera)>packet.range()*packet.range())continue;
            if(symbol){PatternVisualRenderer.symbol(event,packet,point,age);continue;}
            var lines=PatternGeometry.strokes(packet.pattern(),age,packet.duration(),packet.speed(),packet.size());
            if(strokes+lines.size()>8192)break;
            PatternVisualRenderer.strokes(event,point,normal,up,packet.rgb(),lines);strokes+=lines.size();
        }
        PatternVisualRenderer.finish();
    }
    private PatternVisualClient(){}
}
