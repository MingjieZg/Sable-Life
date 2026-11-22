package dev.sablelife.item;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.AuthoringLimits;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class DataCopierItem extends Item {
    private static final String LEGACY_PAYLOAD = "SableLifeConfiguration";
    private static final String LEGACY_SUMMARY = "SableLifeConfigurationSummary";
    private static final String CLIPBOARD = "SableLifeClipboard";
    private static final int COMPRESSED_LIMIT = DataCopierClipboards.MAX_CLIPBOARD_BYTES;
    private static final String FORMATTED_COMPRESSED_LIMIT = String.format(Locale.ROOT, "%,d", COMPRESSED_LIMIT);
    private static final String FORMATTED_CONFIGURATION_LIMIT = String.format(Locale.ROOT, "%,d", AuthoringLimits.MAX_CONFIGURATION_BYTES);

    public DataCopierItem(Properties properties) { super(properties); }

    private static CustomData customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    }

    @Override public void verifyComponentsAfterLoad(ItemStack stack) {
        var data = customData(stack);
        if (data.contains(LEGACY_PAYLOAD) || data.contains(LEGACY_SUMMARY)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.remove(LEGACY_PAYLOAD);
                tag.remove(LEGACY_SUMMARY);
            });
        }
    }

    @Override public boolean isFoil(ItemStack stack) { return customData(stack).contains(CLIPBOARD); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return transfer(context); }
    @Override public InteractionResult useOn(UseOnContext context) { return transfer(context); }
    private InteractionResult transfer(UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof HeartBlockEntity heart)) return InteractionResult.PASS;
        if (!heart.canAccessEditor(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.sablelife.copier_permission"), true);
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        if (!dev.sablelife.module.AuthoringRateLimit.allow(player,20)) return InteractionResult.FAIL;
        var stack = context.getItemInHand();
        if (!isFoil(stack) || player.isShiftKeyDown()) {
            var copied = heart.copyConfiguration();
            copied.putString("SourceName", heart.displayName().getString());
            try {
                var saved = DataCopierClipboards.store(serverLevel.getServer()).save(player.getUUID(), copied);
                var reference = new CompoundTag();
                reference.putString("Id", saved.reference().id());
                if (saved.reference().owner() != null) reference.putUUID("Owner", saved.reference().owner());
                reference.putString("SourceName", copied.getString("SourceName"));
                reference.putInt("ModuleCount", copied.getList("Modules", Tag.TAG_COMPOUND).size());
                reference.putInt("Bytes", saved.bytes());
                CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(CLIPBOARD, reference));
                player.displayClientMessage(Component.translatable("message.sablelife.copier_copied", heart.displayName(), reference.getInt("ModuleCount")), true);
            } catch (DataCopierClipboards.TooLarge e) {
                var message = e.limit == DataCopierClipboards.Limit.COMPRESSED
                        ? Component.translatable("message.sablelife.copier_too_large", FORMATTED_COMPRESSED_LIMIT)
                        : Component.translatable("message.sablelife.copier_configuration_limit", FORMATTED_CONFIGURATION_LIMIT);
                player.displayClientMessage(message, true);
                return InteractionResult.FAIL;
            } catch (DataCopierClipboards.QuotaExceeded e) {
                player.displayClientMessage(Component.translatable(e.account
                        ? "message.sablelife.copier_account_full" : "message.sablelife.copier_store_failed"), true);
                return InteractionResult.FAIL;
            } catch (IOException e) {
                player.displayClientMessage(Component.translatable("message.sablelife.copier_store_failed"), true);
                return InteractionResult.FAIL;
            }
        } else {
            var reference = customData(stack).copyTag().getCompound(CLIPBOARD);
            CompoundTag copied;
            try {
                if (reference.contains("Owner") && !reference.hasUUID("Owner")) throw new IOException("Invalid clipboard owner");
                var owner = reference.hasUUID("Owner") ? reference.getUUID("Owner") : null;
                var key = new DataCopierClipboards.Reference(owner, reference.getString("Id"));
                copied = DataCopierClipboards.store(serverLevel.getServer()).read(key);
            } catch (IllegalArgumentException | IOException e) {
                player.displayClientMessage(Component.translatable("message.sablelife.copier_missing"), true);
                return InteractionResult.FAIL;
            }
            if (!heart.pasteConfiguration(player, copied)) {
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
        boolean loaded = isFoil(stack);
        tooltip.add(Component.translatable(loaded ? "tooltip.sablelife.copier_loaded" : "tooltip.sablelife.copier_empty").withStyle(loaded ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        int usedBytes = 0;
        if (loaded) {
            var reference = customData(stack).copyTag().getCompound(CLIPBOARD);
            usedBytes = Math.clamp(reference.getInt("Bytes"), 0, COMPRESSED_LIMIT);
            tooltip.add(Component.translatable("tooltip.sablelife.copier_source", reference.getString("SourceName"), reference.getInt("ModuleCount"))
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.sablelife.copier_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.copier_marks").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.copier_limit",
                String.format(Locale.ROOT, "%,d", usedBytes), FORMATTED_COMPRESSED_LIMIT).withStyle(ChatFormatting.GRAY));
    }
}
