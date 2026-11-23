package dev.sablelife.client;

import dev.sablelife.heart.BlueprintFaction;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.ModuleNetworking;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Resolves one portable faction reference using local, server-provided options. */
final class BlueprintFactionScreen extends Screen {
    private enum Tab {
        EXISTING("bp_existing"), CREATE("bp_create"), ALLIES("bp_allies");

        private final String title;
        Tab(String title) { this.title = title; }
    }

    private static final int LABEL = 0xD9C4ED;
    private static final int TEXT = 0xAEB8C7;
    private static final int STRIDE = 20;
    private static final UUID NO_MODULE = new UUID(0, 0);
    private final Screen parent;
    private final HeartSettingsMenu menu;
    private CompoundTag view = new CompoundTag();
    private ListTag factions = new ListTag();
    private BlueprintFaction source;
    private UUID selected;
    private Tab tab = Tab.EXISTING;
    private String name = "";
    private String status = "";
    private boolean privateFaction, friendlyFire, alliedFire, editable;
    private Button create;
    private EditBox nameInput;
    private int left, top, panelWidth, panelHeight, page = -1, capacity, rowCount;

    BlueprintFactionScreen(Screen parent, HeartSettingsMenu menu, UUID selected) {
        super(tr("bp_title"));
        this.parent = parent;
        this.menu = menu;
        this.selected = selected;
        sync(menu.snapshot());
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("relations.sablelife." + key, args);
    }

    private static UUID id(CompoundTag row, String field) {
        return row.hasUUID(field) ? row.getUUID(field) : null;
    }

    private boolean sync(CompoundTag snapshot) {
        var nextView = snapshot.getCompound("BlueprintFactionView");
        var nextFactions = snapshot.getList("Factions", Tag.TAG_COMPOUND);
        var nextSource = BlueprintFaction.read(snapshot.getCompound("Relations").getCompound(BlueprintFaction.TAG));
        String nextStatus = snapshot.getString("RelationStatus");
        if (!nextStatus.startsWith("bp_") && !nextStatus.startsWith("faction_")) nextStatus = "";
        boolean nextEditable = snapshot.getBoolean("LiveControls");
        boolean sourceChanged = !Objects.equals(source, nextSource);
        boolean changed = sourceChanged || !view.equals(nextView) || !factions.equals(nextFactions)
                || editable != nextEditable || !status.equals(nextStatus);
        var previousBound = id(view, "Bound");
        var currentBound = id(nextView, "Bound");
        if (tab == Tab.CREATE && nextStatus.equals("bp_bound") && !Objects.equals(previousBound, currentBound)) {
            tab = Tab.EXISTING;
            selected = currentBound;
            page = -1;
        }
        view = nextView;
        factions = nextFactions;
        status = nextStatus;
        editable = nextEditable;
        if (sourceChanged && nextSource != null) {
            name = view.getString("CreatedName");
            privateFaction = nextSource.privateFaction();
            friendlyFire = nextSource.friendlyFire();
            alliedFire = nextSource.alliedFire();
        }
        if (changed && (selected == null || !row(selected).getBoolean("CanJoin"))) {
            selected = id(view, "Bound");
            if (selected == null) selected = id(view, "Suggested");
        }
        source = nextSource;
        return changed;
    }

    private CompoundTag row(UUID faction) {
        if (faction != null) {
            for (var value : factions) {
                var row = (CompoundTag) value;
                if (faction.equals(id(row, "Id"))) return row;
            }
        }
        return new CompoundTag();
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, x, y, width, 18, action));
    }

    private void send(String key, int revision, double value, String text) {
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, 0, NO_MODULE,
                "core", "relation:" + key + ":" + revision, value, text));
    }

    private void changeTab(Tab next) {
        tab = next;
        page = next == Tab.EXISTING ? -1 : 0;
        rebuildWidgets();
    }

    @Override protected void init() {
        panelWidth = Math.min(620, width - 16);
        panelHeight = Math.min(370, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        create = null;
        nameInput = null;
        button(Component.translatable("dashboard.sablelife.back"), left + panelWidth - 70, top + (compact() ? 3 : 8), 60, this::onClose);
        if (source == null || view.isEmpty()) return;
        int tabWidth = (panelWidth - 28) / 3;
        for (var option : Tab.values()) {
            var tabButton = button(tr(option.title), left + 10 + option.ordinal() * (tabWidth + 4), top + (compact() ? 22 : 34),
                    tabWidth, () -> changeTab(option));
            if (option == tab) LifeUiStyle.bookmark(tabButton, 0xFFD9C4ED);
            if (option == Tab.ALLIES && source.allies().isEmpty()) tabButton.active = false;
        }
        switch (tab) {
            case EXISTING -> initExisting();
            case CREATE -> initCreation();
            case ALLIES -> initAllies();
        }
    }

    private void initExisting() {
        int revision = view.getInt("Revision");
        int firstRow = compact() ? 99 : 128;
        initPages(factions.size(), firstRow);
        for (int i = 0; i < capacity && page * capacity + i < rowCount; i++) {
            var row = factions.getCompound(page * capacity + i);
            var faction = row.getUUID("Id");
            var label = Component.literal(row.getString("Name")).append(FactionLabels.visibility(row));
            var option = button(label, left + 10, top + firstRow + i * STRIDE, panelWidth - 20, () -> {
                selected = faction;
                rebuildWidgets();
            });
            option.active = row.getBoolean("CanJoin");
            option.setTooltip(Tooltip.create(label.copy().append("\n").append(rules(row))));
            if (faction.equals(selected)) LifeUiStyle.bookmark(option, 0xFFD9C4ED);
        }
        var target = row(selected);
        int flags = HeartSettingsMenu.factionFlags(target.getBoolean("Private"), target.getBoolean("FriendlyFire"), target.getBoolean("AlliedFire"));
        int actionMargin = compact() ? 32 : 10;
        int actionWidth = panelWidth / 2 - actionMargin - 3;
        var bind = button(tr("bp_bind"), left + actionMargin, actionTop(), actionWidth,
                () -> send("bp_bind", revision, flags, target.getUUID("Id").toString()));
        bind.active = editable && target.getBoolean("CanJoin");
        bind.setTooltip(Tooltip.create(tr("bp_bind_help")));
        var skip = button(tr(id(view, "Bound") == null ? "bp_skip" : "bp_unbind"), left + panelWidth / 2 + 3,
                actionTop(), actionWidth, () -> confirmSkip(revision));
        skip.active = editable;
    }

    private void confirmSkip(int revision) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send("bp_clear", revision, 0, "");
        }, tr("bp_forget_title"), tr("bp_forget_help")));
    }

    private void initCreation() {
        boolean canCreate = canCreate();
        nameInput = addRenderableWidget(new EditBox(font, left + 10, top + (compact() ? 80 : 107), panelWidth - 20, 18, tr("name")));
        nameInput.setHint(tr("name"));
        nameInput.setMaxLength(80);
        nameInput.setFilter(value -> value.codePointCount(0, value.length()) <= 40 && value.codePoints().noneMatch(Character::isISOControl));
        nameInput.setValue(name);
        nameInput.setEditable(canCreate);
        nameInput.setResponder(value -> {
            name = value;
            if (create != null) create.active = canCreate && !name.isBlank();
        });
        int cellWidth = (panelWidth - 28) / 3;
        toggle(privateFaction ? "bp_private" : "bp_public", "privacy_help", 0, cellWidth, () -> privateFaction = !privateFaction);
        toggle(friendlyFire ? "ff_on" : "ff_off", null, 1, cellWidth, () -> friendlyFire = !friendlyFire);
        toggle(alliedFire ? "allied_fire_on" : "allied_fire_off", "allied_fire_help", 2, cellWidth, () -> alliedFire = !alliedFire);
        int revision = view.getInt("Revision");
        create = button(tr("bp_create_confirm"), left + 10, actionTop(), panelWidth - 20, () -> createFaction(revision));
        create.active = canCreate && !name.isBlank();
        if (!canCreate && editable) create.setTooltip(Tooltip.create(tr("bp_use_mapping")));
    }

    private boolean canCreate() {
        return editable && id(view, "Bound") == null && id(view, "Suggested") == null;
    }

    private void toggle(String title, String help, int column, int width, Runnable action) {
        var option = button(tr(title), left + 10 + column * (width + 4), top + (compact() ? 100 : 131), width, () -> {
            action.run();
            rebuildWidgets();
        });
        option.active = canCreate();
        if (help != null) option.setTooltip(Tooltip.create(tr(help)));
    }

    private void createFaction(int revision) {
        if (create != null && create.active) send("bp_create", revision,
                HeartSettingsMenu.factionFlags(privateFaction, friendlyFire, alliedFire), name);
    }

    private void initAllies() {
        var allies = view.getList("Allies", Tag.TAG_COMPOUND);
        var bound = row(id(view, "Bound"));
        int revision = view.getInt("Revision");
        int firstRow = compact() ? 80 : 107;
        initPages(source.allies().size(), firstRow);
        for (int i = 0; i < capacity && page * capacity + i < rowCount; i++) {
            int index = page * capacity + i;
            var ally = source.allies().get(index);
            var state = allies.getCompound(index);
            var local = ally.id().equals(id(state, "Id")) ? id(state, "Mapped") : null;
            var target = row(local);
            boolean mapped = target.hasUUID("Id");
            boolean sameFaction = mapped && local.equals(id(bound, "Id"));
            boolean allied = state.getBoolean("Allied") && !sameFaction;
            Component label = ally.name().isEmpty() ? Component.literal(ally.id().toString()) : Component.literal(ally.name());
            var summary = mapped ? tr("bp_ally_map", label, target.getString("Name")) : tr("bp_ally_unmapped", label);
            int y = top + firstRow + i * STRIDE;
            int actionWidth = Math.min(154, panelWidth / 3);
            var row = button(summary, left + 10, y, panelWidth - actionWidth - 24, () -> {});
            row.active = false;
            row.setTooltip(Tooltip.create(summary));
            var action = button(tr(sameFaction ? "bp_same_faction" : allied ? "allied_label" : "bp_ally_confirm"),
                    left + panelWidth - actionWidth - 10, y, actionWidth,
                    () -> send("bp_ally", revision, 0, ally.id() + "|" + local));
            action.active = editable && bound.getBoolean("Manage") && id(bound, "Id").equals(id(view, "Suggested"))
                    && mapped && !sameFaction && !allied;
            action.setTooltip(Tooltip.create(tr("bp_ally_help")));
        }
    }

    private void initPages(int count, int firstRow) {
        rowCount = count;
        capacity = Math.max(1, (pagerTop() - top - firstRow) / STRIDE);
        if (page < 0 && tab == Tab.EXISTING && selected != null) {
            for (int i = 0; i < factions.size(); i++) {
                if (selected.equals(id(factions.getCompound(i), "Id"))) { page = i / capacity; break; }
            }
        }
        page = Math.clamp(page, 0, pageCount() - 1);
        int arrowWidth = compact() ? 18 : 28;
        button(Component.literal("<"), left + 10, pagerTop(), arrowWidth, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button(Component.literal(">"), left + panelWidth - 10 - arrowWidth, pagerTop(), arrowWidth,
                () -> { page++; rebuildWidgets(); }).active = page + 1 < pageCount();
    }

    private boolean compact() { return panelHeight < 218; }
    private int actionTop() { return top + panelHeight - 42; }
    private int pagerTop() { return compact() ? actionTop() : top + panelHeight - 64; }
    private int pageCount() { return Math.max(1, (rowCount + capacity - 1) / capacity); }

    private Component rules(CompoundTag row) {
        return rules(row.getBoolean("Private"), row.getBoolean("FriendlyFire"), row.getBoolean("AlliedFire"));
    }

    private Component rules(boolean privacy, boolean friendly, boolean allied) {
        return tr("bp_rules", tr(privacy ? "bp_private" : "bp_public"), tr(friendly ? "ff_on" : "ff_off"),
                tr(allied ? "allied_fire_on" : "allied_fire_off"));
    }

    private void line(GuiGraphics g, Component text, int offset, int color) {
        float scale = Math.min(1, (panelWidth - 20f) / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(left + 10, top + offset, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) { minecraft.setScreen(null); return; }
        boolean changed = sync(menu.snapshot());
        if (source == null || view.isEmpty()) { onClose(); return; }
        if (changed) rebuildWidgets();
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (nameInput != null && nameInput.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            createFaction(view.getInt("Revision"));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF9B614E);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF242632);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        line(g, title, compact() ? 8 : 14, LABEL);
        if (source != null && !view.isEmpty()) {
            line(g, tr("bp_source", source.name().isEmpty() ? tr("bp_unknown") : source.name()), compact() ? 44 : 60, LABEL);
            line(g, source.hasSettings() ? rules(source.privateFaction(), source.friendlyFire(), source.alliedFire())
                    : tr("bp_unknown_rules"), compact() ? 55 : 73, TEXT);
            var bound = row(id(view, "Bound"));
            line(g, bound.hasUUID("Id") ? tr("bp_current", bound.getString("Name")) : tr("bp_unassigned"), compact() ? 66 : 87, LABEL);
            if (tab == Tab.EXISTING) {
                var local = row(selected);
                line(g, local.hasUUID("Id") ? tr("bp_selected", local.getString("Name")) : tr("bp_choose"), compact() ? 77 : 102, LABEL);
                if (local.hasUUID("Id")) line(g, rules(local), compact() ? 88 : 115, TEXT);
            }
            if (tab != Tab.CREATE && pageCount() > 1) g.drawCenteredString(font, (page + 1) + " / " + pageCount(),
                    left + panelWidth / 2, compact() ? top + 8 : pagerTop() + 5, LABEL);
            if (!status.isEmpty()) {
                int color = status.equals("bp_stale") || status.equals("bp_rejected") || status.equals("bp_use_mapping") || status.startsWith("faction_") ? 0xEF8D8D : 0x89DFA8;
                line(g, tr(status, menu.snapshot().getInt("FactionCreationLimit")), panelHeight - 17, color);
            }
        }
    }
}
