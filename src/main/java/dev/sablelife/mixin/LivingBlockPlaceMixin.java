package dev.sablelife.mixin;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows a living, single-core Sable body to grow while retaining Sable's normal placement checks. */
@Mixin(value = BlockPlaceContext.class, priority = 1100)
public abstract class LivingBlockPlaceMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void sableLife$allowLivingBodyPlacement(CallbackInfoReturnable<Boolean> cir) {
        var level = ((UseOnContextAccess) (Object) this).sableLife$getLevel();
        var hitResult = ((UseOnContextAccess) (Object) this).sableLife$getHitResult();
        var context = (BlockPlaceContext) (Object) this;
        BlockPos hit = hitResult.getBlockPos();
        var hearts = SableBodyProbe.findConnectedHearts(level, hit);
        if (hearts.size() != 1 || hearts.getFirst().health().state() == HeartState.DORMANT) return;
        if (context.getPlayer() == null || !context.getPlayer().isCreative() || !context.getPlayer().hasInfiniteMaterials()) {
            cir.setReturnValue(false);
            return;
        }
        if (hearts.getFirst().health().state() != HeartState.ALIVE) return;

        BlockPos candidate = hit.relative(hitResult.getDirection());
        BlockState candidateState = level.getBlockState(candidate);
        if (candidateState.canBeReplaced(context)) {
            cir.setReturnValue(true);
        }
    }
}
