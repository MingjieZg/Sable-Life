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

        h.assertTrue(input.getTanks() == 1 && input.getTankCapacity(0) == 1000,
                "Refuel fluid capability has the wrong tank capacity");
        h.assertTrue(input.fill(water, SIMULATE) == 350 && input.getFluidInTank(0).isEmpty()
                && core.survivalTokenBalance() == 0, "Simulated ingress changed the stored liquid or Energy");
        h.assertTrue(input.fill(water, EXECUTE) == 350 && water.getAmount() == 350,
                "Executed ingress rejected water or modified the caller's stack");
        h.assertTrue(input.fill(lava, SIMULATE) == 0 && input.fill(lava, EXECUTE) == 0,
                "A second fluid was accepted into an occupied tank");
        h.assertTrue(input.drain(250, EXECUTE).isEmpty() && input.drain(water, SIMULATE).isEmpty(),
                "Automation withdrew fluid through the input capability");
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 900), SIMULATE) == 650
                && input.getFluidInTank(0).getAmount() == 350, "Simulation ignored remaining capacity or changed its amount");
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 900), EXECUTE) == 650,
                "Executed ingress accepted the wrong amount for available space");

        var level = h.getLevel();
        core.loadWithComponents(core.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        var loadedInput = input(h, core);
        var saved = loadedInput.getFluidInTank(0);
        h.assertTrue(saved.getFluid() == Fluids.WATER && saved.getAmount() == 1000
                && core.survivalTokenBalance() == 0, "Save and reload changed the fluid amount, type or Energy");
        saved.setAmount(1);
        h.assertTrue(loadedInput.getFluidInTank(0).getAmount() == 1000,
                "The capability exposed its mutable stored fluid");

        var save = core.saveWithoutMetadata(level.registryAccess());
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
    public static void ActiveCoreAutomaticallyConvertsOnlySolidFuel(GameTestHelper h) {
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
            h.assertTrue(menu.clickMenuButton(player, menu.snapshot().getInt("TankDumpAction")), "Could not clear the water fixture");
            h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 1000
                    && core.survivalTokenBalance() == 30, "Filling a tank directly credited Energy or rejected fuel");
            h.runAfterDelay(16, () -> {
                h.assertTrue(input.getFluidInTank(0).getAmount() == 1000 && core.survivalTokenBalance() == 30,
                        "Auto Refuel consumed a liquid the player could still withdraw");
                player.setPos(ModuleArena.corePosition(core));
                h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM)
                        && menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM)
                        && input.getFluidInTank(0).isEmpty() && core.survivalTokenBalance() == 55,
                        "Manual Refuel did not exchange one bucket for 25 Energy on an active core");
                h.succeed();
            });
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
        menu.clicked(SurvivalTopUpMenu.BUCKET_OUTPUT, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().is(Items.BUCKET) && menu.getSlot(SurvivalTopUpMenu.BUCKET_OUTPUT).getItem().isEmpty()
                && menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK)
                && menu.getCarried().is(Items.LAVA_BUCKET) && input.getFluidInTank(0).isEmpty(),
                "An empty cursor bucket did not recover the exact stored lava");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.FILL_TANK) && menu.getCarried().is(Items.BUCKET)
                && input.getFluidInTank(0).getAmount() == 1000 && core.survivalTokenBalance() == 0,
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
                && input.getFluidInTank(0).getAmount() == 1000, "A spectator changed the tank through a menu button");
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(menu.clickMenuButton(player, previousDump), "Authorized dump did not clear the tank");
        h.assertTrue(input.getFluidInTank(0).isEmpty() && core.survivalTokenBalance() == 0,
                "Dump retained liquid or converted it into Energy");

        var namedWater = new FluidStack(Fluids.WATER, 1000);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Refuel test fluid"));
        h.assertTrue(input.fill(namedWater, EXECUTE) == 1000, "Could not stock the fluid component fixture");
        menu.broadcastChanges();
        int newDump = menu.snapshot().getInt("TankDumpAction");
        h.assertTrue(newDump != previousDump && !menu.clickMenuButton(player, previousDump)
                && FluidStack.matches(namedWater, input.getFluidInTank(0)),
                "A repeated Dump action cleared the replacement tank contents");
        h.assertTrue(!menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK) && menu.getCarried().is(Items.BUCKET)
                && FluidStack.matches(namedWater, input.getFluidInTank(0)),
                "An ordinary bucket stripped the stored fluid's components");
        h.assertTrue(menu.clickMenuButton(player, newDump), "Could not clear the component fixture");

        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 99), EXECUTE) == 99, "Could not input a partial bucket");
        menu.broadcastChanges();
        h.assertTrue(menu.snapshot().getInt("TankAmount") == 99 && menu.snapshot().getLong("Value") == 2
                && menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 0,
                "The first confirmation did not quote 99 mB of lava without spending it");
        h.assertTrue(input.fill(new FluidStack(Fluids.LAVA, 1), EXECUTE) == 1, "Could not change the quoted tank");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 0,
                "Confirmation spent fluid that was added after the first click");
        h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.CONFIRM) && core.survivalTokenBalance() == 2
                && input.getFluidInTank(0).getAmount() == 20, "Settlement did not conserve exact partial-bucket fuel");
        h.assertTrue(!menu.clickMenuButton(player, SurvivalTopUpMenu.DRAIN_TANK) && menu.getCarried().is(Items.BUCKET)
                && input.getFluidInTank(0).getAmount() == 20, "A partial bucket was lost on a failed withdrawal");
        h.succeed();
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

    private static ServerPlayer survivalPlayer(GameTestHelper h, HeartBlockEntity core) {
        var player = TestActors.creative(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ModuleArena.corePosition(core));
        return player;
    }
}
