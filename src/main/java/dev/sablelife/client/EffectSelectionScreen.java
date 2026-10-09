package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EffectSelectionScreen extends AbstractContainerScreen<EffectSelectionMenu> {
    private final List<Button> availableRows = new ArrayList<>(), selectedRows = new ArrayList<>();
    private List<String> available = List.of();
    private List<ModuleEffects.Entry> selected = List.of();
    private VirtualModule particleModule;
    private String leftSelection = "", rightSelection = "", lastRight = "";
    private int leftPage, rightPage, rows, column;
    private EditBox search, duration, level;
    private Button add, remove, durationApply, levelApply, leftPrev, leftNext, rightPrev, rightNext;
    public EffectSelectionScreen(EffectSelectionMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(SableLife.EFFECT_MENU.get(), EffectSelectionScreen::new); }
    private boolean particlesMode;
    private Component tr(String key) { return Component.translatable((particlesMode ? "particles.sablelife." : "effects.sablelife.") + key); }
    private Component name(String id) {
        if (particlesMode) return Component.literal(ResourceLocation.parse(id).getPath().replace('_', ' '));
        var effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(id));
        return effect == null ? Component.literal(id) : effect.getDisplayName();
    }
    private List<String> filtered() {
        String query = search.getValue().toLowerCase(Locale.ROOT).trim();
        return available.stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(query) || name(id).getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private ModuleEffects.Entry current() { return selected.stream().filter(e -> e.id().equals(rightSelection)).findFirst().orElse(null); }
    private void send(String operation, double value, String id) {
        var tag = menu.snapshot().getCompound("Module"); if (!tag.hasUUID("Id")) return;
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, 0, tag.getUUID("Id"), "effect", operation, value, id));
    }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, leftPos + x, topPos + y, w, 16, action));
    }
    @Override protected void init() {
        imageWidth = Math.max(320, Math.min(420, width - 16)); imageHeight = Math.max(230, Math.min(310, height - 16));
        super.init(); availableRows.clear(); selectedRows.clear(); column = (imageWidth - 30) / 2;
        rows = Math.max(4, (imageHeight - 132) / 18);
        particlesMode = false;
        available = BuiltInRegistries.MOB_EFFECT.keySet().stream().map(Object::toString).filter(ModuleEffects::valid)
                .sorted(Comparator.comparing(id -> name(id).getString())).toList();
        button(tr("back"), imageWidth - 65, 8, 55, () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0));
        search = addRenderableWidget(new EditBox(font, leftPos + 10, topPos + 32, column, 16, tr("search")));
        search.setMaxLength(80); search.setHint(tr("search")); search.setResponder(value -> leftPage = 0);
        for (int i = 0; i < rows; i++) {
            final int row = i;
            availableRows.add(button(Component.empty(), 10, 54 + i * 18, column, () -> {
                var values = filtered(); int index = leftPage * rows + row; if (index < values.size()) leftSelection = values.get(index);
            }));
            selectedRows.add(button(Component.empty(), 20 + column, 54 + i * 18, column, () -> {
                int index = rightPage * rows + row; if (index < selected.size()) rightSelection = selected.get(index).id();
                duration.setFocused(false); level.setFocused(false);
            }));
        }
        int pager = imageHeight - 72, bottom = imageHeight - 54;
        leftPrev = button(Component.literal("<"), 10, pager, 24, () -> leftPage--);
        leftNext = button(Component.literal(">"), 10 + column - 24, pager, 24, () -> leftPage++);
        rightPrev = button(Component.literal("<"), 20 + column, pager, 24, () -> rightPage--);
        rightNext = button(Component.literal(">"), 20 + 2 * column - 24, pager, 24, () -> rightPage++);
        add = button(tr("add"), 10, bottom, column, () -> {
            rightSelection = leftSelection; duration.setFocused(false); level.setFocused(false);
            send("add", 0, leftSelection);
        });
        remove = button(tr("remove"), 20 + column, bottom, column, () -> send("remove", 0, rightSelection));
        int rightX = 20 + column;
        duration = addRenderableWidget(new EditBox(font, leftPos + rightX, topPos + imageHeight - 26, 56, 16, tr("duration")));
        level = addRenderableWidget(new EditBox(font, leftPos + rightX + column - 60, topPos + imageHeight - 26, 30, 16, tr("level")));
        duration.setMaxLength(8); level.setMaxLength(5);
        duration.setTooltip(Tooltip.create(tr("duration_help"))); level.setTooltip(Tooltip.create(tr("level_help")));
        durationApply = button(Component.literal("\u2713"), rightX + 59, imageHeight - 26, 22, () -> apply(false));
        levelApply = button(Component.literal("\u2713"), rightX + column - 26, imageHeight - 26, 22, () -> apply(true));
        sync();
    }
    private void apply(boolean amplifier) {
        if (current() == null) return;
        var box = amplifier ? level : duration;
        try {
            double value = Double.parseDouble(box.getValue());
            if (!Double.isFinite(value)) return;
            value = Math.clamp(value, amplifier ? (particlesMode ? .1 : 1) : 0, amplifier ? (particlesMode ? 4 : 5) : 60);
            if (particlesMode && !amplifier && value > 0) value = Math.max(.05, value);
            box.setValue(amplifier && !particlesMode ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.2f", value));
            send(amplifier ? (particlesMode ? "size" : "level") : "duration", value, rightSelection); box.setFocused(false);
        } catch (NumberFormatException ignored) { box.setTextColor(0xFF7777); }
    }
    private void sync() {
        var module = menu.snapshot().getCompound("Module");
        boolean particle = menu.snapshot().getBoolean("ParticleMode");
        if (particle != particlesMode) {
            particlesMode = particle; leftSelection = rightSelection = lastRight = ""; leftPage = rightPage = 0;
            available = (particlesMode ? BuiltInRegistries.PARTICLE_TYPE.keySet().stream() : BuiltInRegistries.MOB_EFFECT.keySet().stream())
                    .map(Object::toString).filter(id -> particlesMode ? ModuleParticles.valid(id) : ModuleEffects.valid(id))
                    .sorted(Comparator.comparing(id -> name(id).getString())).toList();
            duration.setTooltip(Tooltip.create(tr("duration_help"))); level.setTooltip(Tooltip.create(tr("level_help")));
            search.setHint(tr("search")); add.setMessage(tr("add")); remove.setMessage(tr("remove"));
        }
        particleModule = particlesMode ? VirtualModule.load(module) : null;
        selected = particlesMode ? ModuleParticles.load(module.getList("Particles", 8)).stream().map(id -> new ModuleEffects.Entry(id, 0, 1)).toList()
                : ModuleEffects.load(module.getList("Effects", 10));
        duration.visible = level.visible = durationApply.visible = levelApply.visible = true;
        var values = filtered(); leftPage = Math.clamp(leftPage, 0, Math.max(0, (values.size() - 1) / rows));
        rightPage = Math.clamp(rightPage, 0, Math.max(0, (selected.size() - 1) / rows));
        for (int i = 0; i < rows; i++) {
            int l = leftPage * rows + i, r = rightPage * rows + i;
            var a = availableRows.get(i); var b = selectedRows.get(i); a.visible = l < values.size(); b.visible = r < selected.size();
            if (a.visible) {
                String id = values.get(l); a.setMessage(Component.literal(id.equals(leftSelection) ? "> " : "").append(name(id)));
                a.setTooltip(Tooltip.create(Component.empty().append(name(id)).append("\n" + id).append("\n").append(Component.translatable("effects.sablelife.quick_add"))));
            }
            if (b.visible) {
                var e = selected.get(r); b.setMessage(Component.literal(e.id().equals(rightSelection) ? "> " : "").append(name(e.id())).append(particlesMode ? "" : " " + e.level()));
                b.setTooltip(Tooltip.create(Component.empty().append(name(e.id())).append("\n" + e.id()).append(particlesMode ? "" : "\n" + e.ticks() / 20. + "s").append("\n").append(Component.translatable("effects.sablelife.quick_remove"))));
            }
        }
        boolean editable = menu.snapshot().getBoolean("Editable");
        add.active = editable && !leftSelection.isEmpty() && selected.size() < (particlesMode ? ModuleParticles.LIMIT : ModuleEffects.LIMIT) && selected.stream().noneMatch(e -> e.id().equals(leftSelection));
        remove.active = editable && current() != null;
        leftPrev.active = leftPage > 0; leftNext.active = (leftPage + 1) * rows < values.size();
        rightPrev.active = rightPage > 0; rightNext.active = (rightPage + 1) * rows < selected.size();
        boolean active = editable && current() != null;
        for (var box : List.of(duration, level)) { box.active = active; box.setEditable(active); if (!active) box.setFocused(false); }
        durationApply.active = levelApply.active = active;
        var e = current(); boolean changed = !rightSelection.equals(lastRight);
        if (changed && e != null) rightPage = selected.indexOf(e) / rows;
        if (changed || !duration.isFocused()) duration.setValue(e == null ? "" : String.format(Locale.ROOT, "%.2f", (particlesMode ? particleModule.particleEntry(e.id()).ticks() : e.ticks()) / 20.));
        if (changed || !level.isFocused()) level.setValue(e == null ? "" : particlesMode ? String.format(Locale.ROOT, "%.2f", particleModule.particleEntry(e.id()).size()) : Integer.toString(e.level()));
        duration.setTextColor(0xE0E0E0); level.setTextColor(0xE0E0E0);
        if (e != null || rightSelection.isEmpty()) lastRight = rightSelection;
    }
    @Override protected void containerTick() { super.containerTick(); sync(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) {
            var values = filtered();
            for (int row = 0; row < rows; row++) {
                var left = availableRows.get(row); var right = selectedRows.get(row);
                int index = leftPage * rows + row;
                if (left.visible && left.isMouseOver(x, y) && index < values.size()) {
                    leftSelection = values.get(index); sync();
                    if (add.active) { left.playDownSound(minecraft.getSoundManager()); add.onPress(); }
                    return true;
                }
                index = rightPage * rows + row;
                if (right.visible && right.isMouseOver(x, y) && index < selected.size()) {
                    rightSelection = selected.get(index).id(); duration.setFocused(false); level.setFocused(false); sync();
                    if (remove.active) { right.playDownSound(minecraft.getSoundManager()); remove.onPress(); }
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) { if (duration.isFocused()) { apply(false); return true; } if (level.isFocused()) { apply(true); return true; } }
        if (key != 256) for (var box : List.of(search, duration, level)) if (box.isFocused()) { box.keyPressed(key, scan, modifiers); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) {
            for (var box : List.of(duration, level)) if (box.active && box.isMouseOver(x, y)) {
                try {
                    boolean amplifier = box == level; double n = Math.clamp(Math.round((Double.parseDouble(box.getValue()) + Math.signum(vertical) * (amplifier && !particlesMode ? 1 : .1))*100)/100.,amplifier?(particlesMode?.1:1):0,amplifier?(particlesMode?4:5):60);
                    box.setValue(String.format(Locale.ROOT, "%.2f", n)); apply(amplifier); box.setFocused(true); return true;
                } catch (NumberFormatException ignored) {}
            }
            if (x >= leftPos + 10 && x < leftPos + 10 + column && y >= topPos + 54 && y < topPos + imageHeight - 72) { leftPage -= (int) Math.signum(vertical); sync(); return true; }
            if (x >= leftPos + 20 + column && x < leftPos + imageWidth - 10 && y >= topPos + 54 && y < topPos + imageHeight - 72) { rightPage -= (int) Math.signum(vertical); sync(); return true; }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        g.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xFF19151D);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF9B614E);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF242632);
        g.fill(leftPos + 14 + column, topPos + 30, leftPos + 16 + column, topPos + imageHeight - 8, 0xFFAA79C8);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, tr("title"), 10, 12, 0xE6D8EA, false);
        g.drawString(font, tr("selected"), 20 + column, 36, 0xD9C4ED, false);
        int pager = imageHeight - 68;
        g.drawCenteredString(font, (leftPage + 1) + " / " + Math.max(1, (filtered().size() + rows - 1) / rows), 10 + column / 2, pager, 0xBDA5D0);
        g.drawCenteredString(font, (rightPage + 1) + " / " + Math.max(1, (selected.size() + rows - 1) / rows), 20 + column + column / 2, pager, 0xBDA5D0);
        g.drawString(font, particlesMode ? tr("duration_label") : Component.literal("s ≤60"), 20 + column, imageHeight - 35, 0xBDC5D4, false);
        g.drawString(font, particlesMode ? tr("size_label") : Component.literal("Lv ≤5"), 20 + 2 * column - 60, imageHeight - 35, 0xBDC5D4, false);
        g.drawString(font, tr("editing_help"), 10, imageHeight - 22, 0xBDA5D0, false);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) { renderBackground(g, x, y, partial); super.render(g, x, y, partial); renderTooltip(g, x, y); }
}
