package dev.sablelife.mixin;
import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.heart.CreatureNames;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
public abstract class ModuleDeathMessageMixin {
    @Inject(method="getLocalizedDeathMessage",at=@At("HEAD"),cancellable=true)
    private void sablelife$projectileDeath(LivingEntity victim,CallbackInfoReturnable<Component> cir){
        var source=(DamageSource)(Object)this;var projectile=source.getDirectEntity();
        if(projectile!=null&&projectile.getPersistentData().contains(ModuleDamage.ATTACK_NAME))
            cir.setReturnValue(ModuleDamage.deathMessage(victim,CreatureNames.killer(projectile.getPersistentData().getString(ModuleDamage.ATTACK_NAME))));
    }
}
