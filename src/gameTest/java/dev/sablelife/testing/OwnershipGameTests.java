package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_cleanup_tests")
@PrefixGameTestTemplate(false)
public final class OwnershipGameTests {
    @GameTest(template = "combat", timeoutTicks = 180, batch="audit_ownershipgametests_naturalbreaksplitswithownershipintact")
    public static void naturalBreakSplitsWithOwnershipIntact(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var originalBody = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, heart.getBlockPos()).getUniqueId();
        var creature = heart.creatureId();
        helper.assertTrue(level.destroyBlock(heart.getBlockPos().east(), true),"Allowed split fixture was not destroyed");
        helper.runAfterDelay(40, () -> {
            var data = BodyOwnership.get(level);
            var entries = data.save(new CompoundTag(), level.registryAccess()).getList("Blocks", 10);
            boolean found = false;
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                if (!creature.equals(entry.getUUID("Owner"))) continue;
                var pos = BlockPos.of(entry.getLong("Pos"));
                if (!level.getBlockState(pos).is(Blocks.CHEST)) continue;
                var fragment = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
                helper.assertTrue(fragment != null && !originalBody.equals(fragment.getUniqueId()), "Chest did not naturally split into a new body");
                helper.assertTrue(Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos)).isEmpty(), "Natural fragment lost drop protection");
                found = true;
            }
            helper.assertTrue(found, "Natural split lost chest ownership");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="audit_ownershipgametests_explosiondestroysbodywithoutmaterials")
    public static void explosionDestroysBodyWithoutMaterials(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var pos = heart.getBlockPos().east();
        long before = items(helper);
        var explosion = new net.minecraft.world.level.Explosion(level, null, pos.getX(), pos.getY(), pos.getZ(), 4,
                false, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY, List.of(pos));
        explosion.finalizeExplosion(false);
        helper.assertTrue(level.getBlockState(pos).isAir(), "Explosion did not destroy the body block");
        helper.assertTrue(items(helper) == before, "Explosion produced body materials");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_ownershipgametests_miningandinventorydropssuppressedbutordinaryblocksstilldrop")
    public static void miningAndInventoryDropsSuppressedButOrdinaryBlocksStillDrop(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var iron = heart.getBlockPos().east();
        var chest = iron.east();
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 12));
        long before = items(helper);
        helper.assertTrue(Block.getDrops(level.getBlockState(iron), level, iron, null).isEmpty(), "Body loot table still produces iron");
        helper.assertTrue(level.destroyBlock(iron, true)&&level.destroyBlock(chest, true),"Mining test did not actually remove body blocks");
        helper.assertTrue(items(helper) == before, "Body mining or chest inventory dropped materials");
        var ordinary = helper.absolutePos(new BlockPos(2, 4, 2));
        level.setBlockAndUpdate(ordinary, Blocks.IRON_BLOCK.defaultBlockState());
        helper.assertTrue(!Block.getDrops(level.getBlockState(ordinary), level, ordinary, null).isEmpty(), "Ordinary loot was suppressed");
        level.destroyBlock(ordinary, true);
        helper.assertTrue(items(helper) > before, "Ordinary mining stopped dropping materials");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_ownershipgametests_repeatedassemblykeepsfragmentownerandclearsoldcoordinates")
    public static void repeatedAssemblyKeepsFragmentOwnerAndClearsOldCoordinates(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var data = BodyOwnership.get(level);
        var pos = heart.getBlockPos().east();
        for (int i = 0; i < 2; i++) {
            var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), bounds(pos, pos));
            var moved = body.getPlot().getCenterBlock();
            helper.assertTrue(data.owner(pos) == null, "Old storage position retained ownership");
            helper.assertTrue(heart.creatureId().equals(data.owner(moved)), "Detached fragment lost its creature identity");
            helper.assertTrue(Block.getDrops(level.getBlockState(moved), level, moved, null).isEmpty(), "Fragment drops materials");
            pos = moved;
        }
        helper.assertTrue(data.releaseFragment(level, pos), "Cannot release orphan fragment");
        helper.assertTrue(data.owner(pos) == null && heart.creatureId().equals(data.owner(heart.getBlockPos())), "Fragment release changed parent ownership");
        helper.assertTrue(!Block.getDrops(level.getBlockState(pos), level, pos, null).isEmpty(), "Released fragment still protected");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_ownershipgametests_ownershipsurvivescoreremovalandserialization")
    public static void ownershipSurvivesCoreRemovalAndSerialization(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var data = BodyOwnership.get(level);
        var body = heart.getBlockPos().east();
        level.destroyBlock(heart.getBlockPos(), true);
        helper.assertTrue(Block.getDrops(level.getBlockState(body), level, body, null).isEmpty(), "Core removal disabled body protection");
        var restored = BodyOwnership.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(heart.creatureId().equals(restored.owner(body)), "Saved data lost orphan ownership");
        level.destroyBlock(body, false);
        level.setBlockAndUpdate(body, Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(data.owner(body) == null, "New block inherited stale ownership");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_ownershipgametests_corereleaserestoresmaterialdrops")
    public static void coreReleaseRestoresMaterialDrops(GameTestHelper helper) {
        var heart = fixture(helper);
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(heart.resetForCreative(player), "Creative reset failed");
        var body = heart.getBlockPos().east();
        helper.assertTrue(!Block.getDrops(level.getBlockState(body), level, body, null).isEmpty(), "Reset did not restore body loot");
        helper.succeed();
    }

    private static HeartBlockEntity fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Blocks.CHEST.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east(), origin.east(2)), bounds(origin, origin.east(2)));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        var author=dev.sablelife.testing.TestActors.creative(level);
        heart.editCore(author,"breaking",1,"");heart.editCore(author,"explosion",1,"");
        heart.tryActivate(author);
        helper.assertTrue(heart.creatureId().equals(BodyOwnership.get(level).owner(heart.getBlockPos().east())), "Activation did not capture correct body owner");
        return heart;
    }
    private static long items(GameTestHelper helper) {
        long count = 0;
        for (var entity : helper.getLevel().getAllEntities()) if (entity instanceof ItemEntity) count++;
        return count;
    }
    private static BoundingBox3i bounds(BlockPos a, BlockPos b) {
        return new BoundingBox3i(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }
}
