package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartWirelessMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.ArrayList;
import java.util.List;

/** Channel sidebar, frequency sampler and waveform editor; no chest grid controls. */
@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartWirelessScreen extends AbstractContainerScreen<HeartWirelessMenu> {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(SableLife.WIRELESS_MENU.get(), HeartWirelessScreen::new);
    }
    private int tickCount;
    private final List<Button> channelButtons = new ArrayList<>(), editButtons = new ArrayList<>();
    private final List<EditBox> inputs = new ArrayList<>();
    private int page, lastSelected = -1;
    private int deleteUntil, deleteChannel = -1, deleteCount;
    private Button enabled, loop, master, picker, mode, add, previous, next, remove;
    public HeartWirelessScreen(HeartWirelessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 360; imageHeight = 234;
    }
    private Component tr(String key) { return Component.translatable("wireless.sablelife." + key); }
    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, leftPos + x, topPos + y, width, 18, action));
    }
    @Override protected void init() {
        super.init(); channelButtons.clear(); editButtons.clear(); inputs.clear();
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int row = i;
            channelButtons.add(button(Component.empty(), 10, 34 + i * 20, 119,
                    () -> send(0, 6, page * HeartWirelessMenu.PAGE_SIZE + row)));
        }
        previous = button(Component.literal("‹"), 10, 180, 28, () -> send(0, 12, page - 1));
        next = button(Component.literal("›"), 101, 180, 28, () -> send(0, 12, page + 1));
        add = button(tr("add"), 10, 208, 119, () -> send(0, 10, 0));
        button(tr("back"), 299, 7, 51, () -> send(0, 8, 0));
        master = button(Component.empty(), 10, 9, 119,
                () -> send(0, 7, menu.value(HeartWirelessMenu.MASTER) == 0 ? 1 : 0));
        picker = button(tr("choose_items"), 254, 38, 96, () -> {
            menu.inventoryVisible = !menu.inventoryVisible;
            for (EditBox input : inputs) input.setFocused(false);
        });
        picker.setTooltip(Tooltip.create(tr("sample_help")));
        for (int field = 1; field <= 4; field++) {
            final int parameter = field;
            int y = 70 + (field - 1) * 23;
            var input = new EditBox(font, leftPos + 239, topPos + y, 54, 18, tr("field_" + field));
            input.setFilter(s -> s.matches("[0-9]{0,4}(\\.[0-9]{0,2})?"));
            input.setMaxLength(7); addRenderableWidget(input); inputs.add(input);
            editButtons.add(button(Component.literal("−"), 296, y, 16, () -> adjust(parameter, -1)));
            editButtons.add(button(Component.literal("+"), 315, y, 16, () -> adjust(parameter, 1)));
            editButtons.add(button(Component.literal("✓"), 334, y, 16, () -> apply(parameter)));
        }
        enabled = button(Component.empty(), 145, 208, 65, () -> toggle(0));
        mode = button(Component.empty(), 145, 185, 205, () -> send(menu.selected(), 9, (menu.setting(menu.selected(), 6) + 1) % 5));
        LifeUiStyle.reverse(mode, () -> send(menu.selected(), 9, Math.floorMod(menu.setting(menu.selected(), 6) - 1, 5)));
        LifeUiStyle.reverse(master, master::onPress); LifeUiStyle.reverse(enabled, enabled::onPress); LifeUiStyle.reverse(picker, picker::onPress);
        mode.setTooltip(Tooltip.create(tr("mode_help")));
        loop = button(Component.empty(), 214, 208, 65, () -> toggle(5));
        LifeUiStyle.reverse(loop, loop::onPress);
        remove = button(tr("delete"), 283, 208, 67, () -> {
            if (deleteUntil > tickCount && deleteChannel == menu.selected() && deleteCount == menu.count()) {
                send(0, 11, menu.selected()); deleteUntil = 0;
            } else {
                deleteUntil = tickCount + 60; deleteChannel = menu.selected(); deleteCount = menu.count();
            }
        });
        remove.setTooltip(Tooltip.create(tr("delete_help")));
        editButtons.add(enabled); editButtons.add(loop);
        sync();
    }
    private void send(int channel, int field, int raw) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, field << 16 | Math.clamp(raw, 0, 65535));
    }
    private void toggle(int field) { send(menu.selected(), field, menu.setting(menu.selected(), field) == 0 ? 1 : 0); }
    private void adjust(int field, int direction) {
        int step = field == 4 ? 1 : hasShiftDown() ? 100 : 20;
        send(menu.selected(), field, Math.max(0, menu.setting(menu.selected(), field) + direction * step));
    }
    private void apply(int field) {
        EditBox input = inputs.get(field - 1);
        try {
            double number = Double.parseDouble(input.getValue());
            double limit = field == 4 ? 15 : 600;
            if (!Double.isFinite(number) || number < 0 || number > limit) { input.setTextColor(0xFF7777); return; }
            send(menu.selected(), field, (int) Math.round(number * (field == 4 ? 1 : 20)));
            input.setTextColor(0xE0E0E0); input.setFocused(false);
        } catch (NumberFormatException invalid) { input.setTextColor(0xFF7777); }
    }
    private void sync() {
        boolean editable = menu.value(HeartWirelessMenu.EDITABLE) != 0;
        page = menu.page();
        int selected = menu.selected(); boolean exists = menu.count() > 0;
        if (selected != deleteChannel || menu.count() != deleteCount) deleteUntil = 0;
        remove.setMessage(tr(deleteUntil > tickCount ? "confirm_delete" : "delete"));
        previous.active = page > 0; next.active = (page + 1) * HeartWirelessMenu.PAGE_SIZE < menu.count();
        add.active = editable && menu.count() < dev.sablelife.heart.HeartWireless.CHANNELS;
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int channel = page * HeartWirelessMenu.PAGE_SIZE + i, output = menu.rowPower(i);
            channelButtons.get(i).visible = channel < menu.count();
            channelButtons.get(i).setMessage(Component.literal((channel == selected ? "▶ " : "") + (channel + 1) + "  ")
                    .append(tr(menu.setting(channel, 0) == 0 ? "disabled" : output > 0 ? "powered" : "ready")));
        }
        for (int field = 1; field <= 4; field++) {
            var input = inputs.get(field - 1);
            if (lastSelected != selected || !input.isFocused()) {
                int value = menu.setting(selected, field);
                input.setValue(field == 4 ? Integer.toString(value) : String.format(java.util.Locale.ROOT, "%.2f", value / 20.0));
            }
            input.visible = exists && !menu.inventoryVisible; input.setEditable(exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2));
            input.active = exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2); input.setTextColor(input.active ? 0xE0E0E0 : 0x666666);
            if (!input.active) input.setFocused(false);
        }
        for (Button control : editButtons) { control.active = editable && exists; control.visible = exists && !menu.inventoryVisible; }
        boolean receiving = menu.setting(selected, 6) == 1;
        boolean automatic = receiving || menu.setting(selected, 6) >= 3;
        for (int i = 0; i < 12; i++) editButtons.get(i).active = editable && exists && !automatic;
        loop.active = editable && exists && !automatic;
        mode.active = editable && exists; mode.visible = exists && !menu.inventoryVisible; mode.setMessage(tr(switch (menu.setting(selected, 6)) { case 1 -> "mode_receive"; case 2 -> "mode_output"; case 3 -> "mode_program"; case 4 -> "mode_program_output"; default -> "mode_repeat"; }));
        remove.visible = exists && !menu.inventoryVisible; remove.active = editable && exists;
        enabled.setMessage(tr(menu.setting(selected, 0) == 0 ? "enable_short" : "disable_short"));
        loop.setMessage(tr(menu.setting(selected, 5) == 0 ? "once" : "loop"));
        master.setMessage(tr(menu.value(HeartWirelessMenu.MASTER) == 0 ? "master_off" : "master_on"));
        master.active = editable;
        picker.visible = exists; if (!exists) menu.inventoryVisible = false;
        picker.setMessage(tr(menu.inventoryVisible ? "done_items" : "choose_items"));
        lastSelected = selected;
    }
    @Override protected void containerTick() { super.containerTick(); tickCount++; sync(); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (!input.visible || !input.active || !input.isMouseOver(mouseX, mouseY)) continue;
            int field = i + 1;
            double value;
            try { value = Double.parseDouble(input.getValue()); }
            catch (NumberFormatException ignored) { value = menu.setting(menu.selected(), field) / (field == 4 ? 1. : 20.); }
            double minimum = field == 2 ? .05 : field == 4 ? 1 : 0;
            double nextValue = Math.clamp(Math.round((value + Math.signum(vertical) * (field == 4 ? 1 : .1)) * 100) / 100., minimum, field == 4 ? 15 : 600);
            input.setValue(field == 4 ? Integer.toString((int) nextValue) : String.format(java.util.Locale.ROOT, "%.2f", nextValue));
            apply(field); input.setFocused(true); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).isFocused()) { apply(i + 1); return true; }
        if (key != 256) for (EditBox input : inputs) if (input.visible && input.active && input.isFocused()) {
            input.keyPressed(key, scan, modifiers); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + 362, y + 236, 0xFF19151D);
        g.fill(x, y, x + 360, y + 234, 0xFF9B614E);
        g.fill(x + 3, y + 3, x + 357, y + 231, 0xFF242632);
        g.fill(x + 5, y + 5, x + 134, y + 229, 0xFF363140);
        g.fill(x + 136, y + 5, x + 138, y + 229, 0xFF9770B5);
        g.fill(x + 142, y + 30, x + 350, y + 63, 0xFF37313D);
        frame(g, x + 170, y + 38, 0xFFBE5356);
        frame(g, x + 220, y + 38, 0xFF5686C9);
        if (menu.inventoryVisible) {
            g.fill(x + 141, y + 68, x + 353, y + 204, 0xFF292A35);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) frame(g, x + 142 + col * 18, y + 84 + row * 18, 0xFF796F80);
            for (int col = 0; col < 9; col++) frame(g, x + 142 + col * 18, y + 142, 0xFF796F80);
        } else if (menu.count() > 0 && menu.setting(menu.selected(), 6) < 3) waveform(g);
    }
    private void frame(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 18, y + 18, color); g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF4A4555);
    }
    private void waveform(GuiGraphics g) {
        int channel = menu.selected(), delay = menu.setting(channel, 1), on = menu.setting(channel, 2), off = menu.setting(channel, 3);
        int horizon = Math.max(100, delay + (on + off) * 2);
        if (menu.setting(channel, 6) == 1) return;
        int x = leftPos + 145, y = topPos + 176, width = 205;
        g.fill(x, y, x + width, y + 7, 0xFF151B24);
        for (int pixel = 0; pixel < width; pixel++) {
            long t = (long) pixel * horizon / width;
            boolean active = t >= delay && (menu.setting(channel, 5) != 0 ? (t - delay) % Math.max(1, on + off) < on : t - delay < on);
            if (active) g.fill(x + pixel, y + 1, x + pixel + 1, y + 6, 0xFFAA79C8);
        }
        if (menu.value(HeartWirelessMenu.ALIVE) != 0 && menu.elapsed() <= horizon) {
            int cursor = (int) (menu.elapsed() * width / horizon);
            g.fill(x + cursor, y, x + cursor + 1, y + 7, 0xFF87F3B1);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, tr("title"), 145, 11, 0xF2DDD1, false);
        if (menu.count() > 0) g.drawString(font, Component.translatable("wireless.sablelife.channel", menu.selected() + 1), 145, 24, 0xD9C4ED, false);
        g.drawString(font, Component.literal((page + 1) + " / " + Math.max(1, (menu.count() + HeartWirelessMenu.PAGE_SIZE - 1) / HeartWirelessMenu.PAGE_SIZE)), 56, 185, 0xD9C4ED, false);
        if (menu.count() == 0) { g.drawWordWrap(font, tr("empty"), 145, 75, 199, 0xD9C4ED); return; }
        g.drawString(font, tr("red"), 150, 44, 0xEF8B89, false);
        g.drawString(font, tr("blue"), 200, 44, 0x83B6EE, false);
        if (menu.inventoryVisible) {
            g.drawString(font, playerInventoryTitle, 145, 73, 0xE6D8EA, false);
            g.drawWordWrap(font, tr("sample_help"), 145, 168, 199, 0xC2B1CA);
        } else {
            for (int field = 1; field <= 4; field++) g.drawString(font, tr("field_" + field), 145, 75 + (field - 1) * 23, 0xE6D8EA, false);
            g.drawString(font, menu.setting(menu.selected(), 6) == 1 ? Component.translatable("wireless.sablelife.receive_power", menu.value(HeartWirelessMenu.SELECTED_INPUT)) : tr(menu.setting(menu.selected(), 6) >= 3 ? "program_timeline" : "timeline"), 145, 166, 0xBDA5D0, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sync(); renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
