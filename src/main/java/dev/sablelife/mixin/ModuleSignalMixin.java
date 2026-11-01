package dev.sablelife.mixin;

import dev.sablelife.module.ModuleSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A sensor marker emits without replacing the builder's chosen block. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ModuleSignalMixin {
    @Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
    private void sableLife$sensorOutput(BlockGetter view, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (view instanceof ServerLevel level) {
            int signal = ModuleSignals.signal(level, pos, direction);
            if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
        }
    }
    @Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
    private void sableLife$strongOutput(BlockGetter view, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (view instanceof ServerLevel level) {
            int signal = ModuleSignals.directSignal(level, pos, direction);
            if (signal > cir.getReturnValue()) cir.setReturnValue(signal);
        }
    }
}
