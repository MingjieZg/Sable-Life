package dev.sablelife.client;
import dev.sablelife.module.ShockwaveZoomPacket;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Repeated updates renew a hold, never restart an envelope or stack the same module. */
public final class ShockwaveZoomState {
 public static final int LIMIT=64,LEASE=60;
 private static final class Track {
  ShockwaveZoomPacket packet;double from,target,changed,lastSeen;boolean held;
  double value(double now){double duration=held?3:packet.recovery(),t=Math.clamp((now-changed)/duration,0,1);t=t*t*(3-2*t);return from+(target-from)*t;}
  void release(double now){if(!held)return;from=value(now);target=0;changed=now;held=false;}
 }
 private final Map<UUID,Track> tracks=new LinkedHashMap<>();
 public void clear(){tracks.clear();}
 public void accept(ShockwaveZoomPacket packet,double now){
  if(!packet.valid())return;var track=tracks.get(packet.module());
  if(!packet.active()){if(track!=null){track.packet=packet;track.release(now);}return;}
  if(track==null){if(tracks.size()>=LIMIT)tracks.remove(tracks.keySet().iterator().next());track=new Track();track.changed=now;tracks.put(packet.module(),track);}
  if(!track.held||track.target!=packet.zoom()){double value=track.packet==null?0:track.value(now);track.from=value;track.target=packet.zoom();track.changed=now;}
  track.packet=packet;track.held=true;track.lastSeen=now;
 }
 public void tick(double now){for(var track:tracks.values())if(track.held&&now>=track.lastSeen+LEASE)track.release(track.lastSeen+LEASE);tracks.values().removeIf(t->!t.held&&now>=t.changed+t.packet.recovery());}
 public double zoom(double now,Vec3 camera){tick(now);double result=0;for(var track:tracks.values())result+=track.value(now)*ShockwaveMath.attenuation(camera.distanceTo(track.packet.point()),track.packet.range());return result;}
}
