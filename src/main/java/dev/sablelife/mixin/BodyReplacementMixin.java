package dev.sablelife.mixin;

import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class BodyReplacementMixin {
    /** Also covers mods bypassing BreakEvent/destroyBlock via direct block replacement. */
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void sableLife$protectBlockReplacement(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        var chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ServerLevel level) {
            var previous = chunk.getBlockState(pos);
            // Plot coordinates are reused. Protection belongs to an existing block, never to air
            // left behind by an external deletion. Clear stale ownership before the write gate.
            // Assembly clears the destination and installs the incoming ownership before copying blocks.
            if (previous.isAir() && !state.isAir() && BodyOwnership.MOVING.get() == 0) BodyOwnership.get(level).forget(pos);
            else if (!previous.is(state.getBlock()) && dev.sablelife.ownership.BodyProtection.locked(level, pos))
                cir.setReturnValue(null);
        }
    }
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sableLife$forgetReplacedBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        var old = cir.getReturnValue();
        if (old != null && BodyOwnership.MOVING.get() == 0
                && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            if (!old.is(state.getBlock())) dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
            if (!old.isAir() && !old.is(state.getBlock())) dev.sablelife.module.AnchorTransfers.replaced(level, pos);
            if (state.isAir() && !old.isAir()) BodyOwnership.get(level).noteRemoved(pos);
            else if (!state.isAir() && !old.is(state.getBlock())) BodyOwnership.get(level).forget(pos);
        }
    }
}
