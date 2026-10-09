package dev.sablelife.module;

import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import java.util.*;

/** A frozen fan of hover positions, followed by individually homing projectiles. */
public final class ModuleHoming {
    private static final class State { long due; final List<ModuleShot> shots = new ArrayList<>(); }
    private final Map<UUID,State> states = new HashMap<>();
    private ServerLevel currentLevel;
    private void stop(UUID id,State state) {
        state.shots.forEach(ModuleShot::discard);
        if(currentLevel!=null) for(var player:currentLevel.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ModuleSound(id,"",true,Vec3.ZERO,0,1));
            for(var shot:state.shots) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ModuleSound(shot.getUUID(),"",true,Vec3.ZERO,0,1));
        }
    }
    public void clear() { states.forEach(this::stop); states.clear(); currentLevel=null; }
    public static List<Vec3> formation(HeartBlockEntity heart, VirtualModule m) {
        var origin=ModuleCombat.origin(heart,m,true); var forward=ModuleCombat.direction(heart,m).normalize();
        var up=dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()), new Vec3(0,1,0));
        // A top/bottom marker changes deployment direction, not the fan's vertical axis.
        var heading=forward.subtract(up.scale(forward.dot(up))).normalize();
        if(heading.lengthSqr()<.001) heading=dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()),new Vec3(0,0,-1));
        var right=heading.cross(up).normalize();
        int shape=(int)m.value("homingPlane");
        var rotation=new org.joml.Quaterniond().rotationAxis(Math.toRadians(m.value("homingRotation")),heading.x,heading.y,heading.z);
        var vertical=dev.sablelife.attack.ContactBox.rotate(rotation,shape==2 || shape==4 ? heading : up);
        var horizontal=dev.sablelife.attack.ContactBox.rotate(rotation,right);
        // Roll inside the formation first, then turn the entire plane around the body's up axis.
        var yawRotation=new org.joml.Quaterniond().rotationAxis(Math.toRadians(m.value("homingVerticalRotation")),up.x,up.y,up.z);
        vertical=dev.sablelife.attack.ContactBox.rotate(yawRotation,vertical);
        horizontal=dev.sablelife.attack.ContactBox.rotate(yawRotation,horizontal);
        var center=origin.add(forward.scale(m.value("homingOffset"))).add(0,m.value("homingHeight"),0);
        var points=new ArrayList<Vec3>(); int count=(int)m.value("homingCount");
        double radius=m.value("homingOffset");
        for(int i=0;i<count;i++) {
            if(count==1) { points.add(center); continue; }
            double fraction=2*i/(double)(count-1)-1;
            double angle=Math.toRadians(m.value("homingFan"))*fraction/2;
            double x,y;
            switch(shape) {
                case 1,6,7 -> {
                    double halfWidth=radius*Math.sin(Math.toRadians(m.value("homingFan"))/2);
                    x=fraction*halfWidth;
                    y=shape==6 ? -x : shape==7 ? x : 0;
                    if(shape>=6) { x/=Math.sqrt(2); y/=Math.sqrt(2); }
                }
                case 2,3 -> {
                    // Ten alternating vertices; five shots land on the five outer tips.
                    double segment=10*i/(double)count;
                    int vertex=(int)Math.floor(segment); double blend=segment-vertex;
                    double a0=Math.PI/2-vertex*Math.PI/5, a1=a0-Math.PI/5;
                    double r0=vertex%2==0 ? radius : radius*.38196601125;
                    double r1=vertex%2==0 ? radius*.38196601125 : radius;
                    x=Math.cos(a0)*r0*(1-blend)+Math.cos(a1)*r1*blend;
                    y=Math.sin(a0)*r0*(1-blend)+Math.sin(a1)*r1*blend;
                }
                case 4,5 -> {
                    double theta=Math.PI/2-2*Math.PI*i/count;
                    x=Math.cos(theta)*radius; y=Math.sin(theta)*radius;
                }
                default -> { x=Math.sin(angle)*radius; y=(Math.cos(angle)-1)*radius; }
            }
            points.add(center.add(horizontal.scale(x)).add(vertical.scale(y)));
        }
        return List.copyOf(points);
    }
    public static List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule m, Vec3 center) {
        double range=m.value("targetRange");
        return ModuleTargets.query(level, heart.creatureId(),new AABB(center,center).inflate(range), e -> eligible(e,m))
                .stream().filter(e -> e.getBoundingBox().getCenter().distanceToSqr(center)<=range*range
                        && (m.value("lineOfSight")==0 || ModuleTargets.visible(level,heart.creatureId(),center,e)))
                .sorted(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(center)))
                .limit(m.value("targetMultiple")==1 ? 64 : 1).toList();
    }
    public static boolean eligible(LivingEntity e,VirtualModule m) {
        return ModuleTargets.matches(e,m);
    }
    public void tick(HeartBlockEntity heart) {
        if(!(heart.getLevel() instanceof ServerLevel level)) return;
        currentLevel=level;
        var active=new HashSet<UUID>(); long now=level.getGameTime();
        for(var m:heart.modules().all()) {
            if(!m.type().equals("homing") || heart.health().state()!=HeartState.ALIVE || !heart.wireless().master() || !HeartModules.status(m,heart).equals("valid")) continue;
            active.add(m.id()); var s=states.computeIfAbsent(m.id(),ignored -> {
                var restored=new State();
                for(var entity:level.getAllEntities()) if(entity instanceof ModuleShot shot && m.id().equals(shot.homingModule()) && heart.creatureId().equals(shot.attackOwner())) restored.shots.add(shot);
                return restored;
            }); s.shots.removeIf(ModuleShot::isRemoved);
            if(s.shots.isEmpty()) heart.modules().hurtTriggers.completed(m);
            if(!s.shots.isEmpty() || now<s.due || !HeartModules.triggered(m,heart)) continue;
            var center=ModuleCombat.origin(heart,m,true);
            if(targets(level,heart,m,center).isEmpty()) { s.due=now+10; continue; }
            if(!heart.modules().hurtTriggers.start(m)) continue;
            int index=0;
            for(var point:formation(heart,m)) {
                var shot=ModuleShot.homing(level,point,center,m,heart,index++);
                if(level.addFreshEntity(shot)) s.shots.add(shot);
            }
            ModuleAudio.play(level,m,"chargeSound",center);
            s.due=now+(long)m.value("homingDeploy")+(long)m.value("chargeTime")+(long)m.value("interval");
        }
        for(var id:List.copyOf(states.keySet())) if(!active.contains(id)) stop(id,states.remove(id));
    }
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity heart,VirtualModule m) {
        var level=(ServerLevel)player.level();
        var origin=ModuleCombat.origin(heart,m,true);
        for(var point:formation(heart,m)) {
            int steps=Math.clamp((int)Math.ceil(origin.distanceTo(point)*4),2,128);
            for(int i=0;i<=steps;i++) { var p=origin.lerp(point,i/(double)steps); level.sendParticles(player,net.minecraft.core.particles.ParticleTypes.END_ROD,true,p.x,p.y,p.z,1,0,0,0,0); }
            level.sendParticles(player,net.minecraft.core.particles.ParticleTypes.END_ROD,true,point.x,point.y,point.z,8,.15,.15,.15,0);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.homing_help"),true);
    }
}
