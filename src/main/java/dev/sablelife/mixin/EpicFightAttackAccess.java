package dev.sablelife.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Pseudo @Mixin(targets="yesman.epicfight.api.animation.types.AttackAnimation",remap=false)
public interface EpicFightAttackAccess {
    @Invoker(value="hurtCollidingEntities",remap=false)
    void sablelife$hurtColliding(LivingEntityPatch<?> patch,float previous,float current,EntityState previousState,EntityState state,AttackAnimation.Phase phase);
}
