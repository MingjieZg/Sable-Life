package dev.sablelife.mixin;

import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Containers.class)
public abstract class BodyContainerMixin {
    @Inject(method = "dropItemStack", at = @At("HEAD"), cancellable = true)
    private static void sableLife$noInventoryDrops(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        if (BodyOwnership.protects(level, BlockPos.containing(x, y, z))) ci.cancel();
    }
}
