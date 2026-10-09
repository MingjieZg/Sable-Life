package dev.sablelife.item;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

public final class DataCopierItem extends Item {
    private static final String KEY = "SableLifeConfiguration";
    public DataCopierItem(Properties properties) { super(properties); }
    private static CompoundTag payload(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY); }
    @Override public boolean isFoil(ItemStack stack) { return !payload(stack).isEmpty(); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return transfer(context); }
    @Override public InteractionResult useOn(UseOnContext context) { return transfer(context); }
    private InteractionResult transfer(UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof HeartBlockEntity heart)) return InteractionResult.PASS;
        if (!player.isCreative()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.sablelife.copier_creative"), true);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!dev.sablelife.module.AuthoringRateLimit.allow(player,20)) return InteractionResult.FAIL;
        var stack = context.getItemInHand(); var data = payload(stack);
        if (data.isEmpty() || player.isShiftKeyDown()) {
            var copied = heart.copyConfiguration(); copied.putString("SourceName", heart.displayName().getString());
            var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); custom.put(KEY, copied);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            player.displayClientMessage(Component.translatable("message.sablelife.copier_copied", heart.displayName(), copied.getList("Modules", 10).size()), true);
        } else {
            if (!heart.pasteConfiguration(player, data)) {
                player.displayClientMessage(Component.translatable("message.sablelife.copier_locked"), true); return InteractionResult.FAIL;
            }
            MarkingSessions.cancel(player);
            player.displayClientMessage(Component.translatable("message.sablelife.copier_pasted", heart.modules().all().size()), false);
        }
        var point = SableBodyProbe.toWorld(level, context.getClickedPos().getCenter());
        level.playSound(null, point.x, point.y, point.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, 1.3F);
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var data = payload(stack);
        tooltip.add(Component.translatable(data.isEmpty() ? "tooltip.sablelife.copier_empty" : "tooltip.sablelife.copier_loaded").withStyle(data.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        if (!data.isEmpty()) tooltip.add(Component.translatable("tooltip.sablelife.copier_source", data.getString("SourceName"), data.getList("Modules", 10).size()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.sablelife.copier_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.copier_marks").withStyle(ChatFormatting.GRAY));
    }
}
