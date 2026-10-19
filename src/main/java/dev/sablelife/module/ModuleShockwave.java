package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class ModuleShockwave {
 private static final class Clock{boolean high,touching;ShockwaveZoomPacket zoom;long nextZoom;final Set<UUID> viewers=new HashSet<>();long contactDue,due,token=Long.MIN_VALUE,last=Long.MIN_VALUE;}
 private final Map<UUID,Clock> states=new HashMap<>();private ServerLevel level;
 private void release(Clock state){if(level!=null&&state.zoom!=null&&level.getServer().isRunning())for(var player:level.players())if(state.viewers.contains(player.getUUID()))PacketDistributor.sendToPlayer(player,state.zoom.released());state.zoom=null;state.viewers.clear();}
 public void clear(){states.values().forEach(this::release);states.clear();level=null;}
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
 private static ShockwavePacket withoutZoom(ShockwavePacket p){return new ShockwavePacket(p.dimension(),p.point(),p.radius(),p.expansion(),p.width(),p.distortion(),p.blur(),p.shake(),p.shakeTicks(),0,p.zoomTicks(),p.range(),p.reverse());}
 public static ShockwavePacket packet(HeartBlockEntity heart,VirtualModule m){return packet(heart,m,m.anchors().getFirst().world(heart,false));}
 private static ShockwavePacket packet(HeartBlockEntity heart,VirtualModule m,net.minecraft.world.phys.Vec3 point){return new ShockwavePacket(heart.getLevel().dimension().location(),point,(float)m.value("shockRadius"),(int)m.value("shockExpansion"),(float)m.value("shockWidth"),(float)m.value("shockDistortion"),(float)m.value("shockBlur"),(float)m.value("shockShake"),(int)m.value("shockShakeTime"),(float)m.value("shockZoom"),(int)m.value("shockZoomTime"),(float)m.value("shockRange"),m.value("shockReverse")==1);}
 private static void send(ServerPlayer p,ShockwavePacket packet){if(VisualBudget.allow(p,32))PacketDistributor.sendToPlayer(p,packet);}
 public static boolean preview(ServerPlayer p,HeartBlockEntity heart,VirtualModule m){if(!heart.canEdit(p)||!List.of("valid","disabled").contains(HeartModules.status(m,heart)))return false;var packet=packet(heart,m);if(!packet.valid())return false;send(p,packet);return true;}
 public void tick(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()){clear();return;}
  this.level=level;var keep=new HashSet<UUID>();long now=level.getGameTime();
  for(var m:heart.modules().all())if(m.type().equals("visual_shockwave")){
   keep.add(m.id());var s=states.computeIfAbsent(m.id(),ignored->new Clock());if(s.last==now)continue;s.last=now;
   boolean high=HeartModules.triggered(m,heart);long token=m.value("trigger")==4?heart.modules().moveActivation(m.id()):Long.MIN_VALUE;
   boolean held=high&&m.value("shockMode")==1&&m.value("shockContact")==0&&m.value("shockZoom")!=0;
   if(s.zoom!=null){if(held)hold(s,m,heart,now);else release(s);}
   boolean edge=high&&(!s.high||token!=s.token);s.high=high;s.token=token;
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
   var packet=point==null?packet(heart,m):packet(heart,m,point);if(held){if(s.zoom==null)hold(s,m,heart,now);packet=withoutZoom(packet);}if(packet.valid())for(var player:level.players())if(player.position().distanceToSqr(packet.point())<=packet.range()*packet.range())send(player,packet);
   heart.modules().hurtTriggers.completed(m);
  }
  states.entrySet().removeIf(e->{if(keep.contains(e.getKey()))return false;release(e.getValue());return true;});
 }
}
