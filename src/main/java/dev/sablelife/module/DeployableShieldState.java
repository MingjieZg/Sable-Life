package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side audience and visual lifetime for one module. */
final class DeployableShieldState {
    private static final int HEARTBEAT_TICKS = 10;

    private static final class Viewer {
        long seen;
        long sent;
    }

    private final UUID id = UUID.randomUUID();
    private final Map<ServerPlayer, Viewer> viewers = new HashMap<>();
    private PatternVisual visual;
    private long openedAt;
    private int revision;

    void hold(ServerLevel level, HeartBlockEntity core, VirtualModule module, long now) {
        var current = currentVisual(core, module);
        if (visual == null) openedAt = now;
        var hold = new ShieldVisualPacket(current, ShieldVisualPacket.Phase.HOLD);
        if (!hold.valid()) {
            close(level);
            return;
        }
        visual = current;
        boolean opened = now - openedAt >= ShieldVisualPacket.transitionTicks(current.speed());
        var sync = opened ? new ShieldVisualPacket(current, ShieldVisualPacket.Phase.SYNC) : hold;
        var close = new ShieldVisualPacket(current, ShieldVisualPacket.Phase.CLOSE);
        for (var player : level.players()) {
            if (!canReceive(player)) continue;
            var viewer = viewers.get(player);
            if (!inRange(level, player, current)) {
                if (viewer != null) PacketDistributor.sendToPlayer(player, close);
                continue;
            }
            if (viewer != null) viewer.seen = now;
            if ((viewer == null || now - viewer.sent >= HEARTBEAT_TICKS) && VisualBudget.allow(player, 1)) {
                boolean known = viewer != null && now - viewer.sent < ShieldVisualPacket.LEASE_TICKS;
                PacketDistributor.sendToPlayer(player, known ? hold : sync);
                if (viewer == null) {
                    viewer = new Viewer();
                    viewers.put(player, viewer);
                }
                viewer.seen = viewer.sent = now;
            }
        }
        viewers.values().removeIf(viewer -> viewer.seen != now);
    }

    private PatternVisual currentVisual(HeartBlockEntity core, VirtualModule module) {
        int authoredRevision = core.modules().revision();
        if (visual != null && !visual.follow() && revision == authoredRevision) return visual;
        revision = authoredRevision;
        var next = PatternVisual.create(core, module, null, id);
        if (visual == null || visual.follow() || next.follow() || !next.anchor().equals(visual.anchor())
                || next.startDistance() != visual.startDistance()) return next;
        // Appearance changes on a live Redstone Core leave the fixed world pose in place.
        return new PatternVisual(next.dimension(), id, next.anchor(), visual.point(), visual.normal(), visual.up(),
                next.startDistance(), next.size(), next.speed(), next.duration(), next.rgb(), next.pattern(),
                next.symbol(), false, next.range());
    }

    void close(ServerLevel level) {
        if (visual == null) return;
        if (level != null && level.getServer().isRunning()) {
            var close = new ShieldVisualPacket(visual, ShieldVisualPacket.Phase.CLOSE);
            // Release messages must survive visual-budget exhaustion.
            for (var player : level.players())
                if (viewers.containsKey(player) && canReceive(player)) PacketDistributor.sendToPlayer(player, close);
        }
        visual = null;
        viewers.clear();
    }

    static void preview(ServerPlayer player, HeartBlockEntity core, VirtualModule module) {
        if (!canReceive(player)) return;
        var visual = PatternVisual.create(core, module, null, UUID.randomUUID());
        var preview = new ShieldVisualPacket(visual, ShieldVisualPacket.Phase.PREVIEW);
        if (preview.valid() && inRange(player.serverLevel(), player, visual) && VisualBudget.allow(player, 1))
            PacketDistributor.sendToPlayer(player, preview);
    }

    private static boolean canReceive(ServerPlayer player) {
        var listener = player.connection;
        return listener != null && listener.getConnection().isConnected()
                && NetworkRegistry.hasChannel(listener, ShieldVisualPacket.TYPE.id());
    }

    private static boolean inRange(ServerLevel level, ServerPlayer player, PatternVisual visual) {
        return SableBodyProbe.toWorld(level, player.position()).distanceToSqr(visual.point()) <= visual.range() * visual.range();
    }
}
