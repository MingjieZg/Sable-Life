package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronContinuousBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BloodSlashProjectile.class,remap=false)
public abstract class IronBloodSlashMixin {
 @Inject(method="handleHitDetection",at=@At("HEAD"),cancellable=true)
 private void sablelife$bodyBox(CallbackInfo ci){if(IronContinuousBodyBridge.bloodSlash((BloodSlashProjectile)(Object)this))ci.cancel();}
 @Inject(method="onHitBlock",at=@At("HEAD"))
 private void sablelife$bodyBlock(BlockHitResult hit,CallbackInfo ci){IronContinuousBodyBridge.bloodBlock((BloodSlashProjectile)(Object)this,hit);}
}
