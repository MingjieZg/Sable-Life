package dev.sablelife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/** Shared viewer-only line weight; never changes Minecraft's global line render type. */
public abstract class PreviewLineStyle extends RenderType {
    private static final float WEIGHT=1.4F;
    private PreviewLineStyle(){super("sablelife_preview_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,()->{},()->{});}

    static LineStateShard widthState(float base,boolean scaleWithWindow){
        return new LineStateShard(OptionalDouble.of(base*WEIGHT)){
            @Override public void setupRenderState(){
                float width=scaleWithWindow?Math.max(base,Minecraft.getInstance().getWindow().getWidth()/1920F*base):base;
                RenderSystem.lineWidth(width*WEIGHT);
            }
        };
    }

    private static final RenderType WORLD_LINES=create("sablelife_preview_lines",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(widthState(2.5F,true))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET).setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).createCompositeState(false));

    public static RenderType worldLines(){return WORLD_LINES;}
}
