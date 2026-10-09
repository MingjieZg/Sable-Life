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

    public static Snapshot inspect(Level level, BlockPos position) {
        SubLevel root = Sable.HELPER.getContaining(level, position);
        if (root == null) return new Snapshot(null, List.of(), 0);
        var connected = SubLevelHelper.getConnectedChain(root);
        int otherHearts = 0;
        for (SubLevel body : connected) {
            for (var chunk : body.getPlot().getLoadedChunks()) {
                for (var blockEntity : chunk.getChunk().getBlockEntities().values()) {
                    if (blockEntity instanceof HeartBlockEntity && !blockEntity.getBlockPos().equals(position)) otherHearts++;
                }
            }
        }
        return new Snapshot(root.getUniqueId(), connected.stream().map(SubLevel::getUniqueId).toList(), otherHearts);
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
        var found = new LinkedHashSet<HeartBlockEntity>();
        for (SubLevel body : SubLevelHelper.getConnectedChain(root)) {
            if (body.isRemoved()) continue;
            for (var chunk : body.getPlot().getLoadedChunks()) {
                for (var blockEntity : chunk.getChunk().getBlockEntities().values()) {
                    if (blockEntity instanceof HeartBlockEntity heart && !heart.isRemoved()) {
                        found.add(heart);
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    public static Vec3 toWorld(Level level, Vec3 position) {
        SubLevel body = Sable.HELPER.getContaining(level, position);
        return body == null ? position : body.logicalPose().transformPosition(position);
    }
}
