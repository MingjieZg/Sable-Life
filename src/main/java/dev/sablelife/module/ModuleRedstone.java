package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.Direction;
import java.util.*;

/** Shares the sensor/navigation publication pass, so no producer erases another. */
public final class ModuleRedstone {
    public static final int MAX_OUTPUT_FACES = 64;
    public static final String OUTPUT_FACE = "outputFace";

    static boolean validIndex(VirtualModule module, double index, boolean append) {
        return module != null && module.type().equals("wireless_redstone") && index == Math.rint(index)
                && index >= 0 && index < MAX_OUTPUT_FACES
                && (index < module.redstoneFaceCount() || append && index == module.redstoneFaceCount()
                    && module.redstoneFaceCount() < dev.sablelife.SableLifeServerConfig.maximumRedstoneOutputFaces());
    }

    static String markStatus(HeartBlockEntity heart, VirtualModule module) {
        String firstFailure = "valid";
        for (int i = 0; i < module.redstoneFaceCount(); i++) {
            String status = module.redstoneFaceStatus(i, heart);
            if (status.equals("valid")) return "valid";
            if (i == 0) firstFailure = status;
        }
        return firstFailure;
    }
    private HeartBlockEntity owner;
    private static final class Clock {final SignalPulse signal=new SignalPulse();final SignalRamp ramp=new SignalRamp();SignalBuffer buffer;boolean previous;int sampled;}
    private final Map<UUID, Clock> clocks = new HashMap<>();
    public static boolean followsInput(VirtualModule module){return module.value("redstoneStrengthMode")==1&&module.value("trigger")>=1&&module.value("trigger")<=3;}
    public static int inputStrength(HeartBlockEntity heart,VirtualModule module){
        int value=switch((int)module.value("trigger")){
            case 1 -> anchorPower(heart, module, true);
            case 2 -> heart.wireless().output((int)module.value("channel"));
            case 3 -> heart.wireless().input((int)module.value("channel"));
            default -> (int)module.value("redstonePower");
        };return Math.clamp(value,0,15);
    }
    static int anchorPower(HeartBlockEntity heart, VirtualModule module) {
        return anchorPower(heart, module, false);
    }
    private static int anchorPower(HeartBlockEntity heart, VirtualModule module, boolean followStrength) {
        if (!(heart.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
        var position = heart.getBlockPos();
        boolean marked = module.redstoneFaceCount() > 0;
        if (marked) {
            if (!module.redstoneFaceStatus(0, heart).equals("valid")) return 0;
            var anchor = module.redstoneFace(0);
            position = anchor.position(anchor.body(heart));
        }
        return (marked || followStrength) && module.importData().getBoolean("LegacyPower")
                ? dev.sablelife.attack.ModulePower.signalStrength(level, position)
                : dev.sablelife.attack.ModulePower.controlStrength(level, position);
    }
    public void clear() { clocks.clear();if(owner!=null)owner.wireless().programOutputs("wireless_redstone",Map.of());owner=null; }
    public void tick(HeartBlockEntity heart, Map<ModuleSignals.Face, Integer> faces,Set<UUID> poweredSensors) {
        owner=heart;var retained = new HashSet<UUID>();
        var internal=new HashMap<Integer,Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();var sensorTrace=new HashMap<UUID,Map<Integer,Integer>>();
        var sensorInternal=new HashMap<Integer,Integer>();
        var external = new HashMap<UUID, dev.sablelife.heart.HeartWireless.ModuleOutput>();
        if (heart.operational() && heart.wireless().master()) {
            for (var module : heart.modules().all()) {
                if(module.type().equals("sensor")&&HeartModules.status(module,heart).equals("valid")){
                    int power=poweredSensors.contains(module.id())?(int)module.value("sensorPower"):0;
                    if(power>0&&ModuleHealthPhase.channelStatus(module,heart,"sensorChannel").equals("ready")){sensorInternal.merge((int)module.value("sensorChannel")-1,power,Math::max);ChannelDiagnostics.record(sensorTrace,module.id(),(int)module.value("sensorChannel")-1,power);}
                    if(module.hasRedBlue())external.put(module.id(),new dev.sablelife.heart.HeartWireless.ModuleOutput(module.redBlue(false,heart.getLevel().registryAccess()),module.redBlue(true,heart.getLevel().registryAccess()),power));
                    continue;
                }
                if (!module.type().equals("wireless_redstone") || !HeartModules.status(module, heart).equals("valid")) continue;
                retained.add(module.id());
                var clock = clocks.computeIfAbsent(module.id(), ignored -> new Clock());
                boolean input=HeartModules.controlPowered(module,heart),pulse=module.value("redstoneMode")==1;
                int power;
                if(module.value("redstoneStrengthMode")==2){
                    power=clock.ramp.tick(heart.getLevel().getGameTime(),input,(int)module.value("redstoneRampStart"),(int)module.value("redstoneRampEnd"),(int)module.value("redstoneRampTime"));
                    clock.previous=false;clock.signal.tick(heart.getLevel().getGameTime(),false,false,1);
                }else{
                clock.ramp.tick(heart.getLevel().getGameTime(),false,0,15,1);
                boolean follow=followsInput(module);int strength=follow?inputStrength(heart,module):(int)module.value("redstonePower");
                if(input&&!clock.previous)clock.sampled=strength;clock.previous=input;
                boolean high=clock.signal.tick(heart.getLevel().getGameTime(),input,pulse,(int)module.value("redstonePulse"));
                int active=high?(pulse?clock.sampled:strength):0;
                power=follow?(module.value("redstoneInvert")==1?15-active:active):high!=(module.value("redstoneInvert")==1)?(int)module.value("redstonePower"):0;
                }
                int bufferTicks=(int)module.value("redstoneBufferTime");
                if(bufferTicks>0){if(clock.buffer==null)clock.buffer=new SignalBuffer();power=clock.buffer.tick(heart.getLevel().getGameTime(),power,bufferTicks);}
                else clock.buffer=null;
                if(power>0&&ModuleHealthPhase.channelStatus(module,heart,"redstoneChannel").equals("ready")){internal.merge((int)module.value("redstoneChannel")-1,power,Math::max);ChannelDiagnostics.record(trace,module.id(),(int)module.value("redstoneChannel")-1,power);}
                if(module.hasRedBlue()) external.put(module.id(),new dev.sablelife.heart.HeartWireless.ModuleOutput(module.redBlue(false,heart.getLevel().registryAccess()),module.redBlue(true,heart.getLevel().registryAccess()),power));
                boolean selectedFace = module.value("redstoneFaces") == 1;
                for (int i = 0; i < module.redstoneFaceCount(); i++) {
                    if (!module.redstoneFaceStatus(i, heart).equals("valid")) continue;
                    var anchor = module.redstoneFace(i);
                    var pos = anchor.position(anchor.body(heart));
                    if (selectedFace) {
                        faces.merge(new ModuleSignals.Face(pos.relative(anchor.face()), anchor.face().getOpposite()), power, Math::max);
                    } else for (var face : Direction.values())
                        faces.merge(new ModuleSignals.Face(pos, face), power, Math::max);
                }
            }
        }
        clocks.keySet().retainAll(retained);
        heart.wireless().programOutputs("sensor",sensorInternal,sensorTrace);
        heart.wireless().programOutputs("wireless_redstone",internal,trace);
        heart.modules().damageAccumulation.tick(heart,external);
        heart.modules().hitCounter.publish(heart,external);
        heart.wireless().moduleFrequencies(external);
    }
}
