package dev.sablelife.heart;

import dev.sablelife.SableLife;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class SurvivalFuel extends SimpleContainer {
    public static final String TAG = "SurvivalRefuel";
    private final HeartBlockEntity core;
    private final SurvivalLiquidFuel liquid;
    private boolean automatic;
    private long revision;

    SurvivalFuel(HeartBlockEntity core) {
        super(SurvivalTopUpMenu.INPUTS);
        this.core = core;
        liquid = new SurvivalLiquidFuel(core, this::setChanged);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return SurvivalTokens.depositValue(stack) > 0;
    }

    @Override public void setChanged() {
        super.setChanged();
        revision++;
        core.setChanged();
    }

    boolean automatic() { return automatic; }
    long revision() { return revision; }
    SurvivalLiquidFuel liquid() { return liquid; }

    void automatic(boolean enabled) {
        if (automatic == enabled) return;
        automatic = enabled;
        setChanged();
    }

    CompoundTag save(HolderLookup.Provider registries, boolean includeLiquid) {
        var tag = new CompoundTag();
        if (automatic) tag.putBoolean("Automatic", true);
        if (includeLiquid) liquid.save(tag, registries);
        return ContainerHelper.saveAllItems(tag, getItems(), false, registries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        automatic = tag.getBoolean("Automatic");
        getItems().clear();
        ContainerHelper.loadAllItems(tag, getItems(), registries);
        liquid.load(tag, registries);
        revision++;
    }

    public static void clearBlueprintContents(CompoundTag coreTag) {
        var fuel = coreTag.getCompound(TAG);
        fuel.remove("Items");
        fuel.remove(SurvivalLiquidFuel.TAG);
    }

    int itemCount() {
        int count = 0;
        for (int i = 0; i < getContainerSize(); i++) count += getItem(i).getCount();
        return count;
    }

    private final IItemHandler input = new IItemHandler() {
        private final InvWrapper inventory = new InvWrapper(SurvivalFuel.this);

        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return canPlaceItem(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return core.canRefuel() && isItemValid(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
    };

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SableLife.HEART_ENTITY.get(), (core, side) ->
                core.isSurvivalCore() ? core.survivalFuel().input : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SableLife.HEART_ENTITY.get(), (core, side) ->
                core.isSurvivalCore() ? core.survivalLiquidFuel().input() : null);
    }
}
