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
        var center=origin.add(forward.scale(m.value("homingOffset"))).add(0,m.value("homingHeight"),0);
        return DeploymentFormation.points(center,forward,new org.joml.Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()),(int)m.value("homingPlane"),(int)m.value("homingCount"),m.value("homingOffset"),m.value("homingFan"),Double.NaN,m.value("homingRotation"),m.value("homingVerticalRotation"));
    }
    public static List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule m, Vec3 center) {
        double range=m.value("targetRange");
        return ModuleTargets.query(level, heart.creatureId(),new AABB(center,center).inflate(range), e -> eligible(e,m))
                .stream().filter(e -> e.getBoundingBox().getCenter().distanceToSqr(center)<=range*range
                        && (m.value("lineOfSight")==0 || ModuleTargets.visible(level,heart.creatureId(),center,e)))
                .sorted(ModuleTargets.comparator(heart,e -> e.getBoundingBox().getCenter().distanceToSqr(center)))
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
            if(!heart.modules().hurtTriggers.start(m)||!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,m,1)) continue;
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
