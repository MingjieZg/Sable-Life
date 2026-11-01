package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import java.util.*;

/** One sampled input frame, then one combined write. A chain advances at most one logic hop per tick. */
public final class ModuleSignalLogic {
 private static final class Clock {net.minecraft.nbt.CompoundTag config;boolean previous;long until;Clock(VirtualModule m){config=m.save();}}
 private final Map<UUID,Clock> clocks=new HashMap<>();private final Map<UUID,net.minecraft.nbt.CompoundTag> runtime=new HashMap<>();private HeartBlockEntity owner;private long tick=Long.MIN_VALUE;
 public net.minecraft.nbt.CompoundTag snapshot(UUID id){return runtime.getOrDefault(id,new net.minecraft.nbt.CompoundTag()).copy();}
 public void clear(){clocks.clear();runtime.clear();tick=Long.MIN_VALUE;if(owner!=null)owner.wireless().programOutputs("signal_logic",Map.of());owner=null;}
 public void tick(HeartBlockEntity heart){long now=heart.getLevel().getGameTime();if(tick==now)return;tick=now;owner=heart;runtime.clear();var outputs=new HashMap<Integer,Integer>();var keep=new HashSet<UUID>();
  var inputs=new HashMap<Integer,Integer>();var eligible=new HashMap<UUID,Boolean>();
  for(var m:heart.modules().all())if(m.type().equals("signal_logic")){eligible.put(m.id(),heart.operational()&&heart.wireless().master()&&HeartModules.triggered(m,heart));for(var r:m.signalRules().rows()){int c=(int)r.value("signalInput")-1;if(c>=0&&c<heart.wireless().size()&&!inputs.containsKey(c))inputs.put(c,heart.wireless().channel(c).receiving()?heart.wireless().input(c):heart.wireless().output(c));}}
  for(var m:heart.modules().all())if(m.type().equals("signal_logic")){
   var data=new net.minecraft.nbt.CompoundTag();var rows=new net.minecraft.nbt.ListTag();boolean any=false,all=true,valid=true;int count=0;int output=(int)m.value("logicChannel");
   for(var r:m.signalRules().rows()){int channel=(int)r.value("signalInput");boolean ready=channel>0&&channel<=heart.wireless().size()&&channel!=output&&heart.wireless().channel(channel-1).configured();boolean match=ready&&((inputs.getOrDefault(channel-1,0)>0)==(r.value("signalCondition")==0));var row=new net.minecraft.nbt.CompoundTag();row.putUUID("Id",r.id());row.putBoolean("Match",match);row.putBoolean("Ready",ready);rows.add(row);if(r.enabled()){count++;valid&=ready;any|=match;all&=match;}}
   boolean matched=count>0&&valid&&(m.value("logicMatch")==0?all:any);boolean enabled=eligible.getOrDefault(m.id(),false);boolean ready=ModuleHealthPhase.channelStatus(output,heart).equals("ready");boolean condition=enabled&&ready&&matched;
   Clock clock=clocks.get(m.id());var config=m.save();if(clock==null||!clock.config.equals(config)){clock=new Clock(m);clocks.put(m.id(),clock);}keep.add(m.id());
   if(!enabled||!ready){clock.previous=false;clock.until=0;}else if(condition&&!clock.previous)clock.until=now+(long)m.value("logicPulse");clock.previous=condition;
   boolean powered=enabled&&ready&&(m.value("logicMode")==0?condition:now<clock.until);if(powered)outputs.merge(output-1,(int)m.value("logicPower"),Math::max);
   data.put("Rows",rows);data.putBoolean("Matched",matched);data.putBoolean("Active",powered);data.putBoolean("InputsValid",count>0&&valid);data.putString("ChannelStatus",ModuleHealthPhase.channelStatus(output,heart));runtime.put(m.id(),data);
  }
  clocks.keySet().retainAll(keep);heart.wireless().programOutputs("signal_logic",outputs);
 }
}
