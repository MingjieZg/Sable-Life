package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

public final class ModuleDistortionTrail {
 private static final class Track{final TrailMotion motion=new TrailMotion();ModuleAnchor anchor;Vec3 half;double offset;long tick=Long.MIN_VALUE;}
 private final Map<UUID,Track> tracks=new HashMap<>();
 public void clear(){tracks.clear();}
 public static Vec3 half(VirtualModule m){return new Vec3(m.value("trailWidth")/2,m.value("trailHeight")/2,m.value("trailLength")/2);}
 public static TrailMotion.Pose pose(HeartBlockEntity heart,VirtualModule m){
  var a=m.anchors().getFirst();var body=a.body(heart);var face=Vec3.atLowerCornerOf(a.face().getNormal());
  var q=new Quaterniond(body.logicalPose().orientation()).mul(new Quaterniond().rotationTo(0,0,1,face.x,face.y,face.z)).normalize();
  var center=a.world(heart,false).add(ContactBox.rotate(q,new Vec3(0,0,m.value("trailOffset")+m.value("trailLength")/2)));
  return new TrailMotion.Pose(center,q);
 }
 public static DistortionTrailPacket packet(HeartBlockEntity heart,VirtualModule m,List<TrailMotion.Pose> poses){return new DistortionTrailPacket(heart.getLevel().dimension().location(),m.id(),poses.stream().map(TrailMotion.Pose::sample).toList(),(float)m.value("trailWidth"),(float)m.value("trailHeight"),(float)m.value("trailLength"),(int)m.value("trailLife"),(float)m.value("trailDistortion"),(float)m.value("trailRange"));}
 private static void send(ServerPlayer player,DistortionTrailPacket p){if(p.valid()&&VisualBudget.allow(player,p.samples().size()*4))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,p);}
 public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
  if(!List.of("valid","disabled").contains(HeartModules.status(m,heart)))return;var pose=pose(heart,m);var half=half(m);
  MarkOutline.send(player,MarkOutline.Kind.DETAIL,0x4DE6FF,MarkOutline.box(half.scale(-1),half,
    point->pose.center().add(ContactBox.rotate(pose.rotation(),point))));
 }
 public void tick(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()){clear();return;}
  long now=level.getGameTime();var keep=new HashSet<UUID>();
  for(var m:heart.modules().all())if(m.type().equals("distortion_trail")&&HeartModules.triggered(m,heart)){
   keep.add(m.id());var t=tracks.computeIfAbsent(m.id(),ignored->new Track());if(t.tick==now)continue;t.tick=now;
   var anchor=m.anchors().getFirst();var half=half(m);if(!anchor.equals(t.anchor)||!half.equals(t.half)||t.offset!=m.value("trailOffset")){t.motion.clear();t.anchor=anchor;t.half=half;t.offset=m.value("trailOffset");}
   var points=t.motion.advance(pose(heart,m),half,m.value("trailThreshold"),m.value("trailSpacing"));if(points.isEmpty())continue;
   var packet=packet(heart,m,points);for(var player:level.players())if(points.stream().anyMatch(p->p.center().distanceToSqr(player.position())<=packet.range()*packet.range()))send(player,packet);
  }
  tracks.keySet().retainAll(keep);
 }
}
