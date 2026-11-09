package dev.sablelife.module;

import dev.sablelife.heart.*;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.compat.sable.SableBodyProbe;
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
    public void clear(){states.clear();tick=Long.MIN_VALUE;}
    public String state(UUID id){return states.getOrDefault(id,"idle");}
    public static boolean eligible(LivingEntity target,VirtualModule module){
        if(!target.isAlive()||target.isSpectator())return false;
        if(target instanceof Player player&&player.isCreative())return module.value("facingCreative")==1&&(module.value("target")==0||module.value("target")==2||module.value("target")==4);
        return ModuleTargets.matches(target,module);
    }
    public static LivingEntity selectTarget(ServerLevel level,HeartBlockEntity heart,VirtualModule module,Vec3 pivot){
        double range=module.value("facingRange");boolean sight=module.value("lineOfSight")==1;
        var ownBodies=sight?new HashSet<>(SableBodyProbe.inspect(level,heart.getBlockPos()).connectedBodies()):Set.<UUID>of();
        return ModuleTargets.queryForModule(level,heart,module,new AABB(pivot,pivot).inflate(range),e->eligible(e,module)).stream()
                .filter(e->e.getBoundingBox().getCenter().distanceToSqr(pivot)<=range*range)
                .filter(e->!sight||dev.sablelife.sense.SensorGeometry.visible(level,heart.creatureId(),pivot,e.getBoundingBox().getCenter(),e instanceof ModuleTargets.LifeTarget?e.getUUID():null,ownBodies))
                .min(ModuleTargets.comparator(heart,e->e.getBoundingBox().getCenter().distanceToSqr(pivot)).thenComparing(e->e.getUUID().toString())).orElse(null);
    }
    public void tick(HeartBlockEntity heart,boolean movementBusy){tick(heart,movementBusy,false);}
    public void tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        if(!heart.operational()||!heart.wireless().master()){clear();return;}
        if(tick==level.getGameTime())return;tick=level.getGameTime();boolean controlled=false;var keep=new HashSet<UUID>();
        for(var module:heart.modules().all()){
            if(!module.type().equals("auto_facing"))continue;keep.add(module.id());
            if(!HeartModules.triggered(module,heart)){states.put(module.id(),"idle");continue;}
            if(movementBusy||controlled){states.put(module.id(),"busy");continue;}
            var pivot=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
            var target=selectTarget(level,heart,module,pivot);
            if(target==null){states.put(module.id(),"no_target");continue;}controlled=true;
            var anchor=module.anchors().getFirst();var marked=anchor.body(heart);if(marked==null)continue;
            var front=ContactBox.rotate(new Quaterniond(marked.logicalPose().orientation()),Vec3.atLowerCornerOf(anchor.face().getNormal()));
            boolean vertical=module.value("facingVertical")==1;
            if(!vertical&&front.x*front.x+front.z*front.z<1e-8){states.put(module.id(),"front_invalid");continue;}
            var bodies=LinearMovementGeometry.capture(heart);if(bodies.isEmpty()){states.put(module.id(),"blocked");continue;}
            double radius=AutoFacingGeometry.radius(bodies,pivot);
            double degrees=module.value("facingCollision")==0?module.value("facingSpeed")/20:Math.min(module.value("facingSpeed")/20,Math.toDegrees(4/radius));
            var desired=target.getBoundingBox().getCenter().subtract(pivot);
            var turn=AutoFacingGeometry.turn(front,desired,vertical,degrees);
            double angle=2*Math.acos(Math.min(1,Math.abs(turn.w)));if(angle<1e-5){states.put(module.id(),"tracking");continue;}
            boolean clear=module.value("facingCollision")==0||AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius),verticalBlocked=false;
            if(!clear&&vertical){
                // A blocked pitch must not veto an otherwise safe horizontal turn.
                turn=AutoFacingGeometry.turn(front,desired,false,degrees);
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
        }states.keySet().retainAll(keep);
    }
}
