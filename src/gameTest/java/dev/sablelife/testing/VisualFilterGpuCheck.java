package dev.sablelife.testing;
import dev.sablelife.client.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.List;
final class VisualFilterGpuCheck {
 static void run(Minecraft mc)throws Exception{
  var target=new TextureTarget(256,192,false,Minecraft.ON_OSX);var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
  try{target.bindWrite(true);RenderSystem.disableScissor();RenderSystem.clearColor(.2F,.6F,.3F,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
   try(var before=Screenshot.takeScreenshot(target)){
    before.writeToFile(folder.resolve("filter-baseline.png"));var tint=new VisualFilterState.Tint(.7F,.2F,1,.4F);
    ShockwaveRenderer.render(target,List.of(),Vec3.ZERO,new Matrix4f(),new Matrix4f(),0,1,tint);
    if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)throw new AssertionError("Filter leaked depth/framebuffer state");
    try(var after=Screenshot.takeScreenshot(target)){
     after.writeToFile(folder.resolve("filter-purple.png"));for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){int a=before.getPixelRGBA(x,y),b=after.getPixelRGBA(x,y);float[] rgb={.7F,.2F,1};for(int ch=0;ch<3;ch++){double expected=(a>>(ch*8)&255)*.6+rgb[ch]*255*.4;if(Math.abs((b>>(ch*8)&255)-expected)>2)throw new AssertionError("Filter-only GPU pixel mismatch at "+x+","+y+", channel="+ch);}}
     ShockwaveRenderer.render(target,List.of(),Vec3.ZERO,new Matrix4f(),new Matrix4f(),0,1,VisualFilterState.Tint.NONE);try(var expired=Screenshot.takeScreenshot(target)){if(expired.getPixelRGBA(100,100)!=after.getPixelRGBA(100,100))throw new AssertionError("Empty filter changed scene");}
    }
   }if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Filter GL error");System.out.println("Visual filter GPU passed: all 49152 pixels match RGB blend; filter-only render, empty no-op, depth/framebuffer restore");
  }finally{target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);RenderSystem.clearColor(0,0,0,1);}
 }
}
