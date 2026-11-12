package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Both modes share attributed actual HP loss and the existing internal/external publication pass. */
public final class ModuleDamageAccumulation {
    private static final class State {
        final boolean timed,invert,repeat,part;final double threshold;final int duration;
        final ModuleAnchor watched;
        final DamageAccumulator counter=new DamageAccumulator();final TimedDamageWindow window=new TimedDamageWindow();boolean high;
        State(VirtualModule m){timed=m.value("accumMode")==1;invert=m.value("accumInvert")==1;repeat=m.value("accumRepeat")==1;threshold=m.value(timed?"accumRequired":"accumThreshold");duration=(int)m.value("accumWindow");part=m.listensToPart();watched=m.watchedPart();}
        boolean matches(VirtualModule m){boolean mode=m.value("accumMode")==1;return part==m.listensToPart()&&(!part||Objects.equals(watched,m.watchedPart()))&&timed==mode&&threshold==m.value(mode?"accumRequired":"accumThreshold")&&(!timed||duration==(int)m.value("accumWindow")&&repeat==(m.value("accumRepeat")==1)&&invert==(m.value("accumInvert")==1));}
    }
    private final Map<UUID,State> states=new HashMap<>();private HeartBlockEntity owner;
    private State state(VirtualModule m){return states.compute(m.id(),(id,old)->old!=null&&old.matches(m)?old:new State(m));}
    private static boolean active(VirtualModule m,HeartBlockEntity heart){return heart.health().state()==HeartState.ALIVE&&heart.wireless().master()&&HeartModules.triggered(m,heart);}
    private static boolean outputReady(VirtualModule m,HeartBlockEntity heart){return channelStatus(m,heart).equals("ready")||m.hasRedBlue();}
    private static boolean advance(State s,VirtualModule m,HeartBlockEntity heart){return s.window.tick(heart.getLevel().getGameTime(),active(m,heart)&&outputReady(m,heart),s.duration,s.threshold,s.repeat,s.invert,(int)m.value("accumPulse"));}
    public void record(HeartBlockEntity heart,double actualLoss){record(heart,null,actualLoss);}
    public void record(HeartBlockEntity heart,BlockPos hit,double actualLoss){
        if(!Double.isFinite(actualLoss)||actualLoss<=0||heart.health().state()!=HeartState.ALIVE||!heart.wireless().master())return;
        owner=heart;
        for(var m:heart.modules().all())if(m.type().equals("damage_accumulation")&&HeartModules.triggered(m,heart)){
            if(m.listensToPart()&&!ModulePartBreak.watches(m,ModulePartBreak.hitBody(heart,hit)))continue;
            var s=state(m);if(s.timed){s.high=advance(s,m,heart);s.window.add(actualLoss);}else s.counter.add(actualLoss,s.threshold);
        }
    }
    public static String channelStatus(VirtualModule m,HeartBlockEntity heart){return ModuleHealthPhase.channelStatus(m,heart,"accumChannel");}
    public void tick(HeartBlockEntity heart,Map<UUID,HeartWireless.ModuleOutput> external){
        owner=heart;var retained=new HashSet<UUID>();var internal=new HashMap<Integer,Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();
        if(heart.health().state()==HeartState.ALIVE)for(var m:heart.modules().all()){
            if(!m.type().equals("damage_accumulation"))continue;retained.add(m.id());var s=state(m);
            boolean ready=channelStatus(m,heart).equals("ready"),enabled=active(m,heart);
            s.high=s.timed?advance(s,m,heart):s.counter.tick(heart.getLevel().getGameTime(),enabled&&(ready||m.hasRedBlue()),(int)m.value("accumPulse"));
            boolean powered=s.timed?s.high:s.high!=(m.value("accumInvert")==1);
            int power=enabled&&powered?(int)m.value("accumPower"):0;
            if(ready&&power>0){internal.merge((int)m.value("accumChannel")-1,power,Math::max);ChannelDiagnostics.record(trace,m.id(),(int)m.value("accumChannel")-1,power);}
            if(m.hasRedBlue())external.put(m.id(),new HeartWireless.ModuleOutput(m.redBlue(false,heart.getLevel().registryAccess()),m.redBlue(true,heart.getLevel().registryAccess()),power));
        }
        states.keySet().retainAll(retained);heart.wireless().programOutputs("damage_accumulation",internal,trace);
    }
    public CompoundTag snapshot(VirtualModule m,HeartBlockEntity heart){
        var tag=new CompoundTag();var s=states.get(m.id());boolean timed=m.value("accumMode")==1;tag.putBoolean("Timed",timed);
        if(s!=null&&s.matches(m)){
            tag.putDouble("Accumulated",timed?s.window.damage():s.counter.accumulated());tag.putLong("Pending",timed?0:s.counter.pending());tag.putBoolean("High",timed?s.high:s.counter.high());
            if(timed){tag.putBoolean("Running",s.window.running());tag.putBoolean("Completed",s.window.completed());tag.putBoolean("Met",s.window.met());tag.putDouble("LastDamage",s.window.lastDamage());tag.putInt("Remaining",s.window.remaining(heart.getLevel().getGameTime()));}
        }
        tag.putString("ChannelStatus",channelStatus(m,heart));return tag;
    }
    public void reset(UUID id){states.remove(id);}
    public void clear(){states.clear();if(owner!=null)owner.wireless().programOutputs("damage_accumulation",Map.of());owner=null;}
}
