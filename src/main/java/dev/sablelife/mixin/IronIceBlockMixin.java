package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronAreaBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.ice_block.IceBlockProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IceBlockProjectile.class,remap=false)
public abstract class IronIceBlockMixin {
 @Inject(method="handleFalling",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",remap=true),cancellable=true)
 private void sablelife$fallingBody(CallbackInfo ci){if(IronAreaBodyBridge.iceContact((IceBlockProjectile)(Object)this))ci.cancel();}
 @Inject(method="doImpactDamage",at=@At("HEAD"))
 private void sablelife$bodyBurst(CallbackInfo ci){IronAreaBodyBridge.iceBurst((IceBlockProjectile)(Object)this);}
}
