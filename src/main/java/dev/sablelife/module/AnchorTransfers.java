package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Transfers exact marked blocks through Sable assembly, never guesses from nearby world positions. */
public final class AnchorTransfers {
    public record Entry(BlockPos heart, UUID module, int index, BlockPos source) {}
    public record Batch(ServerLevel source, Set<BlockPos> blocks, List<Entry> entries) {
        public void finish(SubLevelAssemblyHelper.AssemblyTransform transform) {
            var destination = transform.getLevel();
            for (var entry : entries) {
                boolean heartMoves = blocks.contains(entry.heart);
                var heartLevel = heartMoves ? destination : source;
                var heartPos = heartMoves ? transform.apply(entry.heart) : entry.heart;
                if (!(heartLevel.getBlockEntity(heartPos) instanceof HeartBlockEntity heart)) continue;
                var module = heart.modules().find(entry.module);
                if (module == null || entry.index < 0 && module.source() == null || entry.index >= 0 && module.anchors().size() <= entry.index) continue;
                var pos = transform.apply(entry.source); var body = Sable.HELPER.getContaining(destination, pos);
                if (body == null || destination != heartLevel) {
                    module.invalidate(); heart.modules().changed(); heart.setChanged(); continue;
                }
                var anchors = new ArrayList<>(module.anchors()); var old = entry.index < 0 ? module.source() : anchors.get(entry.index);
                var direction = transform.getRotation().rotate(old.face());
                if (entry.index < 0) {
                    module.source(new ModuleAnchor(body.getUniqueId(), pos.subtract(body.getPlot().getCenterBlock()), direction));
                    heart.modules().changed(); heart.setChanged(); continue;
                }
                anchors.set(entry.index, new ModuleAnchor(body.getUniqueId(), pos.subtract(body.getPlot().getCenterBlock()), direction));
                boolean invalidated = module.invalidated(), detached = module.detached(); module.anchors(anchors);
                if (invalidated) module.invalidate(); if (detached) module.detach();
                heart.modules().changed(); heart.setChanged();
            }
        }
    }
    public static Batch capture(ServerLevel level, Iterable<BlockPos> blocks) {
        var moving = new HashSet<BlockPos>(); blocks.forEach(p -> moving.add(p.immutable()));
        var entries = new ArrayList<Entry>();
        for (var body : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (body == null || body.isRemoved()) continue;
            for (var chunk : body.getPlot().getLoadedChunks()) for (var be : chunk.getChunk().getBlockEntities().values()) {
                if (!(be instanceof HeartBlockEntity heart) || heart.modules().all().isEmpty()) continue;
                for (var module : heart.modules().all()) for (int i = -1; i < module.anchors().size(); i++) {
                    var anchor = i < 0 ? module.source() : module.anchors().get(i); if (anchor == null) continue;
                    var owner = anchor.body(heart); if (owner == null) continue;
                    var pos = anchor.position(owner);
                    if (moving.contains(pos)) entries.add(new Entry(heart.getBlockPos(), module.id(), i, pos));
                }
            }
        }
        return new Batch(level, moving, entries);
    }
    public static void replaced(ServerLevel level, BlockPos pos) {
        if (Sable.HELPER.getContaining(level, pos) == null) return;
        for (var heart : SableBodyProbe.findConnectedHearts(level, pos)) for (var module : heart.modules().all()) {
            if (module.invalidated()) continue;
            for (var anchor : module.anchors()) {
                var body = anchor.body(heart);
                if (body != null && anchor.position(body).equals(pos)) {
                    module.invalidate(); heart.modules().changed(); heart.setChanged(); break;
                }
            }
        }
    }
}
