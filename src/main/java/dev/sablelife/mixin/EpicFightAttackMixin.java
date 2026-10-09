package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelife.compat.epicfight.EpicFightBodyBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo @Mixin(targets="yesman.epicfight.api.animation.types.AttackAnimation",remap=false)
public abstract class EpicFightAttackMixin {
    @WrapMethod(method="hurtCollidingEntities")
    private void sablelife$bodyStrike(@Coerce Object patch,float previousTime,float time,@Coerce Object previousState,@Coerce Object state,@Coerce Object phase,Operation<Void> original) {
        var frame=EpicFightBodyBridge.begin(patch,this,phase);
        try {original.call(patch,previousTime,time,previousState,state,phase);EpicFightBodyBridge.finish(frame);}
        finally {EpicFightBodyBridge.close(frame);}
    }
    @Inject(method="getMaxStrikes",at=@At("RETURN"),cancellable=true)
    private void sablelife$sharedStrikeLimit(@Coerce Object patch,@Coerce Object phase,CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EpicFightBodyBridge.remaining(patch,cir.getReturnValue()));
    }
}
