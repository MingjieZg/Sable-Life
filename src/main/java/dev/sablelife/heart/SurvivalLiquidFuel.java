package dev.sablelife.heart;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** A single core-local tank with an input-only automation capability. */
public final class SurvivalLiquidFuel {
    public static final int CAPACITY = 40 * FluidType.BUCKET_VOLUME;
    static final String TAG = "Liquid";

    private final HeartBlockEntity core;
    private final Runnable onChanged;
    private long revision;
    private @Nullable Tag unreadable;
    private final FluidTank tank = new FluidTank(CAPACITY, SurvivalLiquidFuel::registered) {
        @Override protected void onContentsChanged() { changed(); }
    };

    SurvivalLiquidFuel(HeartBlockEntity core, Runnable onChanged) {
        this.core = core;
        this.onChanged = onChanged;
    }

    public FluidStack getFluid() { return tank.getFluid().copy(); }

    public boolean hasUnreadableFluid() { return unreadable != null; }

    long revision() { return revision; }

    private static boolean registered(FluidStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.FLUID.getResourceKey(stack.getFluid()).isPresent();
    }

    private void changed() {
        revision++;
        onChanged.run();
    }

    private boolean canTransfer() { return core.canRefuel() && !hasUnreadableFluid(); }

    public boolean clear() {
        if (!core.canRefuel() || (tank.isEmpty() && !hasUnreadableFluid())) return false;
        reset();
        return true;
    }

    void reset() {
        if (tank.isEmpty() && !hasUnreadableFluid()) return;
        tank.setFluid(FluidStack.EMPTY);
        unreadable = null;
        changed();
    }

    /** Consumes the requested amount only if the authoritative tank still matches the quote. */
    public boolean consume(FluidStack expected, int amount) {
        if (!canTransfer() || amount <= 0 || amount > tank.getFluidAmount()
                || !FluidStack.matches(tank.getFluid(), expected)) return false;
        tank.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    /** Simulates a transfer on detached copies so the menu can first reserve space for the result. */
    public @Nullable Transfer prepare(ItemStack container, boolean toTank) {
        if (!canTransfer() || container.isEmpty()) return null;
        long expectedRevision = revision;
        var original = getFluid();
        var staged = new FluidTank(CAPACITY, SurvivalLiquidFuel::registered);
        staged.setFluid(original.copy());
        var action = toTank ? FluidUtil.tryEmptyContainer(container, staged, CAPACITY, null, true)
                : FluidUtil.tryFillContainer(container, staged, FluidType.BUCKET_VOLUME, null, true);
        var result = action.getResult();
        if (!action.isSuccess() || result.getCount() > 1) return null;
        int change = staged.getFluidAmount() - original.getAmount();
        if (toTank && change <= 0) return null;
        // A container can report a successful fill while discarding the fluid's components.
        if (!toTank && (change >= 0 || result.isEmpty()
                || matchingAmount(result, original) - matchingAmount(container, original) != -change)) return null;
        return new Transfer(this, expectedRevision, staged.getFluid(), result);
    }

    private static long matchingAmount(ItemStack container, FluidStack fluid) {
        var handler = FluidUtil.getFluidHandler(container.copyWithCount(1)).orElse(null);
        if (handler == null) return 0;
        long amount = 0;
        int tanks = handler.getTanks();
        for (int slot = 0; slot < tanks; slot++) {
            var stored = handler.getFluidInTank(slot);
            if (!stored.isEmpty() && FluidStack.isSameFluidSameComponents(stored, fluid)) amount += stored.getAmount();
        }
        return amount;
    }

    public boolean commit(Transfer transfer) {
        if (!canTransfer() || transfer == null || transfer.owner != this || transfer.revision != revision) return false;
        tank.setFluid(transfer.fluid.copy());
        changed();
        return true;
    }

    public static final class Transfer {
        private final SurvivalLiquidFuel owner;
        private final long revision;
        private final FluidStack fluid;
        private final ItemStack result;

        private Transfer(SurvivalLiquidFuel owner, long revision, FluidStack fluid, ItemStack result) {
            this.owner = owner;
            this.revision = revision;
            this.fluid = fluid.copy();
            this.result = result.copy();
        }

        public ItemStack result() { return result.copy(); }
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (hasUnreadableFluid()) tag.put(TAG, unreadable.copy());
        else if (!tank.isEmpty()) tag.put(TAG, tank.getFluid().save(registries));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        var stored = tag.get(TAG);
        unreadable = null;
        tank.setFluid(FluidStack.EMPTY);
        if (stored != null) {
            var parsed = FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), stored)
                    .result().filter(stack -> registered(stack) && stack.getAmount() <= CAPACITY);
            if (parsed.isPresent()) {
                tank.setFluid(parsed.get());
            } else {
                unreadable = stored.copy();
                LogUtils.getLogger().warn("Unreadable liquid fuel in survival core {} kept in its original form", core.creatureId());
            }
        }
        revision++;
    }

    private final IFluidHandler input = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int slot) { return slot == 0 ? getFluid() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int slot) { return slot == 0 ? CAPACITY : 0; }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return slot == 0 && registered(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return canTransfer() ? tank.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    };

    IFluidHandler input() { return input; }
}
