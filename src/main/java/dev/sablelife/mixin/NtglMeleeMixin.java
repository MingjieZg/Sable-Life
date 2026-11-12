package dev.sablelife.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelife.combat.BodyDamageBridge;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.compat.sable.SableBodyProbe;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.nukateam.ntgl.common.util.trackers.MeleeTracker$Tracker", remap = false)
public abstract class NtglMeleeMixin {
    @Shadow @Final private double attackDistance;
    @Shadow @Final private float meleeDamage;
    @Shadow @Final private int maxTargets;

    // Native melee timing and the cancelable Pre event have already accepted the swing.
    @Inject(method = "tryMeleeAttack", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;isEmpty()Z"))
    private void sablelife$meleeBody(@Coerce Object weaponData, CallbackInfo callback, @Local LivingEntity wielder,
                                   @Local(ordinal = 1) ArrayList<?> targets) {
        if (!(wielder instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || !Double.isFinite(attackDistance) || attackDistance <= 0
                || maxTargets > 0 && targets.size() >= maxTargets) return;
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(Math.min(attackDistance, 64)));
        var level = player.serverLevel();
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        var core = SableBodyProbe.findActivatedHeart(level, hit.getBlockPos());
        if (core == null || targets.stream().anyMatch(target -> target instanceof EntityHitbox box && box.core() == core)) return;
        BodyDamageBridge.melee(player, core, hit.getBlockPos(), hit.getLocation(), meleeDamage);
    }
}
