package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Runtime output is ephemeral; saved blueprints only contain the authored steps. */
public final class ModuleSequence {
    private static final class State {
        final PulseSequence clock = new PulseSequence();
        List<PulseSequence.Step> steps = List.of();
        PulseSequence.Step output;
    }
    private final Map<UUID, State> states = new HashMap<>();
    private HeartBlockEntity owner;
    public void clear() { states.clear(); if (owner != null) owner.wireless().programOutputs("sequence", Map.of()); owner = null; }
    public void reset(UUID id) {
        var s = states.get(id); var m = owner == null ? null : owner.modules().find(id);
        if (s != null) { s.clock.reset(m != null && HeartModules.triggered(m, owner)); s.output = null; }
        publish();
    }
    private void publish() {
        if (owner == null) return;
        var outputs = new HashMap<Integer, Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();
        for (var entry : states.entrySet()) { var step = entry.getValue().output;
            if (step != null && !step.waiting() && step.channel() > 0){outputs.merge(step.channel() - 1, step.power(), Math::max);ChannelDiagnostics.record(trace,entry.getKey(),step.channel()-1,step.power());}
        }
        owner.wireless().programOutputs("sequence", outputs,trace);
    }
    public CompoundTag snapshot(UUID id) {
        var tag = new CompoundTag(); var s = states.get(id);
        if (s != null) { tag.putBoolean("Running", s.clock.running()); tag.putInt("Step", s.clock.index()); tag.putInt("Remaining", s.clock.remaining()); }
        return tag;
    }
    public void tick(HeartBlockEntity heart) {
        owner = heart; var active = new HashSet<UUID>();
        if (heart.operational() && heart.wireless().master()) for (var module : heart.modules().all()) {
            if (!module.type().equals("sequence") || !HeartModules.status(module, heart).equals("valid")) continue;
            active.add(module.id()); var state = states.computeIfAbsent(module.id(), ignored -> new State());
            boolean powered = HeartModules.triggered(module, heart); var steps = module.sequence();
            if (!state.steps.equals(steps)) {
                // Initial setup may start immediately. Mid-run editing cancels without replaying a held input.
                if (!state.steps.isEmpty()) state.clock.reset(powered);
                state.steps = steps;
            }
            int resetChannel = (int) module.value("resetChannel") - 1;
            boolean reset = resetChannel >= 0 && (module.value("resetSource") == 0 ? heart.wireless().input(resetChannel) : heart.wireless().output(resetChannel)) > 0;
            var step = state.clock.tick(steps, powered, reset, module.value("sequenceRepeat") == 1 && !ModuleHurt.once(module), module.value("stopOnLow") == 1);
            state.output = step;
        }
        states.keySet().retainAll(active); publish();
    }
}
