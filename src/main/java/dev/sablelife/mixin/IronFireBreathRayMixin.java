package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=FireBreathProjectile.class,remap=false)
public abstract class IronFireBreathRayMixin {
 @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private BlockHitResult sablelife$fireRay(BlockHitResult hit){return IronSpellsBodyBridge.worldBlockHit(((FireBreathProjectile)(Object)this).level(),hit);}
}
