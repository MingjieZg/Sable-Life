package dev.sablelife.mixin;

import dev.sablelife.heart.SurvivalLifeCoreBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Normal despawn and damage destruction only. Pickup and merging also use discard. */
@Mixin(ItemEntity.class)
public abstract class DestroyedEnergyItemMixin {
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/ItemEntity;discard()V",shift=At.Shift.AFTER))
    private void sablelife$expired(CallbackInfo ci){
        SurvivalLifeCoreBlock.retireDestroyedClaim((Entity)(Object)this);
    }
    @Inject(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/ItemEntity;discard()V",shift=At.Shift.AFTER))
    private void sablelife$destroyed(DamageSource source,float damage,CallbackInfoReturnable<Boolean> cir){
        SurvivalLifeCoreBlock.retireDestroyedClaim((Entity)(Object)this);
    }
}
