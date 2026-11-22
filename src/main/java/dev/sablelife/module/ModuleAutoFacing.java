package dev.sablelife.module;

import dev.sablelife.heart.*;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** One active facing controller per connected machine, shared with Travel; Teleport keeps exclusive pose ownership. */
public final class ModuleAutoFacing {
    private final Map<UUID,String> states=new HashMap<>();private long tick=Long.MIN_VALUE;
    private final Map<UUID, SightMemory> memories = new HashMap<>();
    private final Map<UUID, SightMemory.SourceLink> sourceLinks = new HashMap<>();
    private int revision;
    public void clear(){states.clear();memories.clear();sourceLinks.clear();tick=Long.MIN_VALUE;}
    public String state(UUID id){return states.getOrDefault(id,"idle");}
    public static boolean eligible(LivingEntity target,VirtualModule module){
        if(!target.isAlive()||target.isSpectator())return false;
        if(target instanceof Player player&&player.isCreative())return module.value("facingCreative")==1&&(module.value("target")==0||module.value("target")==2||module.value("target")==4);
        return ModuleTargets.matches(target,module);
    }
    public static LivingEntity selectTarget(ServerLevel level,HeartBlockEntity heart,VirtualModule module,Vec3 pivot){
        if (module.value("facingSource") == 1) {
            var shared = heart.modules().events().previewSensorAim(heart, module.facingSensorId());
            var target = shared == null || !shared.visible() ? null : linkedTarget(level, heart, module, shared);
            if (target != null && SightMemory.enabled(module)
                    && !facingVisible(level, heart, pivot, target, shared.point())) return null;
            return target;
        }
        double range=module.value("facingRange");boolean sight=module.value("lineOfSight")==1;
        var ownBodies=sight?new HashSet<>(SableBodyProbe.inspect(level,heart.getBlockPos()).connectedBodies()):Set.<UUID>of();
        return ModuleTargets.queryForModule(level,heart,module,new AABB(pivot,pivot).inflate(range),e->eligible(e,module)).stream()
                .filter(e->e.getBoundingBox().getCenter().distanceToSqr(pivot)<=range*range)
                .filter(e->!sight||dev.sablelife.sense.SensorGeometry.visible(level,heart.creatureId(),pivot,e.getBoundingBox().getCenter(),e instanceof ModuleTargets.LifeTarget?e.getUUID():null,ownBodies))
                .min(ModuleTargets.comparator(heart,e->e.getBoundingBox().getCenter().distanceToSqr(pivot)).thenComparing(e->e.getUUID().toString())).orElse(null);
    }
    private static LivingEntity linkedTarget(ServerLevel level, HeartBlockEntity heart, VirtualModule module, SightMemory.Target aim) {
        var sensor = heart.modules().find(module.facingSensorId());
        var target = sensor == null ? null : ModuleTargets.resolveForModule(level, aim.id(), heart, sensor);
        if (target instanceof Player player && player.isCreative() && module.value("facingCreative") == 0) return null;
        return target;
    }

    private static boolean facingVisible(ServerLevel level, HeartBlockEntity heart, Vec3 pivot, LivingEntity target, Vec3 point) {
        var own = new HashSet<>(SableBodyProbe.inspect(level, heart.getBlockPos()).connectedBodies());
        return SensorGeometry.visible(level, heart.creatureId(), pivot, point,
                target instanceof ModuleTargets.LifeTarget ? target.getUUID() : null, own);
    }

    private Vec3 facingPoint(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 pivot) {
        var order = heart.modules().commandTarget().current(level, CommandTarget.AUTO_FACING);
        if (order != null) {
            memories.remove(module.id());
            sourceLinks.remove(module.id());
            var aim = order.resolve(level, module.value("facingCreative") == 1);
            if (aim == null || SightMemory.enabled(module)
                    && !SensorGeometry.commandVisible(level, heart, pivot, aim.point(), order.lifeId())) return null;
            return aim.point();
        }
        if (module.value("facingSource") == 1) {
            memories.remove(module.id());
            var shared = heart.modules().events().sensorAim(heart, module.facingSensorId());
            var target = shared == null ? null : linkedTarget(level, heart, module, shared);
            var link = sourceLinks.computeIfAbsent(module.id(), ignored -> new SightMemory.SourceLink());
            return link.follow(module.facingSensorId(), target == null ? null : shared, SightMemory.enabled(module),
                    point -> true, point -> facingVisible(level, heart, pivot, target, point));
        }
        double range = module.value("facingRange");
        double rangeSquared = range * range;
        if (module.value("trigger") == 4 && heart.modules().moveTarget(module.id()) != null) {
            memories.remove(module.id());
            var inherited = heart.modules().moveAim(module.id());
            var target = inherited == null ? null : ModuleTargets.resolveForModule(level, inherited.id(), heart, module);
            if (target == null || !eligible(target, module)) {
                sourceLinks.remove(module.id());
                return null;
            }
            boolean sight = SightMemory.enabled(module);
            var action = heart.modules().moveAction(module.id());
            if (action != null) {
                var link = sourceLinks.computeIfAbsent(module.id(), ignored -> new SightMemory.SourceLink());
                return link.follow(action.table(), inherited, sight, point -> point.distanceToSqr(pivot) <= rangeSquared,
                        point -> facingVisible(level, heart, pivot, target, point));
            }
            sourceLinks.remove(module.id());
            if (inherited.point().distanceToSqr(pivot) > rangeSquared
                    || sight && !facingVisible(level, heart, pivot, target, inherited.point())) return null;
            return inherited.point();
        }
        sourceLinks.remove(module.id());
        int ticks = SightMemory.ticks(module);
        var memory = ticks > 0 ? memories.computeIfAbsent(module.id(), ignored -> new SightMemory()) : null;
        if (memory == null) memories.remove(module.id());
        else {
            long now = level.getGameTime();
            var last = memory.aimAt(now);
            var old = last == null ? null : ModuleTargets.resolveForModule(level, last.id(), heart, module);
            if (old != null && eligible(old, module)) {
                var point = SableBodyProbe.toWorld(level, old.getBoundingBox().getCenter());
                boolean withinCurrent = point.distanceToSqr(pivot) <= rangeSquared;
                boolean withinLast = last.point().distanceToSqr(pivot) <= rangeSquared;
                if (withinCurrent || withinLast) {
                    if (SightMemory.canRecheck(pivot, point) && facingVisible(level, heart, pivot, old, point)) {
                        if (withinCurrent) return memory.see(old.getUUID(), point).point();
                    } else if (withinLast) {
                        var recalled = memory.remember(now, ticks);
                        if (recalled != null) return recalled.point();
                    }
                }
            }
            memory.clear();
        }
        var target = selectTarget(level, heart, module, pivot);
        if (target == null) return null;
        var point = SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
        return memory == null ? point : memory.see(target.getUUID(), point).point();
    }
    public void tick(HeartBlockEntity heart,boolean movementBusy){tick(heart,movementBusy,false);}
    public void tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        if(!heart.operational()||!heart.wireless().master()){clear();return;}
        if (revision != heart.modules().revision()) { memories.clear(); sourceLinks.clear(); }
        revision = heart.modules().revision();
        if(tick==level.getGameTime())return;tick=level.getGameTime();boolean controlled=false;var keep=new HashSet<UUID>();
        for(var module:heart.modules().all()){
            if(!module.type().equals("auto_facing"))continue;keep.add(module.id());
            if(!HeartModules.triggered(module,heart)){memories.remove(module.id());sourceLinks.remove(module.id());states.put(module.id(),"idle");continue;}
            if ((movementBusy || controlled) && !memories.containsKey(module.id()) && !sourceLinks.containsKey(module.id())) { states.put(module.id(), "busy"); continue; }
            var pivot=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
            var target=facingPoint(level,heart,module,pivot);
            if(movementBusy||controlled){states.put(module.id(),"busy");continue;}
            if(target==null){states.put(module.id(),"no_target");continue;}controlled=true;
            var anchor=module.anchors().getFirst();var marked=anchor.body(heart);if(marked==null)continue;
            var orientation=new Quaterniond(marked.logicalPose().orientation());
            var localFront=Vec3.atLowerCornerOf(anchor.face().getNormal());
            var front=ContactBox.rotate(orientation,localFront);
            boolean vertical=module.value("facingVertical")==1;
            if(!vertical&&front.x*front.x+front.z*front.z<1e-8){states.put(module.id(),"front_invalid");continue;}
            var bodies=LinearMovementGeometry.capture(heart);if(bodies.isEmpty()){states.put(module.id(),"blocked");continue;}
            double radius=AutoFacingGeometry.radius(bodies,pivot);
            double degrees=module.value("facingCollision")==0?module.value("facingSpeed")/20:Math.min(module.value("facingSpeed")/20,Math.toDegrees(4/radius));
            var offset=AutoFacingGeometry.aimOffset(orientation,localFront,module.value("facingOffsetLeft"),vertical?module.value("facingOffsetUp"):0);
            var desired=target.subtract(pivot);
            var reference=AutoFacingGeometry.aimReference(front,desired,offset,vertical);
            if(reference==null){states.put(module.id(),"offset_unreachable");continue;}
            var turn=AutoFacingGeometry.turn(reference,desired,vertical,degrees);
            double angle=2*Math.acos(Math.min(1,Math.abs(turn.w)));if(angle<1e-5){states.put(module.id(),"tracking");continue;}
            boolean clear=module.value("facingCollision")==0||AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius),verticalBlocked=false;
            if(!clear&&vertical){
                // A blocked pitch must not veto an otherwise safe horizontal turn.
                var horizontalOffset=AutoFacingGeometry.aimOffset(orientation,localFront,module.value("facingOffsetLeft"),0);
                var horizontalReference=AutoFacingGeometry.aimReference(front,desired,horizontalOffset,false);
                if(horizontalReference==null){states.put(module.id(),"offset_unreachable");continue;}
                turn=AutoFacingGeometry.turn(horizontalReference,desired,false,degrees);
                clear=AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius);verticalBlocked=clear;
                if(clear&&2*Math.acos(Math.min(1,Math.abs(turn.w)))<1e-5){states.put(module.id(),"vertical_blocked");continue;}
            }
            var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
            if(!clear||physics==null){states.put(module.id(),CollisionWorkBudget.exhausted(level)?"budget":"blocked");continue;}
            var rotated=AutoFacingGeometry.rotated(bodies,pivot,turn);
            for(var body:rotated){var velocity=(preserveTranslation?physics.getPipeline().getLinearVelocity(body.body(),new Vector3d()):new Vector3d(body.body().latestLinearVelocity));physics.getPipeline().resetVelocity(body.body());physics.getPipeline().teleport(body.body(),body.position(),body.rotation());physics.getPipeline().addLinearAndAngularVelocity(body.body(),velocity,new Vector3d());}
            heart.modules().movementTurned(heart,pivot,turn);
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);
            states.put(module.id(),verticalBlocked?"vertical_blocked":"tracking");
        }states.keySet().retainAll(keep);memories.keySet().retainAll(keep);sourceLinks.keySet().retainAll(keep);
    }
}
