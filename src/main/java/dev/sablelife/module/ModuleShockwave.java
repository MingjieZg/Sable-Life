package dev.sablelife.module;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class ModuleShockwave {
 private static final class Clock{boolean high,touching;VisualFilterPacket filter;long nextFilter;final Set<UUID> filterViewers=new HashSet<>();ShockwaveZoomPacket zoom;long nextZoom;final Set<UUID> viewers=new HashSet<>();long contactDue,due,token=Long.MIN_VALUE,last=Long.MIN_VALUE;}
 private final Map<UUID,Clock> states=new HashMap<>();private ServerLevel level;
 private void release(Clock state){if(level!=null&&state.zoom!=null&&level.getServer().isRunning())for(var player:level.players())if(state.viewers.contains(player.getUUID()))PacketDistributor.sendToPlayer(player,state.zoom.released());state.zoom=null;state.viewers.clear();}
 public void clear(){states.values().forEach(s->{release(s);releaseFilter(s);});states.clear();level=null;}
 private void hold(Clock state,VirtualModule m,HeartBlockEntity heart,long now){
  var p=packet(heart,m);state.zoom=new ShockwaveZoomPacket(p.dimension(),m.id(),p.point(),(float)m.value("shockZoom"),p.zoomTicks(),p.range(),true);
  var nearby=new HashSet<UUID>();boolean refresh=now>=state.nextZoom;
  for(var player:level.players()){
   boolean inRange=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(p.point())<=p.range()*p.range();
   if(!inRange){if(state.viewers.contains(player.getUUID()))PacketDistributor.sendToPlayer(player,state.zoom.released());continue;}
   if(!state.viewers.contains(player.getUUID())||refresh){if(VisualBudget.allow(player,1)){PacketDistributor.sendToPlayer(player,state.zoom);nearby.add(player.getUUID());}else if(state.viewers.contains(player.getUUID()))nearby.add(player.getUUID());}else nearby.add(player.getUUID());
  }
  state.viewers.clear();state.viewers.addAll(nearby);if(refresh)state.nextZoom=now+10;
 }
 private void releaseFilter(Clock state){if(level!=null&&state.filter!=null&&level.getServer().isRunning())for(var player:level.players())if(state.filterViewers.contains(player.getUUID()))PacketDistributor.sendToPlayer(player,state.filter.released());state.filter=null;state.filterViewers.clear();}
 public static VisualFilterPacket filterPacket(HeartBlockEntity heart,VirtualModule m){var rgb=ModuleColors.vector(m,"filterColor");int color=(Math.round(rgb.x*255)<<16)|(Math.round(rgb.y*255)<<8)|Math.round(rgb.z*255);return new VisualFilterPacket(heart.getLevel().dimension().location(),m.id(),color,(float)m.value("filterStrength"),(int)m.value("filterFadeIn"),(int)m.value("filterFadeOut"),true);}
 private void holdFilter(Clock state,VirtualModule m,HeartBlockEntity heart,long now){
  var next=filterPacket(heart,m);boolean refresh=now>=state.nextFilter||!next.equals(state.filter);state.filter=next;var point=m.anchors().getFirst().world(heart,false);double range=m.value("shockRange");var nearby=new HashSet<UUID>();
  for(var player:level.players()){
   boolean inside=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(point)<=range*range;
   if(!inside){if(state.filterViewers.contains(player.getUUID()))PacketDistributor.sendToPlayer(player,next.released());continue;}
   if(!state.filterViewers.contains(player.getUUID())||refresh){if(VisualBudget.allow(player,1)){PacketDistributor.sendToPlayer(player,next);nearby.add(player.getUUID());}else if(state.filterViewers.contains(player.getUUID()))nearby.add(player.getUUID());}else nearby.add(player.getUUID());
  }
  state.filterViewers.clear();state.filterViewers.addAll(nearby);if(refresh)state.nextFilter=now+10;
 }
 private static ShockwavePacket withoutZoom(ShockwavePacket p){return new ShockwavePacket(p.dimension(),p.point(),p.radius(),p.expansion(),p.width(),p.distortion(),p.blur(),p.shake(),p.shakeTicks(),0,p.zoomTicks(),p.range(),p.reverse(),p.shakeFrequency(),p.pattern(),p.clearRadius());}
 public static ShockwavePacket packet(HeartBlockEntity heart,VirtualModule m){return packet(heart,m,m.anchors().getFirst().world(heart,false));}
 private static ShockwavePacket packet(HeartBlockEntity heart,VirtualModule m,net.minecraft.world.phys.Vec3 point){return new ShockwavePacket(heart.getLevel().dimension().location(),point,(float)m.value("shockRadius"),(int)m.value(m.value("shockPattern")==1?"shockRadialTime":"shockExpansion"),(float)m.value("shockWidth"),m.value("shockWaveEnabled")==1&&m.value("shockPattern")==0?(float)m.value("shockDistortion"):0,m.value("shockWaveEnabled")==1&&m.value("shockPattern")<=1?(float)m.value(m.value("shockPattern")==1?"shockRadialStrength":"shockBlur"):0,m.value("shockShakeEnabled")==1?(float)m.value("shockShake"):0,(int)m.value("shockShakeTime"),m.value("shockZoomEnabled")==1?(float)m.value("shockZoom"):0,(int)m.value("shockZoomTime"),(float)m.value("shockRange"),m.value("shockReverse")==1,(float)m.value("shockShakeFrequency"),m.value("shockPattern")==1?1:0,(float)(m.value("shockRadialClear")/100));}
 private static void sendPattern(ServerPlayer player,HeartBlockEntity core,VirtualModule module,net.minecraft.world.phys.Vec3 contact){
  if(module.value("shockWaveEnabled")!=1||module.value("shockPattern")<2)return;
  var effect=PatternVisual.create(core,module,contact);
  if(effect.valid()&&dev.sablelife.compat.sable.SableBodyProbe.toWorld(core.getLevel(),player.position()).distanceToSqr(effect.point())<=effect.range()*effect.range()&&VisualBudget.allow(player,32))PacketDistributor.sendToPlayer(player,effect);
 }
 private static void send(ServerPlayer p,ShockwavePacket packet){if(VisualBudget.allow(p,32))PacketDistributor.sendToPlayer(p,packet);}
 private static void broadcast(ServerLevel level,ShockwavePacket packet){
  double rangeSquared=packet.range()*packet.range();
  for(var player:level.players()){
   var position=player.position();
   if(position.distanceToSqr(packet.point())<=rangeSquared){
    send(player,packet);
   }else if(packet.shake()>0&&SableBodyProbe.toWorld(level,position).distanceToSqr(packet.point())<=rangeSquared){
    // Plot-local player positions can miss the usual visual range check.
    send(player,new ShockwavePacket(packet.dimension(),packet.point(),packet.radius(),packet.expansion(),packet.width(),
            0,0,packet.shake(),packet.shakeTicks(),0,packet.zoomTicks(),packet.range(),packet.reverse(),packet.shakeFrequency(),packet.pattern(),packet.clearRadius()));
   }
  }
 }
 public static boolean preview(ServerPlayer p,HeartBlockEntity heart,VirtualModule m){if(!heart.canEdit(p)||!List.of("valid","disabled").contains(HeartModules.status(m,heart)))return false;var packet=packet(heart,m);if(!packet.valid())return false;send(p,packet);sendPattern(p,heart,m,null);if(m.value("filterEnabled")==1)PacketDistributor.sendToPlayer(p,filterPacket(heart,m));return true;}
 public void tick(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()){clear();return;}
  this.level=level;var keep=new HashSet<UUID>();long now=level.getGameTime();
  for(var m:heart.modules().all())if(m.type().equals("visual_shockwave")){
   keep.add(m.id());var s=states.computeIfAbsent(m.id(),ignored->new Clock());if(s.last==now)continue;s.last=now;
   boolean high=HeartModules.triggered(m,heart);long token=m.value("trigger")==4?heart.modules().moveActivation(m.id()):Long.MIN_VALUE;
   if(high&&m.value("filterEnabled")==1&&m.value("filterStrength")>0)holdFilter(s,m,heart,now);else if(s.filter!=null)releaseFilter(s);
   boolean held=high&&m.value("shockZoomEnabled")==1&&m.value("shockMode")==1&&m.value("shockContact")==0&&m.value("shockZoom")!=0;
   if(s.zoom!=null){if(held)hold(s,m,heart,now);else release(s);}
   boolean edge=high&&(!s.high||token!=s.token);s.high=high;s.token=token;
   boolean pulse=m.value("shockWaveEnabled")==1&&(m.value("shockPattern")>=2|| (m.value("shockPattern")==1?m.value("shockRadialStrength")>0:m.value("shockDistortion")>0||m.value("shockBlur")>0))||m.value("shockShakeEnabled")==1&&m.value("shockShake")>0||m.value("shockZoomEnabled")==1&&m.value("shockZoom")!=0;
   if(!pulse)continue;
   net.minecraft.world.phys.Vec3 point=null;
   if(m.value("shockContact")==1){
    var hit=high?ModuleEvents.contactHit(level,heart,m):null;boolean touching=hit!=null;
    boolean contactEdge=touching&&!s.touching;s.touching=touching;
    if(!contactEdge||now<s.contactDue||!heart.modules().hurtTriggers.start(m))continue;
    s.contactDue=now+Math.max(1,(long)m.value("contactCooldown"));
    point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,hit.getLocation());
   }else{
    s.touching=false;
    if(!high||!edge&&(m.value("shockMode")==0||now<s.due)||!heart.modules().hurtTriggers.start(m))continue;
    s.due=now+Math.max(1,(long)m.value("shockInterval"));
   }
   var packet=point==null?packet(heart,m):packet(heart,m,point);if(held){if(s.zoom==null)hold(s,m,heart,now);packet=withoutZoom(packet);}if(packet.valid())broadcast(level,packet);if(m.value("shockPattern")>=2)for(var viewer:level.players())sendPattern(viewer,heart,m,point);
   heart.modules().hurtTriggers.completed(m);
  }
  states.entrySet().removeIf(e->{if(keep.contains(e.getKey()))return false;release(e.getValue());releaseFilter(e.getValue());return true;});
 }
}
