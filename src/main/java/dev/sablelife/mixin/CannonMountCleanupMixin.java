package dev.sablelife.mixin;

import dev.sablelife.compat.CannonDeathCleanup;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.BodyProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep ordinary wrench disassembly; only death/limb cleanup discards the mounted body. */
@Pseudo
@Mixin(targets={"rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity",
        "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity",
        "riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity"},remap=false)
public abstract class CannonMountCleanupMixin {
    @Shadow(remap=false) private boolean running;
    @Inject(method="disassemble",at=@At("HEAD"),cancellable=true)
    private void sablelife$deathCleanup(CallbackInfo ci) {
        var mount=(BlockEntity)(Object)this;
        if (BodyProtection.CLEANING.get()==0 || !(mount.getLevel() instanceof ServerLevel level)
                || BodyOwnership.get(level).owner(mount.getBlockPos())==null) return;
        if(mount.getClass().getName().startsWith("riftyboi.cbcmodernwarfare.")&&!dev.sablelife.compat.MountedContraptionCleanup.permitted(mount))return;
        CannonDeathCleanup.discardMounted(mount);
        running=false;
        ci.cancel();
    }
}
