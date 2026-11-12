package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One ghost item sample; inventory items are copied, never consumed or extracted. */
public final class ProjectileItemMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final VirtualModule module;
    private final SimpleContainer sample = new SimpleContainer(1);
    private final ContainerData data;
    public ProjectileItemMenu(int id, Inventory inventory) { this(id, inventory, null, null); }
    public ProjectileItemMenu(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule module) {
        super(SableLife.PROJECTILE_MENU.get(), id); this.heart = heart; this.module = module;
        data = heart == null ? new SimpleContainerData(2) : new ContainerData() {
            public int getCount() { return 2; } public void set(int i, int v) {}
            public int get(int i) { return i==1?((module.type().equals("item_display")||module.type().equals("summoned_weapon"))?1:0):(heart.canEdit(inventory.player)&&ModuleAvailability.enabled(heart,module)) ? 1 : 0; }
        }; addDataSlots(data);
        addSlot(new Slot(sample, 0, 82, 40) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(inventory, col+row*9+9, 10+col*18, 99+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(inventory, col, 10+col*18, 157));
        refresh();
    }
    public boolean displayOnly(){return data.get(1)!=0;}
    public boolean editable() { return data.get(0) != 0; }
    private void refresh() { if (heart != null) sample.setItem(0, module.projectileItem(heart.getLevel().registryAccess())); }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canAccessEditor(player) && !heart.isRemoved() && player.level() == heart.getLevel() && heart.modules().find(module.id()) == module
                && heart.editorInRange(player, 64);
    }
    private void set(ItemStack item) { module.projectileItem(item, heart.getLevel().registryAccess()); heart.modules().changed(); heart.setChanged(); refresh(); sendAllDataToRemote(); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (slot == 0) {
            if (heart != null && (heart.canEdit(player)&&ModuleAvailability.enabled(heart,module)) && type == ClickType.PICKUP && (button == 0 || button == 1)) set(getCarried());
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (heart == null || !stillValid(player)) return false;
        if (button == 0) { heart.openDashboard(player); return true; }
        if (button == 1 && (heart.canEdit(player)&&ModuleAvailability.enabled(heart,module))) { set(ItemStack.EMPTY); return true; }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return slot.index != 0; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index != 0; }
}
