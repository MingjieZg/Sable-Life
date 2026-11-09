package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.ownership.BodyProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Explicit authoring removal is scoped to this one server-side break call. */
@Mixin(ServerPlayerGameMode.class)
public abstract class CreativeHeartBreakMixin {
    @Shadow protected ServerPlayer player;
    @WrapMethod(method = "destroyBlock")
    private boolean sableLife$breakBlock(BlockPos pos, Operation<Boolean> original) {
        boolean authorBreak = player.isCreative() && player.level().getBlockState(pos).getBlock() instanceof HeartBlock;
        if (authorBreak && !player.isShiftKeyDown()) return false;
        var previous = BodyProtection.AUTHOR_BREAK.get();
        if (authorBreak) BodyProtection.AUTHOR_BREAK.set(pos.immutable());
        else BodyProtection.AUTHOR_BREAK.remove();
        try { return BodyProtection.playerBreak(player, pos, () -> original.call(pos)); }
        finally {
            if (previous == null) BodyProtection.AUTHOR_BREAK.remove();
            else BodyProtection.AUTHOR_BREAK.set(previous);
        }
    }
}
