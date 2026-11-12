package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Terrain follows the field's projected footprint on its exposed supporting surface. */
final class SurfaceFieldBlast {
    static void present(ServerLevel level, HeartBlockEntity heart, VirtualModule config, SurfaceFieldGeometry.Frame frame) {
        var center = frame.midpoint();
        for (var particle : config.movementParticles("Blast").particleEntries()) {
            ModuleParticles.send(level, particle, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        }
        ModuleAudio.play(level, config, "blastSound", center);
        if (config.value("blastBlocks") == 1) destroyTerrain(level, heart, frame);
    }

    private static void destroyTerrain(ServerLevel level, HeartBlockEntity heart, SurfaceFieldGeometry.Frame frame) {
        var center = frame.midpoint();
        var interaction = level.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY)
                ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
        double radius = frame.circle() ? frame.length() : (frame.length() + frame.width()) / 4;
        var explosion = new Explosion(level, null, null, new ModuleTerrainExplosion(heart.creatureId()),
                center.x, center.y, center.z, (float) radius, false, interaction,
                ParticleTypes.POOF, ParticleTypes.POOF, SoundEvents.GENERIC_EXPLODE);
        if (EventHooks.onExplosionStart(level, explosion)) return;

        var blocks = new LinkedHashSet<BlockPos>();
        addSurfaceBlock(level, explosion, SurfaceFieldGeometry.surfaceHit(level, frame.center(), frame.normal()), blocks);
        var bounds = frame.footprintBounds();
        // World impact faces are cardinal. The flat bounds visit each column of that face once.
        for (var cell : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            var point = cell.getCenter();
            point = point.add(frame.normal().scale(frame.center().subtract(point).dot(frame.normal())));
            if (frame.contains(point)) addSurfaceBlock(level, explosion, SurfaceFieldGeometry.surfaceHit(level, point, frame.normal()), blocks);
        }
        explosion.getToBlow().addAll(blocks);
        EventHooks.onExplosionDetonate(level, explosion, new ArrayList<>(), radius * 2);
        explosion.getToBlow().removeIf(pos -> !blocks.contains(pos) || !level.hasChunkAt(pos));
        explosion.finalizeExplosion(false);
    }

    private static void addSurfaceBlock(ServerLevel level, Explosion explosion, BlockHitResult hit, LinkedHashSet<BlockPos> blocks) {
        if (hit == null) return;
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (level.getWorldBorder().isWithinBounds(pos) && state.getDestroySpeed(level, pos) >= 0
                && state.getExplosionResistance(level, pos, explosion) < 1200 && state.getFluidState().isEmpty()) blocks.add(pos);
    }

    private SurfaceFieldBlast() {}
}
