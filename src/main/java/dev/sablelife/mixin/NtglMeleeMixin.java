package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelife.combat.BodyDamageBridge;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.compat.weapons.WeaponMixinContract;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.nukateam.ntgl.common.util.trackers.MeleeTracker$Tracker", remap = false)
public abstract class NtglMeleeMixin {
    @Shadow @Final private double attackDistance;
    @Shadow @Final private float meleeDamage;
    @Shadow @Final private int maxTargets;

    // Native melee timing and the cancelable Pre event have already accepted the swing.
    @WrapOperation(method = WeaponMixinContract.NTGL_MELEE_ATTACK,
            at = @At(value = "INVOKE", target = WeaponMixinContract.NTGL_MELEE_TARGET_CHECK))
    private boolean sablelife$meleeBody(ArrayList<?> targets, Operation<Boolean> original, @Local LivingEntity wielder) {
        sablelife$hitStructure(wielder, targets);
        return original.call(targets);
    }

    @Unique
    private void sablelife$hitStructure(LivingEntity wielder, ArrayList<?> targets) {
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
