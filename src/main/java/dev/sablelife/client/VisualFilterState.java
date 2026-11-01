package dev.sablelife.client;
import dev.sablelife.module.VisualFilterPacket;
import java.util.*;
/** Heartbeats renew ownership, never reset a fade. Multiple sources blend without order dependence. */
public final class VisualFilterState {
 public static final int LIMIT=64,LEASE=60;
 public record Tint(float red,float green,float blue,float alpha){public static final Tint NONE=new Tint(0,0,0,0);}
 private static final class Track {
  VisualFilterPacket packet;Tint from,to;double changed,lastSeen;int duration;boolean held;
  Tint value(double now){float t=duration==0?1:(float)Math.clamp((now-changed)/duration,0,1);t=t*t*(3-2*t);return new Tint(from.red+(to.red-from.red)*t,from.green+(to.green-from.green)*t,from.blue+(to.blue-from.blue)*t,from.alpha+(to.alpha-from.alpha)*t);}
  void release(double now){if(!held)return;from=value(now);to=new Tint(from.red,from.green,from.blue,0);changed=now;duration=packet.fadeOut();held=false;}
 }
 private final Map<UUID,Track> tracks=new LinkedHashMap<>();
 public void clear(){tracks.clear();}
 public void accept(VisualFilterPacket p,double now){
  if(!p.valid())return;var t=tracks.get(p.module());if(!p.active()){if(t!=null){t.packet=p;t.release(now);}return;}
  if(t==null){if(tracks.size()>=LIMIT)tracks.remove(tracks.keySet().iterator().next());t=new Track();t.from=new Tint(((p.color()>>16)&255)/255F,((p.color()>>8)&255)/255F,(p.color()&255)/255F,0);t.to=t.from;t.changed=now;tracks.put(p.module(),t);}
  if(!t.held||t.packet.color()!=p.color()||t.packet.strength()!=p.strength()){t.from=t.value(now);t.to=new Tint(((p.color()>>16)&255)/255F,((p.color()>>8)&255)/255F,(p.color()&255)/255F,p.strength());t.changed=now;t.duration=p.fadeIn();}
  t.packet=p;t.held=true;t.lastSeen=now;
 }
 public void tick(double now){for(var t:tracks.values())if(t.held&&now>=t.lastSeen+LEASE)t.release(t.lastSeen+LEASE);tracks.values().removeIf(t->!t.held&&now>=t.changed+t.duration);}
 public Tint tint(double now){tick(now);double r=0,g=0,b=0,total=0,transmission=1;for(var t:tracks.values()){var v=t.value(now);r+=v.red*v.alpha;g+=v.green*v.alpha;b+=v.blue*v.alpha;total+=v.alpha;transmission*=1-v.alpha;}return total<=1e-8?Tint.NONE:new Tint((float)(r/total),(float)(g/total),(float)(b/total),(float)(1-transmission));}
}
