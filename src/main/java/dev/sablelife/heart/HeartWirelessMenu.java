package dev.sablelife.heart;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Ghost frequency samples and ordinary player inventory, with server-validated configuration actions. */
public final class HeartWirelessMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 7, OUTPUT = 7, INPUT = 14, ENABLED_ROWS = 21, RECEIVE = 28,
            MASTER = 35, CLOCK_LOW = 36, CLOCK_HIGH = 37, SELECTED = 38, EDITABLE = 39, ALIVE = 40,
            COUNT = 41, PAGE = 42, SELECTED_INPUT = 43, CHANNEL_LIMIT = 44, DATA_COUNT = 45;
    private final HeartBlockEntity heart;
    private final Player viewer;
    private int selected, page;
    public boolean inventoryVisible;
    private final ContainerData data;
    private final SimpleContainer frequencies = new SimpleContainer(2);
    public HeartWirelessMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartWirelessMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        super(SableLife.WIRELESS_MENU.get(), id);
        this.heart = heart; viewer = inventory.player;
        data = heart == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) {}
            @Override public int get(int index) {
                if (index < 7) return heart.wireless().channel(selected).value(index);
                if (index < INPUT) return heart.wireless().output(page * PAGE_SIZE + index - OUTPUT);
                if (index < ENABLED_ROWS) return heart.wireless().input(page * PAGE_SIZE + index - INPUT);
                if (index < RECEIVE) return heart.wireless().channel(page * PAGE_SIZE + index - ENABLED_ROWS).value(0);
                if (index < MASTER) return heart.wireless().channel(page * PAGE_SIZE + index - RECEIVE).value(6);
                return switch (index) {
                    case COUNT -> heart.wireless().size();
                    case CHANNEL_LIMIT -> heart.wireless().limit();
                    case SELECTED_INPUT -> heart.wireless().input(selected);
                    case PAGE -> page;
                    case MASTER -> heart.wireless().master() ? 1 : 0;
                    case CLOCK_LOW -> (int) (heart.wireless().elapsed() & 0xffff);
                    case CLOCK_HIGH -> (int) ((heart.wireless().elapsed() >>> 16) & 0xffff);
                    case SELECTED -> selected;
                    case EDITABLE -> heart.canEdit(viewer) ? 1 : 0;
                    case ALIVE -> heart.operational() ? 1 : 0;
                    default -> 0;
                };
            }
        };
        addDataSlots(data);
        for (int i = 0; i < 2; i++) addSlot(new Slot(frequencies, i, 171 + i * 50, 39) {
            @Override public boolean isActive() { return count() > 0; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(inventorySlot(inventory, col + row * 9 + 9, 143 + col * 18, 85 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(inventorySlot(inventory, col, 143 + col * 18, 143));
        refresh();
    }
    private Slot inventorySlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override public boolean isActive() { return heart != null || inventoryVisible; }
        };
    }
    public int value(int index) { return data.get(index); }
    public int selected() { return value(SELECTED) & 0xffff; }
    public int count() { return value(COUNT) & 0xffff; }
    public int page() { return value(PAGE) & 0xffff; }
    public int rowPower(int row) { return value((value(RECEIVE + row) == 1 ? INPUT : OUTPUT) + row); }
    public long elapsed() { return (value(CLOCK_LOW) & 0xffffL) | ((value(CLOCK_HIGH) & 0xffffL) << 16); }
    public int setting(int channel, int field) {
        if (channel == selected()) return value(field);
        int row = channel - page() * PAGE_SIZE;
        if (row < 0 || row >= PAGE_SIZE) return 0;
        return field == 0 ? value(ENABLED_ROWS + row) : field == 6 ? value(RECEIVE + row) : 0;
    }
    private void refresh() {
        if (heart == null) return;
        selected = Math.clamp(selected, 0, Math.max(0, heart.wireless().size() - 1));
        page = Math.clamp(page, 0, Math.max(0, (heart.wireless().size() - 1) / PAGE_SIZE));
        var channel = heart.wireless().channel(selected);
        frequencies.setItem(0, channel.first()); frequencies.setItem(1, channel.second());
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canAccessEditor(player) && !heart.isRemoved() && player.level() == heart.getLevel()
                && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
                && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(
                        SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (heart == null || !stillValid(player) || !heart.canAccessEditor(player)) return false;
        int channel = button >>> 24, field = (button >>> 16) & 0xff, raw = button & 0xffff;
        if (field == 6) {
            if (raw >= heart.wireless().size()) return false;
            selected = raw; page = selected / PAGE_SIZE; refresh(); sendAllDataToRemote(); return true;
        }
        if (field == 8) { heart.openDashboard(player); return true; }
        if (field == 12) { page = Math.clamp(raw, 0, Math.max(0, (heart.wireless().size() - 1) / PAGE_SIZE)); broadcastChanges(); return true; }
        if (!heart.canEdit(player)) return false;
        if (field == 11) {
            if (raw != selected || !heart.wireless().remove(selected)) return false;
            selected = Math.min(selected, Math.max(0, heart.wireless().size() - 1)); page = selected / PAGE_SIZE;
            refresh(); sendAllDataToRemote(); return true;
        }
        if (field == 10) {
            if (!dev.sablelife.module.AuthoringRateLimit.allow(player, 5)) return false;
            int added = heart.wireless().add(); if (added < 0) return false;
            selected = added; page = selected / PAGE_SIZE; refresh(); sendAllDataToRemote(); return true;
        }
        if (heart.wireless().size() == 0 && field != 7) return false;
        channel = selected;
        if (field == 7) heart.wireless().master(raw != 0);
        else if (!heart.wireless().channel(channel).set(field == 9 ? 6 : field, raw)) return false;
        heart.wireless().configurationChanged(); heart.setChanged(); broadcastChanges(); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (slot == 0 || slot == 1) {
            if (heart != null && heart.wireless().size() > 0 && heart.canEdit(player) && type == ClickType.PICKUP && (button == 0 || button == 1)) {
                heart.wireless().channel(selected).frequency(slot == 1, getCarried());
                heart.wireless().configurationChanged(); heart.setChanged(); refresh(); sendAllDataToRemote();
            }
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return slot.index >= 2; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index >= 2; }
}
