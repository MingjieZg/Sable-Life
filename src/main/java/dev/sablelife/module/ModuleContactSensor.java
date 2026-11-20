package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Isolated contact-to-signal runtime. No damage, physical response, or persisted clocks. */
public final class ModuleContactSensor {
    private static final class State {
        int revision;
        CompoundTag config;
        boolean touching;
        boolean failed;
        long until, ready, activation;
        Set<UUID> projectiles = Set.of();
    }
    private HeartBlockEntity owner;
    private long tick = Long.MIN_VALUE;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, CompoundTag> runtime = new HashMap<>();
    public CompoundTag snapshot(UUID id) { return runtime.getOrDefault(id, new CompoundTag()).copy(); }
    public void clear() {
        states.clear(); runtime.clear(); tick = Long.MIN_VALUE;
        if (owner != null) owner.wireless().programOutputs("contact_sensor", Map.of());
        owner = null;
    }
    public void tick(HeartBlockEntity heart) {
        tick(heart, heart.modules().all().stream().filter(module -> module.type().equals("contact_sensor")).toList());
    }
    void tick(HeartBlockEntity heart, List<VirtualModule> modules) {
        if (modules.isEmpty()) { if (owner != null) clear(); return; }
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (tick == now) return;
        tick = now; owner = heart; runtime.clear();
        var outputs = new HashMap<Integer, Integer>(); var trace = new HashMap<UUID, Map<Integer, Integer>>();
        var keep = new HashSet<UUID>();
        for (var module : modules) {
            var data = new CompoundTag(); runtime.put(module.id(), data);
            String channel = ModuleHealthPhase.channelStatus(module, heart, "sensorChannel");
            data.putString("ChannelStatus", channel);
            if (!heart.wireless().master() || !HeartModules.triggered(module, heart) || !channel.equals("ready")) continue;
            keep.add(module.id());
            var state = states.get(module.id());
            long activation = module.value("trigger") == 4 ? heart.modules().moveActivation(module.id()) : Long.MIN_VALUE;
            if (state == null || state.activation != activation || state.revision != heart.modules().revision() && !module.save().equals(state.config)) {
                state = new State(); state.config = module.save(); state.activation = activation; states.put(module.id(), state);
            }
            state.revision = heart.modules().revision();
            if(state.failed) { data.putBoolean("Error",true); continue; }
            ContactSensorQuery.Result result;
            try { result = ContactSensorQuery.query(level, heart, module); }
            catch(RuntimeException failure) {
                state.failed=true; state.until=0; data.putBoolean("Error",true);
                com.mojang.logging.LogUtils.getLogger().warn("Contact Sensor {} stopped after a query error; toggle or edit this sensor to retry",module.id(),failure);
                continue;
            }
            data.putBoolean("Limited", !result.complete());
            boolean contact = result.touching() || !result.projectiles().isEmpty();
            if (result.complete()) {
                var previousProjectiles = state.projectiles;
                boolean entered = result.touching() && !state.touching || result.projectiles().stream().anyMatch(id -> !previousProjectiles.contains(id));
                if (entered && now >= state.ready && now >= state.until) {
                    state.until = now + (int)module.value("contactPulse");
                    state.ready = now + (int)module.value("contactCooldown");
                }
                state.touching = result.touching(); state.projectiles = result.projectiles();
            }
            boolean active = module.value("contactOutputMode") == 0 ? result.complete() && contact : now < state.until;
            data.putBoolean("Contact", contact); data.putBoolean("Active", active);
            data.putInt("Remaining", (int)Math.max(0, state.until - now));
            if (active) {
                int selected = (int)module.value("sensorChannel") - 1, power = (int)module.value("sensorPower");
                outputs.merge(selected, power, Math::max); ChannelDiagnostics.record(trace, module.id(), selected, power);
            }
        }
        states.keySet().retainAll(keep);
        heart.wireless().programOutputs("contact_sensor", outputs, trace);
    }
}
