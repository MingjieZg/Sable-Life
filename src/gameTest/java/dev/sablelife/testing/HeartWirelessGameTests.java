package dev.sablelife.testing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_control_tests")
@PrefixGameTestTemplate(false)
public final class HeartWirelessGameTests {
    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_heartwirelessgametests_externalreceivermatchescreatelinkandneverrunsownwaveform")
    public static void externalReceiverMatchesCreateLinkAndNeverRunsOwnWaveform(GameTestHelper h) {
        var heart = fixture(h, new BlockPos(3, 6, 3)); var channel = heart.wireless().channel(15);
        channel.frequency(false, new ItemStack(Items.NETHER_STAR)); channel.frequency(true, new ItemStack(Items.COPPER_INGOT));
        channel.set(0, 1); channel.set(6, 1); channel.set(2, 100); channel.set(4, 15);
        var module = heart.modules().add("arrow"); module.anchors(List.of(dev.sablelife.module.ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST)));
        module.edit("trigger", 3, ""); module.edit("channel", 16, ""); heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        heart.wireless().tick(h.getLevel());
        h.assertTrue(heart.wireless().input(15) == 0 && heart.wireless().output(15) == 0 && !dev.sablelife.module.HeartModules.triggered(module, heart), "RX self-triggered from own waveform");
        var pos = h.absolutePos(new BlockPos(5, 6, 7)); var level = h.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(RedstoneLinkBlock.RECEIVER, false).setValue(RedstoneLinkBlock.FACING, Direction.UP));
        var transmitter = (RedstoneLinkBlockEntity) level.getBlockEntity(pos); transmitter.loadWithComponents(new net.minecraft.nbt.CompoundTag(), level.registryAccess()); transmitter.tick();
        var link = transmitter.getBehaviour(LinkBehaviour.TYPE); link.setFrequency(true, new ItemStack(Items.NETHER_STAR)); link.setFrequency(false, new ItemStack(Items.COPPER_INGOT));
        h.runAfterDelay(6, () -> {
            h.assertTrue(heart.wireless().input(15) > 0 && heart.wireless().output(15) == 0 && dev.sablelife.module.HeartModules.triggered(module, heart), "External Create transmitter did not gate virtual module");
            var tag = heart.wireless().save(level.registryAccess()); heart.wireless().load(tag, level.registryAccess());
            h.assertTrue(heart.wireless().channel(15).receiving(), "RX mode lost on reload"); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        });
        h.runAfterDelay(12, () -> { h.assertTrue(heart.wireless().input(15) == 0 && !dev.sablelife.module.HeartModules.triggered(module, heart), "External off left stale power"); heart.onChunkUnloaded(); h.succeed(); });
    }
    @GameTest(template = "combat", batch="audit_heartwirelessgametests_waveformboundariesandpersistence")
    public static void waveformBoundariesAndPersistence(GameTestHelper h) {
        var c = new WirelessChannel(); c.frequency(false, new ItemStack(Items.DIAMOND, 64));
        c.set(0, 1); c.set(1, 3); c.set(2, 2); c.set(3, 4); c.set(4, 7);
        h.assertTrue(c.powerAt(2) == 0 && c.powerAt(3) == 7 && c.powerAt(4) == 7
                && c.powerAt(5) == 0 && c.powerAt(8) == 0 && c.powerAt(9) == 7, "Loop boundaries wrong");
        c.set(5, 0);
        h.assertTrue(c.powerAt(3) == 7 && c.powerAt(5) == 0 && c.powerAt(9) == 0, "Single pulse repeated");
        c.set(5, 1); c.set(3, 0);
        h.assertTrue(c.powerAt(1000) == 7, "Zero off duration must remain powered");
        c.set(1, -1); c.set(2, 0); c.set(3, 99999); c.set(4, 99);
        var copy = WirelessChannel.load(c.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(copy.value(1) == 0 && copy.value(2) == 1 && copy.value(3) == 12000
                && copy.value(4) == 15 && copy.first().getCount() == 1, "Clamps or save roundtrip wrong");
        copy.first().setCount(64);
        h.assertTrue(copy.first().getCount() == 1, "Sample leaked mutable stack"); h.succeed();
    }

    @GameTest(template = "combat", batch="audit_heartwirelessgametests_independentmenucopiessamplesandrejectslockedorremoteedits")
    public static void independentMenuCopiesSamplesAndRejectsLockedOrRemoteEdits(GameTestHelper h) {
        var heart = fixture(h, new BlockPos(4, 6, 4)); var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartWirelessMenu(1, player.getInventory(), heart);
        menu.setCarried(new ItemStack(Items.EMERALD, 23)); menu.clicked(0, 1, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 23 && heart.wireless().channel(0).first().getCount() == 1,
                "Frequency sampler consumed or duplicated inventory items");
        menu.clickMenuButton(player, 6 << 16 | 15);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clickMenuButton(player, 15 << 24 | 1 << 16 | 80);
        h.assertTrue(menu.selected() == 15 && heart.wireless().channel(15).second().is(Items.EMERALD)
                && heart.wireless().channel(15).value(1) == 80, "Last channel editing failed");
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(heart.getBlockPos(),
                heart.getBlockState(), heart.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        h.assertTrue(restored.wireless().channel(15).value(1) == 80
                && restored.wireless().channel(15).second().is(Items.EMERALD), "Heart lost channel configuration");
        h.assertTrue(!menu.clickMenuButton(player, 6 << 16 | 16), "Invalid channel accepted");
        player.setPos(player.position().add(100, 0, 0));
        h.assertTrue(!menu.clickMenuButton(player, 1 << 16 | 5), "Remote settings edited");
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter())); heart.tryActivate(player);
        h.assertTrue(!menu.clickMenuButton(player, 1 << 16 | 5), "Active settings edited");
        menu.setCarried(ItemStack.EMPTY); menu.clicked(1, 0, ClickType.PICKUP, player);
        h.assertTrue(heart.wireless().channel(15).second().is(Items.EMERALD), "Locked sample cleared");
        player.setGameMode(GameType.SURVIVAL); var previous = player.containerMenu; heart.openWireless(player);
        h.assertTrue(!menu.stillValid(player) && previous == player.containerMenu, "Survival opened wireless UI"); h.succeed();
    }

    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_heartwirelessgametests_realcreatereceiveracceptsmovingbodyheartandstopsondeath")
    public static void realCreateReceiverAcceptsMovingBodyHeartAndStopsOnDeath(GameTestHelper h) {
        var heart = fixture(h, new BlockPos(4, 6, 4)); var level = h.getLevel();
        var receiver = receiver(h, new BlockPos(4, 6, 7), Items.DIAMOND, Items.EMERALD);
        var channel = heart.wireless().channel(0);
        channel.frequency(false, new ItemStack(Items.DIAMOND)); channel.frequency(true, new ItemStack(Items.EMERALD));
        channel.set(0, 1);channel.set(6,2); channel.set(2, 100); channel.set(4, 9);
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
        h.runAfterDelay(5, () -> {
            h.assertTrue(receiver.getReceivedSignal() == 9 && heart.wireless().output(0) == 9,
                    "Create receiver did not match Heart's Sable plot frequencies/world range");
            var saved = heart.wireless().save(level.registryAccess()); long elapsed = heart.wireless().elapsed();
            heart.wireless().load(saved, level.registryAccess());
            h.assertTrue(heart.wireless().elapsed() == elapsed, "Reload lost running clock");
            h.assertTrue(receiver.getReceivedSignal() == 0, "Reload failed to remove previous transmitter");
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(receiver.getReceivedSignal() == 9, "Reload failed to register transmitter again");
            heart.damage(1000);
            h.assertTrue(receiver.getReceivedSignal() == 0 && heart.wireless().output(0) == 0, "Death left wireless output");
            h.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_heartwirelessgametests_sharedfrequencypreservesotherheartandunloadclearsownoutput")
    public static void sharedFrequencyPreservesOtherHeartAndUnloadClearsOwnOutput(GameTestHelper h) {
        var a = fixture(h, new BlockPos(3, 6, 3)); var b = fixture(h, new BlockPos(7, 6, 3));
        var receiver = receiver(h, new BlockPos(5, 6, 7), Items.GOLD_INGOT, Items.IRON_INGOT);
        for (var heart : List.of(a, b)) {
            var c = heart.wireless().channel(0); c.frequency(false, new ItemStack(Items.GOLD_INGOT));
            c.frequency(true, new ItemStack(Items.IRON_INGOT)); c.set(0, 1);c.set(6,2); c.set(2, 100);
            c.set(4, heart == a ? 12 : 6); heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        }
        h.runAfterDelay(5, () -> {
            h.assertTrue(receiver.getReceivedSignal() == 12, "Same frequency maximum failed");
            var player = FakePlayerFactory.getMinecraft(h.getLevel()); player.setGameMode(GameType.CREATIVE);
            a.resetForCreative(player);
            h.assertTrue(receiver.getReceivedSignal() == 6, "Release silenced another Heart");
            b.onChunkUnloaded(); h.assertTrue(receiver.getReceivedSignal() == 0, "Unload left stale signal");
            h.succeed();
        });
    }

    private static RedstoneLinkBlockEntity receiver(GameTestHelper h, BlockPos relative,
            net.minecraft.world.item.Item first, net.minecraft.world.item.Item second) {
        var pos = h.absolutePos(relative); var level = h.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, AllBlocks.REDSTONE_LINK.getDefaultState()
                .setValue(RedstoneLinkBlock.RECEIVER, true).setValue(RedstoneLinkBlock.FACING, Direction.UP));
        var be = (RedstoneLinkBlockEntity) level.getBlockEntity(pos);
        be.loadWithComponents(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        be.tick();
        var link = be.getBehaviour(LinkBehaviour.TYPE);
        link.setFrequency(true, new ItemStack(first)); link.setFrequency(false, new ItemStack(second));
        return be;
    }
    private static HeartBlockEntity fixture(GameTestHelper h, BlockPos relative) {
        var level = h.getLevel(); var origin = h.absolutePos(relative);
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        var heart=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        for(int i=0;i<16;i++)heart.wireless().add();
        return heart;
    }
}
