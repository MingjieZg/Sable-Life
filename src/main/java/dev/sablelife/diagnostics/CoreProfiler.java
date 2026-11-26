package dev.sablelife.diagnostics;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.CreatureNames;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Bounded server-thread timing for a short diagnostics session. The inactive path only checks the session. */
@EventBusSubscriber(modid = SableLife.MOD_ID)
public final class CoreProfiler {
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 60;
    public static final int MAX_CORES = 1024;

    public enum Work { SENSOR_SCAN, BLOCK_SCAN, BLOCK_USE, HOMING_PREFLIGHT, HOMING_DEPLOY, MOVE_SELECT, MOVE_START }

    public record Report(long elapsedNanos, int ticks, List<CoreSample> cores, boolean truncated) {}
    public record CoreSample(UUID id, String name, long totalNanos, long peakNanos, Location peak, Location last,
                             Map<Work, WorkSample> work) {}
    public record WorkSample(long count, long nanos) {}
    public record Location(ResourceLocation dimension, Vec3 position, long gameTime) {}
    public record Located(Location location, boolean current) {}

    private static final Comparator<CoreSample> REPORT_ORDER =
            Comparator.comparingLong(CoreSample::totalNanos).reversed().thenComparing(CoreSample::id);

    private record Completed(MinecraftServer server, Report report) {}
    private static Session recording;
    private static Completed completed;

    private static final class WorkTotals {
        long count;
        long nanos;
    }

    private static final class CoreTotals {
        final UUID id;
        final Map<Work, WorkTotals> work = new EnumMap<>(Work.class);
        String name;
        long totalNanos;
        long peakNanos;
        long currentTickNanos;
        int currentTick;
        Location peak;
        Location last;

        CoreTotals(UUID id) {
            this.id = id;
        }

        void addCoreTick(int tick, long elapsed, Location location) {
            if (currentTick != tick) {
                currentTick = tick;
                currentTickNanos = 0;
            }
            currentTickNanos = add(currentTickNanos, elapsed);
            totalNanos = add(totalNanos, elapsed);
            if (location != null) last = location;
            if (currentTickNanos > peakNanos || peakNanos == 0 && peak == null) {
                peakNanos = currentTickNanos;
                peak = location;
            }
        }

        CoreSample snapshot() {
            var samples = new EnumMap<Work, WorkSample>(Work.class);
            work.forEach((type, totals) -> samples.put(type, new WorkSample(totals.count, totals.nanos)));
            return new CoreSample(id, CreatureNames.formatted(name).getString(), totalNanos, peakNanos, peak, last,
                    Collections.unmodifiableMap(samples));
        }
    }

    private static final class Session {
        final MinecraftServer server;
        final long started;
        final long deadline;
        final Map<UUID, CoreTotals> cores = new HashMap<>();
        int ticks;
        int countedTick;
        boolean truncated;

        Session(MinecraftServer server, long started, int seconds) {
            this.server = server;
            this.started = started;
            deadline = started + TimeUnit.SECONDS.toNanos(seconds);
        }

        boolean expired(long now) {
            return now - deadline >= 0;
        }

        void observeTick() {
            int tick = server.getTickCount();
            if (ticks == 0 || countedTick != tick) {
                countedTick = tick;
                ticks++;
            }
        }

        CoreTotals core(HeartBlockEntity heart) {
            var id = heart.creatureId();
            var totals = cores.get(id);
            if (totals == null) {
                if (cores.size() >= MAX_CORES) {
                    truncated = true;
                    return null;
                }
                totals = new CoreTotals(id);
                cores.put(id, totals);
            }
            totals.name = heart.creatureName();
            return totals;
        }

        Report snapshot(long now) {
            var samples = cores.values().stream().map(CoreTotals::snapshot).sorted(REPORT_ORDER).toList();
            return new Report(Math.max(0, now - started), ticks, samples, truncated);
        }
    }

    public static boolean start(MinecraftServer server, int seconds) {
        if (recording != null || !server.isSameThread() || seconds < MIN_SECONDS || seconds > MAX_SECONDS) return false;
        recording = new Session(server, System.nanoTime(), seconds);
        return true;
    }

    public static boolean stop(MinecraftServer server) {
        var session = session(server);
        if (session == null) return false;
        finish(session, System.nanoTime());
        return true;
    }

    public static boolean active(MinecraftServer server) {
        return session(server) != null;
    }

    public static Report report(MinecraftServer server) {
        var session = session(server);
        if (session != null) return session.snapshot(System.nanoTime());
        var result = completed;
        return result != null && result.server() == server && server.isSameThread() ? result.report() : null;
    }

    /** Returns zero when no new span should start. Inactive instrumentation does not read the clock. */
    public static long begin(MinecraftServer server) {
        var session = session(server);
        if (session == null) return 0;
        long now = System.nanoTime();
        return session.expired(now) ? 0 : now == 0 ? 1 : now;
    }

    public static void endCoreTick(HeartBlockEntity heart, long started) {
        if (started == 0 || !(heart.getLevel() instanceof ServerLevel level)) return;
        var session = session(level.getServer());
        if (session == null || started - session.started < 0) return;
        long elapsed = System.nanoTime() - started;
        session.observeTick();
        var totals = session.core(heart);
        if (totals != null) totals.addCoreTick(session.countedTick, elapsed, location(level, heart));
    }

    public static void endWork(HeartBlockEntity heart, Work work, long started, long count) {
        if (started == 0 || work == null || !(heart.getLevel() instanceof ServerLevel level)) return;
        var session = session(level.getServer());
        if (session == null || started - session.started < 0) return;
        long elapsed = System.nanoTime() - started;
        session.observeTick();
        var totals = session.core(heart);
        if (totals == null) return;
        if (totals.last == null) totals.last = location(level, heart);
        var workTotals = totals.work.computeIfAbsent(work, ignored -> new WorkTotals());
        workTotals.count = add(workTotals.count, count);
        workTotals.nanos = add(workTotals.nanos, elapsed);
    }

    /** Finds an indexed loaded core without requesting a chunk, or returns its last sampled position. */
    public static Located locate(MinecraftServer server, UUID id) {
        if (!server.isSameThread() || id == null) return null;
        for (var level : server.getAllLevels()) {
            var heart = LoadedCoreIndex.findLoaded(level, id);
            if (heart == null) continue;
            var current = location(level, heart);
            if (current != null) return new Located(current, true);
        }
        var session = session(server);
        if (session != null) {
            var totals = session.cores.get(id);
            if (totals != null && totals.last != null) return new Located(totals.last, false);
        }
        var result = completed;
        if (result != null && result.server() == server) {
            for (var core : result.report().cores()) {
                if (core.id().equals(id) && core.last() != null) return new Located(core.last(), false);
            }
        }
        return null;
    }

    private static Location location(ServerLevel level, HeartBlockEntity heart) {
        if (heart.isRemoved()) return null;
        var point = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
        return new Location(level.dimension().location(), point, level.getGameTime());
    }

    private static Session session(MinecraftServer server) {
        var session = recording;
        return session != null && session.server == server && server.isSameThread() ? session : null;
    }

    private static void finish(Session session, long now) {
        completed = new Completed(session.server, session.snapshot(now));
        recording = null;
    }

    private static long add(long sum, long amount) {
        return sum + Math.min(Math.max(0, amount), Long.MAX_VALUE - sum);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var session = session(event.getServer());
        if (session == null) return;
        session.observeTick();
        long now = System.nanoTime();
        if (session.expired(now)) finish(session, now);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (recording != null && recording.server == event.getServer()) recording = null;
        if (completed != null && completed.server() == event.getServer()) completed = null;
    }

    private CoreProfiler() {}
}
