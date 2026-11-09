package dev.sablelife.heart;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import dev.sablelife.compat.sable.SableBodyProbe;

/** Uses the vanilla chest screen; server-side slots are configuration samples, never storage. */
public final class HeartLootMenu extends ChestMenu {
    private static final int TEMPLATE_SLOTS = 27, MENU_SLOTS = 54;
    private final HeartBlockEntity heart;
    private final Player viewer;
    public HeartLootMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        super(dev.sablelife.SableLife.LOOT_CHEST.get(), id, inventory, new SimpleContainer(MENU_SLOTS), 6);
        this.heart = heart;this.viewer=inventory.player;
        refresh();
    }
    private void refresh() {
        var templates = heart.lootTemplates();
        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            var display = templates.get(i).copy();
            if (!display.isEmpty()) {
                var original = display.get(DataComponents.LORE);
                var lines = new java.util.ArrayList<Component>(original == null ? java.util.List.of() : original.lines());
                lines.add(Component.translatable(heart.weighted() ? "menu.sablelife.loot_weight" : "menu.sablelife.loot_chance",
                        heart.weighted() ? heart.weight(i) : heart.chance(i)));
                lines.add(Component.translatable("menu.sablelife.loot_slot_help"));
                display.set(DataComponents.LORE, new ItemLore(lines));
            }
            getContainer().setItem(i, display);
        }
        for (int i = TEMPLATE_SLOTS; i < MENU_SLOTS; i++) getContainer().setItem(i, ItemStack.EMPTY);
        icon(31, Items.ENDER_EYE, Component.translatable(heart.weighted() ? "menu.sablelife.loot_mode_weighted" : "menu.sablelife.loot_mode_chance"),
                Component.translatable("menu.sablelife.loot_mode_help"));
        icon(32, Items.REPEATER, Component.translatable("menu.sablelife.loot_rolls", heart.rolls()),
                Component.translatable("menu.sablelife.loot_rolls_help"));
        icon(40, Items.BOOK, Component.translatable("menu.sablelife.loot_help"));
        icon(53, Items.ARROW, Component.translatable("menu.sablelife.back"));metadata();
    }
    public boolean action(Player player,int revision,java.util.UUID id,String key,double value,String text){
        if(!stillValid(player)||!heart.creatureId().equals(id))return false;
        if(key.equals("back")){heart.openDashboard(player);return true;}
        if(revision!=heart.modules().revision()) {refresh();sendAllDataToRemote();return false;}
        int slot;try{slot=Integer.parseInt(text);}catch(NumberFormatException e){return false;}
        boolean accepted=key.equals("copy_inventory")?copyInventory(player,slot):heart.editLootRule(player,slot,key,value);
        if(accepted)heart.modules().changed();refresh();sendAllDataToRemote();return accepted;
    }
    private void metadata(){var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",heart.canEditTemplates(viewer));tag.putBoolean("Weighted",heart.weighted());tag.putInt("Rolls",heart.rolls());tag.putInt("MaxRule",100);tag.putUUID("Id",heart.creatureId());tag.putInt("Revision",heart.modules().revision());for(int i=0;i<27;i++)tag.putInt("Rule"+i,heart.weighted()?heart.weight(i):heart.chance(i));getContainer().getItem(40).set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
    private void icon(int slot, net.minecraft.world.item.Item item, Component name, Component... lines) {
        var display = new ItemStack(item);
        display.set(DataComponents.CUSTOM_NAME, name);
        display.set(DataComponents.LORE, new ItemLore(java.util.List.of(lines)));
        getContainer().setItem(slot, display);
    }
    @Override public boolean stillValid(Player player) {
        return heart.canAccessEditor(player)
                && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
                && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(
                        SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) { sendAllDataToRemote(); return; }
        if (slot >= 0 && slot < MENU_SLOTS) {
            if (slot == 53 && type == ClickType.PICKUP) { heart.openDashboard(player); return; }
            if (slot < TEMPLATE_SLOTS && type == ClickType.QUICK_MOVE)
                heart.adjustLootRule(player, slot, button == 0 ? 1 : -1, false);
            if (slot == 31 && type == ClickType.PICKUP) heart.adjustLootRule(player, slot, 1, false);
            if (slot == 32 && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE))
                heart.adjustLootRule(player, slot, button == 0 ? 1 : -1, type == ClickType.QUICK_MOVE);
            if (slot < TEMPLATE_SLOTS && type == ClickType.PICKUP && heart.canEditTemplates(player)) {
                ItemStack sample = getCarried().copy();
                if (!sample.isEmpty() && button == 1) {
                    var existing = heart.lootTemplates().get(slot);
                    sample.setCount(ItemStack.isSameItemSameComponents(sample, existing)
                            ? Math.min(existing.getCount() + 1, sample.getMaxStackSize()) : 1);
                }
                if(sample.isEmpty()&&button==0){refresh();sendAllDataToRemote();return;}
                if (sample.isEmpty() && button == 1) {
                    sample = heart.lootTemplates().get(slot).copy(); sample.shrink(1);
                }
                heart.setLootTemplate(player, slot, sample);
            }
            refresh(); sendAllDataToRemote(); return;
        }
        super.clicked(slot, button, type, player);
        refresh(); sendAllDataToRemote();
    }
    private boolean copyInventory(Player player, int slot) {
        if (slot >= MENU_SLOTS && slot < slots.size() && stillValid(player) && heart.canEditTemplates(player)) {
            var sample = slots.get(slot).getItem();
            if (!sample.isEmpty()) {
                var templates = heart.lootTemplates();
                for (int i = 0; i < TEMPLATE_SLOTS; i++) if (templates.get(i).isEmpty()) {
                    return heart.setLootTemplate(player, i, sample);
                }
            }
        }
        return false;
    }
    /** Ghost copies use one explicit editor action, never vanilla bulk-transfer retries. */
    @Override public ItemStack quickMoveStack(Player player, int slot) {
        refresh(); sendAllDataToRemote();
        return ItemStack.EMPTY;
    }
    @Override public boolean canDragTo(Slot slot) { return slot.index >= MENU_SLOTS; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index >= MENU_SLOTS; }
}
