package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.ShockwavePacket;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ShockwaveClientPreview {
 private static boolean started;private static int ticks;
 private static ShockwavePacket packet(Vec3 point,float strength,float blur){return new ShockwavePacket(ResourceLocation.withDefaultNamespace("overworld"),point,6,20,.8f,strength,blur,.15f,8,4,10,64);}
 private static Matrix4f projection(RenderTarget t){return new Matrix4f().perspective((float)Math.PI/2,(float)t.width/t.height,.05f,200);}
 private static void pattern(RenderTarget target){target.bindWrite(true);RenderSystem.disableScissor();int cell=12;for(int x=0;x<target.width;x+=cell)for(int y=0;y<target.height;y+=cell){RenderSystem.enableScissor(x,y,Math.min(cell,target.width-x),Math.min(cell,target.height-y));boolean light=(x/cell+y/cell)%2==0;RenderSystem.clearColor(light?.9f:.1f,.15f+.6f*x/target.width,.15f+.6f*y/target.height,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);}RenderSystem.disableScissor();}
 private static int difference(int a,int b){int d=0;for(int s=0;s<24;s+=8)d=Math.max(d,Math.abs((a>>s&255)-(b>>s&255)));return d;}
 private static void verify(Minecraft mc)throws Exception{
  DistortionTrailGpuCheck.run(mc);
  VisualFilterGpuCheck.run(mc);
  if(!ShockwaveRenderer.ready())throw new AssertionError("Shockwave shader did not compile/register");var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);var target=new TextureTarget(512,384,false,Minecraft.ON_OSX);var p=packet(new Vec3(-3,2,-10),1,.5f);var wave=new ShockwaveClient.Wave(p,0);var proj=projection(target);
  try{pattern(target);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);try(var baseline=Screenshot.takeScreenshot(target)){
   baseline.writeToFile(folder.resolve("shockwave-baseline.png"));target.bindWrite(true);ShockwaveRenderer.render(target,List.of(wave),Vec3.ZERO,new Matrix4f(),proj,10,1);
   if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)throw new AssertionError("Post effect leaked depth/framebuffer state");
   if(!ShockwaveRenderer.ready())throw new AssertionError("GPU post effect failed");
   try(var actual=Screenshot.takeScreenshot(target)){
    actual.writeToFile(folder.resolve("shockwave-distorted.png"));var screen=ShockwaveMath.project(p.point(),Vec3.ZERO,new Matrix4f(),proj);double radius=screen.unitRadius()*p.radius()*.5,width=screen.unitRadius()*p.width();int inside=0,outside=0;
    for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){double u=(x+.5)/target.width,v=1-(y+.5)/target.height;double d=Math.hypot((u-screen.x())*target.width/target.height,v-screen.y());if(difference(baseline.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>2){if(Math.abs(d-radius)<=width+.01)inside++;else outside++;}}
    if(inside<50||outside>10)throw new AssertionError("Ring locality failed: inside="+inside+", outside="+outside);System.out.println("GPU ring locality passed: changed ring pixels="+inside+", changed outside="+outside);
   }
   double[] measured=new double[2];int sampleIndex=0;
   for(int duration:new int[]{100,20}){var travel=new ShockwavePacket(p.dimension(),new Vec3(0,0,-30),30,duration,1.5f,1,0,0,1,0,1,64);pattern(target);try(var clean=Screenshot.takeScreenshot(target)){ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(travel,0)),Vec3.ZERO,new Matrix4f(),proj,5,1);try(var image=Screenshot.takeScreenshot(target)){var centre=ShockwaveMath.project(travel.point(),Vec3.ZERO,new Matrix4f(),proj);double r=centre.unitRadius()*ShockwaveMath.radius(5,duration,30),width=centre.unitRadius()*Math.min(1.5,ShockwaveMath.radius(5,duration,30)*.5);int changed=0;double max=0;for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(difference(clean.getPixelRGBA(x,y),image.getPixelRGBA(x,y))>2){double d=Math.hypot(((x+.5)/target.width-centre.x())*target.width/target.height,1-(y+.5)/target.height-centre.y());if(Math.abs(d-r)>width+.01)throw new AssertionError("Radius-30 early wave escaped its travel position");changed++;max=Math.max(max,d);}if(changed<10)throw new AssertionError("Small initial wave invisible: "+duration);measured[sampleIndex++]=max;image.writeToFile(folder.resolve("shockwave-radius30-duration"+duration+"-age5.png"));System.out.println("Radius30 early GPU sample duration="+duration+", pixels="+changed+", extent="+max);}}}
   double previousReverseExtent=Double.POSITIVE_INFINITY;
   var reverse=new ShockwavePacket(p.dimension(),new Vec3(0,0,-15),6,20,.8f,1,0,0,1,0,1,64,true);
   for(int age:new int[]{4,14}){pattern(target);try(var clean=Screenshot.takeScreenshot(target)){
    ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(reverse,0)),Vec3.ZERO,new Matrix4f(),proj,age,1);
    try(var image=Screenshot.takeScreenshot(target)){var centre=ShockwaveMath.project(reverse.point(),Vec3.ZERO,new Matrix4f(),proj);double expected=centre.unitRadius()*ShockwaveMath.radius(age,20,6,true),extent=0;int changed=0;
     for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(difference(clean.getPixelRGBA(x,y),image.getPixelRGBA(x,y))>2){double d=Math.hypot(((x+.5)/target.width-centre.x())*target.width/target.height,1-(y+.5)/target.height-centre.y());if(Math.abs(d-expected)>centre.unitRadius()*.8+.01)throw new AssertionError("Reverse GPU ring escaped expected radius");extent=Math.max(extent,d);changed++;}
     if(changed<20||extent>=previousReverseExtent)throw new AssertionError("Reverse GPU ring did not contract");previousReverseExtent=extent;image.writeToFile(folder.resolve("shockwave-reverse-age"+age+".png"));
    }
   }}
   System.out.println("Reverse shockwave GPU ring contracts from outside toward center");
   if(measured[0]>=measured[1])throw new AssertionError("Longer expansion moved faster on actual GPU");
   for(int interval:new int[]{1,2,5,9}){var rapidPacket=new ShockwavePacket(p.dimension(),new Vec3(0,0,-30),30,100,1.5f,1,0,0,1,0,1,64);var rapid=new ArrayList<ShockwaveClient.Wave>();for(int i=7;i>=1;i--)rapid.add(new ShockwaveClient.Wave(rapidPacket,61L-i*interval));pattern(target);ShockwaveRenderer.render(target,rapid,Vec3.ZERO,new Matrix4f(),proj,64,1);try(var oldOnly=Screenshot.takeScreenshot(target)){rapid.add(new ShockwaveClient.Wave(rapidPacket,61));pattern(target);ShockwaveRenderer.render(target,rapid,Vec3.ZERO,new Matrix4f(),proj,64,1);try(var latest=Screenshot.takeScreenshot(target)){int centreChanges=0;for(int y=target.height/2-10;y<target.height/2+10;y++)for(int x=target.width/2-10;x<target.width/2+10;x++)if(difference(oldOnly.getPixelRGBA(x,y),latest.getPixelRGBA(x,y))>2)centreChanges++;if(centreChanges<8)throw new AssertionError("Rapid release lost newest centre at interval="+interval+", changes="+centreChanges);System.out.println("Rapid-release GPU centre visible: intervalTicks="+interval+", retained="+rapid.size()+", centrePixels="+centreChanges);latest.writeToFile(folder.resolve("shockwave-rapid-interval"+interval+".png"));}}}
   for(var test:List.of(packet(new Vec3(-3,2,10),1,.5f),packet(new Vec3(-3,2,-10),0,0))){pattern(target);ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(test,0)),Vec3.ZERO,new Matrix4f(),proj,10,1);try(var image=Screenshot.takeScreenshot(target)){for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(difference(baseline.getPixelRGBA(x,y),image.getPixelRGBA(x,y))>2)throw new AssertionError("Behind/disabled ring altered scene");}}
   pattern(target);ShockwaveRenderer.render(target,List.of(wave),Vec3.ZERO,new Matrix4f(),proj,21,1);try(var expired=Screenshot.takeScreenshot(target)){for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)if(difference(baseline.getPixelRGBA(x,y),expired.getPixelRGBA(x,y))>2)throw new AssertionError("Expired wave altered scene");}
  }
  target.resize(640,360,Minecraft.ON_OSX);pattern(target);ShockwaveRenderer.render(target,List.of(wave),Vec3.ZERO,new Matrix4f(),projection(target),10,1);if(!ShockwaveRenderer.ready()||GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Resize rendering failed");System.out.println("Shockwave GPU tests passed: locality, behind, disabled, expired, state restore, framebuffer resize");
  }finally{target.destroyBuffers();ShockwaveRenderer.release();mc.getMainRenderTarget().bindWrite(true);RenderSystem.clearColor(0,0,0,1);}
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.shockwavePreview")||Boolean.getBoolean("sablelife.shockwaveWorldPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;verify(mc);mc.setScreen(new Preview());return;}if(++ticks==25){var folder=mc.gameDirectory.toPath().resolve("screenshots");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("shockwave-preview.png"));}}if(ticks>=65){System.out.println("Shockwave preview complete");mc.stop();}}
 private static final class Preview extends Screen {Preview(){super(Component.literal("World-anchored visual shockwave"));}@Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){g.flush();var target=Minecraft.getInstance().getMainRenderTarget();pattern(target);var p=packet(new Vec3(-3,2,-10),1,.5f);ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(p,0)),Vec3.ZERO,new Matrix4f(),projection(target),(ticks%40)/2.,1);g.drawString(font,"Visual shockwave - world point above-left of centre",12,12,0xFFFFFF,true);g.drawString(font,"Only the expanding ring refracts the background. This text stays sharp.",12,26,0xFFFFFF,true);}}
}
