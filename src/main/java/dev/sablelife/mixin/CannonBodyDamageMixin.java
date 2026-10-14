package dev.sablelife.mixin;
import dev.sablelife.combat.BodyDamageBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Capture the actual impact before CBC can remove/penetrate the hit block. */
@Pseudo @Mixin(targets={"rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile","rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile","com.dsvv.cbcat.cannon.heavy_autocannon.munitions.AbstractHeavyAutocannonProjectile"},remap=false)
public abstract class CannonBodyDamageMixin {
 @Inject(method="calculateBlockPenetration",at=@At("HEAD"))
 private void sablelife$cannonHit(@Coerce Object context,BlockState state,BlockHitResult hit,CallbackInfoReturnable<Object> cir){BodyDamageBridge.cannon((Entity)(Object)this,hit);}
}
