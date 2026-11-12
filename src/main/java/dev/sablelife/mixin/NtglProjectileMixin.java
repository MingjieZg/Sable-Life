package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelife.combat.BodyDamageBridge;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.combat.ExternalProjectileDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nukateam.ntgl.common.foundation.entity.ProjectileEntity", remap = false)
public abstract class NtglProjectileMixin {
    @Shadow public abstract float getDamage();
    @Shadow private DamageSource getDamageSource() { throw new AssertionError(); }

    // The native GunProjectileHitEvent has already been accepted here.
    @Inject(method = "onHit", at = @At(value = "INVOKE", target = "Lcom/nukateam/ntgl/common/foundation/entity/ProjectileEntity;onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void sablelife$hitBody(HitResult hit, Vec3 start, Vec3 end, CallbackInfo callback) {
        if (!(hit instanceof BlockHitResult block)) return;
        Entity projectile = (Entity) (Object) this;
        if (!(projectile.level() instanceof net.minecraft.server.level.ServerLevel level)
                || dev.ryanhcode.sable.Sable.HELPER.getContaining(level, block.getBlockPos()) == null) return;
        BodyDamageBridge.externalProjectile(new ExternalProjectileDamageSource(getDamageSource(), projectile), block,
                ExternalProjectileDamageSource.withImpactBonus(projectile, getDamage()));
    }

    @WrapOperation(method = "damageEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", remap = true))
    private boolean sablelife$identifyReceiverHit(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        if (target instanceof EntityHitbox) {
            Entity projectile = (Entity) (Object) this;
            source = new ExternalProjectileDamageSource(source, projectile);
            damage = ExternalProjectileDamageSource.withImpactBonus(projectile, damage);
        }
        return original.call(target, source, damage);
    }

    @Inject(method = "burnEntity", at = @At("HEAD"), cancellable = true)
    private void sablelife$skipReceiverIgnition(Entity target, CallbackInfo callback) {
        if (target instanceof EntityHitbox) callback.cancel();
    }
}
