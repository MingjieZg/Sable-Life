package dev.sablelife.mixin;

import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the initial attack click; suppress held-key mining particles, cracks and repeated digs. */
@Mixin(Minecraft.class)
public abstract class ProtectedBodyMiningMixin {
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void sablelife$protectedAttack(boolean held,CallbackInfo ci) {
        var mc=(Minecraft)(Object)this;
        if(!held||mc.level==null||mc.player==null||mc.gameMode==null||mc.player.isSpectator()
                ||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return;
        var heart=SableBodyProbe.findActivatedHeart(mc.level,hit.getBlockPos());
        if(heart!=null&&(!heart.allowBreaking()
                ||!mc.player.isCreative()&&!heart.isSurvivalCore()&&heart.getBlockPos().equals(hit.getBlockPos()))) {
            mc.gameMode.stopDestroyBlock();
            ci.cancel();
        }
    }
}
