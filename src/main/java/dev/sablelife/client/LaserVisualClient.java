package dev.sablelife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.module.LaserVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class LaserVisualClient {
    private record Frame(LaserVisual previous,LaserVisual visual,long updatedAt,long expires){}
    private static final Map<UUID,Frame> beams=new LinkedHashMap<>();
    private static ClientLevel world;

    public static void receive(LaserVisual visual){
        var level=Minecraft.getInstance().level;
        if(level!=world){beams.clear();world=level;}
        if(level==null)return;
        if(visual.ends().isEmpty()){beams.remove(visual.id());return;}
        if(!Float.isFinite(visual.width())||visual.width()<=0||!finite(visual.start())
                ||visual.body()!=null&&!finite(visual.localStart())||visual.ends().stream().anyMatch(end->!finite(end.point())))return;
        long now=level.getGameTime();
        var old=beams.get(visual.id());
        var previous=old==null||old.expires()<now?visual:old.updatedAt()==now?old.previous():old.visual();
        if(!visual.interpolatesFrom(previous))previous=visual;
        beams.put(visual.id(),new Frame(previous,visual,now,now+3));
        if(beams.size()>256)beams.remove(beams.keySet().iterator().next());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(Minecraft.getInstance().level!=world){beams.clear();world=null;}
        if(world!=null)beams.values().removeIf(frame->frame.expires()<world.getGameTime());
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||beams.isEmpty()||world!=Minecraft.getInstance().level)return;
        var mc=Minecraft.getInstance();
        var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        var type=RenderType.debugQuads();
        var vertices=buffers.getBuffer(type);
        var container=SubLevelContainer.getContainer(world);
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var frame:beams.values()){
            var beam=frame.visual();
            double blend=Math.clamp(world.getGameTime()-frame.updatedAt()+partial,0,1);
            var start=frame.previous().start().lerp(beam.start(),blend);
            dev.ryanhcode.sable.companion.math.Pose3dc renderPose=null;
            if(beam.body()!=null&&container!=null&&container.getSubLevel(beam.body()) instanceof ClientSubLevel body&&!body.isRemoved()){
                renderPose=body.renderPose(partial);
                start=renderPose.transformPosition(frame.previous().localStart().lerp(beam.localStart(),blend));
            }
            for(int i=0;i<beam.ends().size();i++){
                var endpoint=beam.ends().get(i);
                if(endpoint.local()&&renderPose==null)continue;
                var point=frame.previous().ends().get(i).point().lerp(endpoint.point(),blend);
                var end=endpoint.local()?renderPose.transformPosition(point):point;
                draw(event.getPoseStack(),vertices,start.subtract(camera),end.subtract(camera),beam.width(),beam.rgb());
            }
        }
        buffers.endBatch(type);
    }

    private static void draw(PoseStack pose,VertexConsumer vertices,Vec3 start,Vec3 end,float width,int color){
        var direction=end.subtract(start);
        if(direction.lengthSqr()<1e-8)return;
        var side=direction.cross(start.add(end).scale(.5)).normalize();
        if(side.lengthSqr()<1e-8)side=direction.cross(Math.abs(direction.normalize().y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        double radius=Math.clamp(width*.25,.025,1.5);
        quad(pose,vertices,start,end,side.scale(radius*1.8),color,65);
        quad(pose,vertices,start,end,side.scale(radius),color,230);
        int core=((color&0xFEFEFE)>>>1)+0x777777;
        quad(pose,vertices,start,end,side.scale(radius*.28),core,255);
    }

    private static void quad(PoseStack pose,VertexConsumer vertices,Vec3 start,Vec3 end,Vec3 side,int color,int alpha){
        vertex(pose,vertices,start.add(side),color,alpha);
        vertex(pose,vertices,start.subtract(side),color,alpha);
        vertex(pose,vertices,end.subtract(side),color,alpha);
        vertex(pose,vertices,end.add(side),color,alpha);
    }

    private static void vertex(PoseStack pose,VertexConsumer vertices,Vec3 p,int rgb,int alpha){
        vertices.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z)
                .setColor(rgb>>16&255,rgb>>8&255,rgb&255,alpha);
    }
    private static boolean finite(Vec3 value){return value!=null&&Double.isFinite(value.lengthSqr());}
    private LaserVisualClient(){}
}
