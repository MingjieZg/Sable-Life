package dev.sablelife.ownership;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.*;

/** Loaded core registration and a tick-local spatial broad phase. No game entities are spawned. */
public final class LoadedCoreIndex {
    private record Cell(int x, int y, int z) {}
    private static final class State {
        final Map<BlockPos, WeakReference<HeartBlockEntity>> cores = new HashMap<>();
        final Map<UUID, BlockPos> identities = new HashMap<>();
        final Map<UUID, Set<HeartBlockEntity>> remoteIdentities = new HashMap<>();
        final Map<HeartBlockEntity, AABB> bounds = new WeakHashMap<>();
        final Map<Cell, Set<HeartBlockEntity>> cells = new HashMap<>();
        final List<HeartBlockEntity> oversized = new ArrayList<>();
        long tick = Long.MIN_VALUE;
        boolean dirty = true;
        int rebuilds;
    }
    private static final Map<ServerLevel, State> levels = new WeakHashMap<>();
    private static State state(ServerLevel level) { return levels.computeIfAbsent(level, ignored -> new State()); }
    public static void observe(HeartBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level) || core.isRemoved()) return;
        var s = state(level); var old = s.cores.get(core.getBlockPos());
        if (old == null || old.get() != core || !core.getBlockPos().equals(s.identities.get(core.creatureId()))) {
            s.identities.values().removeIf(core.getBlockPos()::equals);
            s.cores.put(core.getBlockPos(), new WeakReference<>(core));
            s.identities.put(core.creatureId(), core.getBlockPos()); s.dirty = true;
        }
    }
    public static void remove(HeartBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level)) return;
        var s = levels.get(level); if (s == null) return;
        forgetRemote(core);
        var ref = s.cores.get(core.getBlockPos());
        if (ref != null && ref.get() == core) { s.cores.remove(core.getBlockPos()); s.identities.values().removeIf(core.getBlockPos()::equals); s.dirty = true; }
        s.bounds.remove(core);s.cells.clear();s.oversized.clear();
    }
    private static boolean valid(ServerLevel level, HeartBlockEntity core) {
        return core != null && !core.isRemoved() && core.getLevel() == level && level.hasChunkAt(core.getBlockPos())
                && level.getBlockEntity(core.getBlockPos()) == core;
    }
    public static boolean isLoadedCore(ServerLevel level, HeartBlockEntity core) {
        if (core.isRemoved() || core.getLevel() != level) return false;
        var pos = core.getBlockPos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(pos,
                net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == core;
    }
    public static List<HeartBlockEntity> all(ServerLevel level) {
        var s = state(level); var result = new ArrayList<HeartBlockEntity>();
        s.cores.entrySet().removeIf(e -> {
            var core = e.getValue().get();
            if (!valid(level, core)) { s.dirty = true; s.identities.values().removeIf(e.getKey()::equals); return true; }
            result.add(core); return false;
        });
        return List.copyOf(result);
    }
    public static HeartBlockEntity find(ServerLevel level, UUID id) {
        var s = state(level); var pos = s.identities.get(id); var ref = pos == null ? null : s.cores.get(pos);
        var core = ref == null ? null : ref.get();
        return valid(level, core) && id.equals(core.creatureId()) ? core : null;
    }
    public static void observeRemote(HeartBlockEntity core) {
        if (core.remoteId() == null || !(core.getLevel() instanceof ServerLevel level) || core.isRemoved()) return;
        state(level).remoteIdentities.computeIfAbsent(core.remoteId(), ignored ->
                Collections.newSetFromMap(new WeakHashMap<>())).add(core);
    }
    public static void forgetRemote(HeartBlockEntity core) {
        if (core.remoteId() == null || !(core.getLevel() instanceof ServerLevel level)) return;
        var index = levels.get(level);
        if (index == null) return;
        var entries = index.remoteIdentities.get(core.remoteId());
        if (entries != null && entries.remove(core) && entries.isEmpty()) index.remoteIdentities.remove(core.remoteId());
    }
    public static HeartBlockEntity findRemote(ServerLevel level, UUID id) {
        var index = levels.get(level);
        var entries = index == null ? null : index.remoteIdentities.get(id);
        if (entries == null) return null;
        HeartBlockEntity match = null;
        for (var core : entries) {
            if (!id.equals(core.remoteId()) || !isLoadedCore(level, core)) continue;
            if (match != null) return null;
            match = core;
        }
        return match;
    }
    public static void geometryChanged(ServerLevel level) { var s = levels.get(level); if (s != null) { s.dirty = true; s.bounds.clear(); } }
    public static AABB bounds(HeartBlockEntity core) {
        var level = (ServerLevel)core.getLevel(); var s = state(level);
        if (s.tick != level.getGameTime()) { s.bounds.clear(); s.tick = level.getGameTime(); s.dirty = true; }
        return s.bounds.computeIfAbsent(core, LoadedCoreIndex::calculateBounds);
    }
    private static AABB calculateBounds(HeartBlockEntity core) {
        var center = dev.sablelife.compat.sable.SableBodyProbe.toWorld(core.getLevel(), core.getBlockPos().getCenter());
        var box = new AABB(center, center).inflate(.5);
        var root = Sable.HELPER.getContaining(core.getLevel(), core.getBlockPos());
        if (root != null) for (var body : SubLevelHelper.getConnectedChain(root)) {
            if (body.isRemoved()) continue;
            var b = body.getPlot().getBoundingBox(); if (b == null) continue;
            for (int x=0;x<2;x++) for (int y=0;y<2;y++) for (int z=0;z<2;z++) {
                var corner=body.logicalPose().transformPosition(new Vec3(x==0?b.minX():b.maxX()+1.,y==0?b.minY():b.maxY()+1.,z==0?b.minZ():b.maxZ()+1.));
                box=box.minmax(new AABB(corner,corner));
            }
        }
        return box;
    }
    private static int cell(double value) { return (int)Math.floor(value / 64); }
    private static long cellCount(AABB box) {
        return (long)(cell(box.maxX)-cell(box.minX)+1)*(cell(box.maxY)-cell(box.minY)+1)*(cell(box.maxZ)-cell(box.minZ)+1);
    }
    public static List<HeartBlockEntity> query(ServerLevel level, AABB box) {
        var s = state(level);
        if (s.tick != level.getGameTime()) { s.bounds.clear(); s.tick = level.getGameTime(); s.dirty = true; }
        if (s.dirty) {
            s.cells.clear(); s.oversized.clear();
            for (var core : all(level)) {
                if (core.health().state()!=HeartState.ALIVE || !core.creatureId().equals(BodyOwnership.get(level).owner(core.getBlockPos()))) continue;
                var b = bounds(core);
                if (cellCount(b)>4096) { s.oversized.add(core); continue; }
                for (int x=cell(b.minX);x<=cell(b.maxX);x++) for (int y=cell(b.minY);y<=cell(b.maxY);y++) for (int z=cell(b.minZ);z<=cell(b.maxZ);z++)
                    s.cells.computeIfAbsent(new Cell(x,y,z), ignored -> new HashSet<>()).add(core);
            }
            s.dirty=false; s.rebuilds++;
        }
        var candidates = new HashSet<>(s.oversized);
        if (cellCount(box)>4096) s.cells.values().forEach(candidates::addAll);
        else for (int x=cell(box.minX);x<=cell(box.maxX);x++) for (int y=cell(box.minY);y<=cell(box.maxY);y++) for (int z=cell(box.minZ);z<=cell(box.maxZ);z++)
            candidates.addAll(s.cells.getOrDefault(new Cell(x,y,z),Set.of()));
        return candidates.stream().filter(c -> valid(level,c)&&c.health().state()==HeartState.ALIVE&&c.creatureId().equals(BodyOwnership.get(level).owner(c.getBlockPos()))&&box.intersects(bounds(c))).toList();
    }
    public static int rebuilds(ServerLevel level) { return state(level).rebuilds; }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { levels.clear(); }
    private LoadedCoreIndex() {}
}
