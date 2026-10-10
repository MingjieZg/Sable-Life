package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AbstractMagicProjectile.class,remap=false)
public abstract class IronMagicProjectileMixin {
 @ModifyExpressionValue(method="handleHitDetection",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private BlockHitResult sablelife$worldCollision(BlockHitResult hit){return (BlockHitResult)IronSpellsBodyBridge.worldHit((AbstractMagicProjectile)(Object)this,hit);}
 @ModifyVariable(method="onHit",at=@At("HEAD"),argsOnly=true)
 private HitResult sablelife$worldImpact(HitResult hit){return IronSpellsBodyBridge.worldHit((AbstractMagicProjectile)(Object)this,hit);}
 @Inject(method="onHit",at=@At("HEAD"))
 private void sablelife$directBodyHit(HitResult hit,CallbackInfo ci){IronSpellsBodyBridge.direct((AbstractMagicProjectile)(Object)this,hit);}
}
