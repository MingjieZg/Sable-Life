package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LifeLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_lifecycle_tests")
@PrefixGameTestTemplate(false)
public final class LifecycleGameTests {
    @GameTest(template = "combat", batch="lifecycle_cleanup", timeoutTicks = 100)
    public static void lethalDamageCleansBodyAndDetachedFragmentOnce(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var pos = heart.getBlockPos();
        var limb = pos.east();
        var fragment = SubLevelAssemblyHelper.assembleBlocks(level, limb, List.of(limb), bounds(limb, limb));
        var fragmentPos = fragment.getPlot().getCenterBlock();
        var point = dev.sablelife.compat.sable.SableBodyProbe.toWorld(level, pos.getCenter());
        var area = new net.minecraft.world.phys.AABB(point, point).inflate(3);
        heart.damage(100);
        helper.assertTrue(!level.getBlockState(pos).isAir(), "Death ignored animation delay");
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!BodyOwnership.get(level).hasOwned(heart.creatureId()), "Death did not clean owned core and fragment; core="
                    + level.getBlockState(pos) + "; fragment=" + level.getBlockState(fragmentPos));
            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.AMETHYST_SHARD), "Death did not produce only configured loot");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area).stream()
                    .mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum() == 7, "Configured death experience was not awarded");
            // Serialize completed settlement, load it, and tick again: no duplicate reward.
            var data = LifeLifecycle.get(level.getServer());
            var restored = LifeLifecycle.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            restored.tickDeaths(level.getServer());
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).size() == 1, "Reload duplicated settled loot");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="lifecycle_reload", timeoutTicks = 100)
    public static void pendingDeathReloadRetainsCountdown(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var pos = heart.getBlockPos();
        heart.damage(100);
        var restored = LifeLifecycle.load(LifeLifecycle.get(level.getServer()).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        level.getServer().overworld().getDataStorage().set("sablelife_lifecycle", restored);
        helper.assertTrue(!level.getBlockState(pos).isAir(), "Reload skipped pending delay");
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!BodyOwnership.get(level).hasOwned(heart.creatureId()), "Reload lost pending cleanup; state="
                    + level.getBlockState(pos) + "; owner=" + BodyOwnership.get(level).owner(pos));
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="lifecycle_missing_core", timeoutTicks = 100)
    public static void brokenCoreContinuesDeathAndAssemblyDoesNotKill(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var old = heart.getBlockPos();
        var moved = SubLevelAssemblyHelper.assembleBlocks(level, old, List.of(old, old.east()), bounds(old, old.east()));
        var pos = moved.getPlot().getCenterBlock();
        var current = (HeartBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(current.health().state() == HeartState.ALIVE, "Assembly killed the core");
        level.destroyBlock(pos, true);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!BodyOwnership.get(level).hasOwned(current.creatureId()), "Core removal lost death cleanup");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="lifecycle_protection")
    public static void breakingToggleSurvivesSplitAndReset(GameTestHelper helper) {
        var heart = fixture(helper, false);
        var level = helper.getLevel();
        var pos = heart.getBlockPos().east();
        helper.assertTrue(!level.destroyBlock(pos, true), "Disabled breaking still destroyed body");
        var fragment = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), bounds(pos, pos));
        var limb = fragment.getPlot().getCenterBlock();
        helper.assertTrue(!level.destroyBlock(limb, true), "Detached limb lost breaking switch");
        var restored = LifeLifecycle.load(LifeLifecycle.get(level.getServer()).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(!restored.breakable(heart.creatureId()), "Saved breaking flag was lost");
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.CREATIVE);
        heart.resetForCreative(player);
        helper.assertTrue(level.destroyBlock(limb, true), "Release did not restore destruction");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="lifecycle_cancel", timeoutTicks = 100)
    public static void creativeResetCancelsPendingWorldDeath(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var pos = heart.getBlockPos();
        heart.damage(100);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.CREATIVE);
        heart.resetForCreative(player);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!level.getBlockState(pos).isAir() && heart.health().state() == HeartState.DORMANT, "Cancelled death destroyed released structure");
            helper.succeed();
        });
    }

    private static HeartBlockEntity fixture(GameTestHelper helper, boolean breaking) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(5, 7, 5));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()), bounds(origin, origin.east()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        heart.configure(100, 5);
        heart.configureOptions(breaking, ResourceLocation.fromNamespaceAndPath("sablelife", "entities/construct"), 7);
        heart.tryActivate(FakePlayerFactory.getMinecraft(level));
        return heart;
    }
    private static BoundingBox3i bounds(BlockPos a, BlockPos b) { return new BoundingBox3i(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()); }
}
