package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@Mod("sablelife_tests")
@GameTestHolder("sablelife_tests")
@PrefixGameTestTemplate(false)
public final class HeartGameTests {
    public HeartGameTests() {}

    @GameTest(template = "empty", batch="audit_heartgametests_placedheartpersistsconfigurationandrejectsgroundactivation")
    public static void placedHeartPersistsConfigurationAndRejectsGroundActivation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        helper.setBlock(relative, SableLife.HEART.get());
        var level = helper.getLevel();
        var pos = helper.absolutePos(relative);
        var heart = (HeartBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(heart != null, "Heart block entity did not register");
        helper.assertTrue(heart.configure(250, 80), "Dormant core should accept configuration");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
        helper.assertTrue(heart.health().state() == HeartState.DORMANT, "Ground block must not activate the terrain");
        var data = heart.saveWithFullMetadata(level.registryAccess());
        var loaded = (HeartBlockEntity) BlockEntity.loadStatic(pos, heart.getBlockState(), data, level.registryAccess());
        helper.assertTrue(loaded != null && loaded.health().maximum() == 250 && loaded.health().deathDelay() == 80,
                "NBT round trip lost configuration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160, batch="audit_heartgametests_assembledheartactivatesticksanddieswithoutresurrection")
    public static void assembledHeartActivatesTicksAndDiesWithoutResurrection(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 3, 2);
        helper.setBlock(relative, SableLife.HEART.get());
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(relative);
        var original = (HeartBlockEntity) level.getBlockEntity(origin);
        original.configure(200, 4);
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin), bounds(origin, origin));
        var movedPos = body.getPlot().getCenterBlock();
        var heart = (HeartBlockEntity) level.getBlockEntity(movedPos);
        helper.assertTrue(heart != null && heart.health().maximum() == 200, "Assembly lost Heart NBT");
        var probe = SableBodyProbe.inspect(level, movedPos);
        helper.assertTrue(probe.assembled() && probe.rootId().equals(body.getUniqueId()), "Wrong Sable structure identified");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
        helper.assertTrue(heart.health().state() == HeartState.ALIVE, "Assembled core did not activate");
        helper.assertTrue(heart.getBlockState().getValue(HeartBlock.ACTIVE), "Activated block state was not set");
        helper.assertTrue(heart.health().comparatorSignal() == 15, "Full health comparator should be 15");
        helper.runAfterDelay(5, () -> {
            heart.damage(90);
            helper.assertTrue(heart.health().current() == 110, "Damage was not applied");
            var saved = heart.saveWithFullMetadata(level.registryAccess());
            var loaded = (HeartBlockEntity) BlockEntity.loadStatic(movedPos, heart.getBlockState(), saved, level.registryAccess());
            helper.assertTrue(loaded != null && loaded.health().current() == 110 && loaded.health().state() == HeartState.ALIVE,
                    "Active health was not preserved by NBT");
            heart.damage(200);
            helper.assertTrue(heart.pulseSignal() == 0, "Lethal damage must disable output immediately");
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(heart.health().state() == HeartState.DEAD, "Assembled block entity ticker did not finish death");
                heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
                helper.assertTrue(heart.health().state() == HeartState.DEAD, "Dead core was resurrected");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch="audit_heartgametests_twoheartsinonebodycannotactivate")
    public static void twoHeartsInOneBodyCannotActivate(GameTestHelper helper) {
        var first = helper.absolutePos(new BlockPos(2, 3, 2));
        var second = first.east();
        var level = helper.getLevel();
        level.setBlockAndUpdate(first, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(second, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, first, List.of(first, second), bounds(first, second));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        helper.assertTrue(SableBodyProbe.inspect(level, heart.getBlockPos()).otherHearts() == 1, "Second core was not detected");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
        helper.assertTrue(heart.health().state() == HeartState.DORMANT, "Multiple-core body must not activate");
        helper.succeed();
    }

    private static BoundingBox3i bounds(BlockPos a, BlockPos b) {
        return new BoundingBox3i(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }
}
