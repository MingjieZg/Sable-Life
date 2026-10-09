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
 public record Frame(Vec3 direction,Vec3 right,Vec3 up){public Quaterniond rotation(){return new Quaterniond().setFromNormalized(new Matrix3d(right.x,right.y,right.z,up.x,up.y,up.z,direction.x,direction.y,direction.z));}}
 private static final class Blade{final Vec3 origin;final Frame frame;final VirtualModule cast;final Set<UUID> hits=new HashSet<>();double distance;Blade(Vec3 o,Frame f,VirtualModule m){origin=o;frame=f;cast=m;}}
 private static final class State{long due;final List<Blade> blades=new ArrayList<>();}
 private final Map<UUID,State> states=new HashMap<>();private ServerLevel level;private long tick=Long.MIN_VALUE;
 public void clear(){if(level!=null)states.keySet().forEach(id->ModuleAudio.stop(level,id));states.clear();level=null;tick=Long.MIN_VALUE;}
 public static List<Frame> frames(Vec3 base,VirtualModule m){
  var dir=ContactBox.rotate(MeleeGeometry.angles(base,m.value("slashHorizontal"),m.value("slashVertical")),base).normalize();var up=Math.abs(dir.y)>.99?new Vec3(0,0,1):new Vec3(0,1,0);var right=up.cross(dir).normalize();up=dir.cross(right).normalize();var result=new ArrayList<Frame>();int count=(int)m.value("slashCount");
  for(int i=0;i<count;i++){double angle=count==1?0:Math.toRadians(m.value("slashFan"))*(i/(double)(count-1)-.5);var q=new Quaterniond().rotationAxis(angle,up.x,up.y,up.z);var d=ContactBox.rotate(q,dir);var r=ContactBox.rotate(q,right);var roll=new Quaterniond().rotationAxis(Math.toRadians(m.value("slashRoll")),d.x,d.y,d.z);r=ContactBox.rotate(roll,r);result.add(new Frame(d,r,d.cross(r).normalize()));}return result;
 }
 public static boolean intersects(Vec3 from,Vec3 to,Frame f,double length,double width,AABB target){return ContactBox.intersects(from.lerp(to,.5).subtract(f.direction.scale(width/2)),new Vec3(length/2,.18,width/2+from.distanceTo(to)/2),f.rotation(),target);}
 private static SlashVisualPacket packet(Blade b,Vec3 a,Vec3 end){var m=b.cast;var styles=new net.minecraft.nbt.CompoundTag();styles.put("Ids",ModuleParticles.save(m.particles()));styles.put("Properties",ModuleParticles.saveEntries(m.particleEntries()));var rgb=ModuleColors.vector(m,"slashColor");int color=(Math.round(rgb.x*255)<<16)|(Math.round(rgb.y*255)<<8)|Math.round(rgb.z*255);return new SlashVisualPacket(a,end,b.frame.direction,b.frame.right,(float)m.value("slashLength"),(float)m.value("slashWidth"),color,m.value("slashVisual")==1,styles);}
 public static final int MAX_FLYING=256;
 public void tick(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel server))return;level=server;long now=server.getGameTime();if(tick==now)return;tick=now;
  if(!heart.operational()){clear();return;}
  // Interval clocks releases, never the lifetime of an older volley.
  for(var m:heart.modules().all()){
   if(!m.type().equals("slash")||!HeartModules.triggered(m,heart))continue;var s=states.computeIfAbsent(m.id(),id->new State());
   if(now>=s.due&&s.blades.size()+(int)m.value("slashCount")<=MAX_FLYING&&heart.modules().hurtTriggers.start(m)){
    var cast=VirtualModule.load(m.save());var mark=m.anchors().getFirst().world(heart,false);for(var frame:frames(ModuleCombat.direction(heart,m),m))s.blades.add(new Blade(mark.add(frame.direction.scale(.55+m.value("startDistance"))),frame,cast));s.due=now+(long)m.value("interval");ModuleAudio.play(server,m,"flightSound",mark);
   }
  }
  // Released blades own their frozen settings; dropping the trigger or mark does not erase them.
  var statesIterator=states.entrySet().iterator();while(statesIterator.hasNext()){
   var state=statesIterator.next();var s=state.getValue();
   var iterator=s.blades.iterator();while(iterator.hasNext()){
    var b=iterator.next();var from=b.origin.add(b.frame.direction.scale(b.distance));double next=Math.min(b.cast.value("range"),b.distance+b.cast.value("slashSpeed")/20);var to=b.origin.add(b.frame.direction.scale(next));
    if(!server.hasChunkAt(BlockPos.containing(to))){iterator.remove();continue;}
    var end=SensorGeometry.beamEnd(server,heart.creatureId(),from,to);boolean blocked=end.distanceToSqr(to)>.000001;
    var bounds=new AABB(from,end).inflate(b.cast.value("slashLength")/2+b.cast.value("slashWidth"));
    for(var victim:ModuleTargets.query(server,heart.creatureId(),bounds,e->ModuleTargets.matches(e,b.cast)&&!b.hits.contains(e.getUUID()))){
     if(!intersects(from,end,b.frame,b.cast.value("slashLength"),b.cast.value("slashWidth"),victim.getBoundingBox())||!ModuleTargets.visible(server,heart.creatureId(),from,victim))continue;
     b.hits.add(victim.getUUID());if(b.cast.value("damage")==0||ModuleDamage.hurt(victim,ModuleDamage.named(ModuleDamage.physical(server),heart),(float)b.cast.value("damage")))ModuleEffects.apply(victim,b.cast.effects(),null);
    }
    var visual=packet(b,from,end);for(var player:server.players())if(player.position().distanceToSqr(end)<256*256&&VisualBudget.allow(player,3*(Math.clamp((int)Math.ceil(b.cast.value("slashLength")/.12),12,64)+1)))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,visual);
    b.distance=next;if(blocked||next>=b.cast.value("range"))iterator.remove();
   }
   var m=heart.modules().find(state.getKey());if(s.blades.isEmpty()){
    if(m!=null)heart.modules().hurtTriggers.completed(m);
    if(m==null||!HeartModules.triggered(m,heart))statesIterator.remove();
   }
  }
 }
 public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
  var level=(ServerLevel)heart.getLevel();var mark=m.anchors().getFirst().world(heart,false);var dust=new DustParticleOptions(new Vector3f(1,1,1),1);
  for(var f:frames(ModuleCombat.direction(heart,m),m)){var start=mark.add(f.direction.scale(.55+m.value("startDistance")));var end=SensorGeometry.beamEnd(level,heart.creatureId(),start,start.add(f.direction.scale(m.value("range"))));int count=Math.clamp((int)Math.ceil(start.distanceTo(end)*2),1,256);for(int i=0;i<=count;i++){var p=start.lerp(end,i/(double)count);level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet(new Blade(end,f,m),end,end));}
 }
}
