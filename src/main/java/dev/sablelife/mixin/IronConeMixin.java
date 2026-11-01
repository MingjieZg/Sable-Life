package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronContinuousBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AbstractConeProjectile.class,remap=false)
public abstract class IronConeMixin {
 @Shadow protected float damage;
 @Inject(method="tick",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/entity/spells/AbstractConeProjectile;getSubEntityCollisions()Ljava/util/Set;"))
 private void sablelife$nativeWindow(CallbackInfo ci){IronContinuousBodyBridge.cone((AbstractConeProjectile)(Object)this,damage);}
}
