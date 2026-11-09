package dev.sablelife.mixin;

import dev.sablelife.combat.*;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class HitboxMobSightMixin {
    @Inject(method="hasLineOfSight",at=@At("HEAD"),cancellable=true)
    private void sablelife$receiverSurfaceSight(Entity target,CallbackInfoReturnable<Boolean> result) {
        if(target instanceof EntityHitbox receiver&&(Object)this instanceof Mob mob)
            result.setReturnValue(HitboxMobGeometry.visible(mob,receiver));
    }
}
