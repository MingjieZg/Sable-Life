package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
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
    private boolean coreDeath;
    private boolean coreRemoval;
    public static EffectSelectionMenu death(int id, Inventory inventory, HeartBlockEntity heart) {
        var menu=new EffectSelectionMenu(id,inventory,heart,heart.deathPresentation().particleSelection(),true);
        menu.coreDeath=true; menu.refresh(); return menu;
    }
    public static EffectSelectionMenu removal(int id, Inventory inventory, HeartBlockEntity heart) {
        var menu=new EffectSelectionMenu(id,inventory,heart,heart.removalPresentation().particleSelection(),true);
        menu.coreDeath=true;menu.coreRemoval=true;menu.refresh();return menu;
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
        var tag = new CompoundTag(); tag.put("Module", module.save()); tag.putBoolean("ParticleMode", particleMode);tag.putBoolean("ArenaBuffMode",arenaGroup!=null); tag.putBoolean("Editable", coreDeath ? heart.canToggleBreaking(viewer) : heart.canEdit(viewer));
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); snapshot.setItem(0, stack);
    }
    @Override public void broadcastChanges() { if (heart != null && heart.getLevel().getGameTime() % 5 == 0) refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || player.isCreative() && !heart.isRemoved() && player.level() == heart.getLevel()
                && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart && (arenaParent!=null?heart.modules().find(arenaParent.id())==arenaParent&&arenaParent.arenaBuffs().find(arenaGroup.id())==arenaGroup:coreDeath ? (coreRemoval?heart.removalPresentation():heart.deathPresentation()).particleSelection()==module : movementParent!=null?heart.modules().find(movementParent.id())==movementParent:heart.modules().find(module.id()) == module)
                && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    public boolean action(Player player, java.util.UUID id, String operation, double value, String effect) {
        if (heart == null || !stillValid(player) || !module.id().equals(id) || !(coreDeath ? heart.canToggleBreaking(player) : heart.canEdit(player))) return false;
        if (!(particleMode ? module.editParticle(operation, effect, value) : arenaGroup!=null?arenaGroup.effect(operation,effect,value):module.editEffect(operation, effect, value))) return false;
        if(!coreDeath) heart.modules().changed(); heart.setChanged(); refresh(); sendAllDataToRemote(); return true;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || heart == null || !stillValid(player)) return false;
        if(arenaParent!=null){ArenaBuffMenu.open(player,heart,arenaParent,arenaGroup.id());return true;}
        if(coreDeath) { heart.openSettings(player, coreRemoval?3:2); return true; }
        DashboardSelection.set(heart, player.getUUID(), movementParent==null?module.id():movementParent.id()); heart.openDashboard(player); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
