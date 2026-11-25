package dev.sablelife.testing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.heart.SurvivalTopUpMenu;
import dev.sablelife.module.ModuleArena;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder("sablelife_core_ui_tests")
@PrefixGameTestTemplate(false)
public final class SurvivalLiquidFuelGameTests {
    @GameTest(template = "combat", batch = "survival_liquid_capability")
    public static void LiquidIngressSimulatesAndPersistsWithoutCreditingEnergy(GameTestHelper h) {
        var core = core(h, false);
        var input = input(h, core);
        var water = new FluidStack(Fluids.WATER, 350);
        var lava = new FluidStack(Fluids.LAVA, 1000);

        h.assertTrue(input.getTanks() == 1 && input.getTankCapacity(0) == 40_000,
                "Refuel fluid capability has the wrong tank capacity");
        h.assertTrue(input.fill(water, SIMULATE) == 350 && input.getFluidInTank(0).isEmpty()
                && core.survivalTokenBalance() == 0, "Simulated ingress changed the stored liquid or Energy");
        h.assertTrue(input.fill(water, EXECUTE) == 350 && water.getAmount() == 350,
                "Executed ingress rejected water or modified the caller's stack");
        h.assertTrue(input.fill(lava, SIMULATE) == 0 && input.fill(lava, EXECUTE) == 0,
                "A second fluid was accepted into an occupied tank");
        h.assertTrue(input.drain(250, EXECUTE).isEmpty() && input.drain(water, SIMULATE).isEmpty(),
                "Automation withdrew fluid through the input capability");
        var excess = new FluidStack(Fluids.WATER, 50_000);
        h.assertTrue(input.fill(excess, SIMULATE) == 39_650
                && input.getFluidInTank(0).getAmount() == 350, "Simulation ignored remaining capacity or changed its amount");
        h.assertTrue(input.fill(excess, EXECUTE) == 39_650 && excess.getAmount() == 50_000
                && input.fill(water, EXECUTE) == 0,
                "Executed ingress accepted the wrong amount for available space");

        var level = h.getLevel();
        core.loadWithComponents(core.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        var loadedInput = input(h, core);
        var saved = loadedInput.getFluidInTank(0);
        h.assertTrue(saved.getFluid() == Fluids.WATER && saved.getAmount() == 40_000
                && core.survivalTokenBalance() == 0, "Save and reload changed the fluid amount, type or Energy");
        saved.setAmount(1);
        h.assertTrue(loadedInput.getFluidInTank(0).getAmount() == 40_000,
                "The capability exposed its mutable stored fluid");

        var save = core.saveWithoutMetadata(level.registryAccess());
        save.getCompound("SurvivalRefuel").put("Liquid", lava.save(level.registryAccess()));
        core.loadWithComponents(save, level.registryAccess());
        h.assertTrue(FluidStack.matches(loadedInput.getFluidInTank(0), lava)
                && !core.survivalLiquidFuel().hasUnreadableFluid()
                && loadedInput.fill(lava, SIMULATE) == 1000,
                "A saved one-bucket tank lost its liquid or retained its old capacity");
        var missingFluid = new CompoundTag();
        missingFluid.putString("id", "no_such_test_mod:fuel");
        missingFluid.putInt("amount", 1000);
        save.getCompound("SurvivalRefuel").put("Liquid", missingFluid);
        core.loadWithComponents(save, level.registryAccess());
        h.assertTrue(core.survivalLiquidFuel().hasUnreadableFluid() && loadedInput.fill(water, EXECUTE) == 0
                && core.saveWithoutMetadata(level.registryAccess()).getCompound("SurvivalRefuel")
                .getCompound("Liquid").equals(missingFluid), "Reload overwrote saved fluid from an unavailable mod");
        var menu = new SurvivalTopUpMenu(1, survivalPlayer(h, core).getInventory(), core);
        h.assertTrue(menu.snapshot().getBoolean("TankUnavailable"), "An unavailable fluid was shown as an empty tank");
        h.assertTrue(core.survivalLiquidFuel().clear(), "Could not clear the unavailable fluid fixture");

        var ordinaryPos = core.getBlockPos().east();
        level.setBlockAndUpdate(ordinaryPos, SableLife.HEART.get().defaultBlockState());
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, ordinaryPos, Direction.WEST) == null,
                "An ordinary Heart Core exposed a Survival fluid tank");
        level.setBlockAndUpdate(core.getBlockPos(), Blocks.AIR.defaultBlockState());
        h.assertTrue(loadedInput.fill(water, EXECUTE) == 0,
                "A retained capability filled an invalidated core");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "survival_liquid_automatic", timeoutTicks = 100)
    public static void ActiveCoreAutomaticallyConvertsOnePricedBucketPerPoll(GameTestHelper h) {
        var core = core(h, true);
        var player = survivalPlayer(h, core);
        h.assertTrue(core.depositSurvivalTokens(player, new ItemStack(Items.COPPER_INGOT, 11)),
                "Could not fund the active core fixture");
        core.tryActivate(player);
        h.assertTrue(core.health().state() == HeartState.ALIVE && core.survivalTokenBalance() == 10,
                "Core did not activate with the expected Energy");
        var menu = new SurvivalTopUpMenu(1, player.getInventory(), core);
        var input = input(h, core);
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.ENABLE_AUTO), "Could not enable Auto Refuel");
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 1000,
                "An active core did not accept water through its capability");
        var solidInput = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, core.getBlockPos(), Direction.UP);
        h.assertTrue(solidInput != null && solidInput.insertItem(0, new ItemStack(Items.COPPER_INGOT, 2), false).isEmpty(),
                "An active core did not accept copper through its item capability");

        h.runAfterDelay(15, () -> {
            h.assertTrue(input.getFluidInTank(0).getAmount() == 1000 && solidInput.getStackInSlot(0).isEmpty()
                    && core.survivalTokenBalance() == 30, "Auto Refuel did not spend only the copper");
            player.setPos(ModuleArena.corePosition(core));
            menu.broadcastChanges();
            h.assertTrue(!menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM), "Water was accepted as fuel");
            dump(h, menu, player);
            h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 2637), EXECUTE) == 2637
                    && core.survivalTokenBalance() == 30, "Filling a tank directly credited Energy or rejected fuel");
            h.runAfterDelay(8, () -> h.assertTrue(input.getFluidInTank(0).getAmount() == 1637
                    && core.survivalTokenBalance() == 90, "The first poll did not convert exactly one 60-Energy bucket"));
            h.runAfterDelay(16, () -> h.assertTrue(input.getFluidInTank(0).getAmount() == 637
                    && core.survivalTokenBalance() == 150, "The second poll did not convert exactly one 60-Energy bucket"));
            h.runAfterDelay(24, () -> {
                h.assertTrue(input.getFluidInTank(0).getAmount() == 637 && core.survivalTokenBalance() == 150,
                        "Automatic settlement did not preserve the sub-bucket remainder");
                h.succeed();
            });
        });
    }

    @GameTest(template = "combat", batch = "survival_liquid_automatic_overflow", timeoutTicks = 100)
    public static void AutomaticLiquidRefuelRequiresRoomForTheWholeBucket(GameTestHelper h) {
        var core = core(h, false);
        var player = survivalPlayer(h, core);
        var level = h.getLevel();
        var saved = core.saveWithoutMetadata(level.registryAccess());
        saved.putLong("SurvivalTokens", Long.MAX_VALUE - 59);
        core.loadWithComponents(saved, level.registryAccess());
        var menu = new SurvivalTopUpMenu(1, player.getInventory(), core);
        var input = input(h, core);
        var solidInput = level.getCapability(Capabilities.ItemHandler.BLOCK, core.getBlockPos(), Direction.UP);
        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 2000), EXECUTE) == 2000
                && solidInput != null && menu.clickMenuButton(player, SurvivalTopUpMenu.ENABLE_AUTO),
                "Could not stock or enable the overflow fixture");

        h.runAfterDelay(8, () -> {
            h.assertTrue(input.getFluidInTank(0).getAmount() == 2000 && core.survivalTokenBalance() == Long.MAX_VALUE - 59,
                    "Automatic refuel charged fluid without room for its full price");
            h.assertTrue(core.spendAttackTokens(1), "Could not make room for one bucket's Energy");
            h.assertTrue(solidInput.insertItem(0, new ItemStack(Items.COPPER_INGOT, 7), false).isEmpty(),
                    "Could not stock a separate solid batch that exceeds the remaining room");
        });
        h.runAfterDelay(16, () -> h.assertTrue(input.getFluidInTank(0).getAmount() == 1000
                && core.survivalTokenBalance() == Long.MAX_VALUE && solidInput.getStackInSlot(0).getCount() == 7,
                "An unaffordable solid batch blocked liquid or consumed solid fuel"));
        h.runAfterDelay(24, () -> {
            h.assertTrue(input.getFluidInTank(0).getAmount() == 1000 && core.survivalTokenBalance() == Long.MAX_VALUE,
                    "Automatic refuel overflowed the Energy balance or wasted the second bucket");
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "survival_liquid_menu")
    public static void RefuelMenuReturnsBucketsAndConfirmsOnlyCurrentTankContents(GameTestHelper h) {
        var core = core(h, false);
        var player = survivalPlayer(h, core);
        var menu = new SurvivalTopUpMenu(1, player.getInventory(), core);
        var input = input(h, core);

        menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
        menu.clicked(SurvivalTopUpMenu.BUCKET_INPUT, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(SurvivalTopUpMenu.BUCKET_INPUT).getItem().isEmpty()
                && menu.getSlot(SurvivalTopUpMenu.BUCKET_OUTPUT).getItem().is(Items.BUCKET)
                && input.getFluidInTank(0).getAmount() == 1000 && core.survivalTokenBalance() == 0,
                "The fluid input slot did not store lava and return the empty bucket exactly once");
        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 1000,
                "Could not stock a second bucket through the pipe interface");
        menu.clicked(SurvivalTopUpMenu.BUCKET_OUTPUT, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().is(Items.BUCKET) && menu.getSlot(SurvivalTopUpMenu.BUCKET_OUTPUT).getItem().isEmpty()
                && menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK)
                && menu.getCarried().is(Items.LAVA_BUCKET) && input.getFluidInTank(0).getAmount() == 1000,
                "An empty cursor bucket did not recover exactly one of the two stored buckets");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.FILL_TANK) && menu.getCarried().is(Items.BUCKET)
                && input.getFluidInTank(0).getAmount() == 2000 && core.survivalTokenBalance() == 0,
                "A lava cursor bucket did not return its container after refilling the tank");

        int previousDump = menu.snapshot().getInt("TankDumpAction");
        var packet = new ServerboundContainerButtonClickPacket(menu.containerId, previousDump);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundContainerButtonClickPacket.STREAM_CODEC.encode(buffer, packet);
            h.assertTrue(previousDump < 0 && ServerboundContainerButtonClickPacket.STREAM_CODEC.decode(buffer).equals(packet),
                    "The revision-bound Dump action did not survive the vanilla button codec");
        } finally {
            buffer.release();
        }
        player.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!menu.clickMenuButton(player, previousDump)
                && !menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK)
                && !menu.clickMenuButton(player, SurvivalTopUpMenu.ENABLE_AUTO)
                && !menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM)
                && input.getFluidInTank(0).getAmount() == 2000, "A spectator changed the tank through a menu button");
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(menu.clickMenuButton(player, previousDump) && menu.snapshot().getBoolean("TankDumpConfirming")
                && input.getFluidInTank(0).getAmount() == 2000, "The first Dump click changed the tank or failed to arm");
        int confirmedDump = menu.snapshot().getInt("TankDumpAction");
        h.assertTrue(confirmedDump != previousDump && !menu.clickMenuButton(player, previousDump)
                && input.getFluidInTank(0).getAmount() == 2000, "Replaying the first Dump click cleared the tank");
        h.assertTrue(menu.clickMenuButton(player, confirmedDump), "Confirmed Dump did not clear the tank");
        h.assertTrue(input.getFluidInTank(0).isEmpty() && core.survivalTokenBalance() == 0,
                "Dump retained liquid or converted it into Energy");

        var namedWater = new FluidStack(Fluids.WATER, 1000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Refuel test fluid"));
        h.assertTrue(input.fill(namedWater, EXECUTE) == 1000, "Could not stock the fluid component fixture");
        menu.broadcastChanges();
        h.assertTrue(!menu.clickMenuButton(player, confirmedDump)
                && FluidStack.matches(namedWater, input.getFluidInTank(0)),
                "A repeated Dump action cleared the replacement tank contents");
        h.assertTrue(!menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK) && menu.getCarried().is(Items.BUCKET)
                && FluidStack.matches(namedWater, input.getFluidInTank(0)),
                "An ordinary bucket stripped the stored fluid's components");
        dump(h, menu, player);

        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 97), EXECUTE) == 97, "Could not input a partial bucket");
        menu.broadcastChanges();
        h.assertTrue(menu.snapshot().getInt("TankAmount") == 97 && menu.snapshot().getLong("Value") == 5
                && menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 0,
                "The first confirmation did not quote 97 mB of lava without spending it");
        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 1), EXECUTE) == 1, "Could not change the quoted tank");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 0,
                "Confirmation spent fluid that was added after the first click");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 5
                && input.getFluidInTank(0).getAmount() == 14, "Settlement did not conserve exact partial-bucket fuel");
        h.assertTrue(!menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK) && menu.getCarried().is(Items.BUCKET)
                && input.getFluidInTank(0).getAmount() == 14, "A partial bucket was lost on a failed withdrawal");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "survival_liquid_dump_confirmation", timeoutTicks = 140)
    public static void DumpConfirmationIsPerViewerAndExpires(GameTestHelper h) {
        var core = core(h, false);
        var input = input(h, core);
        var water = new FluidStack(Fluids.WATER, 1000);
        h.assertTrue(input.fill(water, EXECUTE) == 1000, "Could not stock the Dump fixture");
        var player = survivalPlayer(h, core);
        var other = survivalPlayer(h, core);
        var firstMenu = new SurvivalTopUpMenu(1, player.getInventory(), core);
        var secondMenu = new SurvivalTopUpMenu(2, other.getInventory(), core);

        h.assertTrue(firstMenu.clickMenuButton(player, firstMenu.snapshot().getInt("TankDumpAction"))
                && firstMenu.snapshot().getBoolean("TankDumpConfirming"), "Could not arm the first player's Dump");
        int firstConfirmation = firstMenu.snapshot().getInt("TankDumpAction");
        h.assertTrue(!secondMenu.clickMenuButton(other, firstConfirmation) && !secondMenu.snapshot().getBoolean("TankDumpConfirming"),
                "A second viewer could use another player's confirmation token");
        h.assertTrue(secondMenu.clickMenuButton(other, secondMenu.snapshot().getInt("TankDumpAction"))
                && secondMenu.snapshot().getBoolean("TankDumpConfirming") && input.getFluidInTank(0).getAmount() == 1000,
                "A second viewer inherited the first player's confirmation");
        int secondConfirmation = secondMenu.snapshot().getInt("TankDumpAction");
        h.assertTrue(secondConfirmation != firstConfirmation && !secondMenu.clickMenuButton(other, firstConfirmation)
                && input.getFluidInTank(0).getAmount() == 1000, "Armed viewers shared the same Dump action");

        h.runAfterDelay(101, () -> {
            h.assertTrue(!firstMenu.clickMenuButton(player, firstConfirmation), "An expired Dump token remained valid");
            firstMenu.broadcastChanges();
            h.assertTrue(!firstMenu.snapshot().getBoolean("TankDumpConfirming") && input.getFluidInTank(0).getAmount() == 1000,
                    "Confirmation expiry left Dump armed or cleared fluid");
            dump(h, firstMenu, player);
            h.assertTrue(input.getFluidInTank(0).isEmpty(), "Fresh Dump confirmation did not empty the tank");
            h.assertTrue(input.fill(water, EXECUTE) == 1000 && !secondMenu.clickMenuButton(other, secondConfirmation)
                    && input.getFluidInTank(0).getAmount() == 1000,
                    "Another viewer's stale confirmation cleared replacement fluid");
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "survival_liquid_create_pipe", timeoutTicks = 160)
    public static void CreatePumpMovesFluidFromATankThroughAPipe(GameTestHelper h) {
        var core = core(h, false);
        var level = h.getLevel();
        var pos = core.getBlockPos();
        var pumpPos = pos.east(2);
        var tankPos = pos.east(3);
        var cogPos = pumpPos.north();
        level.setBlockAndUpdate(tankPos, AllBlocks.FLUID_TANK.getDefaultState());
        level.setBlockAndUpdate(pumpPos, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(pos.east(), AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.X));
        level.setBlockAndUpdate(cogPos, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(cogPos.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.WEST));
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, tankPos, Direction.WEST);
        h.assertTrue(tank != null && tank.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 1000,
                "Could not fill the Create supply tank");
        var input = input(h, core);
        h.succeedWhen(() -> {
            var pump = (PumpBlockEntity) level.getBlockEntity(pumpPos);
            h.assertTrue(pump.getSpeed() != 0, "The adjacent cog did not drive the pump");
            h.assertTrue(tank.getFluidInTank(0).isEmpty() && input.getFluidInTank(0).getFluid() == Fluids.LAVA
                    && input.getFluidInTank(0).getAmount() == 1000 && core.survivalTokenBalance() == 0,
                    "The Create network did not deliver the full bucket without converting it");
        });
    }

    private static HeartBlockEntity core(GameTestHelper h, boolean assembled) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 8, 3));
        level.setBlockAndUpdate(pos, SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
        if (assembled) {
            level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
            var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ());
            pos = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()), bounds).getPlot().getCenterBlock();
        }
        return (HeartBlockEntity) level.getBlockEntity(pos);
    }

    private static IFluidHandler input(GameTestHelper h, HeartBlockEntity core) {
        var input = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, core.getBlockPos(), Direction.EAST);
        h.assertTrue(input != null, "Survival Heart Core did not expose its fluid input capability");
        return input;
    }

    private static void dump(GameTestHelper h, SurvivalTopUpMenu menu, ServerPlayer player) {
        menu.broadcastChanges();
        h.assertTrue(menu.clickMenuButton(player, menu.snapshot().getInt("TankDumpAction"))
                && menu.snapshot().getBoolean("TankDumpConfirming"), "Could not arm Dump");
        h.assertTrue(menu.clickMenuButton(player, menu.snapshot().getInt("TankDumpAction")), "Could not confirm Dump");
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h, HeartBlockEntity core) {
        var player = TestActors.creative(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ModuleArena.corePosition(core));
        return player;
    }
}
