package dev.sablelife.testing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.sablelife.client.PreviewLineStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Compare actual pixel coverage against vanilla lines, using the same shader and geometry. */
final class PreviewLineGpuCheck {
    private static void line(BufferBuilder b,float x,float y,float x2,float y2){
        float dx=x2-x,dy=y2-y,len=(float)Math.hypot(dx,dy);
        b.addVertex(x,y,-2).setColor(255,241,173,255).setNormal(dx/len,dy/len,0);
        b.addVertex(x2,y2,-2).setColor(255,241,173,255).setNormal(dx/len,dy/len,0);
    }

    static void run(Minecraft mc)throws Exception{
        int width=mc.getWindow().getWidth(),height=mc.getWindow().getHeight();
        var target=new TextureTarget(width,height,true,Minecraft.ON_OSX);
        var model=RenderSystem.getModelViewStack();var color=RenderSystem.getShaderColor().clone();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        RenderSystem.backupProjectionMatrix();model.pushMatrix();
        var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        try{
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0,width,0,height,.1F,10),VertexSorting.ORTHOGRAPHIC_Z);
            model.identity();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShaderFogStart(10000);RenderSystem.setShaderFogEnd(20000);
            int[] pixels=new int[2];
            for(int i=0;i<2;i++){
                target.bindWrite(true);RenderSystem.disableScissor();RenderSystem.clearColor(0,0,0,1);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
                var b=Tesselator.getInstance().begin(VertexFormat.Mode.LINES,DefaultVertexFormat.POSITION_COLOR_NORMAL);
                float left=width*.2F,right=width*.8F,low=height*.25F+.25F,high=height*.75F+.25F;
                line(b,left,low,right,low);line(b,right,low,right,high);line(b,right,high,left,high);line(b,left,high,left,low);line(b,left,low,right,high);
                (i==0?RenderType.lines():PreviewLineStyle.worldLines()).draw(b.buildOrThrow());
                if(RenderSystem.getShaderLineWidth()!=1)throw new AssertionError("Preview line width leaked into later rendering");
                try(var image=Screenshot.takeScreenshot(target)){
                    image.writeToFile(folder.resolve(i==0?"preview-lines-before.png":"preview-lines-thicker.png"));
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((image.getPixelRGBA(x,y)&255)>40)pixels[i]++;
                }
            }
            if(pixels[0]<100||pixels[1]<pixels[0]*1.15)throw new AssertionError("Preview lines did not get visibly thicker: "+pixels[0]+" -> "+pixels[1]);
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Preview lines GPU error");
            System.out.println("Preview lines GPU passed: coverage "+pixels[0]+" -> "+pixels[1]+", original color and line-width reset preserved");
        }finally{
            model.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);RenderSystem.clearColor(0,0,0,1);
        }
    }
}
