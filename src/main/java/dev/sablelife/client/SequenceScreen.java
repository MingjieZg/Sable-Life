package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SequenceScreen extends AbstractContainerScreen<SequenceMenu> {
    private int tickCount;
    private final List<Button> rows = new ArrayList<>(), apply = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private List<PulseSequence.Step> steps = List.of();
    private int selected, last = -1, page, slots, column, pendingAdd = -1;
    private int wireDeleteUntil, wireDeleteChannel = -1, wireDeleteCount;
    private Button previous, next, add, remove, up, down, mode, reset;
    private int sequenceWidth, wirelessX;
    private EditBox wirelessChannel;
    private Button wirePrev, wireNext, wireAdd, wireDelete, wireMode, wireEnabled, wireMaster, wireApply;
    public SequenceScreen(SequenceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(SableLife.SEQUENCE_MENU.get(), SequenceScreen::new); }
    private Component tr(String key) { return Component.translatable("sequence.sablelife." + key); }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, leftPos + x, topPos + y, w, 18, action));
    }
    private void send(String key, double value) {
        var tag = menu.snapshot().getCompound("Module"); if (!tag.hasUUID("Id")) return;
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, 0, tag.getUUID("Id"), "seq_edit", key, value, Integer.toString(selected)));
    }
    @Override protected void init() {
        imageWidth = Math.max(400, Math.min(500, width - 16)); imageHeight = Math.max(260, Math.min(340, height - 16));
        sequenceWidth = imageWidth - 184; wirelessX = sequenceWidth + 6;
        menu.layoutSlots(wirelessX, imageHeight - 84);
        super.init(); rows.clear(); fields.clear(); apply.clear(); column = (sequenceWidth - 32) / 2; slots = Math.max(4, (imageHeight - 120) / 20);
        button(tr("back"), sequenceWidth - 65, 8, 55, () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0));
        for (int i = 0; i < slots; i++) {
            final int row = i; rows.add(button(Component.empty(), 10, 34 + i * 20, column, () -> {
                selected = page * slots + row; fields.forEach(f -> f.setFocused(false));
            }));
        }
        int pager = imageHeight - 82;
        previous = button(Component.literal("<"), 10, pager, 25, () -> page--);
        next = button(Component.literal(">"), column - 15, pager, 25, () -> page++);
        add = button(tr("add"), 10, imageHeight - 60, column, () -> { pendingAdd = steps.size(); send("add", 0); });
        remove = button(tr("delete"), 10, imageHeight - 38, column / 2 - 2, () -> send("delete", 0));
        up = button(Component.literal("↑"), 12 + column / 2, imageHeight - 38, column / 4 - 3, () -> send("up", 0));
        down = button(Component.literal("↓"), 14 + column * 3 / 4, imageHeight - 38, column / 4 - 4, () -> send("down", 0));
        int x = 22 + column;
        mode = button(Component.empty(), x, 49, column, () -> {
            if (selected < steps.size()) send("mode", steps.get(selected).waiting() ? 0 : 1);
        });
        LifeUiStyle.reverse(mode, () -> { if (selected < steps.size()) send("mode", steps.get(selected).waiting() ? 0 : 1); });
        String[] keys = {"channel", "power", "time"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            var field = addRenderableWidget(new EditBox(font, leftPos + x, topPos + 81 + i * 32, column - 32, 18, tr(keys[i])));
            field.setMaxLength(9); field.setFilter(s -> s.matches("[0-9]{0,6}(\\.[0-9]{0,2})?")); fields.add(field);
            apply.add(button(Component.literal("✓"), x + column - 27, 81 + i * 32, 27, () -> apply(index)));
        }
        reset = button(tr("reset"), x, imageHeight - 38, column, () -> send("reset", 0));
        reset.setTooltip(Tooltip.create(tr("reset_help"))); mode.setTooltip(Tooltip.create(tr("help")));
        wirePrev = button(Component.literal("<"), wirelessX + 3, 49, 22, () -> send("wire_select", menu.snapshot().getInt("WirelessSelected")));
        wireNext = button(Component.literal(">"), wirelessX + 141, 49, 24, () -> send("wire_select", menu.snapshot().getInt("WirelessSelected") + 2));
        wirelessChannel = addRenderableWidget(new EditBox(font, leftPos + wirelessX + 29, topPos + 49, 80, 18, tr("wire_channel")));
        wirelessChannel.setFilter(value -> value.matches("[0-9]{0,5}"));
        wireApply = button(Component.literal("✓"), wirelessX + 113, 49, 24, this::selectWireless);
        wireAdd = button(tr("wire_add"), wirelessX + 3, 73, 79, () -> send("wire_add", 0));
        wireDelete = button(Component.translatable("wireless.sablelife.delete"), wirelessX + 86, 73, 79, () -> {
            var data = menu.snapshot(); int current = data.getInt("WirelessSelected"), count = data.getInt("Channels");
            if (wireDeleteUntil > tickCount && wireDeleteChannel == current && wireDeleteCount == count) {
                send("wire_delete", 0); wireDeleteUntil = 0;
            } else {
                wireDeleteUntil = tickCount + 60; wireDeleteChannel = current; wireDeleteCount = count;
            }
        });
        wireDelete.setTooltip(Tooltip.create(Component.translatable("wireless.sablelife.delete_help")));
        wireEnabled = button(Component.empty(), wirelessX + 3, 95, 79, () -> send("wire_enable", menu.snapshot().getBoolean("WirelessEnabled") ? 0 : 1));
        wireMaster = button(Component.empty(), wirelessX + 86, 95, 79, () -> send("wire_master", menu.snapshot().getBoolean("WirelessMaster") ? 0 : 1));
        wireMode = button(Component.empty(), wirelessX + 3, 117, 162, () -> send("wire_mode", (menu.snapshot().getInt("WirelessMode") + 1) % 5));
        LifeUiStyle.reverse(wireEnabled, wireEnabled::onPress); LifeUiStyle.reverse(wireMaster, wireMaster::onPress);
        LifeUiStyle.reverse(wireMode, () -> send("wire_mode", Math.floorMod(menu.snapshot().getInt("WirelessMode") - 1, 5)));
        wireMode.setTooltip(Tooltip.create(Component.translatable("wireless.sablelife.mode_help")));
        last = -1; sync();
    }
    private void selectWireless() {
        try { send("wire_select", Integer.parseInt(wirelessChannel.getValue())); wirelessChannel.setFocused(false); }
        catch (NumberFormatException ignored) {}
    }
    private void apply(int index) {
        if (!fields.get(index).active) return;
        try {
            double value = Double.parseDouble(fields.get(index).getValue());
            if (Double.isFinite(value)) send(new String[]{"channel", "power", "time"}[index], value);
            fields.get(index).setFocused(false);
        } catch (NumberFormatException ignored) {}
    }
    private void sync() {
        var data = menu.snapshot(); var module = VirtualModule.load(data.getCompound("Module")); steps = module.sequence();
        if (pendingAdd >= 0 && steps.size() > pendingAdd) { selected = steps.size() - 1; page = selected / slots; pendingAdd = -1; }
        selected = Math.clamp(selected, 0, Math.max(0, steps.size() - 1)); page = Math.clamp(page, 0, Math.max(0, (steps.size() - 1) / slots));
        boolean editable = data.getBoolean("Editable"), exists = !steps.isEmpty();
        for (int i = 0; i < rows.size(); i++) {
            int index = page * slots + i; var row = rows.get(i); row.visible = index < steps.size();
            if (!row.visible) continue;
            var step = steps.get(index);
            row.setMessage(Component.literal((index == selected ? "▶ " : "") + (index + 1) + ". ")
                    .append(step.waiting() ? tr("wait") : Component.translatable("sequence.sablelife.row_output", step.channel(), step.power()))
                    .append(String.format(Locale.ROOT, "  %.2fs", step.ticks() / 20.)));
        }
        previous.active = page > 0; next.active = (page + 1) * slots < steps.size();
        add.active = editable && steps.size() < PulseSequence.LIMIT; remove.active = editable && exists;
        up.active = editable && exists && selected > 0; down.active = editable && selected + 1 < steps.size();
        mode.active = editable && exists; reset.active = editable;
        mode.setMessage(tr(exists && steps.get(selected).waiting() ? "wait" : "output"));
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i); f.visible = exists; apply.get(i).visible = exists;
            f.active = editable && exists && (i == 2 || !steps.get(selected).waiting()); f.setEditable(f.active); apply.get(i).active = f.active;
            if (exists && (!f.isFocused() || last != selected)) { var s = steps.get(selected);
                f.setValue(i == 0 ? Integer.toString(s.channel()) : i == 1 ? Integer.toString(s.power()) : String.format(Locale.ROOT, "%.2f", s.ticks() / 20.));
            }
        }
        if (exists && last != selected && !steps.get(selected).waiting() && steps.get(selected).channel() > 0 && steps.get(selected).channel() <= data.getInt("Channels"))
            send("wire_select", steps.get(selected).channel());
        last = selected;
        int count = data.getInt("Channels"), wireSelected = data.getInt("WirelessSelected");
        if (wireSelected != wireDeleteChannel || count != wireDeleteCount) wireDeleteUntil = 0;
        wireDelete.setMessage(Component.translatable("wireless.sablelife." + (wireDeleteUntil > tickCount ? "confirm_delete" : "delete")));
        if (!wirelessChannel.isFocused()) wirelessChannel.setValue(count == 0 ? "0" : Integer.toString(wireSelected + 1));
        wirelessChannel.active = count > 0; wirelessChannel.setEditable(count > 0);
        wireApply.active = count > 0; wirePrev.active = wireSelected > 0; wireNext.active = wireSelected + 1 < count;
        wireAdd.active = editable && count < (data.contains("ChannelLimit")?data.getInt("ChannelLimit"):65535); wireDelete.active = editable && count > 0;
        wireEnabled.active = editable && count > 0; wireMaster.active = editable; wireMode.active = editable && count > 0;
        wireEnabled.setMessage(tr(data.getBoolean("WirelessEnabled") ? "wire_enabled" : "wire_disabled"));
        wireMaster.setMessage(Component.translatable("wireless.sablelife." + (data.getBoolean("WirelessMaster") ? "master_on" : "master_off")));
        wireMode.setMessage(Component.translatable("wireless.sablelife." + switch (data.getInt("WirelessMode")) {
            case 1 -> "mode_receive"; case 2 -> "mode_output"; case 3 -> "mode_program"; case 4 -> "mode_program_output"; default -> "mode_repeat";
        }));
        if (exists) {
            int channel = steps.get(selected).channel();
            boolean available = java.util.Arrays.stream(data.getIntArray("ProgramChannels")).anyMatch(n -> n == channel);
            fields.getFirst().setTooltip(Tooltip.create(tr(available ? "channel_ready" : "channel_help")));
            fields.getFirst().setTextColor(available ? 0xE0E0E0 : 0xEF8B89);
        }
    }
    @Override protected void containerTick() { super.containerTick(); tickCount++; sync(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && wirelessChannel.isFocused()) { selectWireless(); return true; }
        if (key == 257 || key == 335) for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused()) { apply(i); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        if (v != 0 && wirelessChannel.isMouseOver(x, y) && menu.snapshot().getInt("Channels") > 0) { send("wire_select", Math.clamp(menu.snapshot().getInt("WirelessSelected") + 1 + (int) Math.signum(v), 1, menu.snapshot().getInt("Channels"))); return true; }
        if (v != 0) for (int i = 0; i < fields.size(); i++) { var f = fields.get(i);
            if (!f.active || !f.visible || !f.isMouseOver(x, y)) continue;
            try {
                double value = Double.parseDouble(f.getValue());
                value = Math.clamp(value + Math.signum(v) * (i == 2 ? .1 : 1), i == 2 ? .05 : 1, i == 0 ? 65535 : i == 1 ? 15 : 600);
                f.setValue(i == 2 ? String.format(Locale.ROOT, "%.2f", value) : Integer.toString((int) value)); apply(i); return true;
            } catch (NumberFormatException ignored) {}
        }
        return super.mouseScrolled(x, y, h, v);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF252631);
        g.fill(leftPos + 7, topPos + 30, leftPos + column + 13, topPos + imageHeight - 8, 0xFF37313D);
        g.fill(leftPos + sequenceWidth, topPos + 30, leftPos + sequenceWidth + 2, topPos + imageHeight - 8, 0xFF9770B5);
        g.fill(leftPos + wirelessX, topPos + 30, leftPos + imageWidth - 8, topPos + imageHeight - 8, 0xFF37313D);
        frame(g, wirelessX + 38, 145, 0xFFBE5356); frame(g, wirelessX + 104, 145, 0xFF5686C9);
        int inventoryY = imageHeight - 84;
        for (int n = 0; n < 36; n++) frame(g, wirelessX + 3 + n % 9 * 18, inventoryY + (n < 27 ? n / 9 * 18 : 58), 0xFF796F80);
        g.fill(leftPos + column + 16, topPos + 30, leftPos + column + 18, topPos + imageHeight - 8, 0xFF9770B5);
    }
    private void frame(GuiGraphics g, int x, int y, int color) {
        g.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, color);
        g.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xFF4A4555);
    }
    private void label(GuiGraphics g, Component text, int x, int y, int available, int color) {
        float scale = Math.min(LifeUiStyle.scale(), available / (float) Math.max(1, font.width(text)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false); g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        label(g, tr("title"), 10, 12, sequenceWidth - 82, 0xE6D8EA);
        label(g, tr("wire_title"), wirelessX + 3, 35, 162, 0xE6D8EA);
        label(g, tr("wire_items"), wirelessX + 3, 137, 162, 0xD9C4ED);
        label(g, Component.translatable("container.inventory"), wirelessX + 3, imageHeight - 96, 162, 0xD9C4ED);
        g.drawString(font, (page + 1) + " / " + Math.max(1, (steps.size() + slots - 1) / slots), 46, imageHeight - 77, 0xD9C4ED, false);
        int x = 22 + column;
        if (!steps.isEmpty()) {
            label(g, Component.translatable("sequence.sablelife.editing", selected + 1), x, 35, column, 0xD9C4ED);
            label(g, tr("channel"), x, 70, column, 0xD9C4ED);
            label(g, tr("power"), x, 102, column, 0xD9C4ED);
            label(g, tr("time"), x, 134, column, 0xD9C4ED);
        }
        var state = menu.snapshot().getCompound("Runtime");
        Component runtime = state.getBoolean("Running") ? Component.translatable("sequence.sablelife.running", state.getInt("Step") + 1, String.format(Locale.ROOT, "%.2f", state.getInt("Remaining") / 20.)) : tr("idle");
        g.drawWordWrap(font, runtime, x, imageHeight - 72, column, 0x7BE3AA);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        sync(); renderBackground(g, mx, my, partial); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
    }
}
