package dev.sablelife.compat.letitwalk;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.BodyProtection;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Optional death-only cleanup. Saved exact body IDs outlive the removed Walker controller. */
public final class WalkerDeathCleanup {
    private static final int MAX_BODIES = 1024;
    private static final int BODIES_PER_STEP = 16;

    public static ListTag capture(HeartBlockEntity heart) {
        try { return captureLoaded(heart); }
        catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().warn("Could not snapshot optional Let it Walk death attachments for {}", heart.creatureId(), failure);
            return new ListTag();
        }
    }

    private static ListTag captureLoaded(HeartBlockEntity heart) {
        var saved = new ListTag();
        if (!(heart.getLevel() instanceof ServerLevel level)) return saved;
        var bodies = WalkerLegDamage.deathAttachments(heart);
        if (bodies.size() > MAX_BODIES) {
            LogUtils.getLogger().warn("Skipping oversized Let it Walk death attachment snapshot for {}", heart.creatureId());
            return saved;
        }
        var ownership = BodyOwnership.get(level);
        for (var body : bodies) {
            var row = new CompoundTag();
            row.putUUID("Body", body.getUniqueId());
            var owners = new LinkedHashSet<UUID>();
            for (var holder : body.getPlot().getLoadedChunks())
                for (var key : ownershipRecords(ownership, holder.getChunk().getPos().toLong())) {
                    var pos = BlockPos.of(key);
                    if (Sable.HELPER.getContaining(level, pos) == body && ownership.owner(pos) != null) owners.add(ownership.owner(pos));
                }
            var ids = new ListTag();
            for (var id : owners) { var value = new CompoundTag(); value.putUUID("Id", id); ids.add(value); }
            row.put("Owners", ids);
            saved.add(row);
        }
        return saved;
    }

    /** Retains unloaded plots for retry; an ownership change relinquishes only that exact plot. */
    public static boolean step(ServerLevel level, ListTag saved) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        var ownership = BodyOwnership.get(level);
        int work = 0;
        boolean changed = false;
        int remaining = saved.size();
        for (int i = 0; !saved.isEmpty() && remaining-- > 0 && work < BODIES_PER_STEP;) {
            var row = saved.getCompound(i);
            if (!row.hasUUID("Body")) { saved.remove(i); changed = true; continue; }
            var found = container.getSubLevel(row.getUUID("Body"));
            if (found == null && container.getHoldingChunkMap().getHoldingSubLevel(row.getUUID("Body")) != null) {
                saved.remove(i); saved.add(row); changed = true; work++; continue;
            }
            if (found == null || found.isRemoved()) { saved.remove(i); changed = true; continue; }
            work++;
            if (!(found instanceof ServerSubLevel body) || !loaded(level, body)) { saved.remove(i); saved.add(row); changed = true; continue; }
            var allowed = new HashSet<UUID>();
            for (var value : row.getList("Owners", 10)) if (((CompoundTag) value).hasUUID("Id")) allowed.add(((CompoundTag) value).getUUID("Id"));
            var records = new ArrayList<BlockPos>();
            var cores = new ArrayList<HeartBlockEntity>();
            boolean changedOwner = false;
            for (var holder : body.getPlot().getLoadedChunks()) {
                for (var key : ownershipRecords(ownership, holder.getChunk().getPos().toLong())) {
                    var pos = BlockPos.of(key);
                    if (Sable.HELPER.getContaining(level, pos) != body) continue;
                    if (!allowed.contains(ownership.owner(pos))) changedOwner = true;
                    records.add(pos);
                }
                for (var entity : List.copyOf(holder.getChunk().getBlockEntities().values()))
                    if (entity instanceof HeartBlockEntity core && Sable.HELPER.getContaining(level, core.getBlockPos()) == body) cores.add(core);
            }
            if (changedOwner) {
                LogUtils.getLogger().warn("Leaving Let it Walk death attachment {} after its ownership changed", body.getUniqueId());
                saved.remove(i); changed = true; continue;
            }
            // An undefeated segment core is administrative removal, not a second boss kill/reward.
            // A segment already defeated independently keeps its existing death settlement.
            for (var core : cores) if (allowed.contains(core.creatureId())) core.expireWildEncounter();
            BodyProtection.CLEANING.set(BodyProtection.CLEANING.get() + 1);
            try { StructureRemovalApi.remove(level, body); }
            finally {
                BodyProtection.CLEANING.set(BodyProtection.CLEANING.get() - 1);
                if (body.isRemoved() || container.getSubLevel(body.getUniqueId()) == null)
                    for (var pos : records) if (allowed.contains(ownership.owner(pos))) ownership.forget(pos);
                dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
                LoadedCoreIndex.geometryChanged(level);
            }
            saved.remove(i); changed = true;
        }
        return changed;
    }

    private static boolean loaded(ServerLevel level, ServerSubLevel body) {
        var bounds = body.getPlot().getBoundingBox();
        if (bounds == null || body.getPlot().getLoadedChunks().isEmpty()) return false;
        long columns = (long) ((bounds.maxX() >> 4) - (bounds.minX() >> 4) + 1) * ((bounds.maxZ() >> 4) - (bounds.minZ() >> 4) + 1);
        if (columns > 1024) return false;
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }

    private static Set<Long> ownershipRecords(BodyOwnership ownership, long chunk) {
        return ownership.recordsInChunk(chunk);
    }

    private WalkerDeathCleanup() {}
}
