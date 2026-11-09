package dev.sablelife.item;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.ownership.WildEncounterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class WildEncounterToolItem extends Item {
    public WildEncounterToolItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var tool = player.getItemInHand(hand);
        if (!WildEncounterMenu.allowed(player)) return InteractionResultHolder.fail(tool);
        if (player instanceof ServerPlayer server) {
            if (!SableLifeServerConfig.wildEncountersEnabled()) {
                server.displayClientMessage(Component.translatable("wild.sablelife.server_disabled"), true);
                return InteractionResultHolder.fail(tool);
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new WildEncounterMenu(id, inventory, server),
                    Component.translatable("item.sablelife.wild_encounter_tool")));
        }
        return InteractionResultHolder.sidedSuccess(tool, level.isClientSide);
    }
}
