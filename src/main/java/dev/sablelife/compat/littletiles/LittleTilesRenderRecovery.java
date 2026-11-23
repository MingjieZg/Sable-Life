package dev.sablelife.compat.littletiles;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.sablelife.SableLifeClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import team.creative.littletiles.common.block.entity.BETiles;

/** Loaded only by client mixins after optional dependency and bytecode checks succeed. */
public final class LittleTilesRenderRecovery {
    private static volatile boolean failed;

    public static void afterCompile(BETiles be, long section) {
        if (failed) return;
        try {
            if (!(be.render.buffers() instanceof LostMesh lost) || !lost.sablelife$hasLoss()
                    || !SableLifeClientConfig.littleTilesMeshRecovery()) return;
            if (!(be.getLevel() instanceof ClientLevel level) || be.isRemoved()
                    || !(Sable.HELPER.getContaining(level, be.getBlockPos()) instanceof ClientSubLevel body)
                    || body.isRemoved() || !level.hasChunkAt(be.getBlockPos())) return;
            synchronized (be.render) {
                // Let the pending build finish and keep the model boxes when only the vertex buffer was lost.
                if (!be.render.isInQueue() && lost.sablelife$takeRecovery(System.nanoTime())) {
                    be.render.queue(false, true, section);
                }
            }
        } catch (LinkageError | RuntimeException failure) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Sable Life disabled optional LittleTiles mesh recovery after an adapter failure", failure);
        }
    }

    private LittleTilesRenderRecovery() {}
}
