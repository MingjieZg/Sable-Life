package dev.sablelife.mixin;

import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copycat materials use popResource in onRemove rather than the block loot table. */
@Mixin(Block.class)
public abstract class BodyMaterialDropMixin {
    @Inject(method="popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),cancellable=true)
    private static void sablelife$material(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (BodyOwnership.protects(level,pos)) ci.cancel();
    }
    @Inject(method="popResourceFromFace",at=@At("HEAD"),cancellable=true)
    private static void sablelife$faceMaterial(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
        if (BodyOwnership.protects(level,pos)) ci.cancel();
    }
}
