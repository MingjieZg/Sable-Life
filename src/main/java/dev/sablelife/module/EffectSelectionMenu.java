package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartSettingsMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Server-owned selection, synchronized through a non-interactive snapshot slot. */
public final class EffectSelectionMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final VirtualModule module;
    private final Player viewer;
    private final boolean particleMode;
    private VirtualModule movementParent;
    public static EffectSelectionMenu movement(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule parent, String stage) {var menu=new EffectSelectionMenu(id,inventory,heart,parent.movementParticles(stage),true);menu.movementParent=parent;menu.refresh();return menu;}
    private VirtualModule arenaParent;
    private ArenaBuffs.Group arenaGroup;
    public static EffectSelectionMenu arena(int id,Inventory inv,HeartBlockEntity heart,VirtualModule parent,ArenaBuffs.Group group){
        var menu=new EffectSelectionMenu(id,inv,heart,group.editor());menu.arenaParent=parent;menu.arenaGroup=group;menu.refresh();return menu;
    }
    private enum CorePresentation {
        DEATH(2), REMOVAL(3), HIT(HeartSettingsMenu.HIT_EFFECT_PAGE);
        private final int settingsPage;
        CorePresentation(int settingsPage) { this.settingsPage = settingsPage; }
        private VirtualModule selection(HeartBlockEntity heart) {
            return switch (this) {
                case DEATH -> heart.deathPresentation().particleSelection();
                case REMOVAL -> heart.removalPresentation().particleSelection();
                case HIT -> heart.hitPresentation().particleSelection();
            };
        }
    }
    private CorePresentation corePresentation;

    public static EffectSelectionMenu death(int id, Inventory inventory, HeartBlockEntity heart) {
        return coreParticles(id, inventory, heart, CorePresentation.DEATH);
    }
    public static EffectSelectionMenu removal(int id, Inventory inventory, HeartBlockEntity heart) {
        return coreParticles(id, inventory, heart, CorePresentation.REMOVAL);
    }
    public static EffectSelectionMenu hit(int id, Inventory inventory, HeartBlockEntity heart) {
        return coreParticles(id, inventory, heart, CorePresentation.HIT);
    }
    private static EffectSelectionMenu coreParticles(int id, Inventory inventory, HeartBlockEntity heart, CorePresentation presentation) {
        var menu = new EffectSelectionMenu(id, inventory, heart, presentation.selection(heart), true);
        menu.corePresentation = presentation;
        menu.refresh();
        return menu;
    }
    private final SimpleContainer snapshot = new SimpleContainer(1);
    public EffectSelectionMenu(int id, Inventory inventory) { this(id, inventory, null, null); }
    public EffectSelectionMenu(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule module) {
        this(id, inventory, heart, module, false);
    }
    public EffectSelectionMenu(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule module, boolean particleMode) {
        super(SableLife.EFFECT_MENU.get(), id); this.particleMode = particleMode; this.heart = heart; this.module = module; viewer = inventory.player;
        addSlot(new Slot(snapshot, 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        }); refresh();
    }
    public CompoundTag snapshot() { return snapshot.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private void refresh() {
        if (heart == null) return;
        var tag = new CompoundTag(); tag.put("Module", module.save()); tag.putBoolean("ParticleMode", particleMode);tag.putBoolean("ArenaBuffMode",arenaGroup!=null); tag.putBoolean("Editable", canEditSelection(viewer));
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); snapshot.setItem(0, stack);
    }
    @Override public void broadcastChanges() { if (heart != null && heart.getLevel().getGameTime() % 5 == 0) refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canAccessEditor(player) && !heart.isRemoved() && player.level() == heart.getLevel() && (arenaParent!=null?heart.modules().find(arenaParent.id())==arenaParent&&arenaParent.arenaBuffs().find(arenaGroup.id())==arenaGroup:corePresentation!=null ? corePresentation.selection(heart)==module : movementParent!=null?heart.modules().find(movementParent.id())==movementParent:heart.modules().find(module.id()) == module)
                && heart.editorInRange(player, 64);
    }
    private boolean canEditSelection(Player player) {
        return corePresentation != null ? heart.canToggleBreaking(player)
                : heart.canEdit(player) && ModuleAvailability.enabled(heart, arenaParent != null ? arenaParent : movementParent != null ? movementParent : module);
    }
    public boolean action(Player player, java.util.UUID id, String operation, double value, String effect) {
        if (heart == null || !stillValid(player) || !module.id().equals(id) || !canEditSelection(player)) return false;
        if(heart.isSurvivalCore()&&!particleMode)return false;
        if (!(particleMode ? module.editParticle(operation, effect, value) : arenaGroup!=null?arenaGroup.effect(operation,effect,value):module.editEffect(operation, effect, value))) return false;
        if(corePresentation==null) heart.modules().changed(); heart.setChanged(); refresh(); sendAllDataToRemote(); return true;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || heart == null || !stillValid(player)) return false;
        if(arenaParent!=null){ArenaBuffMenu.open(player,heart,arenaParent,arenaGroup.id());return true;}
        if(corePresentation!=null) { heart.openSettings(player, corePresentation.settingsPage); return true; }
        DashboardSelection.set(heart, player.getUUID(), movementParent==null?module.id():movementParent.id()); heart.openDashboard(player); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
