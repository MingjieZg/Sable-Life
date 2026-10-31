package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Warning positions are sampled once. Only eligible targets still in range when it ends are frozen. */
public final class ModuleFreeze {
    private static final class State {java.util.UUID audio;long fire,next;VirtualModule cast;List<Vec3> points=List.of();List<ControlVolume> volumes=List.of();}
    private final Map<UUID,State> states=new HashMap<>();
    private net.minecraft.server.MinecraftServer server;private UUID owner;private ServerLevel audioLevel;
    public void clear(){if(server!=null&&owner!=null)for(var id:states.keySet()){release(server,new PlayerFreeze.Source(owner,states.get(id).audio));if(audioLevel!=null)ModuleAudio.stop(audioLevel,states.get(id).audio);}states.clear();server=null;owner=null;audioLevel=null;}
    private static void release(net.minecraft.server.MinecraftServer server,PlayerFreeze.Source source){PlayerFreeze.release(server,source);MobGrab.release(server,source);}
    public static boolean matches(net.minecraft.world.entity.LivingEntity target,VirtualModule m){int mode=(int)m.value("freezeTarget");return target instanceof ServerPlayer player?mode!=1&&PlayerFreeze.eligible(player):mode!=0&&MobGrab.eligible(target);}
    public static List<Vec3> centers(HeartBlockEntity heart,VirtualModule m){return m.emitter()!=null?List.of(m.emitter().position()):TargetCastOrigin.targeted(m)?TargetCastOrigin.points(heart,m):m.anchors().isEmpty()?List.of():List.of(ModuleCombat.origin(heart,m,true));}
    public static List<ControlVolume> volumes(HeartBlockEntity heart,VirtualModule m){if(centers(heart,m).isEmpty())return List.of();var direction=TargetCastOrigin.targeted(m)?new Vec3(0,0,1):ModuleCombat.direction(heart,m);return centers(heart,m).stream().map(p->ControlVolume.frame(m,p,direction)).toList();}
    public static boolean inRange(ServerPlayer p,Vec3 point,double radius){var b=p.getBoundingBox();return new Vec3(Math.clamp(point.x,b.minX,b.maxX),Math.clamp(point.y,b.minY,b.maxY),Math.clamp(point.z,b.minZ,b.maxZ)).distanceToSqr(point)<=radius*radius;}
    public static int capture(ServerLevel level,HeartBlockEntity heart,VirtualModule m,List<Vec3> points){return captureVolumes(level,heart,m,points.stream().map(p->ControlVolume.frame(m,p,new Vec3(0,0,1))).toList());}
    public static int captureVolumes(ServerLevel level,HeartBlockEntity heart,VirtualModule m,List<ControlVolume> volumes){
        int count=0;var source=new PlayerFreeze.Source(heart.creatureId(),m.audioId());
        var candidates=new LinkedHashMap<UUID,net.minecraft.world.entity.LivingEntity>();for(var p:level.players())candidates.put(p.getUUID(),p);
        if(m.value("freezeTarget")!=0)for(var v:volumes)for(var mob:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,v.bounds(),MobGrab::eligible))candidates.putIfAbsent(mob.getUUID(),mob);
        for(var target:candidates.values())if(matches(target,m)&&volumes.stream().anyMatch(v->level.hasChunkAt(net.minecraft.core.BlockPos.containing(v.center()))&&v.intersects(target.getBoundingBox()))){
            boolean applied;if(target instanceof ServerPlayer player){PlayerFreeze.apply(player,source,(int)m.value("freezeDuration"));applied=true;}else applied=MobGrab.freeze((net.minecraft.world.entity.Mob)target,source,(int)m.value("freezeDuration"));
            if(applied&&++count>=64)break;
        }return count;
    }
    private static void draw(ServerLevel level,ServerPlayer viewer,VirtualModule m,ControlVolume volume){
        var entries=m.particleEntries();if(viewer==null&&entries.isEmpty())return;var points=volume.outline();
        int step=viewer==null?Math.max(1,points.size()/80):1;
        for(int i=0;i<points.size();i+=step){var p=points.get(i);var entry=viewer==null?entries.get(i%entries.size()):new ModuleParticles.Entry("minecraft:dust",60,.65F,0x7DEEFF);
            if(viewer==null)ModuleParticles.send(level,entry,p.x,p.y,p.z,1,0,0,0,0);
            else if(VisualBudget.allow(viewer,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,new ModuleNetworking.ParticleVisual(entry.id(),p,1,Vec3.ZERO,0,entry.ticks(),entry.size(),entry.color()));
        }
    }
    public static void preview(ServerPlayer p,HeartBlockEntity heart,VirtualModule m){var volumes=volumes(heart,m);if(volumes.isEmpty()){p.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}for(var volume:volumes)draw((ServerLevel)heart.getLevel(),p,m,volume);}
    public void tick(HeartBlockEntity heart){tick(heart,heart.modules().all());}
 public void tickEmitter(HeartBlockEntity heart,VirtualModule module){tick(heart,List.of(module));}
 private void tick(HeartBlockEntity heart,List<VirtualModule> modules){
        if(!(heart.getLevel() instanceof ServerLevel level))return;audioLevel=level;server=level.getServer();owner=heart.creatureId();long now=level.getGameTime();var keep=new HashSet<UUID>();
        for(var m:modules){
            if(!m.type().equals("freeze")||!EmitterRuntime.active(m,heart))continue;keep.add(m.id());var s=states.computeIfAbsent(m.id(),id->new State());s.audio=m.audioId();
            if(s.cast==null){
                if(now<s.next)continue;var volumes=volumes(heart,m);if(volumes.isEmpty()||!EmitterRuntime.start(m,heart))continue;
                s.cast=EmitterRuntime.copy(m);s.volumes=List.copyOf(volumes);s.points=volumes.stream().map(ControlVolume::center).toList();s.fire=now+(long)m.value("freezeWarning");s.next=s.fire+(long)m.value("freezeDuration")+(long)m.value("interval");
                for(var v:volumes){draw(level,null,s.cast,v);ModuleAudio.play(level,s.cast,"warningSound",v.center());}
            }
            if(now<s.fire){if(now%4==0)for(var v:s.volumes)draw(level,null,s.cast,v);continue;}
            captureVolumes(level,heart,s.cast,s.volumes);ModuleAudio.stop(level,m.audioId());for(var point:s.points)ModuleAudio.play(level,s.cast,"freezeSound",point);s.cast=null;EmitterRuntime.completed(m,heart);
        }
        for(var id:List.copyOf(states.keySet()))if(!keep.contains(id)){release(server,new PlayerFreeze.Source(owner,states.get(id).audio));ModuleAudio.stop(level,states.get(id).audio);states.remove(id);}
    }
}
