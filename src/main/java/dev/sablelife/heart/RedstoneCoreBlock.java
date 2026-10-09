package dev.sablelife.heart;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Copycat casing and the same configuration backend, with no creature activation. */
public final class RedstoneCoreBlock extends HeartBlock {
    public static final MapCodec<RedstoneCoreBlock> CODEC=simpleCodec(RedstoneCoreBlock::new);
    public RedstoneCoreBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends com.simibubi.create.content.decoration.copycat.CopycatBlock> codec(){return CODEC;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof HeartBlockEntity core&&core.canAccessEditor(player))core.openDashboard(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
