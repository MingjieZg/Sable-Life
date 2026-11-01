package dev.sablelife.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Local vanilla file browser; double-click imports one concrete OGG into the custom library. */
public final class VanillaSoundBrowser extends Screen {
    private final Screen parent;
    private final Consumer<String> select;
    private final List<ResourceLocation> all = VanillaSoundFolder.sounds();
    private final List<Button> entries = new ArrayList<>();
    private EditBox search;
    private Button previous, next;
    private int page, rowCount, left, top, panelWidth, panelHeight;
    private ResourceLocation last;
    private long lastClick;
    private boolean importing;
    private Component status = tr("help");
    public VanillaSoundBrowser(Screen parent, Consumer<String> select) { super(tr("title")); this.parent = parent; this.select = select; }
    private static Component tr(String key) { return Component.translatable("sound_browser.sablelife." + key); }
    private Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, x, y, w, 18, action));
    }
    private List<ResourceLocation> filtered() {
        String query = search.getValue().toLowerCase(Locale.ROOT).trim();
        return all.stream().filter(id -> id.getPath().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        panelWidth = Math.min(520, width - 20); panelHeight = Math.min(340, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        rowCount = Math.max(1, (panelHeight - 110) / 20); entries.clear();
        button(tr("folder"), left + panelWidth - 165, top + 8, 95, VanillaSoundFolder::open);
        button(tr("back"), left + panelWidth - 65, top + 8, 55, this::onClose);
        search = addRenderableWidget(new EditBox(font, left + 10, top + 34, panelWidth - 20, 18, tr("search")));
        search.setMaxLength(120); search.setHint(tr("search")); search.setValue(query);
        search.setResponder(value -> { page = 0; last = null; update(); });
        for (int i = 0; i < rowCount; i++) {
            final int row = i;
            entries.add(button(Component.empty(), left + 10, top + 60 + i * 20, panelWidth - 20, () -> clicked(row)));
        }
        previous = button(Component.literal("<"), left + 10, top + panelHeight - 43, 26, () -> { page--; last = null; update(); });
        next = button(Component.literal(">"), left + panelWidth - 36, top + panelHeight - 43, 26, () -> { page++; last = null; update(); });
        update();
    }
    private void update() {
        var list = filtered(); page = Math.clamp(page, 0, Math.max(0, (list.size() - 1) / rowCount));
        for (int row = 0; row < entries.size(); row++) {
            int index = page * rowCount + row; var button = entries.get(row);
            button.visible = index < list.size(); button.active = !importing;
            if (button.visible) { String path = list.get(index).getPath().substring(7); button.setMessage(Component.literal(path)); button.setTooltip(Tooltip.create(Component.literal(path).append("\n").append(tr("help")))); }
        }
        if (previous != null) previous.active = !importing && page > 0;
        if (next != null) next.active = !importing && (page + 1) * rowCount < list.size();
    }
    private void clicked(int row) {
        var list = filtered(); int index = page * rowCount + row;
        if (importing || index >= list.size()) return;
        var id = list.get(index); long now = net.minecraft.Util.getMillis();
        boolean doubleClick = id.equals(last) && now - lastClick <= 350;
        last = id; lastClick = now;
        if (!doubleClick) return;
        importing = true; status = tr("loading"); update();
        try {
            String sound = VanillaSoundFolder.importSound(id);
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                importing = false;
                if (error != null) { status = tr("error"); update(); return; }
                if (minecraft.screen != this || !validMenu()) return;
                minecraft.setScreen(parent); select.accept(sound);
            }));
        } catch (Exception error) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not import vanilla sound {}", id, error);
            importing = false; status = tr("error"); update();
        }
    }
    private boolean validMenu() { return !(parent instanceof AbstractContainerScreen<?> container) || minecraft.player != null && minecraft.player.containerMenu == container.getMenu(); }
    @Override public void onClose() { if (validMenu()) minecraft.setScreen(parent); else minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { if (!validMenu()) minecraft.setScreen(null); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!importing && vertical != 0) { page -= (int) Math.signum(vertical); last = null; update(); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g, x, y, partial);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF242632);
        g.drawString(font, title, left + 10, top + 12, 0xE6D8EA, false);
        super.render(g, x, y, partial);
        g.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (filtered().size() + rowCount - 1) / rowCount), left + panelWidth / 2, top + panelHeight - 37, 0xD9C4ED);
        float scale = Math.min(LifeUiStyle.scale(), (panelWidth - 20f) / Math.max(1, font.width(status)));
        g.pose().pushPose(); g.pose().translate(left + 10, top + panelHeight - 16, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, status, 0, 0, 0xBDA5D0, false); g.pose().popPose();
    }
}
