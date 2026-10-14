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
    private int detailX, detailWidth, inputX, fieldPitch, sidebarPitch, requestedRows;
    private boolean showTimeline;
    private int deleteUntil, deleteChannel = -1, deleteCount;
    private Button enabled, loop, master, picker, mode, add, previous, next, remove, nameApply, back;
    private EditBox nameInput;
    public HeartWirelessScreen(HeartWirelessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 360; imageHeight = 234;
    }
    private Component tr(String key) { return Component.translatable("wireless.sablelife." + key); }
    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, leftPos + x, topPos + y, width, 18, action));
    }
    @Override protected void init() {
        imageWidth = Math.max(300, Math.min(480, width - 14)); imageHeight = Math.max(200, height - 14);
        detailX = Math.min(155, Math.max(103, Math.round(imageWidth * .32f))); detailWidth = imageWidth - detailX - 10;
        inputX = detailX + Math.round(detailWidth * .45f); showTimeline = imageHeight >= 250;
        fieldPitch = Math.clamp((imageHeight - (showTimeline ? 89 : 55) - 96) / 4, 12, 30);
        sidebarPitch = Math.max(14, (int)Math.ceil(font.lineHeight * LifeUiStyle.scale()) + 8);
        requestedRows = Math.clamp((imageHeight - 51 - 34 - 5) / sidebarPitch, 1, HeartWirelessMenu.PAGE_SIZE);
        menu.layoutSlots(detailX + 35, detailX + 91, 60);
        super.init(); channelButtons.clear(); editButtons.clear(); inputs.clear();
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int row = i;
            var control = button(Component.empty(), 10, 34 + i * sidebarPitch, detailX - 20,
                    () -> send(0, 6, page * menu.pageSize() + row)); control.setHeight(sidebarPitch - 2); channelButtons.add(control);
        }
        previous = button(Component.literal("‹"), 10, imageHeight - 51, 28, () -> send(0, 12, page - 1));
        next = button(Component.literal("›"), detailX - 38, imageHeight - 51, 28, () -> send(0, 12, page + 1));
        add = button(tr("add"), 10, imageHeight - 28, detailX - 20, () -> send(0, 10, 0));
        back = button(tr("back"), imageWidth - 62, 8, 52, () -> send(0, 8, 0));
        var density = button(Component.translatable("dashboard.sablelife.density_" + LifeUiStyle.density()), imageWidth - 106, 8, 40, () -> changeDensity(1));
        LifeUiStyle.reverse(density, () -> changeDensity(-1));
        density.setTooltip(Tooltip.create(Component.translatable("dashboard.sablelife.density_help")));
        master = button(Component.empty(), 10, 8, detailX - 20,
                () -> send(0, 7, menu.value(HeartWirelessMenu.MASTER) == 0 ? 1 : 0));
        nameInput = addRenderableWidget(new EditBox(font, leftPos + detailX + 58, topPos + 34, detailWidth - 82, 18, tr("name")));
        nameInput.setMaxLength(dev.sablelife.heart.WirelessChannel.NAME_LIMIT * 2);
        nameInput.setHint(tr("name")); nameInput.setTooltip(Tooltip.create(tr("name_help")));
        nameApply = addRenderableWidget(LifeUiStyle.button(Component.literal("✓"), leftPos + imageWidth - 30, topPos + 34, 20, 18, this::rename));
        picker = button(tr("choose_items"), detailX + 118, 60, detailWidth - 118, () -> send(0,13,0));
        picker.setTooltip(Tooltip.create(tr("sample_help")));
        for (int field = 1; field <= 4; field++) {
            final int parameter = field;
            int y = 96 + (field - 1) * fieldPitch;
            var input = new EditBox(font, leftPos + inputX, topPos + y, imageWidth - inputX - 68, fieldPitch - 2, tr("field_" + field));
            input.setFilter(s -> s.matches("[0-9]{0,4}(\\.[0-9]{0,2})?"));
            input.setMaxLength(7); addRenderableWidget(input); inputs.add(input);
            editButtons.add(button(Component.literal("−"), imageWidth - 64, y, 16, () -> adjust(parameter, -1)));
            editButtons.add(button(Component.literal("+"), imageWidth - 46, y, 16, () -> adjust(parameter, 1)));
            editButtons.add(button(Component.literal("✓"), imageWidth - 28, y, 18, () -> apply(parameter)));
        }
        for (var control : editButtons) control.setHeight(fieldPitch - 2);
        int third = (detailWidth - 8) / 3;
        enabled = button(Component.empty(), detailX, imageHeight - 28, third, () -> toggle(0));
        mode = button(Component.empty(), detailX, imageHeight - 52, detailWidth, () -> send(menu.selected(), 9, (menu.setting(menu.selected(), 6) + 1) % 5));
        LifeUiStyle.reverse(mode, () -> send(menu.selected(), 9, Math.floorMod(menu.setting(menu.selected(), 6) - 1, 5)));
        LifeUiStyle.reverse(master, master::onPress); LifeUiStyle.reverse(enabled, enabled::onPress); LifeUiStyle.reverse(picker, picker::onPress);
        mode.setTooltip(Tooltip.create(tr("mode_help")));
        loop = button(Component.empty(), detailX + third + 4, imageHeight - 28, third, () -> toggle(5));
        LifeUiStyle.reverse(loop, loop::onPress);
        remove = button(tr("delete"), detailX + (third + 4) * 2, imageHeight - 28, detailWidth - (third + 4) * 2, () -> {
            if (deleteUntil > tickCount && deleteChannel == menu.selected() && deleteCount == menu.count()) {
                send(0, 11, menu.selected()); deleteUntil = 0;
            } else {
                deleteUntil = tickCount + 60; deleteChannel = menu.selected(); deleteCount = menu.count();
            }
        });
        remove.setTooltip(Tooltip.create(tr("delete_help")));
        editButtons.add(enabled); editButtons.add(loop);
        sync(); send(0, 14, requestedRows);
    }
    private void changeDensity(int direction) { LifeUiStyle.cycle(direction); rebuildWidgets(); }
    private void send(int channel, int field, int raw) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, field << 16 | Math.clamp(raw, 0, 65535));
    }
    private void rename() {
        if (!nameInput.active) return;
        if (nameInput.getValue().codePointCount(0, nameInput.getValue().length()) > dev.sablelife.heart.WirelessChannel.NAME_LIMIT) { nameInput.setTextColor(0xFF7777); return; }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.sablelife.module.ModuleNetworking.Action(menu.containerId, 0, new java.util.UUID(0, 0), "wireless_name", "", menu.selected(), nameInput.getValue()));
        nameInput.setTextColor(0xE0E0E0); nameInput.setFocused(false);
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
        previous.active = page > 0; next.active = (page + 1) * menu.pageSize() < menu.count();
        add.active = editable && menu.count() < ((menu.value(HeartWirelessMenu.CHANNEL_LIMIT)&0xffff)==0?dev.sablelife.heart.HeartWireless.CHANNELS:menu.value(HeartWirelessMenu.CHANNEL_LIMIT)&0xffff);
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int channel = page * menu.pageSize() + i, output = menu.rowPower(i);
            channelButtons.get(i).visible = i < Math.min(requestedRows, menu.pageSize()) && channel < menu.count();
            var label = Component.literal((channel == selected ? "▶ " : "") + (channel + 1) + ".  ")
                    .append(tr(menu.setting(channel, 0) == 0 ? "disabled" : output > 0 ? "powered" : "ready"));
            var name = menu.channelName(channel); if (!name.isEmpty()) label.append("  ").append(Component.literal(name));
            channelButtons.get(i).setMessage(label); channelButtons.get(i).setTooltip(name.isEmpty() ? null : Tooltip.create(label));
        }
        for (int field = 1; field <= 4; field++) {
            var input = inputs.get(field - 1);
            if (lastSelected != selected || !input.isFocused()) {
                int value = menu.setting(selected, field);
                input.setValue(field == 4 ? Integer.toString(value) : String.format(java.util.Locale.ROOT, "%.2f", value / 20.0));
            }
            input.visible = exists; input.setEditable(exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2));
            input.active = exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2); input.setTextColor(input.active ? 0xE0E0E0 : 0x666666);
            if (!input.active) input.setFocused(false);
        }
        for (Button control : editButtons) { control.active = editable && exists; control.visible = exists; }
        boolean receiving = menu.setting(selected, 6) == 1;
        boolean automatic = receiving || menu.setting(selected, 6) >= 3;
        for (int i = 0; i < 12; i++) editButtons.get(i).active = editable && exists && !automatic;
        loop.active = editable && exists && !automatic;
        mode.active = editable && exists; mode.visible = exists; mode.setMessage(tr(switch (menu.setting(selected, 6)) { case 1 -> "mode_receive"; case 2 -> "mode_output"; case 3 -> "mode_program"; case 4 -> "mode_program_output"; default -> "mode_repeat"; }));
        remove.visible = exists; remove.active = editable && exists;
        enabled.setMessage(tr(menu.setting(selected, 0) == 0 ? "enable_short" : "disable_short"));
        loop.setMessage(tr(menu.setting(selected, 5) == 0 ? "once" : "loop"));
        master.setMessage(tr(menu.value(HeartWirelessMenu.MASTER) == 0 ? "master_off" : "master_on"));
        master.active = editable;
        picker.visible = exists;
        picker.setMessage(tr("choose_items"));
        nameInput.visible = exists; nameApply.visible = exists;
        nameInput.active = editable && exists; nameInput.setEditable(nameInput.active); nameApply.active = nameInput.active;
        if (lastSelected != selected) nameInput.setFocused(false);
        if (!nameInput.isFocused()) nameInput.setValue(menu.channelName(selected));
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
        if (nameInput.visible && nameInput.active && nameInput.isFocused()) {
            if (key == 257 || key == 335) { rename(); return true; }
            if (key != 256) { nameInput.keyPressed(key, scan, modifiers); return true; }
        }
        if (key == 257 || key == 335) for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).isFocused()) { apply(i + 1); return true; }
        if (key != 256) for (EditBox input : inputs) if (input.visible && input.active && input.isFocused()) {
            input.keyPressed(key, scan, modifiers); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF19151D);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF9B614E);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF242632);
        g.fill(x + 5, y + 5, x + detailX - 8, y + imageHeight - 5, 0xFF363140);
        g.fill(x + detailX - 6, y + 5, x + detailX - 4, y + imageHeight - 5, 0xFF9770B5);
        g.fill(x + detailX - 3, y + 56, x + imageWidth - 10, y + 88, 0xFF37313D);
        frame(g, x + detailX + 34, y + 59, 0xFFBE5356);
        frame(g, x + detailX + 90, y + 59, 0xFF5686C9);
        if (showTimeline && menu.count() > 0 && menu.setting(menu.selected(), 6) < 3) waveform(g);
    }
    private void frame(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 18, y + 18, color); g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF4A4555);
    }
    private void waveform(GuiGraphics g) {
        int channel = menu.selected(), delay = menu.setting(channel, 1), on = menu.setting(channel, 2), off = menu.setting(channel, 3);
        int horizon = Math.max(100, delay + (on + off) * 2);
        if (menu.setting(channel, 6) == 1) return;
        int x = leftPos + detailX, y = topPos + imageHeight - 68, width = detailWidth;
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
    private void text(GuiGraphics g, Component label, int x, int y, int available, int color) {
        float scale = Math.min(LifeUiStyle.scale(), available / (float)Math.max(1, font.width(label)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, label, 0, 0, color, false); g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        text(g, tr("title"), detailX, 12, detailWidth - 100, 0xF2DDD1);
        if (menu.count() > 0) text(g, Component.translatable("wireless.sablelife.channel", menu.selected() + 1), detailX, 39, 54, 0xD9C4ED);
        var pages = Component.literal((page + 1) + " / " + Math.max(1, (menu.count() + menu.pageSize() - 1) / menu.pageSize()));
        text(g, pages, 43, imageHeight - 46, detailX - 84, 0xD9C4ED);
        if (menu.count() == 0) { g.drawWordWrap(font, tr("empty"), detailX, 96, detailWidth, 0xD9C4ED); return; }
        text(g, tr("red"), detailX + 5, 66, 26, 0xEF8B89);
        text(g, tr("blue"), detailX + 61, 66, 26, 0x83B6EE);
        for (int field = 1; field <= 4; field++) text(g, tr("field_" + field), detailX, 96 + (field - 1) * fieldPitch + 3, inputX - detailX - 6, 0xE6D8EA);
        if (showTimeline) text(g, menu.setting(menu.selected(), 6) == 1 ? Component.translatable("wireless.sablelife.receive_power", menu.value(HeartWirelessMenu.SELECTED_INPUT)) : tr(menu.setting(menu.selected(), 6) >= 3 ? "program_timeline" : "timeline"), detailX, imageHeight - 83, detailWidth, 0xBDA5D0);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sync(); renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
