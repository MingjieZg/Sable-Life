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
    public record Entry(BlockPos heart, UUID module, String group, int index, BlockPos source) {}
    public record Batch(ServerLevel source, Set<BlockPos> blocks, List<Entry> entries) {
        public void finish(SubLevelAssemblyHelper.AssemblyTransform transform) {
            var destination = transform.getLevel();
            for (var entry : entries) {
                boolean heartMoves = blocks.contains(entry.heart);
                var heartLevel = heartMoves ? destination : source;
                var heartPos = heartMoves ? transform.apply(entry.heart) : entry.heart;
                if (!(heartLevel.getBlockEntity(heartPos) instanceof HeartBlockEntity heart)) continue;
                var module = heart.modules().find(entry.module);
                if (module == null) continue;
                var stored = module.storedAnchors().stream().filter(a -> a.group().equals(entry.group) && a.index() == entry.index).findFirst().orElse(null);
                if (stored == null) continue;
                var pos = transform.apply(entry.source); var body = Sable.HELPER.getContaining(destination, pos);
                if (body == null || destination != heartLevel) {
                    module.invalidate(); heart.modules().changed(); heart.setChanged(); continue;
                }
                var direction = transform.getRotation().rotate(stored.anchor().face());
                if (module.replaceStoredAnchor(entry.group, entry.index,
                        new ModuleAnchor(body.getUniqueId(), pos.subtract(body.getPlot().getCenterBlock()), direction))) {
                    heart.modules().changed(); heart.setChanged();
                }
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
                for (var module : heart.modules().all()) for (var stored : module.storedAnchors()) {
                    var anchor = stored.anchor();
                    var owner = anchor.body(heart); if (owner == null) continue;
                    var pos = anchor.position(owner);
                    if (moving.contains(pos)) entries.add(new Entry(heart.getBlockPos(), module.id(), stored.group(), stored.index(), pos));
                }
            }
        }
        return new Batch(level, moving, entries);
    }
    public static void replaced(ServerLevel level, BlockPos pos) {
        if (Sable.HELPER.getContaining(level, pos) == null) return;
        for (var heart : SableBodyProbe.findConnectedHearts(level, pos)) for (var module : heart.modules().all()) {
            var watched = module.watchedPart();
            var watchedBody = watched == null ? null : watched.body(heart);
            if (watchedBody != null && watched.position(watchedBody).equals(pos)) {
                module.watchedPart(null); heart.modules().changed(); heart.setChanged();
            }
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
