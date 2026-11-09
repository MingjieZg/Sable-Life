package dev.sablelife.testing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sablelife.client.ShockwaveClient;
import dev.sablelife.client.ShockwaveMath;
import dev.sablelife.client.ShockwaveRenderer;
import dev.sablelife.module.ShockwavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/** Real framebuffer checks: world-point centre, protected core and transient cleanup. */
final class RadialBlurGpuCheck {
    private static ShockwavePacket packet(Vec3 point,float strength){
        return new ShockwavePacket(ResourceLocation.withDefaultNamespace("overworld"),point,18,40,1.5F,0,strength,0,1,0,1,256,false,1,1,.08F);
    }

    private static void pattern(TextureTarget target){
        target.bindWrite(true);RenderSystem.disableScissor();
        for(int x=0;x<target.width;x+=12)for(int y=0;y<target.height;y+=12){
            RenderSystem.enableScissor(x,y,Math.min(12,target.width-x),Math.min(12,target.height-y));
            boolean light=(x/12+y/12)%2==0;
            RenderSystem.clearColor(light?.9F:.1F,.15F+.6F*x/target.width,.15F+.6F*y/target.height,1);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
        }
        RenderSystem.disableScissor();
    }

    private static int difference(int a,int b){
        int max=0;for(int s=0;s<24;s+=8)max=Math.max(max,Math.abs((a>>s&255)-(b>>s&255)));return max;
    }

    static void run(Minecraft mc)throws Exception{
        var target=new TextureTarget(512,384,false,Minecraft.ON_OSX);
        var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        var projection=new Matrix4f().perspective((float)Math.PI/2,(float)target.width/target.height,.05F,200);
        var p=packet(new Vec3(-3,2,-10),.9F);
        try{
            pattern(target);
            try(var clean=Screenshot.takeScreenshot(target)){
                clean.writeToFile(folder.resolve("radial-baseline.png"));
                for(var camera:List.of(Vec3.ZERO,new Vec3(5,0,0))){
                    pattern(target);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
                    ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(p,0)),camera,new Matrix4f(),projection,8,1);
                    if(!ShockwaveRenderer.ready()||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                            ||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)throw new AssertionError("Radial pass leaked render state");
                    var center=ShockwaveMath.project(p.point(),camera,new Matrix4f(),projection);
                    if(Math.abs(center.x()-.5)<.05)throw new AssertionError("Fixture did not use off-centre emitter");
                    int clearChanged=0,outerChanged=0;
                    try(var actual=Screenshot.takeScreenshot(target)){
                        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++){
                            double u=(x+.5)/target.width,v=1-(y+.5)/target.height;
                            double distance=Math.hypot((u-center.x())*target.width/target.height,v-center.y());
                            if(difference(clean.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>2){
                                if(distance<p.clearRadius()-.004)clearChanged++;
                                if(distance>.28)outerChanged++;
                            }
                        }
                        if(clearChanged!=0||outerChanged<1000)throw new AssertionError("Radial clear centre/streaks failed: "+clearChanged+"/"+outerChanged);
                        actual.writeToFile(folder.resolve(camera==Vec3.ZERO?"radial-emitter.png":"radial-camera-shift.png"));
                        System.out.println("Radial GPU centre="+center.x()+","+center.y()+", protected changes="+clearChanged+", outer changes="+outerChanged);
                    }
                }
                for(int test=0;test<4;test++){
                    pattern(target);
                    var sample=test==0?packet(new Vec3(-3,2,10),.9F):test==1?packet(p.point(),0):p;
                    ShockwaveRenderer.render(target,List.of(new ShockwaveClient.Wave(sample,0)),Vec3.ZERO,new Matrix4f(),projection,test==2?41:8,test==3?0:1);
                    try(var actual=Screenshot.takeScreenshot(target)){
                        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)
                            if(difference(clean.getPixelRGBA(x,y),actual.getPixelRGBA(x,y))>2)throw new AssertionError("Inactive radial changed image, case="+test);
                    }
                }
            }
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Radial GPU error");
            System.out.println("Radial blur GPU passed: projected emitter, camera shift, clear centre, outer streaks, behind/zero/expired/client-disabled and render state");
        }finally{target.destroyBuffers();ShockwaveRenderer.release();mc.getMainRenderTarget().bindWrite(true);RenderSystem.clearColor(0,0,0,1);}
    }
}
