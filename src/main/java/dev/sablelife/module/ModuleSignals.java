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
    private static final Map<ServerLevel,Map<BlockPos,Map<HeartBlockEntity,Integer>>> POSITION_INDEX=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<Face,Map<HeartBlockEntity,Integer>>> FACE_INDEX=new WeakHashMap<>(),WEAK_INDEX=new WeakHashMap<>();
    private static <K> void indexUpdate(Map<ServerLevel,Map<K,Map<HeartBlockEntity,Integer>>> index,ServerLevel level,HeartBlockEntity heart,Map<K,Integer> old,Map<K,Integer> next){
        var positions=index.computeIfAbsent(level,k->new java.util.HashMap<>());var keys=new HashSet<>(old.keySet());keys.addAll(next.keySet());
        for(var key:keys){
            if(Objects.equals(old.get(key),next.get(key)))continue;
            var publishers=positions.computeIfAbsent(key,k->new WeakHashMap<>());
            if(next.containsKey(key))publishers.put(heart,next.get(key));else publishers.remove(heart);
            if(publishers.isEmpty())positions.remove(key);
        }
        if(positions.isEmpty())index.remove(level);
    }
    private static <K> int indexedSignal(Map<ServerLevel,Map<K,Map<HeartBlockEntity,Integer>>> index,ServerLevel level,K key){
        var positions=index.get(level);var publishers=positions==null?null:positions.get(key);if(publishers==null)return 0;
        int value=0;for(var entry:publishers.entrySet())if(!entry.getKey().isRemoved()&&entry.getKey().operational())value=Math.max(value,entry.getValue());return value;
    }
    public static boolean connects(ServerLevel level, BlockPos pos, Direction outward) {
        return connects(FACES, level, pos, outward) || connects(WEAK_FACES, level, pos, outward);
    }
    private static boolean connects(Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> source, ServerLevel level, BlockPos pos, Direction outward) {
        var index=source==FACES?FACE_INDEX:WEAK_INDEX;var positions=index.get(level);
        var publishers=positions==null?null:positions.get(new Face(pos,outward));
        if(publishers!=null)for(var heart:publishers.keySet())if(!heart.isRemoved()&&heart.operational())return true;
        return false;
    }

    public static int signal(ServerLevel level, BlockPos pos, Direction query) {
        return Math.max(directSignal(level, pos, query), faceSignal(WEAK_FACES, level, pos, query));
    }
    public static int directSignal(ServerLevel level, BlockPos pos, Direction query) {
        return Math.max(signal(level, pos), faceSignal(FACES, level, pos, query));
    }
    private static int faceSignal(Map<ServerLevel, Map<HeartBlockEntity, Map<Face, Integer>>> source, ServerLevel level, BlockPos pos, Direction query) {
        return indexedSignal(source==FACES?FACE_INDEX:WEAK_INDEX,level,new Face(pos,query.getOpposite()));
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
        indexUpdate(source==FACES?FACE_INDEX:WEAK_INDEX,level,heart,old,next);
        var positions = new HashSet<BlockPos>();
        for (var face : changed) if (!Objects.equals(old.get(face), next.get(face))) positions.add(face.pos());
        for (var pos : positions) if (level.getServer().isRunning() && level.hasChunkAt(pos)) level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
    }
    public static int signal(ServerLevel level, BlockPos pos) {
        return indexedSignal(POSITION_INDEX,level,pos);
    }

    public static void update(HeartBlockEntity heart, Map<BlockPos, Integer> next) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var cores = LEVELS.computeIfAbsent(level, ignored -> new WeakHashMap<>());
        var old = cores.getOrDefault(heart, Map.of());
        if (old.equals(next)) return;
        var changed = new HashSet<BlockPos>(old.keySet()); changed.addAll(next.keySet());
        if (next.isEmpty()) cores.remove(heart); else cores.put(heart, Map.copyOf(next));
        indexUpdate(POSITION_INDEX,level,heart,old,next);
        for (var pos : changed) if (!Objects.equals(old.get(pos), next.get(pos)) && level.getServer().isRunning() && level.hasChunkAt(pos)) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }
    public static void clear(HeartBlockEntity heart) { update(heart, Map.of()); updateFaces(heart, Map.of()); updateWeakFaces(heart, Map.of()); }
    /** Teardown must not turn off blocks after Sable has removed their native rigid bodies. */
    public static void forget(HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        forget(LEVELS,POSITION_INDEX,level,heart);forget(FACES,FACE_INDEX,level,heart);forget(WEAK_FACES,WEAK_INDEX,level,heart);
    }
    private static <K> void forget(Map<ServerLevel,Map<HeartBlockEntity,Map<K,Integer>>> source,Map<ServerLevel,Map<K,Map<HeartBlockEntity,Integer>>> index,ServerLevel level,HeartBlockEntity heart) {
        var cores=source.get(level);if(cores==null)return;var old=cores.remove(heart);if(old!=null)indexUpdate(index,level,heart,old,Map.of());if(cores.isEmpty())source.remove(level);
    }
    private ModuleSignals() {}
}
