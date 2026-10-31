package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Warning sphere stays fixed for each cast. Only victims still inside at activation are captured. */
public final class ModulePull {
 private UUID emitterAudio;private UUID audio(UUID id){return emitterAudio==null?id:emitterAudio;}
    private static final int MAX_TARGETS = 64;
    private static final class State {
        final PullCycle clock = new PullCycle();
        final Map<UUID,Integer> victims = new HashMap<>();
        List<Vec3> centers=List.of();
        long nextScan;
    }
    private final Map<UUID, State> states = new HashMap<>();
    private ServerLevel level;
    public String phase(UUID id) { var s = states.get(id); return s == null ? "waiting" : s.clock.phase().name().toLowerCase(Locale.ROOT); }
    public int victims(UUID id) { var s = states.get(id); return s == null ? 0 : s.victims.size(); }
    public void clear() { if (level != null) states.keySet().forEach(this::stop); states.clear(); level = null; }
    private void stop(UUID id) { sound(audio(id), "", Vec3.ZERO, true); }
    private void sound(UUID id, String sound, Vec3 point, boolean stop) {
        if (!stop && sound.isEmpty()) return;
        for (var player : level.players()) if (stop || player.position().distanceToSqr(point) <= 128 * 128)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.ModuleSound(audio(id), sound, stop, point, 1, 1));
    }
    public static Vec3 destination(HeartBlockEntity heart, VirtualModule module) {
        if(TargetCastOrigin.targeted(module))return TargetCastOrigin.point(heart,module);
        return ModuleCombat.origin(heart, module, true);
    }
    private static boolean eligible(LivingEntity entity, VirtualModule module) {
        return ModuleTargets.matches(entity,module);
    }
    private static boolean inRange(LivingEntity entity, VirtualModule module, Vec3 center) {
        return entity.getBoundingBox().getCenter().distanceToSqr(center) <= module.value("pullRadius") * module.value("pullRadius");
    }
    private static boolean visible(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 point, LivingEntity target) {
        return module.value("lineOfSight") == 1 || ModuleTargets.visible(level, heart.creatureId(), point, target);
    }
    private static List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 center, Vec3 point) {
        return ModuleTargets.query(level, heart.creatureId(), new AABB(center, center).inflate(module.value("pullRadius")),
                        entity -> eligible(entity, module) && inRange(entity, module, center) && visible(level, heart, module, point, entity))
                .stream().sorted(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceToSqr(center))).limit(MAX_TARGETS).toList();
    }
    private static void particle(ServerLevel level, ServerPlayer viewer, DustParticleOptions dust, Vec3 point) {
        if (viewer == null) dev.sablelife.module.ModuleParticles.send(level, dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        else level.sendParticles(viewer, dust, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
    private static DustParticleOptions color(VirtualModule module, String key, float size) {
        return new DustParticleOptions(ModuleColors.vector(module,key),size);
    }
    private static void draw(ServerLevel level, ServerPlayer viewer, VirtualModule module, Vec3 center, Vec3 point, boolean pulling, long remaining) {
        var boundary = color(module, "pullWarningColor", .65F);
        double radius = module.value("pullRadius");
        for (int i = 0; i < PullVisual.BOUNDARY_POINTS; i++) {
            particle(level, viewer, boundary, PullVisual.shell(center,radius,i,PullVisual.BOUNDARY_POINTS,0));
        }
        // Staggered births continuously travel in from the entire shell, rather than a flat shrinking ring.
        for(var packet:PullVisual.particles(module,center,pulling?point:center,remaining,level.getGameTime())){
            for(var player:viewer==null?level.players():List.of(viewer))
                if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(center)<=512*512&&VisualBudget.allow(player,1))
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
        }
        var marker = color(module, "pullCenterColor", 1.3F);
        for (int i = -3; i <= 3; i++) {
            particle(level, viewer, marker, point.add(i * .12, 0, 0));
            particle(level, viewer, marker, point.add(0, i * .12, 0));
            particle(level, viewer, marker, point.add(0, 0, i * .12));
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            particle(level, viewer, marker, point.add(Math.cos(angle) * module.value("pullStop"), 0, Math.sin(angle) * module.value("pullStop")));
        }
        if (pulling && viewer == null && !module.particles().isEmpty()) {
            var selected = module.particleEntry(module.particles().get((int) (level.getGameTime() / 4 % module.particles().size())));
            if (selected != null) dev.sablelife.module.ModuleParticles.send(level, selected, point.x, point.y, point.z, 3, .3, .3, .3, 0);
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        var points=TargetCastOrigin.targeted(module)?TargetCastOrigin.points(heart,module):List.of(destination(heart,module));if(points.isEmpty()){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}
        for(var point:points)draw((ServerLevel) player.level(), player, module, point, point, false, (long)module.value("pullWarning"));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.pull_preview_help"), true);
    }
    public void tick(HeartBlockEntity heart){tick(heart,heart.modules().all());}
 public void tickEmitter(HeartBlockEntity heart,VirtualModule module){emitterAudio=module.audioId();tick(heart,List.of(module));}
 private void tick(HeartBlockEntity heart,List<VirtualModule> modules){
        if (!(heart.getLevel() instanceof ServerLevel server)) return;
        level = server.getServer().getLevel(server.dimension()); if (level == null) return;
        long now = level.getGameTime(); var active = new HashSet<UUID>();
        for (var module : modules) {
            if (!module.type().equals("pull")) continue;
            if (module.emitter()==null && heart.health().state() == HeartState.ALIVE && !module.detached() && HeartModules.status(module, heart).equals("disconnected")) { module.detach(); heart.setChanged(); }
            if (!EmitterRuntime.active(module, heart)) continue;
            active.add(module.id()); var state = states.computeIfAbsent(module.id(), ignored -> new State());
            var before=state.clock.phase();
            var points=before==PullCycle.Phase.WAITING?(TargetCastOrigin.targeted(module)?TargetCastOrigin.points(heart,module):List.of(destination(heart,module))):state.centers;
            if(points.isEmpty())continue;
            boolean canStart=false;
            if(state.clock.ready(now)&&now>=state.nextScan){
                canStart=points.stream().anyMatch(p->!targets(level,heart,module,p,p).isEmpty())&&EmitterRuntime.start(module,heart);state.nextScan=now+5;
            }
            var phase=state.clock.tick(now,(int)module.value("pullWarning"),(int)module.value("pullDuration"),(int)module.value("interval"),canStart);
            if(phase==PullCycle.Phase.WAITING){if(before!=phase){stop(module.id());EmitterRuntime.completed(module,heart);state.victims.clear();}continue;}
            if(before==PullCycle.Phase.WAITING){state.centers=List.copyOf(points);for(var point:points)ModuleAudio.play(level,module,"warningSound",point);}
            var destinations=TargetCastOrigin.targeted(module)?state.centers:List.of(destination(heart,module));
            if(phase==PullCycle.Phase.PULLING&&before!=phase){
                stop(module.id());
                for(int i=0;i<state.centers.size();i++){
                    var center=state.centers.get(i);var point=destinations.get(i);ModuleAudio.play(level,module,"pullSound",point);
                    for(var victim:targets(level,heart,module,center,point)){
                        var old=state.victims.get(victim.getUUID());
                        if(old==null||victim.getBoundingBox().getCenter().distanceToSqr(point)<victim.getBoundingBox().getCenter().distanceToSqr(destinations.get(old)))state.victims.put(victim.getUUID(),i);
                    }
                }
            }
            if(now%4==0||before==PullCycle.Phase.WAITING)for(int i=0;i<state.centers.size();i++)draw(level,null,module,state.centers.get(i),destinations.get(i),phase==PullCycle.Phase.PULLING,phase==PullCycle.Phase.WARNING?state.clock.fireAt()-now:25);
            if(phase!=PullCycle.Phase.PULLING)continue;
            for(var id:List.copyOf(state.victims.keySet())){
                int index=state.victims.get(id);var center=state.centers.get(index);var point=destinations.get(index);
                var entity=ModuleTargets.resolve(level,id,heart.creatureId());
                if(!(entity instanceof LivingEntity victim)||!eligible(victim,module)||!inRange(victim,module,center)||!visible(level,heart,module,point,victim)){state.victims.remove(id);continue;}
                var delta = point.subtract(victim.getBoundingBox().getCenter()); double distance = delta.length();
                if (distance < 1e-6) continue;
                var direction = delta.scale(1 / distance);
                double acceleration = PullMotion.acceleration(distance, victim.getDeltaMovement().dot(direction), module.value("pullStrength"), module.value("pullSpeed"), module.value("pullStop"));
                if (Math.abs(acceleration) < 1e-6) continue;
                var impulse = direction.scale(acceleration);
                victim.push(impulse.x, impulse.y, impulse.z); victim.hurtMarked = true;
            }
        }
        for (var id : List.copyOf(states.keySet())) if (!active.contains(id)) { stop(id); states.remove(id); }
    }
}
