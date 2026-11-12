package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.combat.ExternalProjectileDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.nukateam.cgs.common.faundation.entity.BlazeProjectile", remap = false)
public abstract class CgsBlazeProjectileMixin implements ExternalProjectileDamageSource.ImpactBonus {
    // This version applies a fixed five damage after the base projectile's entity hit.
    @Override
    public float sablelife$impactBonus() { return 5; }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", remap = true))
    private boolean sablelife$skipSeparateReceiverDamage(Entity target, DamageSource source, float amount,
                                                         Operation<Boolean> original) {
        return !(target instanceof EntityHitbox) && original.call(target, source, amount);
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V", remap = true))
    private void sablelife$skipReceiverFire(Entity target, float seconds, Operation<Void> original) {
        if (!(target instanceof EntityHitbox)) original.call(target, seconds);
    }
}
