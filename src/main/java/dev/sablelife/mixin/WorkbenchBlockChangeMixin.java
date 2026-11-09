package dev.sablelife.mixin;

import dev.sablelife.ownership.WildWorkbenchProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class WorkbenchBlockChangeMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void sablelife$workbenchIndex(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir){
        var old=cir.getReturnValue();if(old==null||old.is(Blocks.CRAFTING_TABLE)==state.is(Blocks.CRAFTING_TABLE))return;
        WildWorkbenchProtection.changed((LevelChunk)(Object)this,pos,old.is(Blocks.CRAFTING_TABLE),state.is(Blocks.CRAFTING_TABLE));
    }
}
