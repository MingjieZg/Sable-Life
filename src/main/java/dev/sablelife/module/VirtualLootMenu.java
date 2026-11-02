package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

/** Same copy/increment/chance/weight workflow as the existing physical loot module. */
public final class VirtualLootMenu extends ChestMenu {
    private final HeartBlockEntity heart;
    private final VirtualModule module;
    private final Player viewer;
    public VirtualLootMenu(int id, Inventory inventory, HeartBlockEntity heart, VirtualModule module) {
        super(SableLife.LOOT_CHEST.get(), id, inventory, new SimpleContainer(54), 6);
        this.heart = heart; this.module = module;this.viewer=inventory.player; refresh();
    }
    public ItemStack sample(int slot) { return ItemStack.parseOptional(heart.getLevel().registryAccess(), module.lootSamples().getCompound("Slot" + slot)); }
    public int rule(int slot) {
        var tag = module.lootSamples(); String key = (module.value("weighted") == 0 ? "Chance" : "Weight") + slot;
        return tag.contains(key) ? tag.getInt(key) : module.value("weighted") == 0 ? 100 : 1;
    }
    private void icon(int slot, net.minecraft.world.item.Item item, Component label, Component... help) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, label); stack.set(DataComponents.LORE, new ItemLore(List.of(help))); getContainer().setItem(slot, stack);
    }
    private void refresh() {
        for (int i = 0; i < 27; i++) {
            var display = sample(i);
            if (!display.isEmpty()) {
                var lore = display.get(DataComponents.LORE); var lines = new java.util.ArrayList<Component>(lore == null ? List.of() : lore.lines());
                lines.add(Component.translatable(module.value("weighted") == 0 ? "menu.sablelife.loot_chance" : "menu.sablelife.loot_weight", rule(i)));
                lines.add(Component.translatable("menu.sablelife.template_zone").withStyle(net.minecraft.ChatFormatting.GREEN)); display.set(DataComponents.LORE, new ItemLore(lines));
            }
            getContainer().setItem(i, display);
        }
        icon(31, Items.ENDER_EYE, Component.translatable(module.value("weighted") == 0 ? "menu.sablelife.loot_mode_chance" : "menu.sablelife.loot_mode_weighted"));
        icon(32, Items.REPEATER, Component.translatable("menu.sablelife.loot_rolls", (int) module.value("rolls")));
        icon(40, Items.BOOK, Component.translatable("menu.sablelife.settings_zone").withStyle(net.minecraft.ChatFormatting.GOLD),
                Component.translatable("dashboard.sablelife.authoring_only"));
        icon(53, Items.BARRIER, Component.translatable("dashboard.sablelife.back"));metadata();
    }
    public boolean action(Player player,int revision,java.util.UUID id,String key,double value,String text){
        if(!stillValid(player)||!module.id().equals(id))return false;
        if(key.equals("back")){heart.openDashboard(player);return true;}
        if(!(heart.canEdit(player)&&ModuleAvailability.enabled(heart,module))||revision!=heart.modules().revision()||!Double.isFinite(value)||value!=Math.rint(value)){refresh();sendAllDataToRemote();return false;}
        int slot;try{slot=Integer.parseInt(text);}catch(NumberFormatException e){return false;}
        var tag=module.lootSamples();boolean changed=false;
        switch(key){
            case "weighted"->{if(value<0||value>1)return false;changed=module.edit("weighted",value,"");}
            case "rolls"->{if(value<1||value>27)return false;changed=module.edit("rolls",value,"");}
            case "rule"->{if(slot<0||slot>=27||sample(slot).isEmpty()||value<0||value>(module.value("weighted")==0?100:1000))return false;tag.putInt((module.value("weighted")==0?"Chance":"Weight")+slot,(int)value);module.lootSamples(tag);changed=true;}
            case "quantity","clear"->{if(slot<0||slot>=27)return false;var item=sample(slot);if(key.equals("quantity")&&(item.isEmpty()||value<0||value>item.getMaxStackSize()))return false;tag.remove("Slot"+slot);if(key.equals("quantity")&&value>0)tag.put("Slot"+slot,item.copyWithCount((int)value).save(heart.getLevel().registryAccess()));module.lootSamples(tag);changed=true;}
            default->{return false;}
        }
        if(changed){heart.modules().changed();heart.setChanged();}refresh();sendAllDataToRemote();return changed;
    }
    private void metadata(){var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",(heart.canEdit(viewer)&&ModuleAvailability.enabled(heart,module)));tag.putBoolean("Weighted",module.value("weighted")!=0);tag.putInt("Rolls",(int)module.value("rolls"));tag.putInt("MaxRule",module.value("weighted")==0?100:1000);tag.putUUID("Id",module.id());tag.putInt("Revision",heart.modules().revision());for(int i=0;i<27;i++)tag.putInt("Rule"+i,rule(i));getContainer().getItem(40).set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
    @Override public boolean stillValid(Player player) {
        return player.isCreative() && !heart.isRemoved() && player.level() == heart.getLevel()
                && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart && heart.modules().find(module.id()) == module
                && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == 53 && type == ClickType.PICKUP) { heart.openDashboard(player); return; }
            if (!(heart.canEdit(player)&&ModuleAvailability.enabled(heart,module)) || button < 0 || button > 1) { sendAllDataToRemote(); return; }
            boolean changed = false;
            if (slot < 27 && type == ClickType.PICKUP) {
                var value = getCarried().copy(); var existing = sample(slot);
                if(value.isEmpty()&&button==0){refresh();sendAllDataToRemote();return;}
                if (!value.isEmpty() && button == 1) value.setCount(ItemStack.isSameItemSameComponents(value, existing) ? Math.min(existing.getCount() + 1, value.getMaxStackSize()) : 1);
                else if (value.isEmpty() && button == 1) { value = existing; value.shrink(1); }
                var tag = module.lootSamples(); tag.remove("Slot" + slot);
                if (!value.isEmpty()) tag.put("Slot" + slot, value.save(heart.getLevel().registryAccess()));
                module.lootSamples(tag); changed = true;
            } else if (slot < 27 && type == ClickType.QUICK_MOVE && !sample(slot).isEmpty()) {
                var tag = module.lootSamples(); boolean weighted = module.value("weighted") != 0;
                tag.putInt((weighted ? "Weight" : "Chance") + slot, Math.clamp(rule(slot) + (button == 0 ? 1 : -1) * (weighted ? 1 : 5), 0, weighted ? 1000 : 100));
                module.lootSamples(tag); changed = true;
            } else if (slot == 31 && type == ClickType.PICKUP) changed = module.edit("weighted", module.value("weighted") == 0 ? 1 : 0, "");
            else if (slot == 32 && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) changed = module.edit("rolls", module.value("rolls") + (button == 0 ? 1 : -1) * (type == ClickType.QUICK_MOVE ? 5 : 1), "");
            if (changed) { heart.modules().changed(); heart.setChanged(); }
            refresh(); sendAllDataToRemote(); return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){
        if(slot>=54&&slot<slots.size()&&stillValid(player)&&(heart.canEdit(player)&&ModuleAvailability.enabled(heart,module))){var sample=slots.get(slot).getItem();if(!sample.isEmpty())for(int i=0;i<27;i++)if(sample(i).isEmpty()){var tag=module.lootSamples();tag.put("Slot"+i,sample.copy().save(heart.getLevel().registryAccess()));module.lootSamples(tag);heart.modules().changed();heart.setChanged();break;}}
        refresh();sendAllDataToRemote();return ItemStack.EMPTY;
    }
    @Override public boolean canDragTo(Slot slot) { return slot.index >= 54; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index >= 54; }
}
