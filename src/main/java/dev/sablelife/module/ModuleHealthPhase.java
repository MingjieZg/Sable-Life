package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import java.util.*;

/** Health stages only produce signals; the player's wiring controls all actions. */
public final class ModuleHealthPhase {
    private HeartBlockEntity owner;
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, HealthPhaseSignal> clocks = new HashMap<>();
    public boolean active(UUID id) { return active.contains(id); }
    public int remaining(UUID id) { var clock = clocks.get(id); return clock == null ? 0 : clock.remaining(); }
    public static String channelStatus(VirtualModule module, HeartBlockEntity heart) {
        return channelStatus(module,heart,"phaseChannel");
    }
    public static String channelStatus(VirtualModule module,HeartBlockEntity heart,String field){
        int channel = (int) module.value(field) - 1;
        if (channel < 0) return "unbound";
        if (channel >= heart.wireless().size()) return "missing";
        var output = heart.wireless().channel(channel);
        if (output.value(0) == 0) return "disabled";
        if (!output.configured()) return "frequency";
        if (!output.programmed()) return "mode";
        return "ready";
    }
    public void clear() {
        active.clear(); clocks.clear();
        if (owner != null) owner.wireless().programOutputs("health_phase", Map.of());
        owner = null;
    }
    public void tick(HeartBlockEntity heart) {
        owner = heart; active.clear(); var signals = new HashMap<Integer, Integer>(); var retained = new HashSet<UUID>();
        if (heart.health().state() == HeartState.ALIVE && heart.wireless().master()) {
            for (var module : heart.modules().all()) {
                if (!module.type().equals("health_phase") || !HeartModules.status(module, heart).equals("valid")) continue;
                retained.add(module.id());
                var clock = clocks.computeIfAbsent(module.id(), ignored -> new HealthPhaseSignal());
                boolean inRange = HeartModules.triggered(module, heart) && HealthPhase.contains(heart.health().current(), heart.health().maximum(), module.value("phaseLower"), module.value("phaseUpper"));
                boolean powered = clock.tick(inRange, module.value("phaseMode") == 1, (int) module.value("phasePulse"));
                if (!powered || !channelStatus(module, heart).equals("ready")) continue;
                int channel = (int) module.value("phaseChannel") - 1;
                active.add(module.id()); signals.merge(channel, (int) module.value("phasePower"), Math::max);
            }
        }
        clocks.keySet().retainAll(retained);
        heart.wireless().programOutputs("health_phase", signals);
    }
}
