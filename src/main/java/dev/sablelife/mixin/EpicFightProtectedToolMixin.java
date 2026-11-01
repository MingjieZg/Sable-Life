package dev.sablelife.mixin;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.api.client.camera.EpicFightCameraAPI;
@Mixin(value=LocalPlayerPatch.class,remap=false)
public abstract class EpicFightProtectedToolMixin {
 @Inject(method="canPlayAttackAnimation",at=@At("RETURN"),cancellable=true)
 private void sablelife$attackProtectedBody(CallbackInfoReturnable<Boolean> cir) {
    var patch=(LocalPlayerPatch)(Object)this;var mc=Minecraft.getInstance();
    if(cir.getReturnValue()||patch.isVanillaMode()||mc.level==null||mc.player==null)return;
    var camera=EpicFightCameraAPI.getInstance();var hit=mc.hitResult;var crosshair=camera.getCrosshairHitResult();
    if(camera.isTPSMode()&&crosshair!=null&&crosshair.getLocation().distanceToSqr(mc.player.getEyePosition())<mc.player.blockInteractionRange()*mc.player.blockInteractionRange())hit=crosshair;
    if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK){
        var heart=SableBodyProbe.findActivatedHeart(mc.level,block.getBlockPos());
        if(heart!=null&&!heart.allowBreaking())cir.setReturnValue(true);
    }
 }
}
