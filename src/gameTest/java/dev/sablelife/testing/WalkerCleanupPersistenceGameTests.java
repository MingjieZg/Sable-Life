package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LifeLifecycle;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_plot_cache_tests")
@PrefixGameTestTemplate(false)
public final class WalkerCleanupPersistenceGameTests {
    @GameTest(template = "combat", batch = "walker_cleanup_disk_only_legacy", timeoutTicks = 40)
    public static void LegacyWalkerCleanupWaitsForNativeDiskOnlyBody(GameTestHelper test) throws Exception {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(5, 40, 5));
        level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), bounds);
        var id = body.getUniqueId();
        var storagePos = body.getPlot().getCenterBlock();
        var container = SubLevelContainer.getContainer(level);
        var holding = container.getHoldingChunkMap();
        var ownership = BodyOwnership.get(level);
        UUID death = UUID.randomUUID();
        ownership.bind(storagePos, death);
        var pending = savedDeath(test, death, id, null);
        var knownPlotPending = savedDeath(test, death, id, storagePos);
        var storageChunk = new ChunkPos(64, 64);
        HoldingSubLevel record = null;
        try {
            holding.moveToUnloaded(body, storageChunk);
            record = holding.getHoldingSubLevel(id);
            test.assertTrue(container.getSubLevel(id) == null && record != null,
                    "Native unload did not move the attachment into holding");
            holding.updateChunkStatus(storageChunk, false);
            holding.processChanges();
            holding.saveAll();
            holding.getStorage().flush();
            var pointer = record.pointer();
            test.assertTrue(container.getSubLevel(id) == null && holding.getHoldingSubLevel(id) == null
                            && !StructureRemovalApi.permanentlyRemoved(container, body) && pointer != null,
                    "Fixture did not evict both memory entries while retaining the plot");
            try (var storage = new SubLevelStorage(holding.getStorage().getFolder())) {
                var savedChunk = storage.attemptLoadHoldingChunk(storageChunk);
                var savedBody = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
                test.assertTrue(savedChunk != null && savedChunk.getSubLevelPointers().contains(pointer.local())
                                && savedBody != null && savedBody.uuid().equals(id),
                        "A new storage reader could not find the native body on disk");
            }
            pending.tickDeaths(level.getServer());
            assertPending(test, pending, death, id);
            knownPlotPending.tickDeaths(level.getServer());
            assertPending(test, knownPlotPending, death, id);
            test.assertTrue(death.equals(ownership.owner(storagePos)), "An unloaded attachment lost its ownership");

            var saved = pending.save(new CompoundTag(), level.registryAccess());
            var reloaded = LifeLifecycle.load(saved, level.registryAccess());
            assertPending(test, reloaded, death, id);

            holding.snatchAndLoad(pointer, id);
            test.assertTrue(container.getSubLevel(id) instanceof ServerSubLevel,
                    "Sable did not reload the attachment from disk");
            var restored = (ServerSubLevel) container.getSubLevel(id);
            test.assertTrue(level.getBlockState(storagePos).is(Blocks.IRON_BLOCK), "Native reloading lost the fixture block");
            reloaded.tickDeaths(level.getServer());
            test.assertTrue(StructureRemovalApi.permanentlyRemoved(container, restored),
                    "Cleanup did not permanently remove the reloaded attachment");
            test.assertTrue(reloaded.finished(death) && ownership.owner(storagePos) == null,
                    "Permanent removal did not complete the pending job and release ownership");
        } finally {
            if (record != null && !StructureRemovalApi.permanentlyRemoved(container, body) && container.getSubLevel(id) == null) {
                if (record.pointer() != null) holding.snatchAndLoad(record.pointer(), id);
                else holding.loadHoldingSubLevel(record);
            }
            if (container.getSubLevel(id) instanceof ServerSubLevel remaining) StructureRemovalApi.remove(level, remaining);
            if (death.equals(ownership.owner(storagePos))) ownership.forget(storagePos);
        }
        test.succeed();
    }

    @GameTest(template = "combat", batch = "walker_cleanup_confirmed_plot", timeoutTicks = 40)
    public static void WalkerCleanupCompletesAfterPermanentRemovalAndLeavesReusedPlotIntact(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(5, 40, 5));
        var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        var container = SubLevelContainer.getContainer(level);
        var ownership = BodyOwnership.get(level);
        level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        var original = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), bounds);
        var plotCenter = original.getPlot().getCenterBlock();
        UUID death = UUID.randomUUID();
        UUID replacementOwner = UUID.randomUUID();
        ownership.bind(plotCenter, death);
        var emptyPlotJob = savedDeath(test, death, original.getUniqueId(), plotCenter);
        var reusedPlotJob = LifeLifecycle.load(emptyPlotJob.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        ServerSubLevel replacement = null;
        try {
            StructureRemovalApi.remove(level, original);
            test.assertTrue(StructureRemovalApi.permanentlyRemoved(container, original), "Native removal did not free the original plot");
            emptyPlotJob.tickDeaths(level.getServer());
            test.assertTrue(emptyPlotJob.finished(death), "A confirmed empty plot did not retire its saved attachment");

            level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
            replacement = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), bounds);
            test.assertTrue(replacement.getPlot().getCenterBlock().equals(plotCenter), "Fixture did not reuse the original plot");
            ownership.bind(plotCenter, replacementOwner);
            reusedPlotJob.tickDeaths(level.getServer());
            test.assertTrue(reusedPlotJob.finished(death), "A different resident did not retire the old attachment");
            test.assertTrue(container.getSubLevel(replacement.getUniqueId()) == replacement
                            && level.getBlockState(plotCenter).is(Blocks.GOLD_BLOCK)
                            && replacementOwner.equals(ownership.owner(plotCenter)),
                    "Old attachment cleanup changed the replacement structure or its ownership");
        } finally {
            if (container.getSubLevel(original.getUniqueId()) == original) StructureRemovalApi.remove(level, original);
            if (replacement != null && container.getSubLevel(replacement.getUniqueId()) == replacement) {
                StructureRemovalApi.remove(level, replacement);
            }
            if (death.equals(ownership.owner(plotCenter)) || replacementOwner.equals(ownership.owner(plotCenter))) {
                ownership.forget(plotCenter);
            }
        }
        test.succeed();
    }

    private static LifeLifecycle savedDeath(GameTestHelper test, UUID death, UUID body, BlockPos plotCenter) {
        var owner = new CompoundTag();
        owner.putUUID("Id", death);
        var owners = new ListTag();
        owners.add(owner);
        var attachment = new CompoundTag();
        attachment.putUUID("Body", body);
        attachment.put("Owners", owners);
        if (plotCenter != null) attachment.putLong("PlotCenter", plotCenter.asLong());
        var attachments = new ListTag();
        attachments.add(attachment);

        var job = new CompoundTag();
        job.putUUID("Id", death);
        job.putString("Dimension", test.getLevel().dimension().location().toString());
        job.putString("Loot", "minecraft:empty");
        job.putBoolean("Cleaned", true);
        job.putBoolean("Rewarded", true);
        job.putBoolean("PresentationPlayed", true);
        job.put("WalkerAttachments", attachments);
        var jobs = new ListTag();
        jobs.add(job);
        var state = new CompoundTag();
        state.put("Deaths", jobs);
        return LifeLifecycle.load(state, test.getLevel().registryAccess());
    }

    private static void assertPending(GameTestHelper test, LifeLifecycle lifecycle, UUID death, UUID body) {
        var state = lifecycle.save(new CompoundTag(), test.getLevel().registryAccess());
        var rows = state.getList("Deaths", Tag.TAG_COMPOUND).getCompound(0).getList("WalkerAttachments", Tag.TAG_COMPOUND);
        test.assertTrue(!lifecycle.finished(death) && rows.size() == 1 && rows.getCompound(0).getUUID("Body").equals(body),
                "Saved Walker attachment was discarded while its native body remains on disk");
    }
}
