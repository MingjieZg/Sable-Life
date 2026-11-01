package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.Direction;
import java.util.*;

/** Shares the sensor/navigation publication pass, so no producer erases another. */
public final class ModuleRedstone {
    private HeartBlockEntity owner;
    private static final class Clock {final SignalPulse signal=new SignalPulse();boolean previous;int sampled;}
    private final Map<UUID, Clock> clocks = new HashMap<>();
    public static boolean followsInput(VirtualModule module){return module.value("redstoneStrengthMode")==1&&module.value("trigger")>=1&&module.value("trigger")<=3;}
    public static int inputStrength(HeartBlockEntity heart,VirtualModule module){
        int value=switch((int)module.value("trigger")){
            case 1 -> {var level=(net.minecraft.server.level.ServerLevel)heart.getLevel();var pos=module.anchors().isEmpty()?heart.getBlockPos():module.anchors().getFirst().position(module.anchors().getFirst().body(heart));yield module.importData().getBoolean("LegacyPower")?dev.sablelife.attack.ModulePower.signalStrength(level,pos):dev.sablelife.attack.ModulePower.controlStrength(level,pos);}
            case 2 -> heart.wireless().output((int)module.value("channel"));
            case 3 -> heart.wireless().input((int)module.value("channel"));
            default -> (int)module.value("redstonePower");
        };return Math.clamp(value,0,15);
    }
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
                var clock = clocks.computeIfAbsent(module.id(), ignored -> new Clock());
                boolean input=HeartModules.triggered(module,heart),pulse=module.value("redstoneMode")==1;
                boolean follow=followsInput(module);int strength=follow?inputStrength(heart,module):(int)module.value("redstonePower");
                if(input&&!clock.previous)clock.sampled=strength;clock.previous=input;
                boolean high=clock.signal.tick(heart.getLevel().getGameTime(),input,pulse,(int)module.value("redstonePulse"));
                int active=high?(pulse?clock.sampled:strength):0;
                int power=follow?(module.value("redstoneInvert")==1?15-active:active):high!=(module.value("redstoneInvert")==1)?(int)module.value("redstonePower"):0;
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
        heart.modules().hitCounter.publish(heart,external);
        heart.wireless().moduleFrequencies(external);
    }
}
