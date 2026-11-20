package dev.sablelife.heart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorations use their outline when they have no collision; fluids are never an outline target. */
public final class TerrainBlockShapes {
    public static VoxelShape contact(BlockGetter level, BlockPos pos, BlockState state) {
        var collision = state.getCollisionShape(level, pos);
        return collision.isEmpty() ? outline(level, pos, state) : collision;
    }

    public static VoxelShape outline(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isAir() || state.getBlock() instanceof LiquidBlock ? Shapes.empty() : state.getShape(level, pos);
    }

    private TerrainBlockShapes() {}
}
