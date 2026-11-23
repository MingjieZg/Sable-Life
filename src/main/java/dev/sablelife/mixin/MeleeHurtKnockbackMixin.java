package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelife.attack.BodyMeleeSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MeleeHurtKnockbackMixin {
    @WrapOperation(method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void sablelife$applyHurtKnockback(LivingEntity victim, double strength, double x, double z,
            Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
        if (source instanceof BodyMeleeSource melee && melee.suppressesVanillaKnockback()) return;
        original.call(victim, strength, x, z);
    }
}
