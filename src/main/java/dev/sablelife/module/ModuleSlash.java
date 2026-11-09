package dev.sablelife.module;
import dev.sablelife.attack.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import org.joml.Matrix3d;
import org.joml.Vector3f;
import java.util.*;
/** Frozen launch frames with swept hitboxes. Each blade hits each target once and stops at terrain. */
public final class ModuleSlash {
 private UUID emitterAudio;private UUID audio(UUID id){return emitterAudio==null?id:emitterAudio;}
 public record Frame(Vec3 direction,Vec3 right,Vec3 up){public Quaterniond rotation(){return new Quaterniond().setFromNormalized(new Matrix3d(right.x,right.y,right.z,up.x,up.y,up.z,direction.x,direction.y,direction.z));}}
 private static final class Blade{final UUID sound=UUID.randomUUID();final Set<UUID> listeners=new HashSet<>();int age;final dev.sablelife.sound.SoundTimeline audio;final Vec3 origin;final Frame frame;final VirtualModule cast;final Set<UUID> hits=new HashSet<>();double distance;Blade(Vec3 o,Frame f,VirtualModule m){origin=o;frame=f;cast=m;audio=m.soundTimeline("slashFlightSound");}}
 private static final class State{long due;final List<Blade> blades=new ArrayList<>();}
 private final Map<UUID,State> states=new HashMap<>();private ServerLevel level;private long tick=Long.MIN_VALUE;
 public void clear(){if(level!=null)states.forEach((id,s)->{ModuleAudio.stop(level,audio(id));s.blades.forEach(b->stopSound(level,b));});states.clear();level=null;tick=Long.MIN_VALUE;}
 public static List<Frame> frames(Vec3 base,VirtualModule m){
  var dir=ContactBox.rotate(MeleeGeometry.angles(base,m.value("slashHorizontal"),m.value("slashVertical")),base).normalize();var up=Math.abs(dir.y)>.99?new Vec3(0,0,1):new Vec3(0,1,0);var right=up.cross(dir).normalize();up=dir.cross(right).normalize();var result=new ArrayList<Frame>();int count=(int)m.value("slashCount");
  for(int i=0;i<count;i++){double angle=count==1?0:Math.toRadians(m.value("slashFan"))*(i/(double)(count-1)-.5);var q=new Quaterniond().rotationAxis(angle,up.x,up.y,up.z);var d=ContactBox.rotate(q,dir);var r=ContactBox.rotate(q,right);var roll=new Quaterniond().rotationAxis(Math.toRadians(m.value("slashRoll")),d.x,d.y,d.z);r=ContactBox.rotate(roll,r);result.add(new Frame(d,r,d.cross(r).normalize()));}return result;
 }
 public static boolean intersects(Vec3 from,Vec3 to,Frame f,double length,double width,AABB target){return ContactBox.intersects(from.lerp(to,.5).subtract(f.direction.scale(width/2)),new Vec3(length/2,.18,width/2+from.distanceTo(to)/2),f.rotation(),target);}
 private static SlashVisualPacket packet(Blade b,Vec3 a,Vec3 end){var m=b.cast;var styles=new net.minecraft.nbt.CompoundTag();styles.put("Ids",ModuleParticles.save(m.particles()));styles.put("Properties",ModuleParticles.saveEntries(m.particleEntries()));var rgb=ModuleColors.vector(m,"slashColor");int color=(Math.round(rgb.x*255)<<16)|(Math.round(rgb.y*255)<<8)|Math.round(rgb.z*255);return new SlashVisualPacket(a,end,b.frame.direction,b.frame.right,(float)m.value("slashLength"),(float)m.value("slashWidth"),color,m.value("slashVisual")==1,styles);}
 public static final int MAX_FLYING=256;
 /** Use the incoming direction, not the blade center: a wide or fast blade may already be behind its victim. */
 public static net.minecraft.world.damagesource.DamageSource hitSource(ServerLevel level,net.minecraft.world.entity.LivingEntity victim,Vec3 incoming,String name,VirtualModule cast){
  return cast.value("slashBlockable")==1?new BodyMeleeSource(level,victim.getBoundingBox().getCenter().subtract(incoming.normalize().scale(2)),name):ModuleDamage.physical(level);
 }
 public void tick(HeartBlockEntity heart){tick(heart,heart.modules().all());}
 public void tickEmitter(HeartBlockEntity heart,VirtualModule module){emitterAudio=module.audioId();tick(heart,List.of(module));}
 private void tick(HeartBlockEntity heart,List<VirtualModule> modules){
  if(!(heart.getLevel() instanceof ServerLevel server))return;level=server;long now=server.getGameTime();if(tick==now)return;tick=now;
  if(!heart.operational()){clear();return;}
  // Interval clocks releases, never the lifetime of an older volley.
  for(var m:modules){
   if(!m.type().equals("slash")||!EmitterRuntime.active(m,heart))continue;var s=states.computeIfAbsent(m.id(),id->new State());
   if(now>=s.due&&s.blades.size()+(int)m.value("slashCount")<=MAX_FLYING){
    var mark=ModuleCombat.nozzle(heart,m);var directions=ProjectileAim.directions(heart,m,mark).stream().limit((MAX_FLYING-s.blades.size())/(int)m.value("slashCount")).toList();
    if(directions.isEmpty()||!EmitterRuntime.start(m,heart))continue;
    if(!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,m,directions.size())){s.due=now+(long)m.value("interval");continue;}
    var cast=EmitterRuntime.copy(m);for(var base:directions)for(var frame:frames(base,m))s.blades.add(new Blade(mark,frame,cast));s.due=now+(long)m.value("interval");ModuleAudio.play(server,m,"flightSound",mark);
   }
  }
  // Released blades own their frozen settings; dropping the trigger or mark does not erase them.
  var statesIterator=states.entrySet().iterator();while(statesIterator.hasNext()){
   var state=statesIterator.next();var s=state.getValue();
   var iterator=s.blades.iterator();while(iterator.hasNext()){
    var b=iterator.next();var from=b.origin.add(b.frame.direction.scale(b.distance));double next=Math.min(b.cast.value("range"),b.distance+b.cast.value("slashSpeed")/20);var to=b.origin.add(b.frame.direction.scale(next));
    if(!server.hasChunkAt(BlockPos.containing(to))){stopSound(server,b);iterator.remove();continue;}
    var end=SensorGeometry.beamEnd(server,heart.creatureId(),from,to);boolean blocked=end.distanceToSqr(to)>.000001;
    var bounds=new AABB(from,end).inflate(b.cast.value("slashLength")/2+b.cast.value("slashWidth"));
    for(var victim:ModuleTargets.damageQuery(server,heart.creatureId(),bounds,e->ModuleTargets.matches(e,b.cast)&&!b.hits.contains(e.getUUID()))){
     if(!intersects(from,end,b.frame,b.cast.value("slashLength"),b.cast.value("slashWidth"),victim.getBoundingBox())||!ModuleTargets.visible(server,heart.creatureId(),from,victim))continue;
     b.hits.add(victim.getUUID());
     var contact=dev.sablelife.combat.SegmentBoxContact.closest(from,end,victim.getBoundingBox(),new Vec3(1,1,1)).point();
     var source=hitSource(server,victim,b.frame.direction,heart.creatureName(),b.cast);
     boolean accepted;
     if(b.cast.value("damage")==0){
      if(source instanceof BodyMeleeSource body)body.owner(heart.creatureId()).module(b.cast.id());
      accepted=!(source instanceof BodyMeleeSource body)||(!victim.isDamageSourceBlocked(source)&&!(net.neoforged.fml.ModList.get().isLoaded("epicfight")&&dev.sablelife.compat.epicfight.EpicFightBodyGuard.blockEffectOnly(victim,body)));
      if(!accepted&&source instanceof BodyMeleeSource body){if(!body.wasBlocked())body.blocked(1);dev.sablelife.attack.CombatEventHooks.blocked(victim,body);}
     }else accepted=ModuleDamage.hurt(victim,source,(float)b.cast.value("damage"),heart,b.cast);
     if(accepted){
      SurfaceFieldImpact.entity(heart,b.cast,contact,b.frame.direction,victim.getUUID());
      ModuleEffects.apply(victim,ModuleEffects.forCore(heart,b.cast),null);
      if(b.cast.value("slashIgnite")==1&&b.cast.value("burn")>0&&!(victim instanceof ModuleTargets.LifeTarget))victim.igniteForSeconds((int)b.cast.value("burn"));
     }
    }
    if(blocked)SurfaceFieldImpact.terrainEnd(heart,b.cast,end,b.frame.direction);
    var visual=packet(b,from,end);for(var player:server.players())if(player.position().distanceToSqr(end)<256*256&&VisualBudget.allow(player,3*(Math.clamp((int)Math.ceil(b.cast.value("slashLength")/.12),12,64)+1)))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,visual);
    flightSound(server,b,end);b.age++;b.distance=next;if(blocked||next>=b.cast.value("range")){stopSound(server,b);iterator.remove();}
   }
   var m=modules.stream().filter(x->x.id().equals(state.getKey())).findFirst().orElse(null);if(s.blades.isEmpty()){
    if(m!=null)EmitterRuntime.completed(m,heart);
    if(m==null||!EmitterRuntime.active(m,heart))statesIterator.remove();
   }
  }
 }
 private static void motion(ServerPlayer player,ServerLevel level,Blade b,Vec3 point,boolean stop){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new SoundMotionPacket(level.dimension().location(),b.sound,point,stop));}
 private static void stopSound(ServerLevel level,Blade b){for(var player:level.players())if(b.listeners.contains(player.getUUID()))motion(player,level,b,b.origin,true);b.listeners.clear();}
 private static void flightSound(ServerLevel level,Blade b,Vec3 point){
  var timeline=b.audio;if(!timeline.audible())return;
  double radius=timeline.rows().stream().filter(r->r.enabled()&&r.volume()>0).mapToDouble(r->r.radius()).max().orElse(0);
  for(var player:level.players()){
   boolean known=b.listeners.contains(player.getUUID());
   if(!known&&dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(point)>radius*radius)continue;
   motion(player,level,b,point,false);
   if(!b.listeners.add(player.getUUID()))continue;
   for(var row:timeline.rows())if(row.enabled()&&row.volume()>0){
    int elapsed=Math.max(0,b.age-row.start());if(row.duration()>0&&elapsed>=row.duration())continue;
    int duration=row.duration()>0?Math.max(1,row.duration()-elapsed):0;
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ModuleSound(b.sound,row.sound(),false,point,row.volume(),row.pitch(),row.radius(),Math.max(0,row.start()-b.age),duration,row.fade(),true));
   }
  }
 }
 public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
  MarkingSessions.previewLaunch(player,heart,m);var level=(ServerLevel)heart.getLevel();var mark=ModuleCombat.nozzle(heart,m);var dust=new DustParticleOptions(new Vector3f(1,1,1),1);
  for(var base:ProjectileAim.directions(heart,m,mark))for(var f:frames(base,m)){var start=mark;var end=SensorGeometry.beamEnd(level,heart.creatureId(),start,start.add(f.direction.scale(m.value("range"))));int count=Math.clamp((int)Math.ceil(start.distanceTo(end)*2),1,256);for(int i=0;i<=count;i++){var p=start.lerp(end,i/(double)count);level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet(new Blade(end,f,m),end,end));}
 }
}
