package dev.sablelife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Use handheld geometry while leaving orientation to the projectile's flight renderer. */
public final class ProjectileItemRenderer {
    private static final ItemDisplayContext CONTEXT=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    public static void render(ItemRenderer items, ItemStack item, Level level, int seed, float diameter,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        var original=items.getModel(item,level,null,seed);
        var probe=new PoseStack();
        var held=net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(probe,original,CONTEXT,false);
        var handTransform=new org.joml.Matrix4f(probe.last().pose());
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        if(!held.isCustomRenderer()) for(var pass:held.getRenderPasses(item,true)) for(int face=0;face<7;face++) {
            for(var quad:pass.getQuads(null,face==6 ? null : Direction.values()[face],RandomSource.create(42))) {
                int[] data=quad.getVertices(); int stride=data.length/4;
                for(int vertex=0;vertex<4;vertex++) {
                    int offset=vertex*stride;
                    var v=new org.joml.Vector3f(Float.intBitsToFloat(data[offset]),Float.intBitsToFloat(data[offset+1]),Float.intBitsToFloat(data[offset+2]));
                    v.sub(.5F,.5F,.5F);
                    for(int axis=0;axis<3;axis++) { min[axis]=Math.min(min[axis],v.get(axis)); max[axis]=Math.max(max[axis],v.get(axis)); }
                }
            }
        }
        float extent=Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]));
        pose.pushPose();
        if(Float.isFinite(extent) && extent>.001F) {
            float scale=diameter/extent; pose.scale(scale,scale,scale);
            pose.translate(-(min[0]+max[0])/2,-(min[1]+max[1])/2,-(min[2]+max[2])/2);
        } else pose.scale(diameter,diameter,diameter);
        // Keep the handheld model/context, but cancel its grip pose. Otherwise the grip rotation
        // is applied after the flight rotation and turns a flying sword's tip away from its target.
        if(Math.abs(handTransform.determinant())>.000001F) pose.mulPose(handTransform.invert());
        items.render(item,CONTEXT,false,pose,buffers,light,OverlayTexture.NO_OVERLAY,original);
        pose.popPose();
    }
    private ProjectileItemRenderer() {}
}
