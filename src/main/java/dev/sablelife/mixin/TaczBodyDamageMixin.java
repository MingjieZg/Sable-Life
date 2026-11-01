package dev.sablelife.mixin;
import dev.sablelife.combat.BodyDamageBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Runs after TaCZ's cancelable AmmoHitBlockEvent check, before native block effects. */
@Pseudo @Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class TaczBodyDamageMixin {
 @Inject(method="onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/Projectile;onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V"))
 private void sablelife$gunHit(BlockHitResult hit,Vec3 start,Vec3 end,CallbackInfo ci){BodyDamageBridge.projectile((Entity)(Object)this,hit);}
}
