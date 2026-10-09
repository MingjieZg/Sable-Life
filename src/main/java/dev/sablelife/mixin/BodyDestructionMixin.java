package dev.sablelife.mixin;

import dev.sablelife.ownership.BodyProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class BodyDestructionMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void sableLife$respectBreakingToggle(BlockPos pos, boolean drops, Entity entity, int depth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level && BodyProtection.locked(level, pos)) cir.setReturnValue(false);
    }
}
