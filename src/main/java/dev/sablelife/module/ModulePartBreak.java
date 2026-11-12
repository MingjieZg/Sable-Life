package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ModulePartBreak {
    public static final int MAX_CELLS = 512;
    static final String SOUND = "partBreakSound";

    private record Setup(List<ModuleAnchor> cut, ModuleAnchor watch, boolean byDamage, double threshold) {}

    private static final class State {
        final Setup setup;
        double damage;
        boolean powered;
        boolean pending;
        boolean spent;

        State(Setup setup) { this.setup = setup; }
    }

    private final Map<UUID, State> states = new HashMap<>();

    private State state(VirtualModule module) {
        var setup = new Setup(module.anchors(), module.watchedPart(), module.listensToPart(), module.value("partBreakThreshold"));
        return states.compute(module.id(), (id, previous) -> previous != null && previous.setup.equals(setup) ? previous : new State(setup));
    }

    public static boolean validSelection(VirtualModule module, List<ModuleAnchor> anchors) {
        if (anchors.size() != (module.area() ? 2 : 1)) return false;
        var first = anchors.getFirst();
        var last = anchors.getLast();
        return first.bodyId().equals(last.bodyId()) && cells(first.offset(), last.offset()) <= MAX_CELLS;
    }

    private static long cells(BlockPos a, BlockPos b) {
        long x = Math.abs((long)a.getX() - b.getX()) + 1;
        long y = Math.abs((long)a.getY() - b.getY()) + 1;
        long z = Math.abs((long)a.getZ() - b.getZ()) + 1;
        return x > 32 || y > 32 || z > 32 ? Long.MAX_VALUE : x * y * z;
    }

    static UUID hitBody(HeartBlockEntity heart, BlockPos hit) {
        if (hit == null) return null;
        var body = Sable.HELPER.getContaining(heart.getLevel(), hit);
        return body == null ? null : body.getUniqueId();
    }

    static boolean watches(VirtualModule module, UUID body) {
        var mark = module.watchedPart();
        return mark != null && mark.bodyId().equals(body);
    }

    public void record(HeartBlockEntity heart, BlockPos hit, double loss) {
        if (heart.health().state() != HeartState.ALIVE || !heart.wireless().master() || loss <= 0) return;
        UUID body = null;
        for (var module : heart.modules().partBreakModules()) {
            if (!module.listensToPart() || module.watchedPart() == null) continue;
            if (body == null) {
                body = hitBody(heart, hit);
                if (body == null) return;
            }
            if (!watches(module, body) || !HeartModules.status(module, heart).equals("valid")) continue;
            var state = state(module);
            if (!state.spent) state.damage = Math.min(state.setup.threshold(), state.damage + loss);
        }
    }

    public void tick(HeartBlockEntity heart) {
        var modules = heart.modules().partBreakModules();
        if (modules.isEmpty()) { clear(); return; }
        if (!(heart.getLevel() instanceof ServerLevel level) || heart.health().state() != HeartState.ALIVE) { clear(); return; }
        states.keySet().removeIf(id -> heart.modules().find(id) == null);
        boolean cutThisTick = false;
        for (var module : modules) {
            var state = state(module);
            if (!heart.wireless().master() || !HeartModules.status(module, heart).equals("valid")) {
                state.powered = state.pending = false;
                continue;
            }
            if (state.setup.byDamage()) {
                state.pending = !state.spent && state.damage >= state.setup.threshold();
            } else {
                boolean powered = HeartModules.controlPowered(module, heart);
                state.pending |= powered && !state.powered;
                state.powered = powered;
            }
            if (!state.pending || cutThisTick) continue;
            if (cut(level, heart, module, state.setup.cut())) {
                state.pending = false;
                state.spent = true;
                cutThisTick = true;
                if (!state.setup.byDamage() && ModuleHurt.once(module)) heart.modules().hurtTriggers.finish(module.id());
            }
        }
    }

    private static boolean cut(ServerLevel level, HeartBlockEntity heart, VirtualModule module, List<ModuleAnchor> anchors) {
        var first = anchors.getFirst();
        var body = first.body(heart);
        if (body == null || body.isRemoved()) return false;
        var from = first.position(body);
        var to = anchors.getLast().position(body);
        var min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        var max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        if (!level.hasChunksAt(min, max)) return false;
        var sound = module.soundTimeline(SOUND);
        var soundPoint = sound.audible() ? body.logicalPose().transformPosition(from.getCenter().lerp(to.getCenter(), 0.5)) : null;
        var ownership = BodyOwnership.get(level);
        boolean removed = false;
        for (var pos : BlockPos.betweenClosed(min, max)) removed |= ownership.breakMarkedBlock(level, pos, heart.creatureId(), body);
        SableBodyProbe.invalidate(level);
        // The break can invalidate the module immediately. Let the one-shot mix finish on its own.
        if (removed && soundPoint != null) ModuleAudio.play(level, UUID.randomUUID(), sound, soundPoint);
        return true;
    }

    public void clear() { states.clear(); }
}
