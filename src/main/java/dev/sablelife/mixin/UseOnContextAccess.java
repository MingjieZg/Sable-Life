package dev.sablelife.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.item.context.UseOnContext;

@Mixin(UseOnContext.class)
public interface UseOnContextAccess {
    @Accessor("level") Level sableLife$getLevel();
    @Accessor("hitResult") BlockHitResult sableLife$getHitResult();
}
