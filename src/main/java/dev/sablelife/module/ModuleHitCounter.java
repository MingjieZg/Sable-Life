package dev.sablelife.module;
import dev.sablelife.heart.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
/** Successful HP losses only. Pulses latch one cycle, with bounded per-module clocks. */
public final class ModuleHitCounter {
 public static final Set<String> ATTACKS=Set.of("melee","flame","arrow","gun","mortar","homing","laser","sphere_blast","slash","ground_strike","quake","pulse");
 private static final class State {final DamageAccumulator count=new DamageAccumulator();long next=Long.MIN_VALUE;double threshold;UUID source;State(VirtualModule m){threshold=m.value("hitThreshold");source=m.hitSourceId();}}
 private final Map<UUID,State> states=new HashMap<>();private HeartBlockEntity owner;
 private State state(VirtualModule m){return states.compute(m.id(),(id,old)->old!=null&&old.threshold==m.value("hitThreshold")&&Objects.equals(old.source,m.hitSourceId())?old:new State(m));}
 public void record(HeartBlockEntity heart,UUID attack,double loss){
  if(loss<=0||!Double.isFinite(loss)||heart.health().state()!=HeartState.ALIVE||!heart.wireless().master())return;
  var source=heart.modules().find(attack);if(source==null||!ATTACKS.contains(source.type()))return;owner=heart;long now=heart.getLevel().getGameTime();
  for(var m:heart.modules().all())if(m.type().equals("hit_counter")&&HeartModules.triggered(m,heart)&&(m.hitSourceId()==null||m.hitSourceId().equals(attack))){var s=state(m);if(now<s.next)continue;s.count.add(1,m.value("hitThreshold"));s.next=now+(long)m.value("hitGap");}
 }
 public void tick(HeartBlockEntity heart){
  owner=heart;var outputs=new HashMap<Integer,Integer>();var keep=new HashSet<UUID>();
  if(heart.health().state()==HeartState.ALIVE&&heart.wireless().master())for(var m:heart.modules().all())if(m.type().equals("hit_counter")&&HeartModules.triggered(m,heart)){
   keep.add(m.id());var s=state(m);boolean ready=ModuleHealthPhase.channelStatus(m,heart,"hitChannel").equals("ready");
   boolean high=s.count.tick(heart.getLevel().getGameTime(),ready,(int)m.value("hitPulse"));if(high)outputs.merge((int)m.value("hitChannel")-1,(int)m.value("hitPower"),Math::max);
  }
  states.keySet().retainAll(keep);heart.wireless().programOutputs("hit_counter",outputs);
 }
 public CompoundTag snapshot(UUID id){var t=new CompoundTag();var s=states.get(id);if(s!=null){t.putInt("Count",(int)s.count.accumulated());t.putBoolean("High",s.count.high());}return t;}
 public void clear(){states.clear();if(owner!=null)owner.wireless().programOutputs("hit_counter",Map.of());owner=null;}
}
