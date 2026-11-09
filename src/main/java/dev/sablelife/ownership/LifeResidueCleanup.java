package dev.sablelife.ownership;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Bounded administrator maintenance for tracked blocks, with no chunk loading. */
public final class LifeResidueCleanup {
    private enum Match { NONE, UNKNOWN, RECORD, FRAGMENT }
    private static final Map<MinecraftServer, Job> JOBS = new IdentityHashMap<>();
    private record DeletedPlot(ServerLevel level, int x, int z, int shift) {
        boolean contains(long chunk) {
            return (net.minecraft.world.level.ChunkPos.getX(chunk) >> shift) == x
                    && (net.minecraft.world.level.ChunkPos.getZ(chunk) >> shift) == z;
        }
    }
    private static final Map<MinecraftServer, LinkedHashSet<DeletedPlot>> DELETED_PLOTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Job> AUTO_JOBS = new IdentityHashMap<>();
    private static final int VISITS_PER_TICK = 2048;
    private static final int MAX_BLOCKS_PER_TICK = 128;

    public static void commands(RegisterCommandsEvent event) {
        var cleanup = Commands.literal("cleanup").requires(source -> source.hasPermission(2));
        for (boolean apply : new boolean[]{false, true}) {
            cleanup.then(Commands.literal(apply ? "apply" : "scan")
                    .then(Commands.literal("all").executes(ctx -> start(ctx.getSource(), apply, 0)))
                    .then(Commands.literal("radius").then(Commands.argument("blocks", IntegerArgumentType.integer(1, 1024))
                            .executes(ctx -> start(ctx.getSource(), apply, IntegerArgumentType.getInteger(ctx, "blocks"))))));
        }
        event.getDispatcher().register(Commands.literal("sablelife").then(cleanup));
    }

    private static int start(CommandSourceStack source, boolean apply, int radius) {
        var server = source.getServer();
        if (JOBS.containsKey(server)) {
            source.sendFailure(Component.literal("A Sable Life cleanup job is already running."));
            return 0;
        }
        JOBS.put(server, new Job(source, apply, radius));
        source.sendSuccess(() -> Component.literal(apply ? "Sable Life cleanup started." : "Sable Life residue scan started."), false);
        return 1;
    }

    public static void permanentlyRemoved(ServerLevel level, int plotX, int plotZ, int shift, List<HeartBlockEntity> cores) {
        var server = level.getServer();
        var lifecycle = LifeLifecycle.get(server);
        for (var core : cores) {
            var owner = core.creatureId();
            if (!core.isRemoved() || lifecycle.isDying(owner)) continue;
            BlueprintSpawns.release(core);
            ArenaRespawns.release(core);
            CoreChunkLoading.release(core);
            lifecycle.cancel(owner);
            lifecycle.rememberExternalRemoval(owner);
            retireProtection(server, owner);
        }
        DELETED_PLOTS.computeIfAbsent(server, ignored -> new LinkedHashSet<>()).add(new DeletedPlot(level, plotX, plotZ, shift));
    }

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var job = JOBS.get(server);
        if (job != null) {
            if (job.step()) {
                JOBS.remove(server);
                job.report();
            }
            return;
        }
        var automatic = AUTO_JOBS.get(server);
        var plots = DELETED_PLOTS.get(server);
        if (automatic == null && plots != null && !plots.isEmpty()) {
            var iterator = plots.iterator();
            automatic = new Job(server.createCommandSourceStack(), true, 0, iterator.next());
            iterator.remove();
            if (plots.isEmpty()) DELETED_PLOTS.remove(server);
            AUTO_JOBS.put(server, automatic);
        }
        if (automatic != null && automatic.step()) AUTO_JOBS.remove(server);
    }

    public static void stopped(ServerStoppedEvent event) {
        JOBS.remove(event.getServer());
        AUTO_JOBS.remove(event.getServer());
        DELETED_PLOTS.remove(event.getServer());
    }

    private static final class Job {
        final CommandSourceStack source;
        final boolean apply;
        final Vec3 origin;
        final double radiusSquared;
        final Iterator<ServerLevel> levels;
        final DeletedPlot deletedPlot;
        Iterator<Long> chunks = Collections.emptyIterator();
        Iterator<Long> positions = Collections.emptyIterator();
        ServerLevel level;
        BodyOwnership ownership;
        long records, fragments, unknown, failed;

        Job(CommandSourceStack source, boolean apply, int radius) {
            this(source, apply, radius, null);
        }

        Job(CommandSourceStack source, boolean apply, int radius, DeletedPlot deletedPlot) {
            this.source = source;
            this.apply = apply;
            this.deletedPlot = deletedPlot;
            origin = SableBodyProbe.toWorld(source.getLevel(), source.getPosition());
            radiusSquared = (double) radius * radius;
            var dimensions = new ArrayList<ServerLevel>();
            if (deletedPlot != null) dimensions.add(deletedPlot.level());
            else if (radius == 0) source.getServer().getAllLevels().forEach(dimensions::add);
            else dimensions.add(source.getLevel());
            levels = dimensions.iterator();
        }

        boolean step() {
            int writes = Math.min(MAX_BLOCKS_PER_TICK, SableLifeServerConfig.deathCleanupBlocksPerTick());
            int visited = 0;
            while (visited++ < VISITS_PER_TICK && writes > 0) {
                if (!positions.hasNext()) {
                    if (chunks.hasNext()) positions = new ArrayList<>(ownership.chunkRecords(chunks.next())).iterator();
                    else if (levels.hasNext()) {
                        level = levels.next();
                        ownership = BodyOwnership.get(level);
                        chunks = deletedPlot == null ? new ArrayList<>(ownership.trackedChunks()).iterator()
                                : ownership.trackedChunks().stream().filter(deletedPlot::contains).toList().iterator();
                    } else return true;
                    continue;
                }
                var pos = BlockPos.of(positions.next());
                var owner = ownership.owner(pos);
                if (owner == null) continue;
                Match match = deletedPlot == null ? classify(level, ownership, pos, owner, origin, radiusSquared)
                        : vacantPlot(level, pos) ? Match.RECORD : Match.NONE;
                switch (match) {
                    case RECORD -> {
                        records++;
                        if (apply) { ownership.forget(pos); retireProtection(level.getServer(), owner); writes--; }
                    }
                    case FRAGMENT -> {
                        if (!apply) fragments++;
                        else {
                            writes--;
                            if (BodyOwnership.eraseOwnedBlock(level, pos)) { ownership.forget(pos); retireProtection(level.getServer(), owner); fragments++; }
                            else failed++;
                        }
                    }
                    case UNKNOWN -> unknown++;
                    case NONE -> { }
                }
            }
            return false;
        }

        void report() {
            source.sendSuccess(() -> Component.literal((apply ? "Cleanup complete" : "Scan complete")
                    + " | Empty ownership records: " + records + " | Detached blocks: " + fragments
                    + " | Unverified or unavailable positions: " + unknown + (failed > 0 ? " | Failed removals: " + failed : "")), false);
        }
    }

    private static Match classify(ServerLevel level, BodyOwnership ownership, BlockPos pos, UUID owner,
                                  Vec3 origin, double radiusSquared) {
        var lifecycle = LifeLifecycle.get(level.getServer());
        var part = ownership.part(pos);
        if (lifecycle.isDying(owner) || part != null && lifecycle.isDying(part)) return Match.NONE;
        var container = SubLevelContainer.getContainer(level);
        var body = Sable.HELPER.getContaining(level, pos);
        if (container != null && container.inBounds(pos) && (body == null || body.isRemoved())) {
            return radiusSquared == 0 && vacantPlot(level, pos) ? Match.RECORD : Match.UNKNOWN;
        }
        var point = body == null ? pos.getCenter() : body.logicalPose().transformPosition(pos.getCenter());
        if (radiusSquared > 0 && point.distanceToSqr(origin) > radiusSquared) return Match.NONE;
        if (!level.hasChunkAt(pos)) return Match.UNKNOWN;
        if (level.getBlockState(pos).isAir()) return Match.RECORD;
        if (level.getBlockEntity(pos) instanceof HeartBlockEntity) return Match.NONE;
        var core = ownership.loadedHeart(level, owner);
        if (body != null && !SableBodyProbe.findConnectedHearts(level, pos).isEmpty()) return Match.NONE;
        if (core == null) {
            if (!lifecycle.externallyRemoved(owner)) return Match.UNKNOWN;
            for (var dimension : level.getServer().getAllLevels())
                if (LoadedCoreIndex.find(dimension, owner) != null) return Match.UNKNOWN;
        } else {
            if (core.health().state() != HeartState.ALIVE || !SableBodyProbe.inspect(level, core.getBlockPos()).assembled()) return Match.UNKNOWN;
            if (body != null && SableBodyProbe.connected(level, core.getBlockPos(), body.getUniqueId())) return Match.NONE;
        }
        return Match.FRAGMENT;
    }

    private static boolean vacantPlot(ServerLevel level, BlockPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return false;
        var body = Sable.HELPER.getContaining(level, pos);
        if (body != null && !body.isRemoved()) return false;
        int shift = 4 + container.getLogPlotSize();
        int x = (pos.getX() >> shift) - container.getOrigin().x;
        int z = (pos.getZ() >> shift) - container.getOrigin().y;
        // Sable's occupancy includes unloaded plots.
        return !container.getOccupancy().get(container.getIndex(x, z));
    }

    private static void retireProtection(MinecraftServer server, UUID owner) {
        for (var dimension : server.getAllLevels()) {
            if (BodyOwnership.get(dimension).hasOwned(owner) || LoadedCoreIndex.find(dimension, owner) != null) return;
        }
        var lifecycle = LifeLifecycle.get(server);
        lifecycle.setBreakable(owner, true);
        lifecycle.setExplodable(owner, true);
        lifecycle.setInteractionAllowed(owner, false);
        lifecycle.forgetExternalRemoval(owner);
    }

    private LifeResidueCleanup() {}
}
