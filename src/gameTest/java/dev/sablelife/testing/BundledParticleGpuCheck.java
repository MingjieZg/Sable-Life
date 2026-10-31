package dev.sablelife.testing;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.particle.Particle;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.sablelife.SableLife;

/** Read back real GPU pixels with 32 coincident copies at three different camera rotations. */
public final class BundledParticleGpuCheck {
 public static void run(Minecraft mc)throws Exception{
  var target=new TextureTarget(512,384,true,Minecraft.ON_OSX);var model=RenderSystem.getModelViewStack();float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();var color=RenderSystem.getShaderColor().clone();
  RenderSystem.backupProjectionMatrix();model.pushMatrix();
  var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
  try{
   RenderSystem.setProjectionMatrix(new Matrix4f().ortho(-3,3,-2.25F,2.25F,.1F,100),VertexSorting.ORTHOGRAPHIC_Z);
   RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);RenderSystem.setShaderColor(1,1,1,1);mc.gameRenderer.lightTexture().turnOnLightLayer();RenderSystem.enableCull();
   for(int view=0;view<3;view++){
    var camera=new Camera();camera.rotation().rotationXYZ(view*.18F,view*.37F,view*.11F);model.identity().rotate(new Quaternionf(camera.rotation()).conjugate());RenderSystem.applyModelViewMatrix();
    target.setClearColor(0,0,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
    var dot=mc.particleEngine.createParticle(SableLife.ENERGY_MOTE.get(),0,0,-5,0,0,0);var star=mc.particleEngine.createParticle(SableLife.STARBURST.get(),0,0,-5,0,0,0);
    if(dot==null||star==null)throw new AssertionError("Shape provider unavailable");dot.scale(4);star.scale(2);dot.setColor(1,0,0);star.setColor(1,1,1);dot.setLifetime(80);star.setLifetime(80);
    for(int n=0;n<10;n++){dot.tick();star.tick();}
    place(dot,new Vector3f(-1.5F,0,-5).rotate(camera.rotation()));place(star,new Vector3f(1.3F,0,-5).rotate(camera.rotation()));
    var buffer=dot.getRenderType().begin(Tesselator.getInstance(),mc.getTextureManager());
    for(int n=0;n<32;n++){dot.render(buffer,camera,1);star.render(buffer,camera,1);}var mesh=buffer.build();if(mesh==null)throw new AssertionError("Empty shape mesh");BufferUploader.drawWithShader(mesh);
    try(var image=Screenshot.takeScreenshot(target)){
     image.writeToFile(folder.resolve("particle-overlap-camera-"+view+".png"));
     // Interior of the red dot must contain no missing radial wedges, including its center.
     int cx=128,cy=192;
     for(int dy=-22;dy<=22;dy++)for(int dx=-22;dx<=22;dx++)if(dx*dx+dy*dy<22*22){int p=image.getPixelRGBA(cx+dx,cy+dy);if((p&255)<220||(p>>8&255)>35||(p>>16&255)>35)throw new AssertionError("Dot pixel gap at view "+view+" offset "+dx+","+dy+" color "+Integer.toHexString(p));}
     // Center and all four arms of the fixed star must remain joined.
     int sx=Math.round(256+1.3F*512/6);for(int axis=0;axis<4;axis++)for(int distance=0;distance<=50;distance++){int px=sx+(axis==0?distance:axis==2?-distance:0),py=cy+(axis==1?distance:axis==3?-distance:0);int p=image.getPixelRGBA(px,py);if((p&255)<220||(p>>8&255)<220||(p>>16&255)<220)throw new AssertionError("Star pixel gap at view "+view+" arm "+axis+" distance "+distance);}
    }finally{dot.remove();star.remove();}
   }
   System.out.println("Bundled particle GPU passed: solid dot interior and joined fixed-star center/arms, 32 overlaps at 3 camera rotations");
  }finally{model.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.enableCull();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
 }
 private static void place(Particle p,Vector3f world)throws Exception{p.setPos(world.x,world.y,world.z);for(String key:new String[]{"xo","yo","zo"}){var f=Particle.class.getDeclaredField(key);f.setAccessible(true);f.setDouble(p,key.equals("xo")?world.x:key.equals("yo")?world.y:world.z);}}
}
