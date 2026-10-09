package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Per-player warning windows; entering late never skips the full advertised countdown. */
public final class ModuleArena {
    private static final class Warning { long deadline; int lastSeconds = -1; Warning(long deadline) { this.deadline = deadline; } }
    private final Map<UUID, Map<UUID, Warning>> warnings = new HashMap<>();
    private final Map<UUID, String> settings = new HashMap<>();
    private HeartBlockEntity owner;
    private void cancel(UUID module) {
        var old = warnings.remove(module); settings.remove(module);
        if (old == null || owner == null || !(owner.getLevel() instanceof ServerLevel level)) return;
        for (UUID id : old.keySet()) {
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) player.connection.send(new ClientboundClearTitlesPacket(false));
        }
    }
    public void clear() { for (UUID id : List.copyOf(warnings.keySet())) cancel(id); settings.clear(); owner = null; }
    public boolean warning(UUID module) { return warnings.containsKey(module) && !warnings.get(module).isEmpty(); }
    public static double horizontalDistance(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }
    public static Vec3 corePosition(HeartBlockEntity heart) {
        var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(), heart.getBlockPos());
        return body == null ? heart.getBlockPos().getCenter() : body.logicalPose().transformPosition(heart.getBlockPos().getCenter());
    }
    /** Translate the connected mechanism as a whole; preserve height and joint offsets. */
    private static boolean returnToCenter(HeartBlockEntity heart, Vec3 center) {
        var level = (ServerLevel) heart.getLevel();
        var root = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, heart.getBlockPos());
        if (!(root instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel) || root.isRemoved()) return false;
        var system = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
        if (system == null) return false;
        var core = corePosition(heart);
        var delta = new org.joml.Vector3d(center.x - core.x, 0, center.z - core.z);
        record Move(dev.ryanhcode.sable.sublevel.ServerSubLevel body, org.joml.Vector3d position, org.joml.Quaterniond orientation) {}
        var moves = new ArrayList<Move>();
        for (var body : dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root)) {
            if (!(body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server) || body.isRemoved()) return false;
            moves.add(new Move(server, new org.joml.Vector3d(body.logicalPose().position()).add(delta),
                    new org.joml.Quaterniond(body.logicalPose().orientation())));
        }
        level.getChunk(net.minecraft.core.BlockPos.containing(center.x, core.y, center.z));
        for (var move : moves) {
            system.getPipeline().resetVelocity(move.body());
            system.getPipeline().teleport(move.body(), move.position(), move.orientation());
        }
        dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);
        return true;
    }
    public void tick(HeartBlockEntity heart) {
        owner = heart;
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var active = new HashSet<UUID>();
        for (var module : heart.modules().all()) {
            if (!module.type().equals("arena") || !HeartModules.triggered(module, heart)) continue;
            var boss = corePosition(heart);
            if (horizontalDistance(boss, module.arenaCenter()) <= module.value("arenaRadius")) { cancel(module.id()); continue; }
            // Immediate reset takes precedence over player punishment, including an expired warning.
            if (module.value("arenaForceReturn") == 1) {
                if (returnToCenter(heart, module.arenaCenter())) heart.restoreFullHealth();
                cancel(module.id());
                continue;
            }
            active.add(module.id());
            boolean deathEnabled = module.value("arenaDeath") == 1;
            boolean returnEnabled = module.value("arenaReturn") == 1;
            String signature = module.arenaDimension() + ":" + module.arenaCenter() + ":" + module.value("arenaRadius") + ":" + module.value("warningRange") + ":" + module.value("countdown") + ":" + returnEnabled + ":" + deathEnabled;
            if (!signature.equals(settings.get(module.id()))) { cancel(module.id()); settings.put(module.id(), signature); }
            var pending = warnings.computeIfAbsent(module.id(), ignored -> new HashMap<>());
            var nearby = new HashSet<UUID>(); long now = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator()
                        || horizontalDistance(player.position(), boss) > module.value("warningRange")) continue;
                nearby.add(player.getUUID());
                var warning = pending.computeIfAbsent(player.getUUID(), ignored -> new Warning(now + (long) module.value("countdown")));
                int seconds = (int) Math.max(0, Math.ceil((warning.deadline - now) / 20.));
                if (seconds != warning.lastSeconds) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(deathEnabled ? "arena.sablelife.countdown" : "arena.sablelife.warning_countdown", Component.literal(Integer.toString(seconds)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED)));
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("arena.sablelife.warning").withStyle(ChatFormatting.RED)));
                    warning.lastSeconds = seconds;
                }
                if (now >= warning.deadline) {
                    // Vanilla genericKill bypasses armor, resistance, normal invulnerability and totems.
                    if (deathEnabled) dev.sablelife.attack.ModuleDamage.hurt(player, dev.sablelife.attack.ModuleDamage.named(level.damageSources().genericKill(),heart), Float.MAX_VALUE);
                    if (!player.isAlive()) nearby.remove(player.getUUID());
                    warning.deadline = now + (long) module.value("countdown"); warning.lastSeconds = -1;
                }
            }
            for (UUID id : List.copyOf(pending.keySet())) if (!nearby.contains(id)) {
                var player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null && player.level() == level) player.connection.send(new ClientboundClearTitlesPacket(false));
                pending.remove(id);
            }
            // The same nearby-player range governs both punishment and returning an unattended boss.
            if (returnEnabled && pending.isEmpty()
                    && returnToCenter(heart, module.arenaCenter())) cancel(module.id());
        }
        for (UUID id : List.copyOf(warnings.keySet())) if (!active.contains(id)) cancel(id);
    }
}
