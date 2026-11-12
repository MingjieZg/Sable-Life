package dev.sablelife.mixin;

import dev.sablelife.combat.BodyExplosionDamage;
import dev.sablelife.ownership.BodyProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nukateam.ntgl.common.util.world.ProjectileExplosion", remap = false)
public abstract class NtglExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private DamageSource damageSource;
    @Shadow @Final private float radius;
    @Shadow @Final private float damage;
    @Shadow @Final private boolean damageDecreaseWithDistance;

    @Inject(method = "explode", at = @At("HEAD"))
    private void sablelife$damageStructures(CallbackInfo callback) {
        if (level instanceof ServerLevel server) {
            BodyExplosionDamage.externalDetonation(server, (Explosion) (Object) this, damageSource,
                    radius * 2.0, damage, damageDecreaseWithDistance);
        }
    }

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void sablelife$protectStructureBlocks(boolean particles, CallbackInfo callback) {
        if (level instanceof ServerLevel server) {
            ((Explosion) (Object) this).getToBlow().removeIf(pos -> BodyProtection.explosionLocked(server, pos));
        }
    }
}
