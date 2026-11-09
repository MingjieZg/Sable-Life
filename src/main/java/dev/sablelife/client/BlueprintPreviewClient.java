package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.MarkOutline;
import dev.sablelife.module.BlueprintProjection;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Viewer-only projection and outlines, with no blocks or entities added to the world. */
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class BlueprintPreviewClient {
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static ModuleNetworking.BlueprintPreview preview;
    private static long expires;
    private static net.minecraft.client.multiplayer.ClientLevel projectionWorld;
    private static BlueprintProjection projection;
    private static long projectionExpires;
    private record Outline(MarkOutline packet,long expires) {}
    private static net.minecraft.client.multiplayer.ClientLevel markWorld;
    private static final java.util.Map<MarkOutline.Kind,Outline> marks=new java.util.EnumMap<>(MarkOutline.Kind.class);
    public static void receive(BlueprintProjection packet){
        var level=Minecraft.getInstance().level;
        if(level==null)return;
        projection=packet;
        projectionWorld=level;
        projectionExpires=level.getGameTime()+112;
    }
    public static void receive(MarkOutline packet){
        var level=Minecraft.getInstance().level;
        if(level==null||!level.dimension().location().equals(packet.dimension())
                ||packet.endpoints().stream().anyMatch(p->!Double.isFinite(p.lengthSqr())))return;
        if(level!=markWorld){marks.clear();markWorld=level;}
        marks.put(packet.kind(),new Outline(packet,level.getGameTime()+6));
    }
    public static void receive(ModuleNetworking.BlueprintPreview packet){
        var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        if(!Double.isFinite(packet.min().lengthSqr())||!Double.isFinite(packet.max().lengthSqr())||!Double.isFinite(packet.anchor().lengthSqr())||!Double.isFinite(packet.forward().lengthSqr()))return;
        world=level;preview=packet;expires=level.getGameTime()+Math.clamp(packet.duration(),1,100);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(preview!=null&&(Minecraft.getInstance().level!=world||world==null||world.getGameTime()>expires)){preview=null;world=null;}
        if(projectionWorld!=null&&(Minecraft.getInstance().level!=projectionWorld||projectionWorld.getGameTime()>projectionExpires)){
            projection=null;projectionWorld=null;
        }
        if(Minecraft.getInstance().level!=markWorld||markWorld==null){marks.clear();markWorld=null;}
        else marks.values().removeIf(mark->markWorld.getGameTime()>mark.expires());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();
        renderMarks(event,mc);
        if(preview==null)return;if(mc.level!=world||world==null||world.getGameTime()>expires){preview=null;world=null;return;}
        if(projection!=null&&world==projectionWorld&&projection.id().equals(preview.projection()))renderProjection(event,mc);
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(PreviewLineStyle.worldLines());
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        boolean entity=preview.forward().lengthSqr()==0;
        LevelRenderer.renderLineBox(pose,lines,new AABB(preview.min(),preview.max()),entity?1F:.2F,1F,entity?1F:.85F,1F);
        pose.popPose();buffers.endBatch(PreviewLineStyle.worldLines());
    }
    private static void renderProjection(RenderLevelStageEvent event,Minecraft mc){
        var pose=event.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();
        var layer=Sheets.translucentItemSheet();
        var vertices=new GhostVertices(buffers.getBuffer(layer));
        var position=preview.anchor().subtract(event.getCamera().getPosition());
        var dispatcher=mc.getBlockRenderer();
        pose.pushPose();
        try{
            pose.translate(position.x,position.y,position.z);
            pose.mulPose(preview.rotation());
            for(var part:projection.parts()){
                pose.pushPose();
                try{
                    pose.translate(part.origin().x,part.origin().y,part.origin().z);
                    pose.mulPose(part.rotation());
                    pose.scale((float)part.scale().x,(float)part.scale().y,(float)part.scale().z);
                    for(var block:part.blocks()){
                        var state=Block.stateById(block.state());
                        if(state.isAir())continue;
                        pose.pushPose();
                        try{
                            pose.translate(block.x(),block.y(),block.z());
                            dispatcher.renderSingleBlock(state,pose,ignored->vertices,LightTexture.FULL_BRIGHT,
                                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY,layer);
                        }finally{pose.popPose();}
                    }
                }finally{pose.popPose();}
            }
        }catch(RuntimeException failure){
            projection=null;
            com.mojang.logging.LogUtils.getLogger().warn("Unable to render blueprint block projection",failure);
        }finally{
            pose.popPose();
            buffers.endBatch(layer);
        }
    }
    private record GhostVertices(VertexConsumer delegate) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x,float y,float z){delegate.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(r,g,b,a*112/255);return this;}
        @Override public VertexConsumer setUv(float u,float v){delegate.setUv(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){delegate.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
    }
    private static void renderMarks(RenderLevelStageEvent event,Minecraft mc){
        if(marks.isEmpty()||mc.level!=markWorld)return;
        var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        var vertices=buffers.getBuffer(PreviewLineStyle.worldLines());
        for(var mark:marks.values()){
            var points=mark.packet().endpoints();
            for(int i=0;i<points.size();i+=2){
                var a=points.get(i).subtract(camera);var b=points.get(i+1).subtract(camera);
                if(a.distanceToSqr(b)>1e-12)line(event.getPoseStack(),vertices,a,b,mark.packet().color());
            }
        }
        buffers.endBatch(PreviewLineStyle.worldLines());
    }
    static void line(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,Vec3 a,Vec3 b,int color){
        var n=b.subtract(a).normalize();var matrix=pose.last().pose();
        int r=color>>16&255,g=color>>8&255,blue=color&255;
        vertices.addVertex(matrix,(float)a.x,(float)a.y,(float)a.z).setColor(r,g,blue,255).setNormal((float)n.x,(float)n.y,(float)n.z);
        vertices.addVertex(matrix,(float)b.x,(float)b.y,(float)b.z).setColor(r,g,blue,255).setNormal((float)n.x,(float)n.y,(float)n.z);
    }
    private BlueprintPreviewClient(){}
}
