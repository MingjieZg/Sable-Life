package dev.sablelife.heart;

import dev.sablelife.SableLife;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public final class SurvivalTopUpMenu extends AbstractContainerMenu {
    public static final int INPUTS = 9, INVENTORY_Y = 141;
    public static final int BUCKET_INPUT = INPUTS + 36, BUCKET_OUTPUT = BUCKET_INPUT + 1;
    public static final int CONFIRM = 0, BACK = 1, ENABLE_AUTO = 2, DISABLE_AUTO = 3;
    public static final int FILL_TANK = 4, DRAIN_TANK = 5;
    private final SimpleContainer inputs;
    private final SimpleContainer buckets = new SimpleContainer(2);
    private final SimpleContainer data = new SimpleContainer(1);
    private final HeartBlockEntity heart;
    private List<ItemStack> confirmed = List.of();
    private FluidStack confirmedFluid = FluidStack.EMPTY;
    private SurvivalFluidPrices.Quote confirmedQuote;
    private long confirmedValue, confirmedRevision, confirmationUntil;

    public SurvivalTopUpMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public SurvivalTopUpMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        super(SableLife.SURVIVAL_TOP_UP_MENU.get(), id);
        this.heart = heart;
        inputs = heart == null ? new SimpleContainer(INPUTS) : heart.survivalFuel();
        for (int i = 0; i < INPUTS; i++) {
            addSlot(new Slot(inputs, i, 12 + i * 18, 106) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return heart == null ? catalogContains(stack) : SurvivalTokens.depositValue(stack) > 0;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 12 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 12 + column * 18, INVENTORY_Y + 58));
        }
        addSlot(new Slot(buckets, 0, 249, 24) {
            @Override public boolean mayPlace(ItemStack stack) {
                return heart == null ? hasFluid(stack) : prepareBucket(stack) != null;
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(buckets, 1, 249, 112) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(data, 0, -10000, -10000) {
            @Override public boolean isActive() { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        refresh();
    }

    public CompoundTag snapshot() {
        return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static boolean hasFluid(ItemStack stack) {
        var handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return false;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) return true;
        }
        return false;
    }

    private boolean catalogContains(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (var entry : snapshot().getList("Catalog", 10)) {
            var row = (CompoundTag) entry;
            if (!row.getBoolean("Fluid") && row.getString("Id").equals(id)) return true;
        }
        return false;
    }

    private boolean confirmationMatches(long value, FluidStack fluid, SurvivalFluidPrices.Quote quote) {
        if (confirmed.isEmpty() || value != confirmedValue || heart.survivalFuel().revision() != confirmedRevision
                || heart.getLevel().getGameTime() > confirmationUntil || !quote.equals(confirmedQuote)
                || !FluidStack.matches(fluid, confirmedFluid)) return false;
        for (int i = 0; i < INPUTS; i++) {
            if (!ItemStack.matches(confirmed.get(i), inputs.getItem(i))) return false;
        }
        return true;
    }

    private void refresh() {
        if (heart == null) return;
        var tank = heart.survivalLiquidFuel();
        var fluid = tank.getFluid();
        var quote = SurvivalFluidPrices.quote(fluid, Long.MAX_VALUE);
        long balance = heart.survivalTokenBalance();
        long value = SurvivalTokens.inputValue(inputs) + quote.energy();
        var tag = new CompoundTag();
        tag.putLong("Balance", balance);
        tag.putLong("Value", value);
        tag.putBoolean("CanConfirm", value > 0 && value <= Long.MAX_VALUE - balance);
        tag.putBoolean("Confirming", confirmationMatches(value, fluid, quote));
        tag.putBoolean("Automatic", heart.survivalFuel().automatic());
        tag.putInt("TankAmount", fluid.getAmount());
        tag.putInt("TankDumpAction", dumpAction(tank));
        tag.putBoolean("TankUnavailable", tank.hasUnreadableFluid());
        if (!fluid.isEmpty()) {
            tag.putString("TankFluid", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
        }

        var catalog = new ListTag();
        SurvivalTokens.catalog().forEach((item, price) -> catalog.add(catalogRow(BuiltInRegistries.ITEM.getKey(item).toString(), price)));
        SurvivalFluidPrices.catalog().forEach((fuel, price) -> {
            var row = catalogRow(BuiltInRegistries.FLUID.getKey(fuel).toString(), price);
            row.putBoolean("Fluid", true);
            catalog.add(row);
        });
        tag.put("Catalog", catalog);
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        data.setItem(0, stack);
    }

    private static CompoundTag catalogRow(String id, int price) {
        var row = new CompoundTag();
        row.putString("Id", id);
        row.putInt("Price", price);
        return row;
    }

    private static int dumpAction(SurvivalLiquidFuel tank) {
        // Negative button IDs carry the liquid revision the player saw.
        return Integer.MIN_VALUE | (int) (tank.revision() & Integer.MAX_VALUE);
    }

    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canRefuel() && heart.canToggleBreaking(player) && heart.editorInRange(player, 64);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (heart == null || !stillValid(player)) return false;
        boolean handled = switch (id) {
            case BACK -> {
                heart.openDashboard(player);
                yield true;
            }
            case ENABLE_AUTO, DISABLE_AUTO -> {
                heart.survivalFuel().automatic(id == ENABLE_AUTO);
                confirmed = List.of();
                yield true;
            }
            case CONFIRM -> confirm();
            case FILL_TANK, DRAIN_TANK -> transferCarried(player, id == FILL_TANK);
            default -> {
                var tank = heart.survivalLiquidFuel();
                yield id == dumpAction(tank) && tank.clear();
            }
        };
        if (handled && id != BACK) broadcastChanges();
        return handled;
    }

    private boolean confirm() {
        var fluid = heart.survivalLiquidFuel().getFluid();
        var quote = SurvivalFluidPrices.quote(fluid, Long.MAX_VALUE);
        long value = SurvivalTokens.inputValue(inputs) + quote.energy();
        if (value <= 0 || value > Long.MAX_VALUE - heart.survivalTokenBalance()) return false;
        if (confirmationMatches(value, fluid, quote)) {
            if (!heart.settleRefuel(fluid, quote)) return false;
            confirmed = List.of();
        } else {
            var copy = new ArrayList<ItemStack>(INPUTS);
            for (int i = 0; i < INPUTS; i++) copy.add(inputs.getItem(i).copy());
            confirmed = List.copyOf(copy);
            confirmedValue = value;
            confirmedRevision = heart.survivalFuel().revision();
            confirmedFluid = fluid;
            confirmedQuote = quote;
            confirmationUntil = heart.getLevel().getGameTime() + 100;
        }
        return true;
    }

    private boolean transferCarried(Player player, boolean toTank) {
        var carried = getCarried();
        var tank = heart.survivalLiquidFuel();
        var transfer = tank.prepare(carried, toTank);
        if (transfer == null || !tank.commit(transfer)) return false;
        var remaining = carried.copyWithCount(carried.getCount() - 1);
        setCarried(transfer.result());
        if (!remaining.isEmpty()) player.getInventory().placeItemBackInInventory(remaining);
        return true;
    }

    private SurvivalLiquidFuel.Transfer prepareBucket(ItemStack source) {
        var transfer = heart.survivalLiquidFuel().prepare(source, true);
        if (transfer == null) return null;
        var output = buckets.getItem(1);
        var result = transfer.result();
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize()) ? transfer : null;
    }

    private void emptyInputBucket() {
        var source = buckets.getItem(0);
        var transfer = prepareBucket(source);
        if (transfer == null || !heart.survivalLiquidFuel().commit(transfer)) return;
        buckets.removeItem(0, 1);
        var output = buckets.getItem(1);
        var result = transfer.result();
        buckets.setItem(1, result.copyWithCount(output.getCount() + result.getCount()));
    }

    @Override public void clicked(int slot, int button, ClickType clickType, Player player) {
        if (!stillValid(player)) return;
        super.clicked(slot, button, clickType, player);
        if (heart != null && !buckets.getItem(0).isEmpty()) emptyInputBucket();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index > BUCKET_OUTPUT || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        var stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var copy = stack.copy();
        if (index < INPUTS || index >= BUCKET_INPUT) {
            if (!moveItemStackTo(stack, INPUTS, BUCKET_INPUT, true)) return ItemStack.EMPTY;
        } else if (getSlot(BUCKET_INPUT).mayPlace(stack)) {
            if (!moveItemStackTo(stack, BUCKET_INPUT, BUCKET_OUTPUT, false)) return ItemStack.EMPTY;
        } else if (!getSlot(0).mayPlace(stack) || !moveItemStackTo(stack, 0, INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        if (heart != null && !buckets.getItem(0).isEmpty()) emptyInputBucket();
        return copy;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (heart != null) clearContainer(player, buckets);
    }

    @Override public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }
}
