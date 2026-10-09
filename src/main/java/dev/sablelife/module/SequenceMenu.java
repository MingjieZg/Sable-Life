package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

public final class SequenceMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final VirtualModule module;
    private final Player viewer;
    private final Inventory inventory;
    private final SimpleContainer frequencies = new SimpleContainer(2);
    private int wirelessSelected;
    private final SimpleContainer data = new SimpleContainer(1);
    public SequenceMenu(int id, Inventory inventory) { this(id, inventory, null, null); }
    public SequenceMenu(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule module) {
        super(SableLife.SEQUENCE_MENU.get(), id); this.heart = heart; this.module = module; viewer = inventory.player; this.inventory = inventory;
        if (module != null && !module.sequence().isEmpty()) wirelessSelected = Math.max(0, module.sequence().getFirst().channel() - 1);
        addSlot(new Slot(data, 0, -10000, -10000) {
            @Override public boolean isActive() { return false; }
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) { return false; }
        });
        addSlot(frequencySlot(0, -10000, -10000)); addSlot(frequencySlot(1, -10000, -10000));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, -10000, -10000));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, -10000, -10000));
        refresh();
    }
    private Slot frequencySlot(int frequency, int x, int y) {
        return new Slot(frequencies, frequency, x, y) {
            @Override public boolean isActive() { return snapshot().getInt("Channels") > 0; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        };
    }
    /** Only client presentation changes. Indices and backing containers stay identical to the server. */
    public void layoutSlots(int x, int inventoryY) {
        for (int i = 1; i < slots.size(); i++) {
            Slot slot;
            if (i <= 2) slot = frequencySlot(i - 1, x + (i == 1 ? 38 : 104), 145);
            else { int n = i - 3; slot = new Slot(inventory, n < 27 ? n + 9 : n - 27, x + 3 + n % 9 * 18, inventoryY + (n < 27 ? n / 9 * 18 : 58)); }
            slot.index = i; slots.set(i, slot);
        }
    }
    public CompoundTag snapshot() { return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private void refresh() {
        if (heart == null) return;
        wirelessSelected = Math.clamp(wirelessSelected, 0, Math.max(0, heart.wireless().size() - 1));
        var wireless = heart.wireless().channel(wirelessSelected);
        frequencies.setItem(0, wireless.first()); frequencies.setItem(1, wireless.second());
        var tag = new CompoundTag();
        tag.putInt("WirelessSelected", wirelessSelected); tag.putInt("WirelessMode", wireless.value(6));
        tag.putBoolean("WirelessEnabled", wireless.value(0) == 1); tag.putBoolean("WirelessMaster", heart.wireless().master());
        tag.putInt("WirelessPower", heart.wireless().output(wirelessSelected)); tag.put("Module", module.save()); tag.put("Runtime", heart.modules().sequenceSnapshot(module.id()));
        tag.putBoolean("Editable", heart.canEdit(viewer)); tag.putInt("Channels", heart.wireless().size());
        tag.putInt("ChannelLimit", heart.wireless().limit());
        tag.putIntArray("ProgramChannels", java.util.stream.IntStream.range(0, heart.wireless().size())
                .filter(i -> heart.wireless().channel(i).programmed() && heart.wireless().channel(i).configured()).map(i -> i + 1).toArray());
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); data.setItem(0, stack);
    }
    @Override public void broadcastChanges() { if (heart != null) refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player p) {
        return heart == null || heart.canAccessEditor(p) && !heart.isRemoved() && p.level() == heart.getLevel()
                && heart.modules().find(module.id()) == module && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
                && SableBodyProbe.toWorld(p.level(), p.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(p.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    public boolean action(Player p, java.util.UUID id, String key, double value, String index) {
        if (heart == null || !stillValid(p) || !module.id().equals(id) || !Double.isFinite(value)) return false;
        if (key.equals("wire_select")) {
            int channel = (int) Math.round(value) - 1;
            if (channel < 0 || channel >= heart.wireless().size()) return false;
            wirelessSelected = channel; refresh(); sendAllDataToRemote(); return true;
        }
        if (!heart.canEdit(p)) return false;
        if (key.startsWith("wire_")) {
            switch (key) {
                case "wire_add" -> { int added = heart.wireless().add(); if (added < 0) return false; wirelessSelected = added; }
                case "wire_delete" -> { if (!heart.wireless().remove(wirelessSelected)) return false; heart.modules().resetSequence(id); }
                case "wire_master" -> heart.wireless().master(value != 0);
                case "wire_enable", "wire_mode" -> {
                    if (heart.wireless().size() == 0) return false;
                    heart.wireless().channel(wirelessSelected).set(key.equals("wire_mode") ? 6 : 0, (int) Math.round(value));
                }
                default -> { return false; }
            }
            heart.wireless().configurationChanged(); heart.setChanged(); refresh(); sendAllDataToRemote(); return true;
        }
        if (key.equals("reset")) { heart.modules().resetSequence(id); refresh(); return true; }
        int step;
        try { step = Integer.parseInt(index); } catch (NumberFormatException ignored) { return false; }
        if (!module.editSequence(key, step, value)) return false;
        heart.modules().resetSequence(id); heart.modules().changed(); heart.setChanged(); refresh(); sendAllDataToRemote(); return true;
    }
    @Override public boolean clickMenuButton(Player p, int button) {
        if (button != 0 || heart == null || !stillValid(p)) return false;
        DashboardSelection.set(heart, p.getUUID(), module.id()); heart.openDashboard(p); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player p) {
        if (heart == null || !stillValid(p)) return;
        if (slot == 0) return;
        if (slot == 1 || slot == 2) {
            if (heart.canEdit(p) && heart.wireless().size() > 0 && type == ClickType.PICKUP && (button == 0 || button == 1)) {
                heart.wireless().channel(wirelessSelected).frequency(slot == 2, getCarried());
                heart.wireless().configurationChanged(); heart.setChanged(); refresh(); sendAllDataToRemote();
            }
            return;
        }
        super.clicked(slot, button, type, p);
    }
    @Override public boolean canDragTo(Slot slot) { return slot.index >= 3; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index >= 3; }
    @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
}
