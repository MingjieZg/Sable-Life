package dev.sablelife.module;
import dev.sablelife.heart.*;
import java.util.*;
/** Self HP adjustment is administrative, not a incoming attack or a hit-counter event. */
public final class ModuleHealthAdjustment {
 private static final class Clock {boolean high;long next,token=Long.MIN_VALUE,last=Long.MIN_VALUE;}
 private final Map<UUID,Clock> states=new HashMap<>();
 public void clear(){states.clear();}
 public void tick(HeartBlockEntity heart){
  if(heart.health().state()!=HeartState.ALIVE||!heart.wireless().master()){clear();return;}
  var keep=new HashSet<UUID>();long now=heart.getLevel().getGameTime();
  for(var m:heart.modules().all())if(m.type().equals("health_adjustment")){
   keep.add(m.id());var s=states.computeIfAbsent(m.id(),ignored->new Clock());if(s.last==now)continue;s.last=now;
   boolean powered=HeartModules.triggered(m,heart);long token=m.value("trigger")==4?heart.modules().moveActivation(m.id()):Long.MIN_VALUE;
   boolean edge=powered&&(!s.high||token!=s.token);s.high=powered;s.token=token;
   if(!powered)continue;if(!edge&&(m.value("healthMode")==0||now<s.next))continue;
   if(!heart.modules().hurtTriggers.start(m))continue;s.next=now+Math.max(1,(long)m.value("healthInterval"));
   double amount=m.value("healthAmount")*(m.value("healthUnits")==1?heart.health().maximum()/100.:1);
   heart.adjustOwnHealth(amount,m.value("healthLethal")==1);heart.modules().hurtTriggers.completed(m);
   if(heart.health().state()!=HeartState.ALIVE)return;
  }
  states.keySet().retainAll(keep);
 }
}
