package dev.sablelife.mixin;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps vanilla survival checks but removes Sable's entity-overlap rejection for a living body extension. */
@Mixin(value = BlockItem.class, priority = 1100)
public abstract class LivingBlockItemPlacementMixin {
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void sableLife$allowLivingBodyPlacement(BlockPlaceContext context, BlockState state,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (context.getPlayer() == null || !context.getPlayer().isCreative() || !context.getPlayer().hasInfiniteMaterials()) return;
        var access = (UseOnContextAccess) (Object) context;
        var hearts = SableBodyProbe.findConnectedHearts(context.getLevel(), access.sableLife$getHitResult().getBlockPos());
        if (hearts.size() == 1 && hearts.getFirst().health().state() == HeartState.ALIVE
                && state.canSurvive(context.getLevel(), context.getClickedPos())) {
            cir.setReturnValue(true);
        }
    }
}
