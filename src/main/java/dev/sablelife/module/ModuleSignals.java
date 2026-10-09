package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Server-only ephemeral output at sensor anchors. Cleared on death, unload and reset. */
public final class ModuleSignals {
    public record Face(BlockPos pos, Direction outward) {}
    private static final Map<ServerLevel, Map<HeartBlockEntity, Map<BlockPos, Integer>>> LEVELS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> FACES = new WeakHashMap<>();
    // Wireless marker outputs are weak: a neighboring solid block must not relay to diagonal cells.
    private static final Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> WEAK_FACES = new WeakHashMap<>();
    public static boolean connects(ServerLevel level, BlockPos pos, Direction outward) {
        return connects(FACES, level, pos, outward) || connects(WEAK_FACES, level, pos, outward);
    }
    private static boolean connects(Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> source, ServerLevel level, BlockPos pos, Direction outward) {
        var cores = source.get(level); if (cores == null) return false;
        for (var entry : cores.entrySet()) if (!entry.getKey().isRemoved() && entry.getKey().operational()
                && entry.getValue().containsKey(new Face(pos, outward))) return true;
        return false;
    }
    public static int signal(ServerLevel level, BlockPos pos, Direction query) {
        return Math.max(directSignal(level, pos, query), faceSignal(WEAK_FACES, level, pos, query));
    }
    public static int directSignal(ServerLevel level, BlockPos pos, Direction query) {
        return Math.max(signal(level, pos), faceSignal(FACES, level, pos, query));
    }
    private static int faceSignal(Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> source, ServerLevel level, BlockPos pos, Direction query) {
        int value = 0;
        var cores = source.get(level);
        if (cores != null) for (var entry : cores.entrySet())
            if (!entry.getKey().isRemoved() && entry.getKey().operational())
                value = Math.max(value, entry.getValue().getOrDefault(new Face(pos, query.getOpposite()), 0));
        return value;
    }
    public static void updateFaces(HeartBlockEntity heart, Map<Face, Integer> next) {
        updateFaces(FACES, heart, next);
    }
    public static void updateWeakFaces(HeartBlockEntity heart, Map<Face, Integer> next) {
        updateFaces(WEAK_FACES, heart, next);
    }
    private static void updateFaces(Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> source, HeartBlockEntity heart, Map<Face, Integer> next) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var cores = source.computeIfAbsent(level, ignored -> new WeakHashMap<>());
        var old = cores.getOrDefault(heart, Map.of());
        if (old.equals(next)) return;
        var changed = new HashSet<Face>(old.keySet()); changed.addAll(next.keySet());
        if (next.isEmpty()) cores.remove(heart); else cores.put(heart, Map.copyOf(next));
        var positions = new HashSet<BlockPos>();
        for (var face : changed) if (!Objects.equals(old.get(face), next.get(face))) positions.add(face.pos());
        for (var pos : positions) if (level.hasChunkAt(pos)) level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
    }
    public static int signal(ServerLevel level, BlockPos pos) {
        var cores = LEVELS.get(level); if (cores == null) return 0;
        int value = 0;
        for (var entry : cores.entrySet()) if (!entry.getKey().isRemoved() && entry.getKey().operational())
            value = Math.max(value, entry.getValue().getOrDefault(pos, 0));
        return value;
    }
    public static void update(HeartBlockEntity heart, Map<BlockPos, Integer> next) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var cores = LEVELS.computeIfAbsent(level, ignored -> new WeakHashMap<>());
        var old = cores.getOrDefault(heart, Map.of());
        if (old.equals(next)) return;
        var changed = new HashSet<BlockPos>(old.keySet()); changed.addAll(next.keySet());
        if (next.isEmpty()) cores.remove(heart); else cores.put(heart, Map.copyOf(next));
        for (var pos : changed) if (!Objects.equals(old.get(pos), next.get(pos)) && level.hasChunkAt(pos)) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }
    public static void clear(HeartBlockEntity heart) { update(heart, Map.of()); updateFaces(heart, Map.of()); updateWeakFaces(heart, Map.of()); }
    private ModuleSignals() {}
}
