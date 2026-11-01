package dev.sablelife.mixin;

import dev.sablelife.heart.HeartBlock;
import dev.sablelife.ownership.BodyProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit authoring removal is scoped to this one server-side break call. */
@Mixin(ServerPlayerGameMode.class)
public abstract class CreativeHeartBreakMixin {
    @Shadow protected ServerPlayer player;
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void sableLife$requireDeliberateBreak(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        BodyProtection.AUTHOR_BREAK.remove();
        if (!player.isCreative() || !(player.level().getBlockState(pos).getBlock() instanceof HeartBlock)) return;
        if (!player.isShiftKeyDown()) result.setReturnValue(false);
        else BodyProtection.AUTHOR_BREAK.set(pos.immutable());
    }
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void sableLife$finishAuthorBreak(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        BodyProtection.AUTHOR_BREAK.remove();
    }
}
