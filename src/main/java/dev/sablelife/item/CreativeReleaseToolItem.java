package dev.sablelife.item;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Creative authoring utility: reset life state without disassembling or deleting the physical build. */
public final class CreativeReleaseToolItem extends Item {
    public CreativeReleaseToolItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Run before Heart inspection and before a machine's normal right-click action.
        return release(context);
    }

    @Override public InteractionResult useOn(UseOnContext context) { return release(context); }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    private InteractionResult release(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null) return InteractionResult.FAIL;
        if (!player.isCreative()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.sablelife.release_creative_only"), true);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var clicked = context.getClickedPos();
        if (!player.canInteractWithBlock(clicked, 1) || !level.mayInteract(player, clicked)) return InteractionResult.FAIL;

        var hearts = SableBodyProbe.findConnectedHearts(level, clicked);
        // A disassembled core may still retain its life state; it must remain recoverable too.
        if (hearts.isEmpty() && level.getBlockEntity(clicked) instanceof HeartBlockEntity heart) hearts = List.of(heart);
        if (hearts.isEmpty()) {
            if (BodyOwnership.get((ServerLevel) level).releaseFragment((ServerLevel) level, clicked)) {
                player.displayClientMessage(Component.translatable("message.sablelife.fragment_released"), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.sablelife.release_no_core"), true);
            return InteractionResult.FAIL;
        }
        int reset = 0;
        for (var heart : hearts) if (heart.resetForCreative(player)) reset++;
        if (reset == 0) return InteractionResult.FAIL;

        Vec3 soundAt = SableBodyProbe.toWorld(level, Vec3.atCenterOf(clicked));
        level.playSound(null, soundAt.x, soundAt.y, soundAt.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.2F);
        player.displayClientMessage(Component.translatable("message.sablelife.released", reset), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sablelife.release_creative_only").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sablelife.release_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.release_preserve").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.release_defaults").withStyle(ChatFormatting.GRAY));
    }
}
