package dev.sablelife.client;
import dev.sablelife.SableLife;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ShockwaveRenderer {
 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
 private static long renderedPasses;
 private static ShaderInstance shader;private static TextureTarget copy;private static boolean failed;
 @SubscribeEvent public static void shaders(RegisterShadersEvent event){try{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"visual_shockwave"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;failed=false;});}catch(java.io.IOException|RuntimeException e){shader=null;failed=true;LOG.error("Sable Life shockwave shader unavailable; other rendering remains enabled",e);}}
 public static boolean ready(){return shader!=null&&!failed;}
 public static void release(){if(copy!=null){copy.destroyBuffers();copy=null;}}
 public static void render(RenderTarget target,List<ShockwaveClient.Wave> waves,Vec3 camera,Matrix4f view,Matrix4f projection,double time,float scale){
  render(target,waves,camera,view,projection,time,scale,VisualFilterState.Tint.NONE);
 }
 public static void render(RenderTarget target,List<ShockwaveClient.Wave> waves,Vec3 camera,Matrix4f view,Matrix4f projection,double time,float scale,VisualFilterState.Tint filter){
  if(!ready()||scale<=0||target.width<=0||target.height<=0||target.width>8192||target.height>8192||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)return;
  var chosen=waves.stream().filter(w->time>=w.born()&&time-w.born()<w.packet().expansion()).sorted(Comparator.comparingLong(ShockwaveClient.Wave::born).reversed().thenComparingDouble(w->camera.distanceToSqr(w.packet().point()))).toList();
  var projected=new ArrayList<float[]>();
  for(var w:chosen){
   if(projected.size()>=ShockwaveClient.MAX_WAVES)break;
   var p=w.packet();var screen=ShockwaveMath.project(p.point(),camera,view,projection);
   if(screen==null)continue;
   float fade=(float)ShockwaveMath.attenuation(camera.distanceTo(p.point()),p.range())*scale;
   double age=time-w.born();
   if(fade<=0||p.distortion()==0&&p.blur()==0)continue;
   if(p.pattern()==1){
    float strength=p.blur()*fade*(float)ShockwaveMath.envelope(age,p.expansion());
    if(strength<=0)continue;
    projected.add(new float[]{screen.x(),screen.y(),0,1,0,strength,1,p.clearRadius()});
   }else{
    double radius=ShockwaveMath.radius(age,p.expansion(),p.radius(),p.reverse());
    float strength=fade*(float)ShockwaveMath.waveStrength(age,p.expansion());
    projected.add(new float[]{screen.x(),screen.y(),(float)Math.min(8,screen.unitRadius()*radius),
            (float)Math.max(1f/target.height,screen.unitRadius()*Math.min(p.width(),Math.max(.01,radius*.5))),
            p.distortion()*strength,p.blur()*strength,0,0});
   }
  }
  if(projected.isEmpty()&&filter.alpha()<=0)return;
  boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);var oldShader=RenderSystem.getShader();int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);int[] viewport=new int[4],scissorBox=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
  try{
   RenderSystem.disableScissor();
   if(copy==null||copy.width!=target.width||copy.height!=target.height){release();copy=new TextureTarget(target.width,target.height,false,Minecraft.ON_OSX);copy.setFilterMode(GL11.GL_LINEAR);}
   GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,copy.frameBufferId);GlStateManager._glBlitFrameBuffer(0,0,target.width,target.height,0,0,copy.width,copy.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);target.bindWrite(false);RenderSystem.viewport(0,0,target.width,target.height);
   RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShader(()->shader);shader.setSampler("Scene",copy.getColorTextureId());shader.safeGetUniform("ViewportSize").set((float)target.width,(float)target.height);shader.safeGetUniform("FilterColor").set(filter.red(),filter.green(),filter.blue(),Math.clamp(filter.alpha()*scale,0,1));
   for(int i=0;i<ShockwaveClient.MAX_WAVES;i++){var v=i<projected.size()?projected.get(i):new float[8];shader.safeGetUniform("Wave"+i).set(v[0],v[1],v[2],v[3]);shader.safeGetUniform("Fx"+i).set(v[4],v[5],v[6],v[7]);}
   var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.addVertex(0,0,0).setUv(0,0);b.addVertex(1,0,0).setUv(1,0);b.addVertex(1,1,0).setUv(1,1);b.addVertex(0,1,0).setUv(0,1);BufferUploader.drawWithShader(b.buildOrThrow());renderedPasses++;
  }catch(RuntimeException e){failed=true;LOG.error("Sable Life shockwave rendering disabled after an error",e);}finally{GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);target.bindWrite(false);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(mask);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.setShader(()->oldShader);}
 }
}
