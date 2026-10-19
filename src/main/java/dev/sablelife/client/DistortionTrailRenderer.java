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
public final class DistortionTrailRenderer {
 public static final int MAX_TRAILS=32;
 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
 private static long renderedPasses;
 private static ShaderInstance shader;private static TextureTarget copy;private static boolean failed;
 @SubscribeEvent public static void shaders(RegisterShadersEvent event){try{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"distortion_trail"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;failed=false;});}catch(java.io.IOException|RuntimeException e){shader=null;failed=true;LOG.error("Sable Life distortion trail shader unavailable; other rendering remains enabled",e);}}
 public static boolean ready(){return shader!=null&&!failed;}
 public static void release(){if(copy!=null){copy.destroyBuffers();copy=null;}}
 public static void render(RenderTarget target,List<DistortionTrailState.Birth> births,Vec3 camera,Matrix4f view,Matrix4f projection,double time,float scale){
  if(!ready()||scale<=0||target.width<=0||target.height<=0||target.width>8192||target.height>8192||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)return;
  var visible=new ArrayList<float[]>();
  for(int n=1;n<births.size();n++){
   var old=births.get(n-1);var birth=births.get(n);var p=birth.packet();
   // Packet identity is a stroke boundary: never join different modules, restarts or teleports.
   if(old.packet()!=p)continue;
   double age=time-birth.born(),fade=DistortionTrailState.fade(age,p.life())*ShockwaveMath.attenuation(camera.distanceTo(birth.sample().center()),p.range())*scale*p.strength();if(fade<=0)continue;
   var v=DistortionTrailMath.ribbon(old.sample(),birth.sample(),new Vec3(p.width()/2,p.height()/2,p.length()/2),camera,view,projection,(float)target.width/target.height,.2+.8*Math.sqrt(DistortionTrailState.fade(age,p.life())));if(v==null)continue;
   visible.add(new float[]{v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],(float)fade});
  }
  var projected=visible;
  if(projected.isEmpty())return;
  boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);var oldShader=RenderSystem.getShader();int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);int[] viewport=new int[4],scissorBox=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
  try{
   RenderSystem.disableScissor();
   if(copy==null||copy.width!=target.width||copy.height!=target.height){release();copy=new TextureTarget(target.width,target.height,false,Minecraft.ON_OSX);copy.setFilterMode(GL11.GL_LINEAR);}
   for(int batch=0;batch<projected.size();batch+=MAX_TRAILS){
   GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,copy.frameBufferId);GlStateManager._glBlitFrameBuffer(0,0,target.width,target.height,0,0,copy.width,copy.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);target.bindWrite(false);RenderSystem.viewport(0,0,target.width,target.height);
   RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShader(()->shader);shader.setSampler("Scene",copy.getColorTextureId());shader.safeGetUniform("ViewportSize").set((float)target.width,(float)target.height);
   for(int i=0;i<MAX_TRAILS;i++){var v=batch+i<projected.size()?projected.get(batch+i):new float[9];shader.safeGetUniform("Trail"+i).set(v[0],v[1],v[2],v[3]);shader.safeGetUniform("End"+i).set(v[4],v[5],v[6],v[7]);shader.safeGetUniform("Fx"+i).set(v[8],0f,0f,0f);}
   var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.addVertex(0,0,0).setUv(0,0);b.addVertex(1,0,0).setUv(1,0);b.addVertex(1,1,0).setUv(1,1);b.addVertex(0,1,0).setUv(0,1);BufferUploader.drawWithShader(b.buildOrThrow());renderedPasses++;
   }
  }catch(RuntimeException e){failed=true;LOG.error("Sable Life distortion trail rendering disabled after an error",e);}finally{GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);target.bindWrite(false);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(mask);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.setShader(()->oldShader);}
 }
}
