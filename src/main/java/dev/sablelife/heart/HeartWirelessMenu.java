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

/** Channel editor contains only ghost samples; player inventory lives in the separate frequency picker. */
public final class HeartWirelessMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 64, DEFAULT_ROWS = 7, OUTPUT = 7, INPUT = OUTPUT + PAGE_SIZE,
            ENABLED_ROWS = INPUT + PAGE_SIZE, RECEIVE = ENABLED_ROWS + PAGE_SIZE, MASTER = RECEIVE + PAGE_SIZE,
            CLOCK_LOW = MASTER + 1, CLOCK_HIGH = MASTER + 2, SELECTED = MASTER + 3, EDITABLE = MASTER + 4,
            ALIVE = MASTER + 5, COUNT = MASTER + 6, PAGE = MASTER + 7, SELECTED_INPUT = MASTER + 8,
            CHANNEL_LIMIT = MASTER + 9, ROWS = MASTER + 10, LATCH = MASTER + 11, LATCH_RESET = MASTER + 12, DATA_COUNT = MASTER + 13;
    private final HeartBlockEntity heart;
    private final Player viewer;
    private int selected, page, rowCount = DEFAULT_ROWS;
    private final ContainerData data;
    private final SimpleContainer frequencies = new SimpleContainer(2);
    private final SimpleContainer names = new SimpleContainer(1);
    private net.minecraft.world.item.component.CustomData decodedNames = net.minecraft.world.item.component.CustomData.EMPTY;
    private net.minecraft.nbt.CompoundTag nameSnapshot = new net.minecraft.nbt.CompoundTag();
    public HeartWirelessMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartWirelessMenu(int id, Inventory inventory, HeartBlockEntity heart) { this(id,inventory,heart,0); }
    public HeartWirelessMenu(int id, Inventory inventory, HeartBlockEntity heart,int channel) {
        super(SableLife.WIRELESS_MENU.get(), id);
        this.heart = heart; viewer = inventory.player; selected=Math.max(0,channel);page=selected/rowCount;
        data = heart == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) {}
            @Override public int get(int index) {
                if (index < 7) return heart.wireless().channel(selected).value(index);
                if (index < INPUT) return index - OUTPUT < rowCount ? heart.wireless().output(page * rowCount + index - OUTPUT) : 0;
                if (index < ENABLED_ROWS) return index - INPUT < rowCount ? heart.wireless().input(page * rowCount + index - INPUT) : 0;
                if (index < RECEIVE) return index - ENABLED_ROWS < rowCount ? heart.wireless().channel(page * rowCount + index - ENABLED_ROWS).value(0) : 0;
                if (index < MASTER) return index - RECEIVE < rowCount ? heart.wireless().channel(page * rowCount + index - RECEIVE).value(6) : 0;
                return switch (index) {
                    case LATCH -> heart.wireless().channel(selected).value(7);
                    case LATCH_RESET -> heart.wireless().channel(selected).value(8);
                    case COUNT -> heart.wireless().size();
                    case ROWS -> rowCount;
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
        addSlot(new Slot(names, 0, -10000, -10000) {
            @Override public boolean isActive() { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        refresh();
    }
    public String channelName(int channel) {
        var data = names.getItem(0).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        if (data != decodedNames) { decodedNames = data; nameSnapshot = data.copyTag(); }
        var tag = nameSnapshot;
        if (channel == tag.getInt("Selected") && tag.contains("SelectedName")) return tag.getString("SelectedName");
        var rows = tag.getList("Names", 8); int row = channel - tag.getInt("Page") * Math.max(1, tag.getInt("Rows"));
        return tag.contains("Page") && row >= 0 && row < rows.size() ? rows.getString(row) : "";
    }
    public boolean rename(Player player, double channel, String text) {
        if (heart == null || !stillValid(player) || !heart.canEdit(player) || !Double.isFinite(channel)
                || channel != selected || selected < 0 || selected >= heart.wireless().size()) return false;
        if (!heart.wireless().channel(selected).name(text)) return false;
        heart.setChanged(); refresh(); broadcastChanges(); return true;
    }
    public int value(int index) { return data.get(index); }
    public int selected() { return value(SELECTED) & 0xffff; }
    public int pageSize() { return value(ROWS) == 0 ? DEFAULT_ROWS : Math.clamp(value(ROWS), 1, PAGE_SIZE); }
    public void layoutSlots(int redX, int blueX, int y) {
        for (int i = 0; i < 2; i++) {
            var slot = new Slot(frequencies, i, i == 0 ? redX : blueX, y) {
                @Override public boolean isActive() { return count() > 0; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            };
            slot.index = i; slots.set(i, slot);
        }
    }
    public int count() { return value(COUNT) & 0xffff; }
    public int page() { return value(PAGE) & 0xffff; }
    public int rowPower(int row) { return value((value(RECEIVE + row) == 1 ? INPUT : OUTPUT) + row); }
    public long elapsed() { return (value(CLOCK_LOW) & 0xffffL) | ((value(CLOCK_HIGH) & 0xffffL) << 16); }
    public int setting(int channel, int field) {
        if (channel == selected()) return value(field==7?LATCH:field==8?LATCH_RESET:field) & (field==8?0xffff:-1);
        int row = channel - page() * pageSize();
        if (row < 0 || row >= pageSize()) return 0;
        return field == 0 ? value(ENABLED_ROWS + row) : field == 6 ? value(RECEIVE + row) : 0;
    }
    private void refresh() {
        if (heart == null) return;
        selected = Math.clamp(selected, 0, Math.max(0, heart.wireless().size() - 1));
        page = Math.clamp(page, 0, Math.max(0, (heart.wireless().size() - 1) / rowCount));
        var channel = heart.wireless().channel(selected);
        frequencies.setItem(0, channel.first()); frequencies.setItem(1, channel.second());
        var tag = new net.minecraft.nbt.CompoundTag(); var rows = new net.minecraft.nbt.ListTag();
        tag.putInt("Selected", selected); tag.putString("SelectedName", channel.name()); tag.putInt("Page", page); tag.putInt("Rows", rowCount);
        for (int i = page * rowCount; i < Math.min(heart.wireless().size(), (page + 1) * rowCount); i++)
            rows.add(net.minecraft.nbt.StringTag.valueOf(heart.wireless().channel(i).name()));
        tag.put("Names", rows); var stack = new ItemStack(net.minecraft.world.item.Items.PAPER);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag)); names.setItem(0, stack);
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
            selected = raw; page = selected / rowCount; refresh(); sendAllDataToRemote(); return true;
        }
        if (field == 8) { heart.openDashboard(player); return true; }
        if (field == 13) {
            if (heart.wireless().size()==0) return false;
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->
                    new dev.sablelife.module.ModuleFrequencyMenu(id,inventory,heart,selected),
                    net.minecraft.network.chat.Component.translatable("frequency.sablelife.title")));
            return true;
        }
        if (field == 14) {
            if (raw < 1 || raw > PAGE_SIZE) return false;
            rowCount = raw; page = selected / rowCount; refresh(); sendAllDataToRemote(); return true;
        }
        if (field == 12) { page = Math.clamp(raw, 0, Math.max(0, (heart.wireless().size() - 1) / rowCount)); broadcastChanges(); return true; }
        if (!heart.canEdit(player)) return false;
        if (field == 11) {
            if (raw != selected || !heart.wireless().remove(selected)) return false;
            selected = Math.min(selected, Math.max(0, heart.wireless().size() - 1)); page = selected / rowCount;
            refresh(); sendAllDataToRemote(); return true;
        }
        if (field == 10) {
            if (!dev.sablelife.module.AuthoringRateLimit.allow(player, 5)) return false;
            int added = heart.wireless().add(); if (added < 0) return false;
            selected = added; page = selected / rowCount; refresh(); sendAllDataToRemote(); return true;
        }
        if (heart.wireless().size() == 0 && field != 7) return false;
        channel = selected;
        if (field == 7) heart.wireless().master(raw != 0);
        else {
            if(field==16&&(raw>heart.wireless().size()||raw==selected+1))return false;
            if((field==15||field==16)&&heart.wireless().channel(channel).receiving())return false;
            if (!heart.wireless().channel(channel).set(field==15?7:field==16?8:field == 9 ? 6 : field, raw)) return false;
        }
        heart.wireless().configurationChanged(); heart.setChanged(); broadcastChanges(); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot == 2) return;
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
    @Override public boolean canDragTo(Slot slot) { return slot.index >= 3; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index >= 3; }
}
