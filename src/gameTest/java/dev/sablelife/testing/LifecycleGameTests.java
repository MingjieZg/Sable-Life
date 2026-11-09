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

    @GameTest(template = "combat", batch="lifecycle_mining_drops")
    public static void MiningDropsRequireOptInAndPlayerBreak(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var author = TestActors.creative(level);
        author.setPos(dev.sablelife.module.ModuleArena.corePosition(heart));
        var miner = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BodyMiner"));
        miner.setGameMode(GameType.SURVIVAL);
        miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
        var pos = helper.absolutePos(new BlockPos(3, 7, 3));
        miner.setPos(pos.getCenter());
        var area = new net.minecraft.world.phys.AABB(pos).inflate(2);
        Runnable place = () -> {
            level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
            BodyOwnership.get(level).bind(pos, heart.creatureId());
        };
        place.run();
        helper.assertTrue(miner.gameMode.destroyBlock(pos) && level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Owned block dropped by default");
        helper.assertTrue(heart.editCore(author, "miningDrops", 1, ""), "Mining drops could not be enabled");
        place.run();
        helper.assertTrue(level.destroyBlock(pos, true) && level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Non-player destruction used mining permission");
        place.run();
        helper.assertTrue(level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS), "Test world has block drops disabled");
        helper.assertTrue(!miner.isCreative() && level.getBlockState(pos).canHarvestBlock(level, pos, miner), "Test player cannot harvest iron");
        helper.assertTrue(LifeLifecycle.get(level.getServer()).miningDrops(heart.creatureId()), "Active owner missing mining permission, state=" + heart.health().state());
        helper.assertTrue(miner.gameMode.destroyBlock(pos), "Survival player could not mine owned block");
        helper.assertTrue(level.getBlockState(pos).isAir(), "Player break did not remove the test block");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).stream().anyMatch(item -> item.getItem().is(Items.IRON_BLOCK)), "Opted-in mining did not drop the block");
        var saved = LifeLifecycle.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(LifeLifecycle.load(saved, level.registryAccess()).miningDrops(heart.creatureId()), "Mining permission was lost on save");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="ordinary_limb_cleanup", timeoutTicks=110)
    public static void DetachedLimbCleansWithoutDropsAfterDelay(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var author = TestActors.creative(level);
        helper.assertTrue(!heart.limbCleanup().enabled() && heart.limbCleanup().delay() == 3, "Unexpected defaults");
        helper.assertTrue(heart.editCore(author, "limbCleanup", 1, ""), "Could not enable limb cleanup");
        var limb = detach(helper, heart);
        heart.wireless().master(false);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(!limb.isRemoved(), "Limb was removed before the configured delay");
            helper.assertTrue(heart.limbCleanup().save().getList("Pending", 10).size() == 1, "Pending cleanup was not saved");
            helper.assertTrue(heart.copyConfiguration().getCompound("DetachedLimbCleanup").getList("Pending", 10).isEmpty(), "Copied config included runtime cleanup");
        });
        helper.runAfterDelay(85, () -> {
            helper.assertTrue(limb.isRemoved() && !heart.isRemoved(), "Cleanup missed the detached limb or removed the core");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="ordinary_limb_release", timeoutTicks=110)
    public static void ReleasingLifeCancelsDetachedLimbCleanup(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var author = TestActors.creative(helper.getLevel());
        helper.assertTrue(heart.editCore(author, "limbCleanup", 1, ""), "Could not enable limb cleanup");
        var limb = detach(helper, heart);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(heart.limbCleanup().save().getList("Pending", 10).size() == 1, "Limb cleanup did not start");
            helper.assertTrue(heart.resetForCreative(author), "Release was rejected");
        });
        helper.runAfterDelay(85, () -> {
            helper.assertTrue(!limb.isRemoved() && heart.limbCleanup().save().getList("Pending", 10).isEmpty(), "Release left a destructive timer");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="ordinary_limb_loot_priority", timeoutTicks=70)
    public static void LootClaimTakesPriorityOverOrdinaryLimbCleanup(GameTestHelper helper) {
        var heart = fixture(helper, true);
        var level = helper.getLevel();
        var author = TestActors.creative(level);
        helper.assertTrue(heart.editCore(author, "limbCleanupDelay", 1, "") && heart.editCore(author, "limbCleanup", 1, ""), "Could not configure cleanup");
        var limb = detach(helper, heart);
        helper.assertTrue(BodyOwnership.get(level).claimPart(level, limb.getPlot().getCenterBlock(), heart.creatureId(), java.util.UUID.randomUUID()), "Could not claim loot limb");
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(!limb.isRemoved() && heart.limbCleanup().save().getList("Pending", 10).isEmpty(), "Ordinary cleanup queued a claimed loot limb");
            helper.succeed();
        });
    }

    private static dev.ryanhcode.sable.sublevel.ServerSubLevel detach(GameTestHelper helper, HeartBlockEntity heart) {
        var pos = heart.getBlockPos().east();
        return SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), pos, List.of(pos), bounds(pos, pos));
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
