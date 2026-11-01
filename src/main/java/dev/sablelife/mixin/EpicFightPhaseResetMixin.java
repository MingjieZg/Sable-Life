package dev.sablelife.mixin;

import dev.sablelife.compat.epicfight.EpicFightBodyBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo @Mixin(targets="yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch",remap=false)
public abstract class EpicFightPhaseResetMixin {
    @Inject(method="removeHurtEntities",at=@At("HEAD"))
    private void sablelife$nextStrike(CallbackInfo ci){EpicFightBodyBridge.reset(this);}
}
