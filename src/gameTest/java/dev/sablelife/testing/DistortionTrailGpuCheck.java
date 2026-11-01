package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.DistortionTrailPacket;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;
public final class DistortionTrailGpuCheck {
 private static void pattern(RenderTarget t){t.bindWrite(true);RenderSystem.disableScissor();for(int x=0;x<t.width;x+=10)for(int y=0;y<t.height;y+=10){RenderSystem.enableScissor(x,y,Math.min(10,t.width-x),Math.min(10,t.height-y));boolean light=(x/10+y/10)%2==0;RenderSystem.clearColor(light?.9f:.1f,.2f+.5f*x/t.width,.2f+.5f*y/t.height,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);}RenderSystem.disableScissor();}
 private static int diff(int a,int b){int d=0;for(int s=0;s<24;s+=8)d=Math.max(d,Math.abs((a>>s&255)-(b>>s&255)));return d;}
 private static DistortionTrailPacket packet(Vec3 p){var q=new org.joml.Quaterniond().rotateY(Math.PI/2);return new DistortionTrailPacket(ResourceLocation.withDefaultNamespace("overworld"),new UUID(1,2),List.of(new DistortionTrailPacket.Sample(p, (float)q.x,(float)q.y,(float)q.z,(float)q.w),new DistortionTrailPacket.Sample(p.add(2,2,0),(float)q.x,(float)q.y,(float)q.z,(float)q.w)),.3f,.3f,7,20,1,256);}
 private static List<DistortionTrailState.Birth> births(DistortionTrailPacket p){return p.samples().stream().map(s->new DistortionTrailState.Birth(p,s,0)).toList();}
 public static void run(Minecraft mc)throws Exception{
  if(!DistortionTrailRenderer.ready())throw new AssertionError("Trail shader failed registration");var target=new TextureTarget(512,384,false,Minecraft.ON_OSX);var projection=new Matrix4f().perspective((float)Math.PI/2,512f/384,.05f,200);var p=packet(new Vec3(-2,1,-10));var birth=new DistortionTrailState.Birth(p,p.samples().getFirst(),0);var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
  try{pattern(target);try(var baseline=Screenshot.takeScreenshot(target)){
   baseline.writeToFile(folder.resolve("trail-baseline.png"));RenderSystem.enableDepthTest();RenderSystem.depthMask(true);DistortionTrailRenderer.render(target,births(p),Vec3.ZERO,new Matrix4f(),projection,2,1);
   if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)throw new AssertionError("Trail leaked render state");
   long early=0;int inside=0,outside=0;var shape=DistortionTrailMath.ribbon(p.samples().getFirst(),p.samples().getLast(),new Vec3(.15,.15,3.5),Vec3.ZERO,new Matrix4f(),projection,512f/384,1);
   try(var image=Screenshot.takeScreenshot(target)){image.writeToFile(folder.resolve("trail-active.png"));for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){int d=diff(baseline.getPixelRGBA(x,y),image.getPixelRGBA(x,y));double px=(x+.5)/target.height,py=1-(y+.5)/target.height;double minX=99,maxX=-99,minY=99,maxY=-99;for(int k=0;k<8;k+=2){minX=Math.min(minX,shape[k]);maxX=Math.max(maxX,shape[k]);minY=Math.min(minY,shape[k+1]);maxY=Math.max(maxY,shape[k+1]);}boolean outsideBox=px<minX-.01||px>maxX+.01||py<minY-.01||py>maxY+.01;if(d>2){if(outsideBox)outside++;else inside++;early+=d;}}}
   if(inside<100||outside>8)throw new AssertionError("Trail locality wrong: "+inside+"/"+outside);
   pattern(target);DistortionTrailRenderer.render(target,births(p),Vec3.ZERO,new Matrix4f(),projection,18,1);long late=0;try(var image=Screenshot.takeScreenshot(target)){image.writeToFile(folder.resolve("trail-fading.png"));for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)late+=diff(baseline.getPixelRGBA(x,y),image.getPixelRGBA(x,y));}if(late>=early)throw new AssertionError("Old trail did not fade: "+early+"/"+late);
   for(int test=0;test<3;test++){pattern(target);var b=test==0?new DistortionTrailState.Birth(packet(new Vec3(0,0,10)),packet(new Vec3(0,0,10)).samples().getFirst(),0):birth;DistortionTrailRenderer.render(target,births(b.packet()),Vec3.ZERO,new Matrix4f(),projection,test==1?21:2,test==2?0:1);try(var image=Screenshot.takeScreenshot(target)){for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(diff(baseline.getPixelRGBA(x,y),image.getPixelRGBA(x,y))>2)throw new AssertionError("Behind/expired/disabled trail altered scene: "+test);}}
  }
  target.resize(640,360,Minecraft.ON_OSX);pattern(target);DistortionTrailRenderer.render(target,births(p),Vec3.ZERO,new Matrix4f(),new Matrix4f().perspective((float)Math.PI/2,640f/360,.05f,200),3,1);if(!DistortionTrailRenderer.ready()||GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Trail framebuffer resize/GL failure");// More than one shader batch must retain the full path and restore state.
  var many=new ArrayList<DistortionTrailState.Birth>();for(int i=0;i<40;i++)many.addAll(births(packet(new Vec3(-3+i*.1,-2,-12))));pattern(target);DistortionTrailRenderer.render(target,many,Vec3.ZERO,new Matrix4f(),projection,2,1);if(!DistortionTrailRenderer.ready()||GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Multi-batch ribbon failed");
  // Unrelated packets, including stop/restart, must not create a bridge.
  pattern(target);var separated=List.of(new DistortionTrailState.Birth(p,p.samples().getFirst(),0),new DistortionTrailState.Birth(packet(new Vec3(3,0,-10)),p.samples().getLast(),0));try(var baseline=Screenshot.takeScreenshot(target)){DistortionTrailRenderer.render(target,separated,Vec3.ZERO,new Matrix4f(),projection,2,1);try(var image=Screenshot.takeScreenshot(target)){for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(diff(baseline.getPixelRGBA(x,y),image.getPixelRGBA(x,y))>2)throw new AssertionError("Separate strokes bridged");}}
  var curveSamples=new ArrayList<DistortionTrailPacket.Sample>();
  for(int i=0;i<=7;i++){double angle=Math.toRadians(20+i*18);var q=new org.joml.Quaterniond().rotateZ(angle).rotateY(Math.PI/2);var axis=new org.joml.Vector3d(0,0,3.5);q.transform(axis);curveSamples.add(new DistortionTrailPacket.Sample(new Vec3(axis.x,axis.y-2,-12+axis.z),(float)q.x,(float)q.y,(float)q.z,(float)q.w));}
  var curve=new DistortionTrailPacket(p.dimension(),p.module(),curveSamples,.3f,.3f,7,20,1,256);pattern(target);DistortionTrailRenderer.render(target,births(curve),Vec3.ZERO,new Matrix4f(),projection,2,1);try(var image=Screenshot.takeScreenshot(target)){image.writeToFile(folder.resolve("trail-curved-swing.png"));}
  // Straight thrust and pure rotation must both remain projectable.
  var half=new Vec3(.15,.15,3.5);if(DistortionTrailMath.ribbon(p.samples().getFirst(),p.samples().getLast(),half,Vec3.ZERO,new Matrix4f(),projection,512f/384,1)==null||DistortionTrailMath.ribbon(curveSamples.get(0),curveSamples.get(1),half,Vec3.ZERO,new Matrix4f(),projection,512f/384,1)==null)throw new AssertionError("Swing ribbon missing");
  System.out.println("Distortion trail GPU passed: localized oriented refraction, fading, behind/expired/disabled rejection, state restore, resize");
  }finally{target.destroyBuffers();DistortionTrailRenderer.release();mc.getMainRenderTarget().bindWrite(true);}
 }
}
