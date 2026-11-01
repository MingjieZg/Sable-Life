package dev.sablelife.client;

import dev.sablelife.SableLife;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Draw the existing see-through respawn label after shader compositing, at its world projection. */
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class RespawnLabelClient {
    private static Matrix4f view,projection;
    private static Vec3 camera;
    private static net.minecraft.client.multiplayer.ClientLevel capturedLevel;
    private RespawnLabelClient() {}

    public static boolean replaces(Display.TextDisplay text) {
        return ShaderParticleCompat.active() && ((dev.sablelife.mixin.RespawnTextDisplayAccess)(Object)text).sablelife$getText().getContents() instanceof TranslatableContents translation
                && translation.getKey().startsWith("arena_respawn.sablelife.");
    }

    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        capturedLevel=Minecraft.getInstance().level;
        view=new Matrix4f(event.getModelViewMatrix());projection=new Matrix4f(event.getProjectionMatrix());
        camera=event.getCamera().getPosition();
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=capturedLevel||view==null||mc.options.hideGui||!ShaderParticleCompat.active())return;
        var g=event.getGuiGraphics();int width=g.guiWidth(),height=g.guiHeight();
        for(var entity:mc.level.entitiesForRendering()) {
            if(!(entity instanceof Display.TextDisplay text)||!replaces(text)||!text.shouldRenderAtSqrDistance(text.position().distanceToSqr(camera)))continue;
            var relative=text.position().subtract(camera);
            var eye=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1).mul(view);
            var clip=new Vector4f(eye).mul(projection);
            if(clip.w<=0||clip.z < -clip.w||clip.z>clip.w)continue;
            float x=(clip.x/clip.w+1)*width/2,y=(1-clip.y/clip.w)*height/2;
            float scale=Math.abs(projection.m11()*height*.0125F/clip.w);
            if(scale<=0)continue;
            var lines=mc.font.split(((dev.sablelife.mixin.RespawnTextDisplayAccess)(Object)text).sablelife$getText(),256);int max=0;
            for(var line:lines)max=Math.max(max,mc.font.width(line));
            if(x+(max/2F+2)*scale<0||x-(max/2F+2)*scale>width||y<0||y-lines.size()*10*scale>height)continue;
            var pose=g.pose();pose.pushPose();pose.translate(x,y-lines.size()*10*scale,0);pose.scale(scale,scale,1);
            g.fill(-max/2-2,-1,max/2+2,lines.size()*10,0xAA101018);
            for(int i=0;i<lines.size();i++)g.drawString(mc.font,lines.get(i),-mc.font.width(lines.get(i))/2,i*10,0xFFFFFFFF,true);
            pose.popPose();
        }
    }
}
