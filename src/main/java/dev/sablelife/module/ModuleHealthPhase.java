package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import java.util.*;

/** Health stages only produce signals; the player's wiring controls all actions. */
public final class ModuleHealthPhase {
    private HeartBlockEntity owner;
    private final Set<UUID> active = new HashSet<>();
    private record Key(UUID module,UUID row){}
    private static final class Clock { final HealthPhaseRules.Row row;final int revision;final HealthPhaseSignal signal=new HealthPhaseSignal();Clock(HealthPhaseRules.Row row){this.row=row;revision=row.revision();}}
    private final Map<Key,Clock> clocks=new HashMap<>();
    private final Map<UUID,net.minecraft.nbt.CompoundTag> runtime=new HashMap<>();
    public boolean active(UUID id){return active.contains(id);}
    public int remaining(UUID id){return clocks.entrySet().stream().filter(e->e.getKey().module.equals(id)).mapToInt(e->e.getValue().signal.remaining()).max().orElse(0);}
    public net.minecraft.nbt.CompoundTag snapshot(UUID id){return runtime.getOrDefault(id,new net.minecraft.nbt.CompoundTag()).copy();}
    public static String channelStatus(VirtualModule module,HeartBlockEntity heart){return channelStatus(module,heart,"phaseChannel");}
    public static String channelStatus(VirtualModule module,HeartBlockEntity heart,String field){
        return channelStatus((int)module.value(field),heart);
    }
    public static String channelStatus(int selected,HeartBlockEntity heart){
        int channel=selected-1;
        if (channel < 0) return "unbound";
        if (channel >= heart.wireless().size()) return "missing";
        var output = heart.wireless().channel(channel);
        if (output.value(0) == 0) return "disabled";
        if (!output.configured()) return "frequency";
        if (!output.programmed()) return "mode";
        return "ready";
    }
    public void clear() {
        active.clear(); clocks.clear();runtime.clear();
        if (owner != null) owner.wireless().programOutputs("health_phase", Map.of());
        owner = null;
    }
    public void tick(HeartBlockEntity heart) {
        owner=heart;active.clear();runtime.clear();var signals=new HashMap<Integer,Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();var retained=new HashSet<Key>();
        if(heart.health().state()==HeartState.ALIVE&&heart.wireless().master())for(var module:heart.modules().all()){
            if(!module.type().equals("health_phase")||!HeartModules.status(module,heart).equals("valid"))continue;
            var data=new net.minecraft.nbt.CompoundTag();var rows=new net.minecraft.nbt.ListTag();int count=0;
            for(var row:module.healthPhaseRules().rows()){
                var key=new Key(module.id(),row.id());var status=channelStatus((int)row.value("phaseChannel"),heart);
                boolean validRange=row.value("phaseLower")<row.value("phaseUpper"),inRange=validRange&&HeartModules.triggered(module,heart)&&HealthPhase.contains(heart.health().current(),heart.health().maximum(),row.value("phaseLower"),row.value("phaseUpper"));
                var state=new net.minecraft.nbt.CompoundTag();state.putUUID("Id",row.id());state.putString("ChannelStatus",status);state.putBoolean("RangeValid",validRange);state.putBoolean("InRange",inRange);
                boolean powered=false;int remaining=0;
                if(row.enabled()) {retained.add(key);var clock=clocks.get(key);if(clock==null||clock.row!=row||clock.revision!=row.revision()){clock=new Clock(row);clocks.put(key,clock);}powered=clock.signal.tick(inRange,row.value("phaseMode")==1,(int)row.value("phasePulse"));remaining=clock.signal.remaining();}
                boolean output=powered&&status.equals("ready");state.putBoolean("Active",output);state.putInt("Remaining",remaining);rows.add(state);
                if(output){count++;active.add(module.id());signals.merge((int)row.value("phaseChannel")-1,(int)row.value("phasePower"),Math::max);ChannelDiagnostics.record(trace,module.id(),(int)row.value("phaseChannel")-1,(int)row.value("phasePower"));}
            }
            data.put("Rows",rows);data.putInt("ActiveCount",count);runtime.put(module.id(),data);
        }
        clocks.keySet().retainAll(retained);heart.wireless().programOutputs("health_phase",signals,trace);
    }
}
