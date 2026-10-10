package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Normalize the Sable hit before the native builder expands its entity search AABB. */
@Mixin(value = RaycastBuilder.class, remap = false)
public abstract class IronRaycastBuilderMixin {
    @Shadow @Final private Level level;

    @ModifyExpressionValue(method = "performRaycast", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;", remap = true))
    private BlockHitResult sablelife$worldRayEnd(BlockHitResult hit) {
        return IronSpellsBodyBridge.worldBlockHit(level, hit);
    }
}
