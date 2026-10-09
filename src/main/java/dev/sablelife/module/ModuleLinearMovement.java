package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import java.util.*;

/** At most one displacement controls a connected machine; Forced paths use frozen translation origins so terrain response cannot erase forward progress. */
public final class ModuleLinearMovement {
    private static final class State { long ready,chargeEnd; int phase; Vec3 offset=Vec3.ZERO,direction,goal,forceOrigin; double distance; List<LinearMovementGeometry.Body> bodies=List.of(),forceStart=List.of(); String status="idle"; }
    public record Plan(Vec3 delta,Vec3 goal){}
    private final Map<UUID,State> states=new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    private boolean movedThisTick;
    public void clear(){states.clear();lastTick=Long.MIN_VALUE;movedThisTick=false;}
    public boolean busy(){return movedThisTick||states.values().stream().anyMatch(s->s.phase>0);}
    public String phase(UUID id){var s=states.get(id);return s==null?"idle":s.status;}
    private static Vec3 point(HeartBlockEntity heart,VirtualModule module){return module.anchors().getFirst().world(heart,true);}
    public static Vec3 towards(Vec3 start,Vec3 target,double stopDistance){var delta=target.subtract(start);double distance=delta.length();return distance<=Math.max(0,stopDistance)||distance<.00001?Vec3.ZERO:delta.scale((distance-Math.max(0,stopDistance))/distance);}
    public static Plan plan(HeartBlockEntity heart,VirtualModule module){
        if(module.value("moveAim")==0)return new Plan(LinearMovementGeometry.direction(heart,module).scale(module.value("moveDistance")),null);
        if(!(heart.getLevel() instanceof ServerLevel level))return null;
        var start=point(heart,module);double range=module.value("moveTargetRange");
        var target=ModuleTargets.query(level,heart.creatureId(),new net.minecraft.world.phys.AABB(start,start).inflate(range),e->
            e instanceof net.minecraft.world.entity.player.Player p&&p.isCreative()?module.value("facingCreative")==1&&p.isAlive()&&!p.isSpectator()&&(module.value("target")==0||module.value("target")==2):ModuleTargets.matches(e,module))
            .stream().map(e->dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter())).filter(p->p.distanceToSqr(start)<=range*range).min(Comparator.comparingDouble(p->p.distanceToSqr(start))).orElse(null);
        if(target==null)return null;if(module.value("moveTargetVertical")==0)target=new Vec3(target.x,start.y,target.z);var delta=towards(start,target,module.value("moveStopDistance"));return new Plan(delta,start.add(delta));
    }
    private static void effect(ServerLevel level,VirtualModule module,String stage,Vec3 point,boolean sound){
        for(var particle:module.movementParticles(stage).particleEntries())ModuleParticles.send(level,particle,point.x,point.y,point.z,16,.4,.4,.4,.03);
        if(sound){String key="move"+stage+"Sound";ModuleAudio.play(level,module,key,point);}
    }
    private static void finish(HeartBlockEntity heart,VirtualModule module,State s,String status){s.phase=0;s.status=status;s.bodies=List.of();s.forceStart=List.of();heart.modules().hurtTriggers.completed(module);}
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        if(heart.health().state()!=HeartState.ALIVE||!heart.wireless().master()){clear();return;}
        long now=level.getGameTime();if(lastTick==now)return;lastTick=now;movedThisTick=false;var keep=new HashSet<UUID>();
        boolean busy=states.values().stream().anyMatch(s->s.phase>0);
        for(var module:heart.modules().all()){
            if(!module.type().equals("linear_movement"))continue;keep.add(module.id());var s=states.computeIfAbsent(module.id(),ignored->new State());
            if(!HeartModules.triggered(module,heart)){if(s.phase>0)finish(heart,module,s,"cancelled");continue;}
            if(s.phase==0){
                if(busy||now<s.ready)continue;
                var plan=plan(heart,module);if(plan==null){s.status="no_target";s.ready=now+5;if(ModuleHurt.once(module))heart.modules().hurtTriggers.finish(module.id());continue;}
                if(!heart.modules().hurtTriggers.start(module))continue;
                s.goal=plan.goal();s.ready=now+(long)module.value("interval");
                if(plan.delta().lengthSqr()<.00000001){finish(heart,module,s,"close");continue;}
                s.bodies=LinearMovementGeometry.capture(heart);s.offset=Vec3.ZERO;
                if(s.bodies.isEmpty()){finish(heart,module,s,"blocked");s.ready=now+20;continue;}
                s.direction=plan.delta().normalize();s.distance=plan.delta().length();s.phase=1;s.status="charging";busy=true;
                s.chargeEnd=now+(long)module.value("chargeTime");s.ready=now+(long)module.value("interval");effect(level,module,"Charge",point(heart,module),true);
            }
            if(s.phase==1){
                if(now%5==0)effect(level,module,"Charge",point(heart,module),false);
                if(now<s.chargeEnd)continue;
                s.bodies=LinearMovementGeometry.capture(heart);s.offset=Vec3.ZERO;
                if(s.goal!=null){if(module.value("moveTargetVertical")==0)s.goal=new Vec3(s.goal.x,point(heart,module).y,s.goal.z);var remaining=s.goal.subtract(point(heart,module));s.distance=remaining.length();s.direction=remaining.normalize();if(s.distance<.0001){finish(heart,module,s,"arrived");continue;}}
                var end=s.direction.scale(s.distance);boolean teleport=module.value("linearMode")==0;boolean force=module.value("moveForce")==1;
                s.forceStart=s.bodies;s.forceOrigin=point(heart,module);
                if(force?!LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(s.bodies,s.bodies,teleport?end:Vec3.ZERO)):!LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,teleport?end:Vec3.ZERO,teleport&&module.value("moveObstruction")==0)){finish(heart,module,s,"blocked");continue;}
                var depart=point(heart,module);
                if(teleport){
                    if(!LinearMovementGeometry.move(level,s.bodies,end)){finish(heart,module,s,"blocked");continue;}
                    movedThisTick=true;
                    effect(level,module,"Departure",depart,true);effect(level,module,"Arrival",depart.add(end),true);finish(heart,module,s,"arrived");continue;
                }
                s.phase=2;s.status="travelling";effect(level,module,"Departure",depart,true);
            }
            if(s.phase==2){
                if(!LinearMovementGeometry.connected(heart,s.bodies)){finish(heart,module,s,"cancelled");continue;}
                // Re-capture actual poses and bounds each tick, including blocks added during Travel.
                s.bodies=LinearMovementGeometry.capture(heart);
                var departure=point(heart,module);double next;Vec3 delta,step;
                if(module.value("moveForce")==1){
                    // Advance along the planned translation regardless of solver pushback during prior ticks.
                    next=Math.min(s.distance,s.offset.length()+module.value("travelSpeed")/20);delta=s.direction.scale(next);
                    var destination=LinearMovementGeometry.forcedDestination(s.forceStart,s.bodies,delta);
                    if(!LinearMovementGeometry.loadedDestination(level,destination)||!LinearMovementGeometry.move(level,destination,Vec3.ZERO)){finish(heart,module,s,"blocked");continue;}
                    movedThisTick=true;s.offset=delta;
                    if(next>=s.distance-.0001){effect(level,module,"Arrival",s.forceOrigin.add(delta),true);finish(heart,module,s,"arrived");}continue;
                }
                if(s.goal!=null){if(module.value("moveTargetVertical")==0)s.goal=new Vec3(s.goal.x,departure.y,s.goal.z);var remaining=s.goal.subtract(departure);double distance=remaining.length();if(distance<.0001){finish(heart,module,s,"arrived");continue;}s.direction=remaining.normalize();next=Math.min(distance,module.value("travelSpeed")/20);step=s.direction.scale(next);delta=s.offset.add(step);}
                else {next=Math.min(s.distance,s.offset.length()+module.value("travelSpeed")/20);delta=s.direction.scale(next);step=delta.subtract(s.offset);}
                if(!LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,step,true)){
                    double low=0,high=step.length();for(int i=0;i<10;i++){double mid=(low+high)/2;if(LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,s.direction.scale(mid),true))low=mid;else high=mid;}
                    var safe=s.direction.scale(low);if(low>.001)movedThisTick=LinearMovementGeometry.move(level,s.bodies,safe)||movedThisTick;
                    effect(level,module,"Arrival",departure.add(safe),true);finish(heart,module,s,"blocked");continue;
                }
                if(!LinearMovementGeometry.move(level,s.bodies,step)){finish(heart,module,s,"blocked");continue;}
                movedThisTick=true;
                s.offset=delta;
                if(s.goal!=null?departure.add(step).distanceToSqr(s.goal)<.00000001:next>=s.distance-.0001){effect(level,module,"Arrival",departure.add(step),true);finish(heart,module,s,"arrived");}
            }
        }states.keySet().retainAll(keep);
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
        var level=(ServerLevel)heart.getLevel();var start=point(heart,module);var plan=plan(heart,module);if(plan==null){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}var delta=plan.delta();var bodies=LinearMovementGeometry.capture(heart);
        boolean path=module.value("linearMode")==1||module.value("moveObstruction")==0;
        boolean clear=module.value("moveForce")==1?LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(bodies,bodies,delta)):LinearMovementGeometry.clear(level,bodies,Vec3.ZERO,delta,path);
        var dust=new DustParticleOptions(new org.joml.Vector3f(clear?.2F:1,clear?1:.1F,.2F),1);
        int steps=Math.clamp((int)(delta.length()*4),8,512);for(int i=0;i<=steps;i++){var p=start.add(delta.scale(i/(double)steps));level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}
        for(var b:bodies){var box=b.box().move(delta);for(int edge=0;edge<12;edge++)for(int i=0;i<=8;i++){
            double t=i/8.,x,y,z;if(edge<4){x=box.minX+(box.maxX-box.minX)*t;y=(edge&1)==0?box.minY:box.maxY;z=(edge&2)==0?box.minZ:box.maxZ;}else if(edge<8){y=box.minY+(box.maxY-box.minY)*t;x=(edge&1)==0?box.minX:box.maxX;z=(edge&2)==0?box.minZ:box.maxZ;}else{z=box.minZ+(box.maxZ-box.minZ)*t;x=(edge&1)==0?box.minX:box.maxX;y=(edge&2)==0?box.minY:box.maxY;}level.sendParticles(player,dust,true,x,y,z,1,0,0,0,0);
        }}player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.move_preview_"+(clear?"clear":"blocked")),true);
    }
}
