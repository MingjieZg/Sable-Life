package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One marked mouth/hand, a moving capture telegraph, then body-local player/mob attachments. */
public final class ModuleGrab {
    private static final class State {java.util.UUID audio;long fire,next;VirtualModule cast;}
    private final Map<UUID,State> states=new HashMap<>();private ServerLevel level;private UUID owner;
    public void clear(){if(level!=null&&owner!=null)for(var id:states.keySet()){release(level,new PlayerFreeze.Source(owner,states.get(id).audio));ModuleAudio.stop(level,states.get(id).audio);}states.clear();level=null;owner=null;}
    /** The held player's center follows this point. The player stays upright and retains their view. */
    public static Vec3 holdPoint(HeartBlockEntity heart,VirtualModule m){
        if(heart.isRemoved())return null;if(m.emitter()!=null)return m.emitter().position().add(dev.sablelife.attack.ContactBox.rotate(m.emitter().rotation(),new Vec3(m.value("grabOffsetX"),m.value("grabOffsetY"),0)));if(m.anchors().isEmpty())return null;var mark=m.anchors().getFirst();if(!mark.status(heart).equals("valid"))return null;
        var body=mark.body(heart);var face=Vec3.atLowerCornerOf(mark.face().getNormal());
        var up=new Vec3(0,1,0);var right=up.cross(face);if(right.lengthSqr()<.1)right=new Vec3(1,0,0);else right=right.normalize();
        var local=mark.position(body).getCenter().add(face.scale(.55+m.value("startDistance"))).add(right.scale(m.value("grabOffsetX"))).add(up.scale(m.value("grabOffsetY")));
        return body.logicalPose().transformPosition(local);
    }
    public static net.minecraft.world.phys.Vec3 followPoint(HeartBlockEntity heart,VirtualModule snapshot,net.minecraft.world.entity.LivingEntity target){
        if(snapshot.emitter()!=null){var point=holdPoint(heart,snapshot);return point==null?null:point.subtract(0,target.getBbHeight()/2,0);}
        var current=heart.modules().find(snapshot.id());if(current==null||!HeartModules.triggered(current,heart))return null;
        var center=holdPoint(heart,snapshot);return center==null?null:center.subtract(0,target.getBbHeight()/2,0);
    }
    public static PlayerFreeze.Follow follow(HeartBlockEntity heart,VirtualModule snapshot){return player->followPoint(heart,snapshot,player);}
    public static boolean matches(net.minecraft.world.entity.LivingEntity target,VirtualModule m){
        int mode=(int)m.value("grabTarget");return target instanceof ServerPlayer player?mode!=1&&PlayerFreeze.eligible(player):mode!=0&&MobGrab.eligible(target);
    }
    private static void release(ServerLevel level,PlayerFreeze.Source source){PlayerFreeze.release(level.getServer(),source);MobGrab.release(level.getServer(),source);}
    public static int capture(ServerLevel level,HeartBlockEntity heart,VirtualModule m){
        var point=holdPoint(heart,m);if(point==null)return 0;double radius=m.value("grabRadius");
        var candidates=new ArrayList<net.minecraft.world.entity.LivingEntity>();candidates.addAll(level.players());
        if(m.value("grabTarget")!=0)candidates.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(point,point).inflate(radius),MobGrab::eligible));
        var sorted=candidates.stream().filter(p->matches(p,m)&&p.getBoundingBox().getCenter().distanceToSqr(point)<=radius*radius).sorted(Comparator.comparingDouble(p->p.getBoundingBox().getCenter().distanceToSqr(point))).toList();
        var source=new PlayerFreeze.Source(heart.creatureId(),m.audioId());
        for(var target:sorted){boolean caught=target instanceof ServerPlayer player?PlayerFreeze.grab(player,source,(int)m.value("grabDuration"),follow(heart,m)):MobGrab.grab((net.minecraft.world.entity.Mob)target,source,(int)m.value("grabDuration"),mob->followPoint(heart,m,mob));if(caught)return 1;}
        return 0;
    }
    private static void draw(ServerLevel level,ServerPlayer viewer,VirtualModule m,Vec3 center){
        var points=new ArrayList<Vec3>();double radius=m.value("grabRadius");
        for(int i=0;i<64;i++){double y=1-2*(i+.5)/64,a=i*Math.PI*(3-Math.sqrt(5)),r=Math.sqrt(1-y*y);points.add(center.add(Math.cos(a)*r*radius,y*radius,Math.sin(a)*r*radius));}
        if(viewer!=null){var box=new ControlVolume(center,new Vec3(0,0,1),0,.6,.6,1.8,false);points.addAll(box.outline());}
        var entries=m.particleEntries();if(viewer==null&&entries.isEmpty())return;
        for(int i=0;i<points.size();i++){var p=points.get(i);var entry=viewer==null?entries.get(i%entries.size()):new ModuleParticles.Entry("minecraft:dust",60,.65F,i<64?0xFFB855:0x70FFB0);
            if(viewer==null)ModuleParticles.send(level,entry,p.x,p.y,p.z,1,0,0,0,0);
            else if(VisualBudget.allow(viewer,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,new ModuleNetworking.ParticleVisual(entry.id(),p,1,Vec3.ZERO,0,entry.ticks(),entry.size(),entry.color()));
        }
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){var point=holdPoint(heart,m);if(point!=null)draw((ServerLevel)heart.getLevel(),player,m,point);}
    public void tick(HeartBlockEntity heart){tick(heart,heart.modules().all());}
 public void tickEmitter(HeartBlockEntity heart,VirtualModule module){tick(heart,List.of(module));}
 private void tick(HeartBlockEntity heart,List<VirtualModule> modules){
        if(!(heart.getLevel() instanceof ServerLevel server))return;level=server;owner=heart.creatureId();long now=level.getGameTime();var keep=new HashSet<UUID>();
        for(var m:modules){
            if(!m.type().equals("grab")||!EmitterRuntime.active(m,heart))continue;keep.add(m.id());var s=states.computeIfAbsent(m.id(),id->new State());s.audio=m.audioId();
            if(s.cast==null){if(now<s.next||!EmitterRuntime.start(m,heart))continue;s.cast=EmitterRuntime.copy(m);s.fire=now+(long)m.value("grabWarning");s.next=s.fire+(long)m.value("grabDuration")+(long)m.value("interval");var point=holdPoint(heart,m);if(point!=null)ModuleAudio.play(level,s.cast,"warningSound",point);}
            var point=holdPoint(heart,m.emitter()!=null?m:s.cast);if(point==null){release(level,new PlayerFreeze.Source(owner,m.audioId()));s.cast=null;continue;}
            if(now<s.fire){if(now%4==0)draw(level,null,s.cast,point);continue;}
            capture(level,heart,m.emitter()!=null?m:s.cast);ModuleAudio.stop(level,m.audioId());ModuleAudio.play(level,s.cast,"grabSound",point);EmitterRuntime.completed(m,heart);s.cast=null;
        }
        for(var id:List.copyOf(states.keySet()))if(!keep.contains(id)){release(level,new PlayerFreeze.Source(owner,states.get(id).audio));ModuleAudio.stop(level,states.get(id).audio);states.remove(id);}
    }
}
