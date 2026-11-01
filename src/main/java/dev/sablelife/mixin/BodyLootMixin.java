package dev.sablelife.mixin;

import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BodyLootMixin {
    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void sableLife$noMaterials(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        var origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && BodyOwnership.protects(params.getLevel(), BlockPos.containing(origin))) cir.setReturnValue(List.of());
    }
}
