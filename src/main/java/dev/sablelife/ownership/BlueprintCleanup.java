package dev.sablelife.ownership;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.StructureRemovalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Retains rollback targets until Sable confirms permanent removal. Never loads a world chunk. */
public final class BlueprintCleanup extends SavedData {
    private static final String STORAGE = "sablelife_blueprint_cleanup";
    private static final String PLACED = "placed_sublevels";
    public static final String PLOT_CENTERS = "SableLifeRollbackPlots";
    private static final int JOBS_PER_TICK = 16;
    private static final int VISITS_PER_JOB = 16;
    private static final int BODY_VISITS_PER_TICK = 128;
    private static final int REMOVALS_PER_TICK = 8;
    private static final int RETRY_TICKS = 20;

    private record BodyKey(ResourceLocation level, UUID body) {}

    private static final class Job {
        final UUID id, owner;
        final ResourceLocation dimension;
        // Legacy jobs can supply only the body UUID, with no known plot position.
        final LinkedHashMap<UUID, BlockPos> bodies = new LinkedHashMap<>();
        long retryAt = Long.MIN_VALUE;

        Job(UUID id, UUID owner, ResourceLocation dimension) {
            this.id = id;
            this.owner = owner;
            this.dimension = dimension;
        }
    }

    private final LinkedHashMap<UUID, Job> jobs = new LinkedHashMap<>();
    private final Map<BodyKey, Set<UUID>> byBody = new HashMap<>();
    private final Map<UUID, Integer> byOwner = new HashMap<>();
    private final Set<ServerLevel> initialized = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private int budgetTick = Integer.MIN_VALUE;
    private int bodyVisits, removalAttempts;

    public static BlueprintCleanup get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BlueprintCleanup::new, BlueprintCleanup::load), STORAGE);
    }

    // Only placement addresses are needed after restart. Avoid retaining a second native progress record.
    static CompoundTag placement(CompoundTag progress) {
        var placed = new CompoundTag();
        var rows = progress.getList(PLACED, Tag.TAG_COMPOUND);
        if (!rows.isEmpty()) placed.put(PLACED, rows.copy());
        var plots = progress.getCompound(PLOT_CENTERS);
        if (!plots.isEmpty()) placed.put(PLOT_CENTERS, plots.copy());
        return placed;
    }

    static void rollback(ServerLevel level, UUID owner, ArenaRespawns.Build build) {
        request(level.getServer(), UUID.randomUUID(), level.dimension().location(), owner, build.bodies(), build.save());
    }

    static void request(MinecraftServer server, UUID id, ResourceLocation dimension, UUID owner,
                        Set<UUID> bodies, CompoundTag progress) {
        if (dimension == null) return;
        var data = get(server);
        if (data.jobs.containsKey(id)) return;
        var job = new Job(id, owner, dimension);
        for (var body : bodies) job.bodies.put(body, null);
        // Legacy Photomancy progress includes UUIDs but its block origin is a coordinate transform, not a plot address.
        for (var value : progress.getList(PLACED, Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            if (row.hasUUID("placed_uuid")) job.bodies.putIfAbsent(row.getUUID("placed_uuid"), null);
        }
        var plots = progress.getCompound(PLOT_CENTERS);
        job.bodies.replaceAll((body, unused) -> plots.contains(body.toString(), Tag.TAG_LONG)
                ? BlockPos.of(plots.getLong(body.toString())) : null);
        if (job.bodies.isEmpty() && owner == null) return;
        data.register(job);
        data.setDirty();
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level != null) {
            ArenaBuildTicks.hold(level, id, job.bodies.keySet());
            data.process(level, job);
        }
    }

    private void register(Job job) {
        jobs.put(job.id, job);
        if (job.owner != null) byOwner.merge(job.owner, 1, Integer::sum);
        for (var body : job.bodies.keySet()) {
            byBody.computeIfAbsent(new BodyKey(job.dimension, body), ignored -> new HashSet<>()).add(job.id);
        }
    }

    public static boolean retainsOwnership(MinecraftServer server, UUID owner) {
        return get(server).byOwner.containsKey(owner);
    }

    public static void permanentlyRemoved(ServerLevel level, UUID body) {
        var data = get(level.getServer());
        var key = new BodyKey(level.dimension().location(), body);
        var affected = data.byBody.remove(key);
        if (affected == null) return;
        for (var id : affected) {
            var job = data.jobs.get(id);
            if (job != null) job.bodies.remove(body);
        }
        ArenaBuildTicks.release(level, Set.of(body));
        data.setDirty();
    }

    public static void tick(ServerTickEvent.Pre event) {
        var server = event.getServer();
        var data = get(server);
        if (data.jobs.isEmpty()) return;
        // Recreate the transient tick holds once per level after a restart.
        for (var level : server.getAllLevels()) {
            if (data.initialized.add(level)) for (var job : data.jobs.values()) {
                if (job.dimension.equals(level.dimension().location())) ArenaBuildTicks.hold(level, job.id, job.bodies.keySet());
            }
        }
        data.refreshBudget(server);
        int visits = Math.min(JOBS_PER_TICK, data.jobs.size());
        for (int i = 0; i < visits && data.hasBudget() && !data.jobs.isEmpty(); i++) {
            var next = data.jobs.firstEntry();
            var job = next.getValue();
            data.jobs.putLast(next.getKey(), job);
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.dimension));
            if (level != null) data.process(level, job);
        }
    }

    private void refreshBudget(MinecraftServer server) {
        int now = server.getTickCount();
        if (now != budgetTick) {
            budgetTick = now;
            bodyVisits = removalAttempts = 0;
        }
    }

    private boolean hasBudget() {
        return bodyVisits < BODY_VISITS_PER_TICK && removalAttempts < REMOVALS_PER_TICK;
    }

    private void process(ServerLevel level, Job job) {
        if (level.getGameTime() < job.retryAt) return;
        refreshBudget(level.getServer());
        var container = SubLevelContainer.getContainer(level);
        int visits = Math.min(VISITS_PER_JOB, job.bodies.size());
        for (int i = 0; container != null && i < visits && hasBudget() && !job.bodies.isEmpty(); i++) {
            bodyVisits++;
            var entry = job.bodies.firstEntry();
            var id = entry.getKey();
            var origin = entry.getValue();
            job.bodies.putLast(id, origin);
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                if (origin == null) {
                    job.bodies.put(id, body.getPlot().getCenterBlock());
                    setDirty();
                }
                removalAttempts++;
                try {
                    StructureRemovalApi.remove(level, body);
                    if (StructureRemovalApi.permanentlyRemoved(container, body)) permanentlyRemoved(level, id);
                } catch (RuntimeException failure) {
                    job.retryAt = level.getGameTime() + RETRY_TICKS;
                    LogUtils.getLogger().warn("Blueprint rollback will retry for body {}", id, failure);
                    break;
                }
            } else if (StructureRemovalApi.permanentlyRemoved(container, id, origin)) {
                permanentlyRemoved(level, id);
            }
        }
        if (job.bodies.isEmpty()) finish(level, job);
    }

    private void finish(ServerLevel level, Job job) {
        if (job.owner != null) {
            var server = level.getServer();
            int otherJobs = byOwner.get(job.owner) - 1;
            if (otherJobs == 0) {
                CoreChunkLoading.release(server, job.owner);
                // A restored arena template may already have installed idle module references.
                BlueprintSpawns.retireIdleOwner(server, job.owner);
                ArenaRespawns.retireIdleOwner(server, job.owner);
                for (var dimension : server.getAllLevels()) BodyOwnership.get(dimension).release(job.owner);
                LifeLifecycle.get(server).cancel(job.owner);
                byOwner.remove(job.owner);
            } else {
                byOwner.put(job.owner, otherJobs);
            }
        }
        jobs.remove(job.id);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var rows = new ListTag();
        for (var job : jobs.values()) {
            var row = new CompoundTag();
            row.putUUID("Id", job.id);
            row.putString("Dimension", job.dimension.toString());
            if (job.owner != null) row.putUUID("Owner", job.owner);
            var targets = new ListTag();
            for (var entry : job.bodies.entrySet()) {
                var target = new CompoundTag();
                target.putUUID("Body", entry.getKey());
                if (entry.getValue() != null) target.putLong("Origin", entry.getValue().asLong());
                targets.add(target);
            }
            row.put("Bodies", targets);
            rows.add(row);
        }
        tag.put("Jobs", rows);
        return tag;
    }

    public static BlueprintCleanup load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new BlueprintCleanup();
        for (var value : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
            if (dimension == null || !row.hasUUID("Id") || data.jobs.containsKey(row.getUUID("Id"))) continue;
            var job = new Job(row.getUUID("Id"), row.hasUUID("Owner") ? row.getUUID("Owner") : null, dimension);
            for (var targetValue : row.getList("Bodies", Tag.TAG_COMPOUND)) {
                var target = (CompoundTag) targetValue;
                if (target.hasUUID("Body")) job.bodies.put(target.getUUID("Body"),
                        target.contains("Origin", Tag.TAG_LONG) ? BlockPos.of(target.getLong("Origin")) : null);
            }
            if (job.owner != null || !job.bodies.isEmpty()) data.register(job);
        }
        return data;
    }
}
