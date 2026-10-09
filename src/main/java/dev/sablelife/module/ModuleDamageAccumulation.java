package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Core damage event producer, published with all other internal and external signal sources. */
public final class ModuleDamageAccumulation {
    private record State(double threshold,DamageAccumulator counter){}
    private final Map<UUID,State> states=new HashMap<>();
    private HeartBlockEntity owner;
    private State state(VirtualModule module){return states.compute(module.id(),(id,old)->old!=null&&old.threshold==module.value("accumThreshold")?old:new State(module.value("accumThreshold"),new DamageAccumulator()));}
    public void record(HeartBlockEntity heart,double actualLoss){
        if(!Double.isFinite(actualLoss)||actualLoss<=0||heart.health().state()!=HeartState.ALIVE||!heart.wireless().master())return;
        owner=heart;
        for(var module:heart.modules().all())if(module.type().equals("damage_accumulation")&&HeartModules.triggered(module,heart))state(module).counter.add(actualLoss,module.value("accumThreshold"));
    }
    public static String channelStatus(VirtualModule module,HeartBlockEntity heart){return ModuleHealthPhase.channelStatus(module,heart,"accumChannel");}
    public void tick(HeartBlockEntity heart,Map<UUID,HeartWireless.ModuleOutput> external){
        owner=heart;var retained=new HashSet<UUID>();var internal=new HashMap<Integer,Integer>();
        if(heart.health().state()==HeartState.ALIVE)for(var module:heart.modules().all()){
            if(!module.type().equals("damage_accumulation"))continue;retained.add(module.id());var counter=state(module).counter;
            boolean ready=channelStatus(module,heart).equals("ready"),active=heart.wireless().master()&&HeartModules.triggered(module,heart);
            boolean high=counter.tick(heart.getLevel().getGameTime(),active&&(ready||module.hasRedBlue()),(int)module.value("accumPulse"));
            int power=active&&(high!=(module.value("accumInvert")==1))?(int)module.value("accumPower"):0;
            if(ready&&power>0)internal.merge((int)module.value("accumChannel")-1,power,Math::max);
            if(module.hasRedBlue())external.put(module.id(),new HeartWireless.ModuleOutput(module.redBlue(false,heart.getLevel().registryAccess()),module.redBlue(true,heart.getLevel().registryAccess()),power));
        }
        states.keySet().retainAll(retained);heart.wireless().programOutputs("damage_accumulation",internal);
    }
    public CompoundTag snapshot(VirtualModule module,HeartBlockEntity heart){
        var tag=new CompoundTag();var s=states.get(module.id());if(s!=null&&s.threshold==module.value("accumThreshold")){tag.putDouble("Accumulated",s.counter.accumulated());tag.putLong("Pending",s.counter.pending());tag.putBoolean("High",s.counter.high());}
        tag.putString("ChannelStatus",channelStatus(module,heart));return tag;
    }
    public void reset(UUID id){states.remove(id);}
    public void clear(){states.clear();if(owner!=null)owner.wireless().programOutputs("damage_accumulation",Map.of());owner=null;}
}
