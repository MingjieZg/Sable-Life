package dev.sablelife.mixin;

import dev.sablelife.module.ModuleSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep dust attached to virtual navigation outputs, including while their strength is zero. */
@Mixin(RedStoneWireBlock.class)
public abstract class NavigationWireMixin {
    @Inject(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;", at = @At("HEAD"), cancellable = true)
    private void sableLife$navigationConnection(BlockGetter view, BlockPos pos, Direction towardSource, boolean above, CallbackInfoReturnable<RedstoneSide> cir) {
        if (view instanceof ServerLevel level && ModuleSignals.connects(level, pos.relative(towardSource), towardSource.getOpposite()))
            cir.setReturnValue(RedstoneSide.SIDE);
    }
}
