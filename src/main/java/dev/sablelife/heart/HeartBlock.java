package dev.sablelife.heart;

import com.mojang.serialization.MapCodec;
import dev.sablelife.SableLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class HeartBlock extends CopycatBlock {
    public static final MapCodec<HeartBlock> CODEC = simpleCodec(HeartBlock::new);
    // Stays true after death; only the explicit creative authoring reset restores normal block behavior.
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public HeartBlock(Properties properties) {
        // The bare Copycat casing contains transparent pixels. It must not hide the
        // adjacent block's face behind those pixels; this does not change collision.
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override protected MapCodec<? extends CopycatBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HeartBlockEntity(pos, state); }

    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.sablelife.heart_disguise")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable(this instanceof RedstoneCoreBlock ? "tooltip.sablelife.redstone_core" : "tooltip.sablelife.heart_crystal")
                .withStyle(this instanceof RedstoneCoreBlock ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.RED));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        var core=level.getBlockEntity(pos) instanceof HeartBlockEntity entity?entity:null;
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && dev.sablelife.ownership.BodyOwnership.MOVING.get() == 0
                && core!=null
                && core.health().state() == HeartState.ALIVE) {
            core.forceDeath();
        }
        // Sable moves block entities through ordinary replacement calls; Create must not refund
        // the disguise during that transfer, since the saved entity still owns the material.
        // Death cleanup must not invoke CopycatBlock's automatic material refund.
        if(!level.isClientSide&&!state.is(replacement.getBlock())
                &&dev.sablelife.ownership.BodyOwnership.MOVING.get()==0&&dev.sablelife.ownership.BodyProtection.CLEANING.get()==0
                &&core!=null)dev.sablelife.ownership.CoreChunkLoading.release(core);
        boolean lifeOwned = state.getValue(ACTIVE)
                || core!=null
                && core.health().state() != HeartState.DORMANT;
        if ((dev.sablelife.ownership.BodyOwnership.MOVING.get() != 0
                || dev.sablelife.ownership.BodyProtection.CLEANING.get() != 0 || lifeOwned)
                && !state.is(replacement.getBlock()))
            level.removeBlockEntity(pos);
        else super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SableLife.HEART_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> {
            var heart = (HeartBlockEntity) entity;
            heart.tick();
            if (!world.isClientSide) HeartBlockEntity.serverTick(world, pos, blockState, heart);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HeartBlockEntity heart) {
            if (player.isShiftKeyDown()) heart.tryActivate(player);
            else {
                player.displayClientMessage(heart.status(), false);
                player.displayClientMessage(heart.interactionHint(), true);
                if (heart.canAccessEditor(player)) heart.openLastEditor(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() { return SableLife.HEART_ENTITY.get(); }
    @Override public InteractionResult onSneakWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        // Never enter Create's sneak-wrench pickup path, even for a dormant, bare core.
        onWrenched(state, context);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return Math.max(state.getValue(ACTIVE) ? 7 : 0, super.getLightEmission(state, world, pos));
    }
    @Override public boolean canConnectTexturesToward(net.minecraft.world.level.BlockAndTintGetter world, BlockPos pos, BlockPos other, BlockState state) {
        return true;
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HeartBlockEntity heart ? heart.health().comparatorSignal() : 0;
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof HeartBlockEntity heart ? heart.pulseSignal() : 0;
    }
}
