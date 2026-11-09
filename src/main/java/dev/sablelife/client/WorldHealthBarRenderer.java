package dev.sablelife.client;

import dev.sablelife.SableLife;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** One textureless SDF quad: feathered rounded edges, depth tested and emissive. */
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class WorldHealthBarRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE=create("sablelife_world_health_bar",DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,256,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    // Fabulous composites item entities against water using this target's depth.
                    // Color-only bars inherit the background depth and incorrectly tint behind water.
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).setOutputState(ITEM_ENTITY_TARGET).createCompositeState(false));
    private static final MultiBufferSource.BufferSource NAME_BUFFERS=MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private WorldHealthBarRenderer(){super("sablelife_world_health_bar",DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"world_health_bar"),DefaultVertexFormat.POSITION_TEX),value->shader=value);
    }
    /** Same billboard and depth test as the bar; a separate batch isolates font materials. */
    public static void drawName(PoseStack pose,net.minecraft.network.chat.Component name,float barHeight,float size,float gap){
        var font=net.minecraft.client.Minecraft.getInstance().font;float scale=size/font.lineHeight;
        pose.pushPose();pose.translate(0,barHeight/2+gap+size,.002F);pose.scale(scale,-scale,scale);
        font.drawInBatch(name,-font.width(name)/2F,0,0xFFFFFFFF,false,pose.last().pose(),NAME_BUFFERS,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        NAME_BUFFERS.endBatch();pose.popPose();
    }
    public static void draw(PoseStack pose,MultiBufferSource.BufferSource buffers,float width,float height,float fraction,int style){
        if(ShaderParticleCompat.active()){ShaderHealthBarRenderer.draw(pose,buffers,width,height,fraction,style);return;}
        if(shader==null)return;
        shader.safeGetUniform("BarSize").set(width,height);shader.safeGetUniform("Fill").set(fraction);shader.safeGetUniform("Style").set((float)style);
        var out=buffers.getBuffer(TYPE);var matrix=pose.last().pose();
        out.addVertex(matrix,-width/2,-height/2,0).setUv(0,0);out.addVertex(matrix,width/2,-height/2,0).setUv(1,0);
        out.addVertex(matrix,width/2,height/2,0).setUv(1,1);out.addVertex(matrix,-width/2,height/2,0).setUv(0,1);
        buffers.endBatch(TYPE);
    }
}
