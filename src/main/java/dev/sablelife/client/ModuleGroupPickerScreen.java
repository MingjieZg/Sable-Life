package dev.sablelife.client;

import dev.sablelife.module.ModuleFolders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ModuleGroupPickerScreen extends Screen {
    private final HeartDashboardScreen parent;
    private EditBox groupName;
    private String draft = "";
    private int x, y, panelWidth, panelHeight, page;

    ModuleGroupPickerScreen(HeartDashboardScreen parent, int selectedCount) {
        super(Component.translatable("dashboard.sablelife.group_move_title", selectedCount));
        this.parent = parent;
    }

    private static Component tr(String key) {
        return Component.translatable("dashboard.sablelife." + key);
    }

    @Override protected void init() {
        panelWidth = Math.min(350, width - 14);
        panelHeight = Math.min(260, height - 14);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        addRenderableWidget(LifeUiStyle.button(tr("back"), x + panelWidth - 64, y + 8, 56, 18, this::onClose));

        var create = addRenderableWidget(LifeUiStyle.button(tr("group_create"), x + panelWidth - 83, y + 34, 75, 18,
                () -> choose(groupName.getValue())));
        groupName = addRenderableWidget(new EditBox(font, x + 9, y + 35, panelWidth - 99, 16, tr("group_new_name")));
        groupName.setMaxLength(64);
        groupName.setFilter(ModuleFolders::valid);
        groupName.setHint(tr("group_new_name"));
        groupName.setValue(draft);
        create.active = !draft.isBlank();
        groupName.setResponder(value -> { draft = value; create.active = !value.isBlank(); });

        var groups = parent.groups();
        int capacity = Math.max(1, (panelHeight - 92) / 21);
        page = Math.clamp(page, 0, Math.max(0, (groups.size() - 1) / capacity));
        for (int index = page * capacity; index < Math.min((page + 1) * capacity, groups.size()); index++) {
            String name = groups.get(index);
            var label = Component.literal(name);
            var row = addRenderableWidget(LifeUiStyle.button(label, x + 8, y + 57 + (index % capacity) * 21,
                    panelWidth - 16, 18, () -> choose(name)));
            row.setTooltip(Tooltip.create(label));
        }
        int footerY = y + panelHeight - 26;
        addRenderableWidget(LifeUiStyle.button(Component.literal("‹"), x + 8, footerY, 27, 18,
                () -> { page--; rebuildWidgets(); })).active = page > 0;
        addRenderableWidget(LifeUiStyle.button(tr("group_remove"), x + 40, footerY, panelWidth - 80, 18,
                () -> choose("")));
        addRenderableWidget(LifeUiStyle.button(Component.literal("›"), x + panelWidth - 35, footerY, 27, 18,
                () -> { page++; rebuildWidgets(); })).active = (page + 1) * capacity < groups.size();
    }

    private void choose(String group) {
        parent.moveToGroup(group);
        onClose();
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (groupName.isFocused() && (key == 257 || key == 335) && !draft.isBlank()) {
            choose(draft);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != parent.getMenu()) minecraft.setScreen(null);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(x - 2, y - 2, x + panelWidth + 2, y + panelHeight + 2, 0xFF9B614E);
        g.fill(x, y, x + panelWidth, y + panelHeight, 0xFF242632);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), panelWidth - 78), x + 8, y + 12, 0xFFE6D8EA, false);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
