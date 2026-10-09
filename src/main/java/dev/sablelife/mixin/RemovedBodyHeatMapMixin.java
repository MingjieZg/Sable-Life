package dev.sablelife.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** A removed plot must never start another assembly, including removal between split batches. */
@Mixin(value=SubLevelHeatMapManager.class,remap=false)
public abstract class RemovedBodyHeatMapMixin {
    @Shadow(remap=false) @Final private ServerSubLevel subLevel;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void sablelife$removedTick(CallbackInfo ci){if(subLevel.isRemoved())ci.cancel();}
    @Inject(method="step",at=@At("HEAD"),cancellable=true)
    private void sablelife$removedStep(CallbackInfoReturnable<Boolean> cir){if(subLevel.isRemoved())cir.setReturnValue(true);}
    @Inject(method="split",at=@At("HEAD"),cancellable=true)
    private void sablelife$removedSplit(CallbackInfo ci){if(subLevel.isRemoved())ci.cancel();}
    @Inject(method="split",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"),cancellable=true)
    private void sablelife$removedBetweenBatches(CallbackInfo ci){if(subLevel.isRemoved())ci.cancel();}
}
