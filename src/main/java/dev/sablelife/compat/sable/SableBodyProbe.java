package dev.sablelife.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Live connectivity queries. Detached fragments cannot forward damage without a connected Heart. */
public final class SableBodyProbe {
    private SableBodyProbe() {}
    private record Graph(List<UUID> bodies, java.util.Set<UUID> membership, List<HeartBlockEntity> hearts) {}
    private static final class Cache { long tick=Long.MIN_VALUE; final java.util.Map<UUID,Graph> graphs=new java.util.HashMap<>(); }
    private static final java.util.Map<net.minecraft.server.level.ServerLevel,Cache> caches=new java.util.WeakHashMap<>();
    public static void invalidate(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            var cache=caches.get(server);if(cache!=null)cache.graphs.clear();
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(server);
        }
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { caches.clear(); }
    private static Graph graph(Level level, SubLevel root) {
        Cache cache=null;
        if(level instanceof net.minecraft.server.level.ServerLevel server) {
            cache=caches.computeIfAbsent(server,ignored->new Cache());
            if(cache.tick!=level.getGameTime()){cache.tick=level.getGameTime();cache.graphs.clear();}
            var old=cache.graphs.get(root.getUniqueId());if(old!=null)return old;
        }
        var connected=SubLevelHelper.getConnectedChain(root);
        var hearts=new LinkedHashSet<HeartBlockEntity>();
        for(var body:connected)if(!body.isRemoved())for(var chunk:body.getPlot().getLoadedChunks())
            for(var entity:chunk.getChunk().getBlockEntities().values())if(entity instanceof HeartBlockEntity heart&&!heart.isRemoved())hearts.add(heart);
        var ids=connected.stream().filter(b->!b.isRemoved()).map(SubLevel::getUniqueId).toList();
        var result=new Graph(ids,java.util.Set.copyOf(ids),List.copyOf(hearts));
        if(cache!=null)for(var id:ids)cache.graphs.put(id,result);
        return result;
    }
    public static boolean connected(Level level, BlockPos core, UUID body) {
        var root=Sable.HELPER.getContaining(level,core);
        return root!=null&&!root.isRemoved()&&graph(level,root).membership.contains(body);
    }

    public static Snapshot inspect(Level level, BlockPos position) {
        SubLevel root = Sable.HELPER.getContaining(level, position);
        if (root == null || root.isRemoved()) return new Snapshot(null, List.of(), 0);
        var graph=graph(level,root);
        int otherHearts=(int)graph.hearts.stream().filter(h->!h.isRemoved()&&!h.getBlockPos().equals(position)).count();
        return new Snapshot(root.getUniqueId(),graph.bodies,otherHearts);
    }

    public record Snapshot(UUID rootId, List<UUID> connectedBodies, int otherHearts) {
        public boolean assembled() { return rootId != null; }
    }

    public static @Nullable HeartBlockEntity findActivatedHeart(Level level, BlockPos hitBlock) {
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && dev.sablelife.ownership.BodyOwnership.get(server).part(hitBlock) != null) return null;
        if (level.getBlockState(hitBlock).isAir()) return null;
        var hearts = findConnectedHearts(level, hitBlock);
        return hearts.size() == 1 && hearts.getFirst().health().state() != HeartState.DORMANT ? hearts.getFirst() : null;
    }

    /** All cores in the live connection graph, including conflicting or dead cores that an author may need to reset. */
    public static List<HeartBlockEntity> findConnectedHearts(Level level, BlockPos hitBlock) {
        SubLevel root = Sable.HELPER.getContaining(level, hitBlock);
        if (root == null || root.isRemoved()) return List.of();
        return graph(level,root).hearts.stream().filter(h->!h.isRemoved()).toList();
    }

    public static Vec3 toWorld(Level level, Vec3 position) {
        SubLevel body = Sable.HELPER.getContaining(level, position);
        return body == null ? position : body.logicalPose().transformPosition(position);
    }
}
