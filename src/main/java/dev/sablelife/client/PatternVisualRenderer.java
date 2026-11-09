package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

/** Uses the proven full-bright font material and real depth, including under Iris shader packs. */
public final class PatternVisualRenderer extends RenderType {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"runtime/pattern_stroke");
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
    private static RenderType plain,shader;
    private static boolean texture;
    private static long renderedStrokes,renderedSymbols;
    private static RenderType material(){
        if(!texture){
            var image=new NativeImage(8,8,false);
            for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                int alpha=(int)(255*Math.min(1,Math.min(Math.min(x+.5,7.5-x),Math.min(y+.5,7.5-y))/1.5));
                image.setPixelRGBA(x,y,(alpha<<24)|0xFFFFFF);
            }
            var dynamic=new DynamicTexture(image);Minecraft.getInstance().getTextureManager().register(WHITE,dynamic);
            dynamic.setFilter(true,false);texture=true;
        }
        boolean shaders=ShaderParticleCompat.active();
        var old=shaders?shader:plain;if(old!=null)return old;
        var created=create(shaders?"sablelife_pattern_shader":"sablelife_pattern",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,65536,false,true,CompositeState.builder()
                .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeTextShader))
                .setTextureState(new TextureStateShard(WHITE,true,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_DEPTH_WRITE)
                .setOutputState(shaders?MAIN_TARGET:ITEM_ENTITY_TARGET).createCompositeState(false));
        if(shaders)shader=created;else plain=created;return created;
    }
    public static void release(){if(texture)Minecraft.getInstance().getTextureManager().release(WHITE);texture=false;plain=null;shader=null;}
    private static Vec3 local(Vec3 p,Vec3 origin,Vec3 u,Vec3 v,Vec3 n){return origin.add(u.scale(p.x)).add(v.scale(p.y)).add(n.scale(p.z));}
    public static void strokes(RenderLevelStageEvent event,Vec3 origin,Vec3 normal,Vec3 up,int rgb,List<PatternGeometry.Stroke> lines){
        var camera=event.getCamera().getPosition();var out=BUFFERS.getBuffer(material());var matrix=event.getPoseStack().last().pose();
        var u=up.cross(normal).normalize();var v=normal.cross(u).normalize();
        for(var line:lines){
            var a=local(line.a(),origin,u,v,normal);var b=local(line.b(),origin,u,v,normal);
            var side=b.subtract(a).cross(camera.subtract(a)).normalize();
            if(side.lengthSqr()<.1)side=u;side=side.scale(line.width()*.5);
            int color=((int)(line.alpha()*255)<<24)|(rgb&0xFFFFFF);
            vertex(out,matrix,a.add(side).subtract(camera),0,0,color);vertex(out,matrix,a.subtract(side).subtract(camera),0,1,color);
            vertex(out,matrix,b.subtract(side).subtract(camera),1,1,color);vertex(out,matrix,b.add(side).subtract(camera),1,0,color);
            renderedStrokes++;
        }
    }
    private static void vertex(VertexConsumer out,org.joml.Matrix4f matrix,Vec3 p,float u,float v,int color){
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v).setLight(LightTexture.FULL_BRIGHT);
    }
    public static void symbol(RenderLevelStageEvent event,PatternVisual packet,Vec3 origin,double age){
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        var text=PatternVisual.SYMBOLS.get(packet.symbol());double t=age/packet.duration();
        double fade=Math.min(1,age/Math.min(4,packet.duration()*.15)+.08)*Math.min(1,(1-t)*7);
        float scale=packet.size()/mc.font.lineHeight*(float)(1+.06*Math.sin(age*.3*packet.speed()));
        pose.pushPose();pose.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);pose.mulPose(event.getCamera().rotation());
        pose.translate(0,Math.sin(t*Math.PI)*packet.size()*.16,0);pose.scale(scale,-scale,scale);
        mc.font.drawInBatch(text,-mc.font.width(text)/2F,-mc.font.lineHeight/2F,((int)(fade*255)<<24)|packet.rgb(),
                false,pose.last().pose(),BUFFERS,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        BUFFERS.endBatch();pose.popPose();renderedSymbols++;
    }
    public static void finish(){BUFFERS.endBatch();}
    private PatternVisualRenderer(){super("sablelife_pattern",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,65536,false,true,()->{},()->{});}
}
