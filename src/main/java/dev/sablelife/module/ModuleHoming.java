package dev.sablelife.module;

import dev.sablelife.attack.ModuleShot;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.stream.Stream;

/** A frozen fan of hover positions, followed by individually homing projectiles. */
public final class ModuleHoming {
    private static final class State { long due; final List<ModuleShot> shots = new ArrayList<>(); }

    /** Shared only by shots from the same deployment attempt. */
    public static final class Salvo {
        private final Set<UUID> claimedTargets = new HashSet<>();
        private boolean launched;

        public boolean recordLaunch(UUID claimedTarget) {
            if (claimedTarget != null) claimedTargets.add(claimedTarget);
            if (launched) return false;
            launched = true;
            return true;
        }
    }

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
        return launchTargets(level, heart, m, center, center, null);
    }

    public static List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 center, UUID inherited) {
        var current = ModuleTargets.attackAim(heart, module);
        var preferred = current != null && current.id().equals(inherited) ? current : ModuleTargets.AttackAim.fixed(inherited);
        return launchTargets(level, heart, module, center, center, preferred);
    }

    public static List<LivingEntity> launchTargets(ServerLevel level, HeartBlockEntity heart, VirtualModule module,
                                                    Vec3 center, Vec3 shooter, ModuleTargets.AttackAim aim) {
        boolean multiple = module.value("targetMultiple") == 1;
        return launchTargets(level, heart, module, center, shooter, aim, multiple).limit(multiple ? 64 : 1).toList();
    }

    public static LivingEntity automaticLaunchTarget(ServerLevel level, HeartBlockEntity heart, VirtualModule module,
                                                       Vec3 hover, Vec3 shooter, Salvo salvo) {
        var targets = launchTargets(level, heart, module, hover, shooter, null, false).iterator();
        if (!targets.hasNext()) return null;
        var best = targets.next();
        if (salvo == null || !salvo.claimedTargets.contains(best.getUUID())) return best;
        while (targets.hasNext()) {
            var target = targets.next();
            if (!salvo.claimedTargets.contains(target.getUUID())) return target;
        }
        return best;
    }

    private static Stream<LivingEntity> launchTargets(ServerLevel level, HeartBlockEntity heart, VirtualModule module,
                                                       Vec3 center, Vec3 shooter, ModuleTargets.AttackAim aim, boolean multiple) {
        double range = module.value("targetRange");
        var bounds = new AABB(center, center).inflate(range);
        java.util.function.ToDoubleFunction<LivingEntity> distance = target ->
                ModuleTargets.attackPoint(level, heart, module, target, aim).distanceToSqr(center);
        java.util.function.Predicate<LivingEntity> eligible = target -> eligible(target, module) && distance.applyAsDouble(target) <= range * range;
        var order = ModuleTargets.comparator(heart, distance);
        return ModuleTargets.attackResults(level, heart, module, bounds, eligible, order,
                target -> ModuleTargets.attackVisible(level, heart, module, shooter, target, aim) ? target : null,
                aim, multiple, false);
    }

    private static boolean hasHoverTarget(ServerLevel level, HeartBlockEntity heart, VirtualModule module, List<Vec3> points) {
        double range = module.value("targetRange");
        double squaredRange = range * range;
        boolean requireSight = SightMemory.enabled(module);
        var owner = heart.creatureId();
        var bounds = new AABB(points.getFirst(), points.getFirst());
        for (int i = 1; i < points.size(); i++) {
            var point = points.get(i);
            bounds = bounds.minmax(new AABB(point, point));
        }
        // Only gate deployment here. Each shot makes its own selection after it has finished charging.
        for (var target : ModuleTargets.query(level, owner, bounds.inflate(range), candidate -> eligible(candidate, module))) {
            var targetPoint = SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
            for (var point : points) {
                if (targetPoint.distanceToSqr(point) <= squaredRange
                        && (!requireSight || ModuleTargets.visible(level, owner, point, target))) return true;
            }
        }
        return false;
    }

    public static boolean targetInRange(ServerLevel level, VirtualModule module, Vec3 center, LivingEntity target) {
        double range = module.value("targetRange");
        return !target.isRemoved() && eligible(target, module)
                && dev.sablelife.compat.sable.SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter()).distanceToSqr(center) <= range * range;
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
                if (!restored.shots.isEmpty()) {
                    var salvo = new Salvo();
                    restored.shots.forEach(shot -> shot.joinHomingSalvo(salvo));
                }
                return restored;
            }); s.shots.removeIf(ModuleShot::isRemoved);
            if(s.shots.isEmpty()) heart.modules().hurtTriggers.completed(m);
            if(!s.shots.isEmpty() || now<s.due || !HeartModules.triggered(m,heart)) continue;
            var center=ModuleCombat.origin(heart,m,true);
            var inherited = ModuleTargets.attackAim(heart, m);
            var points = formation(heart, m);
            boolean hasTarget = inherited == null ? hasHoverTarget(level, heart, m, points)
                    : launchTargets(level, heart, m, center, center, inherited, false).findFirst().isPresent();
            if(!hasTarget) { s.due=now+10; continue; }
            if(!heart.modules().hurtTriggers.start(m)||!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,m,1)) continue;
            var salvo = new Salvo();
            int index=0;
            for(var point:points) {
                var shot=ModuleShot.homingWithAim(level, point, center, m, heart, index++, inherited);
                shot.joinHomingSalvo(salvo);
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
