package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** A finite formation per trigger session. Each member owns its lifetime, audio and attack clocks. */
public final class ModuleSummonedWeapon {
    public static final Set<String> ATTACKS=Set.of("gun","arrow","flame","laser","slash","ground_strike","quake","pull","freeze","pulse","grab");
    public record Frame(Vec3 item,Vec3 muzzle,Vec3 direction,Quaterniond aim,Quaterniond visual){}
    private static final class Visual extends Display.ItemDisplay {
        Visual(ServerLevel level){super(EntityType.ITEM_DISPLAY,level);}
        @Override public boolean shouldBeSaved(){return false;}
    }
    private static final class State {
        final UUID audioId;final VirtualModule attack;final long spawnAt,end,attackAt;
        final VirtualModule lifecycle;final Vec3 fixedPoint,fixedDirection,localOffset;final ModuleCombat combat=new ModuleCombat();final ModuleLaser laser=new ModuleLaser();final ModuleSlash slash=new ModuleSlash();final ModuleGroundStrike strike=new ModuleGroundStrike();final ModuleQuake quake=new ModuleQuake();final ModulePull pull=new ModulePull();final ModuleFreeze freeze=new ModuleFreeze();final ModuleGrab grab=new ModuleGrab();
        UUID target;long targetRevision=Long.MIN_VALUE;Vec3 direction;Frame frame;Visual display;boolean started,finished;
        State(VirtualModule source,VirtualModule summon,Vec3 point,Vec3 forward,Vec3 localOffset,long spawnAt,UUID audioId){
            attack=VirtualModule.load(source.save());attack.edit("enabled",1,"");attack.edit("trigger",0,"");attack.edit("shotAim",0,"");attack.edit("laserAim",0,"");attack.edit("sweepAngle",0,"");attack.edit("target",summon.value("target"),"");attack.edit("releasePosition",0,"");attack.edit("quakeOrigin",0,"");lifecycle=VirtualModule.load(summon.save());
            fixedPoint=point;fixedDirection=forward;direction=forward;this.localOffset=localOffset;this.spawnAt=spawnAt;end=spawnAt+(long)summon.value("summonDuration");attackAt=spawnAt+(long)summon.value("summonWindup");this.audioId=audioId;
        }
    }
    private static final class Group {
        final UUID source;final VirtualModule config;final long activation;final List<State> members;
        final SummonTargetCache targets=new SummonTargetCache();final List<State> ready=new ArrayList<>();final List<Vec3> points=new ArrayList<>();
        Group(HeartBlockEntity heart,VirtualModule source,VirtualModule summon,long now,long activation){
            this.source=source.id();this.config=VirtualModule.load(summon.save());this.activation=activation;
            var center=position(heart,summon);var forward=ModuleCombat.direction(heart,summon);var points=formation(heart,summon);
            var inverse=new Quaterniond(summon.anchors().getFirst().body(heart).logicalPose().orientation()).invert();var list=new ArrayList<State>();
            for(int i=0;i<points.size();i++)list.add(new State(source,summon,points.get(i),forward,dev.sablelife.attack.ContactBox.rotate(inverse,points.get(i).subtract(center)),now+i*(long)summon.value("summonDeployGap"),UUID.randomUUID()));
            members=List.copyOf(list);
        }
    }
    private final Map<UUID,List<Group>> states=new HashMap<>();private final Map<UUID,Long> held=new HashMap<>();private ServerLevel level;private long lastTick=Long.MIN_VALUE;
    private void stop(State s){if(s.finished)return;if(s.display!=null){s.display.discard();s.display=null;}s.combat.clear();s.laser.clear();s.slash.clear();s.strike.clear();s.quake.clear();s.pull.clear();s.freeze.clear();s.grab.clear();if(s.started&&level!=null)ModuleAudio.stop(level,s.audioId);if(s.started&&s.frame!=null&&level!=null)lifecycle(level,s.lifecycle,s.frame.item(),false);s.target=null;s.finished=true;}
    private void stop(Group group){group.members.forEach(this::stop);group.targets.suspend();group.ready.clear();group.points.clear();}
    public void clear(){states.values().forEach(groups->groups.forEach(this::stop));states.clear();held.clear();level=null;lastTick=Long.MIN_VALUE;}
    private static void lifecycle(ServerLevel level,VirtualModule config,Vec3 point,boolean spawn){
        var child=config.movementParticles(spawn?"Spawn":"End");if(child!=null)for(var entry:child.particleEntries())ModuleParticles.send(level,entry,point.x,point.y,point.z,1,0,0,0,0);
        // Separate one-shot owner: removing the weapon must not immediately cancel its final cue.
        ModuleAudio.play(level,UUID.randomUUID(),config.soundTimeline(spawn?"summonSpawnSound":"summonEndSound"),point);
    }
    public static List<Vec3> formation(HeartBlockEntity heart,VirtualModule m){return DeploymentFormation.points(position(heart,m),ModuleCombat.direction(heart,m),new Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()),(int)m.value("summonFormation"),(int)m.value("summonCount"),m.value("summonRadius"),m.value("summonFan"),m.value("summonSpacing"),m.value("summonFormationRoll"),m.value("summonFormationYaw"));}
    public static Vec3 position(HeartBlockEntity heart,VirtualModule m){var anchor=m.anchors().getFirst();var body=anchor.body(heart);return anchor.world(heart,false).add(ModuleCombat.direction(heart,m).scale(m.value("startDistance"))).add(dev.sablelife.attack.ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()),new Vec3(m.value("displayX"),m.value("displayY"),m.value("displayZ"))));}
    public static Quaterniond orientation(Vec3 direction){return new Quaterniond().rotationTo(0,0,1,direction.x,direction.y,direction.z);}
    public static Vec3 turn(Vec3 current,Vec3 desired,double degrees){
        if(desired.lengthSqr()<1e-12)return current;desired=desired.normalize();current=current.normalize();double angle=Math.acos(Math.clamp(current.dot(desired),-1,1));if(angle<1e-9)return desired;
        var q=new Quaterniond().rotationTo(current.x,current.y,current.z,desired.x,desired.y,desired.z);var rotation=new Quaterniond().slerp(q,Math.min(1,Math.toRadians(degrees)/angle));return dev.sablelife.attack.ContactBox.rotate(rotation,current).normalize();
    }
    public static Frame frame(VirtualModule m,Vec3 point,Vec3 direction){
        var aim=orientation(direction).rotateYXZ(Math.toRadians(m.value("summonYaw")),Math.toRadians(m.value("summonPitch")),0);
        var forward=dev.sablelife.attack.ContactBox.rotate(aim,new Vec3(0,0,1));var muzzle=point.add(dev.sablelife.attack.ContactBox.rotate(aim,new Vec3(m.value("summonMuzzleX"),m.value("summonMuzzleY"),m.value("summonMuzzleZ"))));
        var visual=new Quaterniond(aim).rotateXYZ(Math.toRadians(m.value("displayRotX")),Math.toRadians(m.value("displayRotY")),Math.toRadians(m.value("displayRotZ")));
        return new Frame(point,muzzle,forward,aim,visual);
    }
    private void display(HeartBlockEntity heart,VirtualModule m,State s){
        if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(s.frame.item)))return;
        if(s.display==null||s.display.isRemoved()){
            s.display=new Visual(level);var tag=new CompoundTag();tag.put("item",m.projectileItem(level.registryAccess()).save(level.registryAccess()));tag.putString("item_display","fixed");tag.putInt("interpolation_duration",1);tag.putInt("teleport_duration",1);tag.putFloat("width",(float)m.value("displaySize")*2);tag.putFloat("height",(float)m.value("displaySize")*2);var brightness=new CompoundTag();brightness.putInt("sky",15);brightness.putInt("block",15);tag.put("brightness",brightness);s.display.load(tag);s.display.setPos(s.frame.item);applyPose(m,s);level.addFreshEntity(s.display);
        }else{s.display.setPos(s.frame.item);applyPose(m,s);}
    }
    private static void applyPose(VirtualModule m,State s){var access=(dev.sablelife.mixin.RespawnDisplayAccess)(Object)s.display;access.sablelife$transform(new com.mojang.math.Transformation(new Vector3f(),new Quaternionf(s.frame.visual),new Vector3f((float)m.value("displaySize")),new Quaternionf()));access.sablelife$interpolation(0);}
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel server))return;level=server;long now=level.getGameTime();if(now==lastTick)return;lastTick=now;
        if(!heart.operational()){clear();return;}
        var powered=new HashSet<UUID>();
        for(var m:heart.modules().all()){
            if(!m.type().equals("summoned_weapon")||!HeartModules.triggered(m,heart))continue;
            powered.add(m.id());long token=heart.modules().moveActivation(m.id());var previous=held.put(m.id(),token);
            if(previous!=null&&previous==token)continue;
            var source=heart.modules().find(m.summonAttackId());if(source==null||!ModuleAvailability.enabled(heart,source)||!ATTACKS.contains(source.type())||m.projectileItem(level.registryAccess()).isEmpty())continue;
            // Bound concurrent independent attacks, including pending staggered members.
            long live=states.values().stream().flatMap(List::stream).flatMap(g->g.members.stream()).filter(x->!x.finished).count();
            if(live+(int)m.value("summonCount")>256)continue;
            var groups=states.computeIfAbsent(m.id(),id->new ArrayList<>());groups.removeIf(g->g.members.stream().allMatch(x->x.finished));groups.add(new Group(heart,source,m,now,token));
        }
        held.keySet().retainAll(powered);
        for(var entry:states.entrySet())for(var group:entry.getValue()){
            var current=heart.modules().find(entry.getKey());var m=current==null?group.config:current;
            boolean marked=!m.anchors().isEmpty()&&m.anchors().getFirst().status(heart).equals("valid");
            var center=marked?position(heart,m):null;var rotation=marked?new Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()):null;
            group.ready.clear();group.points.clear();
            for(var member:group.members){
                if(member.finished||now<member.spawnAt)continue;
                if(now>=member.end){stop(member);continue;}
                var point=m.value("summonPosition")==0&&center!=null?center.add(dev.sablelife.attack.ContactBox.rotate(rotation,member.localOffset)):member.frame==null?member.fixedPoint:member.frame.item;
                if(m.value("summonPosition")==1)point=member.fixedPoint;
                if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(point)))continue;
                group.ready.add(member);group.points.add(point);
            }
            if(m.value("summonAim")==1)group.targets.prepare(level,heart.creatureId(),(int)m.value("target"),group.points,m.value("targetRange"),now);else group.targets.suspend();
            var forward=m.value("summonPosition")==0&&marked?ModuleCombat.direction(heart,m):null;
            for(int i=0;i<group.ready.size();i++){
                var member=group.ready.get(i);var point=group.points.get(i);
                if(m.value("summonAim")==1){member.target=group.targets.select(member.target,point,m.value("targetRange"),member.targetRevision!=group.targets.revision());member.targetRevision=group.targets.revision();var target=group.targets.point(member.target);if(target!=null)member.direction=turn(member.direction,target.subtract(point),m.value("summonTurnSpeed"));}
                else{member.target=null;member.direction=forward==null?member.fixedDirection:forward;}
                member.frame=frame(m,point,member.direction);display(heart,m,member);if(!member.started){member.started=true;lifecycle(level,member.lifecycle,point,true);}
                member.attack.emitter(new VirtualModule.Emitter(member.frame.muzzle,member.frame.direction,member.frame.aim,member.audioId));
                if(now>=member.attackAt)switch(member.attack.type()){
                    case "laser"->member.laser.tickEmitter(heart,member.attack);
                    case "slash"->member.slash.tickEmitter(heart,member.attack);
                    case "ground_strike"->member.strike.tickEmitter(heart,member.attack);
                    case "quake"->member.quake.tickEmitter(heart,member.attack);
                    case "pull"->member.pull.tickEmitter(heart,member.attack);
                    case "freeze"->member.freeze.tickEmitter(heart,member.attack);
                    case "grab"->member.grab.tickEmitter(heart,member.attack);
                    default->member.combat.tickEmitter(heart,member.attack);
                }
            }
        }
        // Keep one completed group for dashboard feedback, not an unbounded history.
        for(var groups:states.values())if(groups.size()>1){var last=groups.getLast();groups.removeIf(g->g!=last&&g.members.stream().allMatch(x->x.finished));}
    }
    public CompoundTag snapshot(UUID id,HeartBlockEntity heart){
        var tag=new CompoundTag();var groups=states.get(id);if(groups==null)return tag;int active=0,pending=0,spawned=0;long remaining=0;
        int total=0;for(var group:groups){total+=group.members.size();for(var s:group.members){if(s.started)spawned++;if(!s.finished){if(s.started)active++;else pending++;remaining=Math.max(remaining,s.end-heart.getLevel().getGameTime());}}}
        tag.putBoolean("Active",active>0);tag.putBoolean("Pending",pending>0);tag.putBoolean("Finished",active==0&&pending==0);tag.putInt("ActiveCount",active);tag.putInt("PendingCount",pending);tag.putInt("Spawned",spawned);tag.putInt("Total",total);tag.putInt("Remaining",(int)Math.max(0,remaining));
        var frame=frame(id);if(frame!=null){tag.putDouble("X",frame.item.x);tag.putDouble("Y",frame.item.y);tag.putDouble("Z",frame.item.z);}return tag;
    }
    public long targetQueries(UUID id){var groups=states.get(id);return groups==null?0:groups.stream().mapToLong(g->g.targets.queries()).sum();}
    public boolean using(UUID source){return states.values().stream().flatMap(List::stream).anyMatch(g->g.source.equals(source)&&g.members.stream().anyMatch(s->s.started&&!s.finished));}
    public Frame frame(UUID id){return frames(id).stream().findFirst().orElse(null);}
    public List<Frame> frames(UUID id){var groups=states.get(id);return groups==null?List.of():groups.stream().flatMap(g->g.members.stream()).filter(s->s.started&&!s.finished&&s.frame!=null).map(s->s.frame).toList();}
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
        var points=formation(heart,m);var direction=ModuleCombat.direction(heart,m);var targets=new SummonTargetCache();if(m.value("summonAim")==1)targets.prepare((ServerLevel)heart.getLevel(),heart.creatureId(),(int)m.value("target"),points,m.value("targetRange"),heart.getLevel().getGameTime());
        var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.25F,1,.5F),1);var aimDust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.3F,.65F,1),1);var previews=new net.minecraft.nbt.ListTag();double half=m.value("displaySize")/2;int detail=points.size()>8?2:8,rays=points.size()>8?4:12;
        for(var point:points){var aim=direction;var target=m.value("summonAim")==1?targets.point(targets.select(null,point,m.value("targetRange"),true)):null;if(target!=null&&target.distanceToSqr(point)>1e-9)aim=turn(direction,target.subtract(point),m.value("summonTurnSpeed"));var frame=frame(m,point,aim);
            var row=new CompoundTag();row.put("Item",m.projectileItem(player.registryAccess()).save(player.registryAccess()));row.putDouble("X",point.x);row.putDouble("Y",point.y);row.putDouble("Z",point.z);row.putFloat("Size",(float)m.value("displaySize"));row.putDouble("QX",frame.visual.x);row.putDouble("QY",frame.visual.y);row.putDouble("QZ",frame.visual.z);row.putDouble("QW",frame.visual.w);previews.add(row);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ParticleVisual("sablelife:energy_mote",frame.muzzle,1,Vec3.ZERO,0,60,.35F,0xFFAA33));
            for(int axis=0;axis<3;axis++)for(int i=0;i<=detail;i++){var v=new org.joml.Vector3d();v.setComponent(axis,-half+2*half*i/detail);frame.visual.transform(v);var p=point.add(v.x,v.y,v.z);((ServerLevel)player.level()).sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}
            for(int i=0;i<=rays;i++){var p=frame.muzzle.add(frame.direction.scale(3.*i/rays));((ServerLevel)player.level()).sendParticles(player,aimDust,true,p.x,p.y,p.z,1,0,0,0,0);}
        }
        var tag=new CompoundTag();tag.put("SummonPreview",previews);net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ItemDisplayPacket(heart.getBlockPos(),m.id(),tag));
    }
}
