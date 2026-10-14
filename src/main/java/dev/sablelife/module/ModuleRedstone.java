package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.Direction;
import java.util.*;

/** Shares the sensor/navigation publication pass, so no producer erases another. */
public final class ModuleRedstone {
    private HeartBlockEntity owner;
    private final Map<UUID, SignalPulse> clocks = new HashMap<>();
    public void clear() { clocks.clear();if(owner!=null)owner.wireless().programOutputs("wireless_redstone",Map.of());owner=null; }
    public void tick(HeartBlockEntity heart, Map<ModuleSignals.Face, Integer> faces,Set<UUID> poweredSensors) {
        owner=heart;var retained = new HashSet<UUID>();
        var internal=new HashMap<Integer,Integer>();
        var sensorInternal=new HashMap<Integer,Integer>();
        var external = new HashMap<UUID, dev.sablelife.heart.HeartWireless.ModuleOutput>();
        if (heart.operational() && heart.wireless().master()) {
            for (var module : heart.modules().all()) {
                if(module.type().equals("sensor")&&HeartModules.status(module,heart).equals("valid")){
                    int power=poweredSensors.contains(module.id())?(int)module.value("sensorPower"):0;
                    if(power>0&&ModuleHealthPhase.channelStatus(module,heart,"sensorChannel").equals("ready"))sensorInternal.merge((int)module.value("sensorChannel")-1,power,Math::max);
                    if(module.hasRedBlue())external.put(module.id(),new dev.sablelife.heart.HeartWireless.ModuleOutput(module.redBlue(false,heart.getLevel().registryAccess()),module.redBlue(true,heart.getLevel().registryAccess()),power));
                    continue;
                }
                if (!module.type().equals("wireless_redstone") || !HeartModules.status(module, heart).equals("valid")) continue;
                retained.add(module.id());
                var clock = clocks.computeIfAbsent(module.id(), ignored -> new SignalPulse());
                boolean high = clock.tick(heart.getLevel().getGameTime(),HeartModules.triggered(module, heart), module.value("redstoneMode") == 1,
                        (int) module.value("redstonePulse"));
                int power = high != (module.value("redstoneInvert") == 1) ? (int) module.value("redstonePower") : 0;
                if(power>0&&ModuleHealthPhase.channelStatus(module,heart,"redstoneChannel").equals("ready"))internal.merge((int)module.value("redstoneChannel")-1,power,Math::max);
                if(module.hasRedBlue()) external.put(module.id(),new dev.sablelife.heart.HeartWireless.ModuleOutput(module.redBlue(false,heart.getLevel().registryAccess()),module.redBlue(true,heart.getLevel().registryAccess()),power));
                if(module.anchors().isEmpty()) continue;
                var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
                if (module.value("redstoneFaces") == 1) {
                    faces.merge(new ModuleSignals.Face(anchor.position(body).relative(anchor.face()), anchor.face().getOpposite()), power, Math::max);
                } else for (var face : Direction.values())
                    faces.merge(new ModuleSignals.Face(anchor.position(body), face), power, Math::max);
            }
        }
        clocks.keySet().retainAll(retained);
        heart.wireless().programOutputs("sensor",sensorInternal);
        heart.wireless().programOutputs("wireless_redstone",internal);
        heart.modules().damageAccumulation.tick(heart,external);
        heart.wireless().moduleFrequencies(external);
    }
}
