package dev.sablelife.mixin;

import dev.sablelife.compat.CannonDeathCleanup;
import dev.sablelife.ownership.BodyProtection;
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
        if(!dev.sablelife.compat.MountedContraptionCleanup.dyingOwned(mount))return;
        // Loss of assembly power during death must not restore unowned gun blocks.
        // Keep the mounted visual until the scheduled cleanup reaches this controller.
        if(BodyProtection.CLEANING.get()==0){ci.cancel();return;}
        CannonDeathCleanup.discardMounted(mount);
        running=false;
        ci.cancel();
    }
}
