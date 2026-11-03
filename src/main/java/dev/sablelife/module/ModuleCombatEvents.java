package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.*;
import java.util.*;

/** Event occurrences arm bounded per-rule pulse clocks; no queue or runtime survives release/reload. */
public final class ModuleCombatEvents {
 private record Key(UUID module,UUID row){}
 private static final class Clock {final SignalRules.Row row;final int revision;long until,next;int count;boolean pending;Clock(SignalRules.Row r){row=r;revision=r.revision();}}
 private final Map<Key,Clock> clocks=new HashMap<>();private final Map<UUID,CompoundTag> runtime=new HashMap<>();
 private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();private HeartBlockEntity owner;private long tick=Long.MIN_VALUE;
 public CompoundTag snapshot(UUID id){return runtime.getOrDefault(id,new CompoundTag()).copy();}
 public void clear(){clocks.clear();runtime.clear();seen.clear();tick=Long.MIN_VALUE;if(owner!=null)owner.wireless().programOutputs("combat_event",Map.of());owner=null;}
 /** Stable hook IDs: kill=0, blocked=2. Retired part hook 1 is ignored. */
 public void emit(HeartBlockEntity heart,int kind,int filter,UUID source,UUID subject){
  if(kind!=0&&kind!=2)return;owner=heart;if(!heart.operational()||!heart.wireless().master())return;long now=heart.getLevel().getGameTime();
  // Ordinary victims can die again after respawn. Mechanical life IDs identify one activation.
  // Duplicate reports within one tick still count only once.
  String key=kind+":"+filter+":"+subject+(kind==2?":"+source+":"+now:kind==0&&filter!=2?":"+now:"");if(seen.containsKey(key))return;
  boolean used=false;
  for(var m:heart.modules().all())if(m.type().equals("combat_event")&&HeartModules.triggered(m,heart))for(var row:m.signalRules().rows()){
   if(!row.enabled()||(int)row.value("eventKind")!=kind||!ModuleHealthPhase.channelStatus((int)row.value("eventChannel"),heart).equals("ready"))continue;
   int selected=(int)row.value("eventFilter");boolean matches=kind==0?(selected==3||selected==filter):(selected==0||selected==filter);
   if(!matches||row.source()!=null&&(!row.source().equals(source)||heart.modules().find(row.source())==null))continue;
   var k=new Key(m.id(),row.id());var c=clocks.get(k);if(c==null||c.row!=row||c.revision!=row.revision()){c=new Clock(row);clocks.put(k,c);}if(now<c.next)continue;
   used=true;if(++c.count<(int)row.value("eventCount"))continue;c.count=0;c.pending=true;c.next=now+(long)row.value("eventCooldown");
  }
  if(used){seen.put(key,now);while(seen.size()>2048)seen.remove(seen.keySet().iterator().next());}
 }
 public void tick(HeartBlockEntity heart){long now=heart.getLevel().getGameTime();if(tick==now)return;tick=now;owner=heart;runtime.clear();var outputs=new HashMap<Integer,Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();var keep=new HashSet<Key>();
  if(!heart.operational()||!heart.wireless().master()){clear();return;}
  seen.entrySet().removeIf(e->(e.getKey().startsWith("2:")||e.getKey().startsWith("0:0:")||e.getKey().startsWith("0:1:"))&&now-e.getValue()>2);
  for(var m:heart.modules().all())if(m.type().equals("combat_event")){
   var data=new CompoundTag();var rows=new ListTag();boolean enabled=HeartModules.triggered(m,heart);int active=0;
   for(var row:m.signalRules().rows()){var key=new Key(m.id(),row.id());keep.add(key);var clock=clocks.get(key);if(clock!=null&&(clock.row!=row||clock.revision!=row.revision())){clocks.remove(key);clock=null;}
    boolean ready=enabled&&row.enabled()&&ModuleHealthPhase.channelStatus((int)row.value("eventChannel"),heart).equals("ready");
    if(clock!=null){if(!ready){clocks.remove(key);clock=null;}else if(clock.pending){clock.until=now+(long)row.value("eventPulse");clock.pending=false;}}
    boolean powered=ready&&clock!=null&&now<clock.until;var state=new CompoundTag();state.putUUID("Id",row.id());state.putBoolean("Active",powered);state.putString("ChannelStatus",ModuleHealthPhase.channelStatus((int)row.value("eventChannel"),heart));state.putInt("Count",clock==null?0:clock.count);state.putInt("Required",(int)row.value("eventCount"));state.putInt("Remaining",clock==null?0:(int)Math.max(0,clock.until-now));rows.add(state);
    if(powered){active++;outputs.merge((int)row.value("eventChannel")-1,(int)row.value("eventPower"),Math::max);ChannelDiagnostics.record(trace,m.id(),(int)row.value("eventChannel")-1,(int)row.value("eventPower"));}
   }data.put("Rows",rows);data.putInt("ActiveCount",active);runtime.put(m.id(),data);
  }
  clocks.keySet().retainAll(keep);heart.wireless().programOutputs("combat_event",outputs,trace);
 }
}
