package dev.sablelife.client;

import dev.sablelife.SableLife;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Iris-compatible vanilla text material; no custom world shader or spawned entity. */
final class ShaderHealthBarRenderer extends RenderType {
    private record Key(float width,float height,int style){}
    private record Images(ResourceLocation base,ResourceLocation fill,float inset){}
    private static final Map<Key,Images> cache=new LinkedHashMap<>(16,.75F,true);
    private static final Map<ResourceLocation,RenderType> types=new HashMap<>();
    private static int serial;
    private static double distance(double x,double y,double halfX,double halfY,double radius){
        double qx=Math.abs(x)-halfX+radius,qy=Math.abs(y)-halfY+radius;
        return Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-radius;
    }
    private static double coverage(double d,double feather){double t=Math.clamp((d+feather)/(2*feather),0,1);return 1-t*t*(3-2*t);}
    private static int color(double r,double g,double b,double a){return (int)Math.round(r*255)|((int)Math.round(g*255)<<8)|((int)Math.round(b*255)<<16)|((int)Math.round(a*255)<<24);}
    private static ResourceLocation register(NativeImage image,String suffix){
        var id=ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"runtime/health_bar_"+serial+suffix);
        var texture=new DynamicTexture(image);Minecraft.getInstance().getTextureManager().register(id,texture);texture.setFilter(true,false);return id;
    }
    private static Images images(float width,float height,int style){
        var key=new Key(width,height,style);var existing=cache.get(key);if(existing!=null)return existing;
        int w=width>=height?512:32,h=width>=height?32:512;
        var base=new NativeImage(w,h,false);var fill=new NativeImage(w,h,false);
        double shortSide=Math.min(width,height),halfX=width*.5-shortSide*.035,halfY=height*.5-shortSide*.035;
        double radius=style==1?Math.min(halfX,halfY):shortSide*.06,rim=shortSide*.15,feather=shortSide*.035;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double px=((x+.5)/w-.5)*width,py=((y+.5)/h-.5)*height;
            double outer=coverage(distance(px,py,halfX,halfY,radius),feather);
            double inner=coverage(distance(px,py,halfX-rim,halfY-rim,Math.max(0,radius-rim)),feather);
            double border=Math.max(0,outer-inner),content=inner*.78,alpha=border+content;
            base.setPixelRGBA(x,y,alpha<.004?0:color((.075*content+55./255*border)/alpha,(.025*content+53./255*border)/alpha,(.035*content+66./255*border)/alpha,alpha));
            // Iris text programs handle vertex tint consistently; retain only shape alpha here.
            fill.setPixelRGBA(x,y,color(1,1,1,inner));
        }
        serial++;var created=new Images(register(base,"_base"),register(fill,"_fill"),(float)(halfX-rim));cache.put(key,created);
        if(cache.size()>32){var oldest=cache.keySet().iterator().next();var removed=cache.remove(oldest);var textures=Minecraft.getInstance().getTextureManager();textures.release(removed.base);textures.release(removed.fill);types.remove(removed.base);types.remove(removed.fill);}
        return created;
    }
    private static void quad(PoseStack pose,MultiBufferSource.BufferSource buffers,ResourceLocation texture,float width,float height,float end,int tint){
        // Write real billboard depth as well as color. Otherwise TAA reprojects it as sky,
        // smearing the bar into the background even though the material draws it.
        var type=types.computeIfAbsent(texture,id->create("sablelife_shader_health_bar",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,256,false,true,
                CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getRendertypeTextShader))
                        .setTextureState(new TextureStateShard(id,true,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false)));
        var out=buffers.getBuffer(type);var matrix=pose.last().pose();float right=(end-.5F)*width;
        out.addVertex(matrix,-width/2,-height/2,0).setColor(tint).setUv(0,1).setLight(15728880);
        out.addVertex(matrix,right,-height/2,0).setColor(tint).setUv(end,1).setLight(15728880);
        out.addVertex(matrix,right,height/2,0).setColor(tint).setUv(end,0).setLight(15728880);
        out.addVertex(matrix,-width/2,height/2,0).setColor(tint).setUv(0,0).setLight(15728880);
        buffers.endBatch(type);
    }
    static void draw(PoseStack pose,MultiBufferSource.BufferSource buffers,float width,float height,float fraction,int style){
        var textures=images(width,height,style);quad(pose,buffers,textures.base,width,height,1,-1);
        // Avoid coplanar rejection in shader-pack depth passes. Both layers remain wall-occluded.
        if(fraction>0){pose.pushPose();pose.translate(0,0,.005);quad(pose,buffers,textures.fill,width,height,fraction>=1?1:(-textures.inset+2*textures.inset*fraction)/width+.5F,0xFFE0080E);pose.popPose();}
    }
    private ShaderHealthBarRenderer(){super("sablelife_shader_health_bar",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
}

