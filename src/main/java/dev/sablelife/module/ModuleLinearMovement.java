package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import java.util.*;

/** At most one displacement controls a connected machine; Forced paths use frozen translation origins so terrain response cannot erase forward progress. */
public final class ModuleLinearMovement {
    private static final class State { long ready,chargeEnd,travelEnd; boolean gravity,holdStepHeight,steerable; final GravityTravel.Session stepping=new GravityTravel.Session(); int phase; Vec3 offset=Vec3.ZERO,direction,goal,forceOrigin,lastCore; double distance,progress,driveSpeed; List<LinearMovementGeometry.Body> bodies=List.of(),forceStart=List.of(); String status="idle"; }
    public boolean gravityActive(){return gravityMovedThisTick||states.values().stream().anyMatch(s->s.phase>0&&s.gravity);}
    public boolean locksRotation(){return movedThisTick&&!gravityMovedThisTick||states.values().stream().anyMatch(s->s.phase>0&&!s.gravity);}
    public record Plan(Vec3 delta,Vec3 goal){}
    private final Map<UUID,State> states=new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    private boolean movedThisTick,gravityMovedThisTick,teleportedThisTick;
    public void clear(){states.clear();lastTick=Long.MIN_VALUE;movedThisTick=false;gravityMovedThisTick=false;teleportedThisTick=false;}
    public boolean busy(){return movedThisTick||states.values().stream().anyMatch(s->s.phase>0);}
    public boolean blocksTurning(){return teleportedThisTick||states.values().stream().anyMatch(s->s.phase>0&&!s.steerable);}
    public boolean preserveTranslation(){return busy()||gravityMovedThisTick;}
    private static Vec3 corePoint(HeartBlockEntity heart){return dev.sablelife.compat.sable.SableBodyProbe.toWorld(heart.getLevel(),heart.getBlockPos().getCenter());}
    /** Carry the frozen route through a rotation, rather than snapping limbs back to old offsets. */
    public void turned(HeartBlockEntity heart,Vec3 pivot,org.joml.Quaterniond turn){
        for(var e:states.entrySet()){
            var s=e.getValue();if(s.phase!=2)continue;
            if(!s.gravity){s.forceStart=AutoFacingGeometry.rotated(s.forceStart,pivot,turn);s.offset=dev.sablelife.attack.ContactBox.rotate(turn,s.offset);}
            else if(s.goal==null&&heart.getLevel() instanceof ServerLevel level){
                var m=heart.modules().find(e.getKey());if(m==null)continue;
                var direction=GravityTravel.horizontal(LinearMovementGeometry.direction(heart,m)).normalize();
                var change=direction.subtract(s.direction).scale(s.driveSpeed);
                var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
                if(physics!=null)for(var body:s.bodies)physics.getPipeline().addLinearAndAngularVelocity(body.body(),new org.joml.Vector3d(change.x,0,change.z),new org.joml.Vector3d());
                s.direction=direction;
            }
        }
    }
    public String phase(UUID id){var s=states.get(id);return s==null?"idle":s.status;}
    private static Vec3 point(HeartBlockEntity heart,VirtualModule module){return module.anchors().getFirst().world(heart,true);}
    private static Vec3 plannedPoint(HeartBlockEntity heart,VirtualModule module,State s){
        var marked=module.anchors().getFirst().body(heart);
        var current=s.bodies.stream().filter(b->b.body()==marked).findFirst().orElse(null);
        var start=s.forceStart.stream().filter(b->b.body()==marked).findFirst().orElse(null);
        if(current==null||start==null)return null;
        return point(heart,module).add(start.position().x+s.offset.x-current.position().x,start.position().y+s.offset.y-current.position().y,start.position().z+s.offset.z-current.position().z);
    }
    public static Vec3 towards(Vec3 start,Vec3 target,double stopDistance){var delta=target.subtract(start);double distance=delta.length();return distance<=Math.max(0,stopDistance)||distance<.00001?Vec3.ZERO:delta.scale((distance-Math.max(0,stopDistance))/distance);}
    public static Plan plan(HeartBlockEntity heart,VirtualModule module){
        if(module.value("moveAim")==0){var dir=LinearMovementGeometry.direction(heart,module);if(GravityTravel.enabled(module))dir=GravityTravel.horizontal(dir).normalize();return new Plan(dir.scale(module.value("moveDistance")),null);}
        if(!(heart.getLevel() instanceof ServerLevel level))return null;
        var start=point(heart,module);double range=module.value("moveTargetRange");
        var target=ModuleTargets.query(level,heart.creatureId(),new net.minecraft.world.phys.AABB(start,start).inflate(range),e->
            e instanceof net.minecraft.world.entity.player.Player p&&p.isCreative()?module.value("facingCreative")==1&&p.isAlive()&&!p.isSpectator()&&(module.value("target")==0||module.value("target")==2):ModuleTargets.matches(e,module))
            .stream().map(e->dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter())).filter(p->p.distanceToSqr(start)<=range*range).min(Comparator.comparingDouble(p->p.distanceToSqr(start))).orElse(null);
        if(target==null)return null;if(module.value("moveTargetVertical")==0||GravityTravel.enabled(module))target=new Vec3(target.x,start.y,target.z);var delta=towards(start,target,module.value("moveStopDistance"));return new Plan(delta,start.add(delta));
    }
    private static void effect(ServerLevel level,VirtualModule module,String stage,Vec3 point,boolean sound){
        for(var particle:module.movementParticles(stage).particleEntries())ModuleParticles.send(level,particle,point.x,point.y,point.z,16,.4,.4,.4,.03);
        if(sound){String key="move"+stage+"Sound";ModuleAudio.play(level,module,key,point);}
    }
    private static void finish(HeartBlockEntity heart,VirtualModule module,State s,String status){if(s.gravity&&s.phase==2&&heart.getLevel() instanceof ServerLevel level)GravityTravel.stop(level,s.bodies);s.stepping.clear();s.phase=0;s.status=status;s.bodies=List.of();s.forceStart=List.of();heart.modules().hurtTriggers.completed(module);}
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        if(heart.health().state()!=HeartState.ALIVE||!heart.wireless().master()){clear();return;}
        long now=level.getGameTime();if(lastTick==now)return;lastTick=now;movedThisTick=false;teleportedThisTick=false;gravityMovedThisTick=states.values().stream().anyMatch(s->s.phase>0&&s.gravity);var keep=new HashSet<UUID>();
        boolean busy=states.values().stream().anyMatch(s->s.phase>0);
        for(var module:heart.modules().all()){
            if(!module.type().equals("linear_movement"))continue;keep.add(module.id());var s=states.computeIfAbsent(module.id(),ignored->new State());
            if(s.phase>0&&(s.gravity!=GravityTravel.enabled(module)||s.steerable!=(module.value("linearMode")==1))){finish(heart,module,s,"cancelled");continue;}
            if(!HeartModules.triggered(module,heart)){if(s.phase>0)finish(heart,module,s,"cancelled");continue;}
            if(s.phase==0){
                if(busy||now<s.ready)continue;
                var plan=plan(heart,module);if(plan==null){s.status="no_target";s.ready=now+5;if(ModuleHurt.once(module))heart.modules().hurtTriggers.finish(module.id());continue;}
                if(!heart.modules().hurtTriggers.start(module))continue;
                s.gravity=GravityTravel.enabled(module);s.steerable=module.value("linearMode")==1;s.progress=0;s.driveSpeed=0;s.lastCore=null;s.holdStepHeight=false;s.stepping.clear();s.goal=plan.goal();s.ready=now+(long)module.value("interval");
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
                if(s.goal==null){s.direction=LinearMovementGeometry.direction(heart,module);if(s.gravity)s.direction=GravityTravel.horizontal(s.direction).normalize();}
                if(s.goal!=null){if(module.value("moveTargetVertical")==0||s.gravity)s.goal=new Vec3(s.goal.x,point(heart,module).y,s.goal.z);var remaining=s.goal.subtract(point(heart,module));s.distance=remaining.length();s.direction=remaining.normalize();if(s.distance<.0001){finish(heart,module,s,"arrived");continue;}}
                var end=s.direction.scale(s.distance);boolean teleport=module.value("linearMode")==0;boolean force=module.value("moveForce")==1&&!s.gravity;
                s.forceStart=s.bodies;s.forceOrigin=point(heart,module);
                boolean clear=s.gravity?LinearMovementGeometry.loadedDestination(level,s.bodies):force?LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(s.bodies,s.bodies,teleport?end:Vec3.ZERO)):LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,teleport?end:Vec3.ZERO,teleport&&module.value("moveObstruction")==0);
                if(!clear){if(!force&&CollisionWorkBudget.exhausted(level)){s.status="budget";continue;}finish(heart,module,s,"blocked");continue;}
                var depart=point(heart,module);
                if(teleport){
                    if(!LinearMovementGeometry.move(level,s.bodies,end)){finish(heart,module,s,"blocked");continue;}
                    movedThisTick=true;teleportedThisTick=true;
                    effect(level,module,"Departure",depart,true);effect(level,module,"Arrival",depart.add(end),true);finish(heart,module,s,"arrived");continue;
                }
                s.travelEnd=now+Math.max(100,(long)Math.ceil(s.distance/module.value("travelSpeed")*80)+40);s.phase=2;s.lastCore=corePoint(heart);s.status="travelling";effect(level,module,"Departure",depart,true);
            }
            if(s.phase==2){
                if(!LinearMovementGeometry.connected(heart,s.bodies)){finish(heart,module,s,"cancelled");continue;}
                // Re-capture actual poses and bounds each tick, including blocks added during Travel.
                s.bodies=LinearMovementGeometry.capture(heart);
                var departure=point(heart,module);double next;Vec3 delta,step;
                if(s.gravity){
                    var currentCore=corePoint(heart);if(s.goal==null&&s.lastCore!=null)s.progress+=Math.max(0,GravityTravel.horizontal(currentCore.subtract(s.lastCore)).dot(s.direction));s.lastCore=currentCore;
                    double remaining=s.goal==null?s.distance-s.progress:GravityTravel.horizontal(s.goal.subtract(departure)).length();
                    if(remaining<=.05){effect(level,module,"Arrival",departure,true);finish(heart,module,s,"arrived");continue;}
                    if(now>=s.travelEnd){finish(heart,module,s,"blocked");continue;}
                    var driveDirection=s.goal==null?GravityTravel.horizontal(LinearMovementGeometry.direction(heart,module)).normalize():GravityTravel.horizontal(s.goal.subtract(departure)).normalize();
                    double speed=Math.min(module.value("travelSpeed"),remaining*20);s.direction=driveDirection;s.driveSpeed=speed;
                    if(!GravityTravel.drive(level,heart,module,s.bodies,driveDirection,speed,s.stepping)){finish(heart,module,s,"blocked");continue;}
                    movedThisTick=true;gravityMovedThisTick=true;s.status=CollisionWorkBudget.exhausted(level)?"budget":"travelling";continue;
                }
                if(s.goal==null)s.direction=LinearMovementGeometry.direction(heart,module);
                // Route Travel must retain its new height after a step, rather than sag back into the obstacle.
                if(module.value("moveStep")==1&&now>=s.travelEnd){finish(heart,module,s,"blocked");continue;}
                if(s.holdStepHeight&&module.value("moveForce")!=1){
                    var baseline=s.forceStart.getFirst();var reference=s.bodies.stream().filter(b->b.body()==baseline.body()).findFirst().orElse(null);
                    if(reference==null){finish(heart,module,s,"cancelled");continue;}
                    double correction=baseline.position().y+s.offset.y-reference.position().y;
                    if(Math.abs(correction)>1e-6){
                        var aligned=LinearMovementGeometry.forcedDestination(s.bodies,s.bodies,new Vec3(0,correction,0));
                        if(!LinearMovementGeometry.loadedDestination(level,aligned)||!LinearMovementGeometry.move(level,aligned,Vec3.ZERO)){finish(heart,module,s,"blocked");continue;}
                        s.bodies=LinearMovementGeometry.capture(heart);departure=point(heart,module);
                    }
                }
                // Step is independent from gravity. Its lift is added to the route, never counted as travel distance.
                var stepDirection=GravityTravel.horizontal(s.direction).normalize();
                var stepping=GravityTravel.step(level,module,s.bodies,stepDirection,module.value("travelSpeed"),s.stepping);
                if(!stepping.valid()){finish(heart,module,s,"blocked");continue;}
                if(stepping.lift()>0){
                    s.holdStepHeight=true;s.bodies=LinearMovementGeometry.capture(heart);departure=point(heart,module);movedThisTick=true;
                    if(s.goal!=null)s.goal=s.goal.add(0,stepping.lift(),0);
                    s.forceStart=LinearMovementGeometry.forcedDestination(s.forceStart,s.forceStart,new Vec3(0,stepping.lift(),0));
                }
                if(module.value("moveForce")==1){
                    // Advance along the planned translation regardless of solver pushback during prior ticks.
                    boolean arrived;
                    if(s.goal!=null){
                        var planned=plannedPoint(heart,module,s);if(planned==null){finish(heart,module,s,"cancelled");continue;}
                        if(module.value("moveTargetVertical")==0)s.goal=new Vec3(s.goal.x,planned.y,s.goal.z);
                        var remaining=s.goal.subtract(planned);double distance=remaining.length();s.direction=remaining.normalize();double advance=Math.min(distance,module.value("travelSpeed")/20);
                        next=s.progress+advance;delta=s.offset.add(s.direction.scale(advance));arrived=distance<=advance+.0001;
                    }else{next=Math.min(s.distance,s.progress+module.value("travelSpeed")/20);delta=s.offset.add(s.direction.scale(next-s.progress));arrived=next>=s.distance-.0001;}
                    var destination=LinearMovementGeometry.forcedDestination(s.forceStart,s.bodies,delta);
                    if(!LinearMovementGeometry.loadedDestination(level,destination)||!LinearMovementGeometry.move(level,destination,Vec3.ZERO)){finish(heart,module,s,"blocked");continue;}
                    movedThisTick=true;s.progress=next;s.offset=delta;
                    if(arrived){effect(level,module,"Arrival",point(heart,module),true);finish(heart,module,s,"arrived");}continue;
                }
                if(s.goal!=null){if(module.value("moveTargetVertical")==0||s.gravity)s.goal=new Vec3(s.goal.x,departure.y,s.goal.z);var remaining=s.goal.subtract(departure);double distance=remaining.length();if(distance<.0001){finish(heart,module,s,"arrived");continue;}s.direction=remaining.normalize();next=Math.min(distance,module.value("travelSpeed")/20);step=s.direction.scale(next);delta=s.offset.add(step);}
                else {next=Math.min(s.distance,s.progress+module.value("travelSpeed")/20);delta=s.offset.add(s.direction.scale(next-s.progress));step=delta.subtract(s.offset);}
                if(!LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,step,true)){
                    if(CollisionWorkBudget.exhausted(level)){s.status="budget";continue;}
                    if(stepping.lift()>0){s.status="travelling";continue;}
                    double low=0,high=step.length();for(int i=0;i<10;i++){double mid=(low+high)/2;if(LinearMovementGeometry.clear(level,s.bodies,Vec3.ZERO,s.direction.scale(mid),true))low=mid;else high=mid;}
                    if(CollisionWorkBudget.exhausted(level)){s.status="budget";continue;}
                    var safe=s.direction.scale(low);if(low>.001){movedThisTick=LinearMovementGeometry.move(level,s.bodies,safe)||movedThisTick;s.offset=s.offset.add(safe);s.progress+=safe.length();}
                    if(module.value("moveStep")==1){s.status="travelling";continue;}
                    effect(level,module,"Arrival",departure.add(safe),true);finish(heart,module,s,"blocked");continue;
                }
                if(!LinearMovementGeometry.move(level,s.bodies,step)){finish(heart,module,s,"blocked");continue;}
                movedThisTick=true;
                s.offset=delta;if(s.goal==null)s.progress=next;
                if(s.goal!=null?departure.add(step).distanceToSqr(s.goal)<.00000001:next>=s.distance-.0001){effect(level,module,"Arrival",departure.add(step),true);finish(heart,module,s,"arrived");}
            }
        }states.keySet().retainAll(keep);
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
        var level=(ServerLevel)heart.getLevel();var start=point(heart,module);var plan=plan(heart,module);if(plan==null){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}var delta=plan.delta();var bodies=LinearMovementGeometry.capture(heart);
        boolean path=module.value("linearMode")==1||module.value("moveObstruction")==0;
        boolean clear=GravityTravel.enabled(module)?LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(bodies,bodies,delta)):module.value("moveForce")==1?LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(bodies,bodies,delta)):LinearMovementGeometry.clear(level,bodies,Vec3.ZERO,delta,path);
        var dust=new DustParticleOptions(new org.joml.Vector3f(clear?.2F:1,clear?1:.1F,.2F),1);
        int steps=Math.clamp((int)(delta.length()*4),8,512);for(int i=0;i<=steps;i++){var p=start.add(delta.scale(i/(double)steps));level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}
        for(var b:bodies){var box=b.box().move(delta);for(int edge=0;edge<12;edge++)for(int i=0;i<=8;i++){
            double t=i/8.,x,y,z;if(edge<4){x=box.minX+(box.maxX-box.minX)*t;y=(edge&1)==0?box.minY:box.maxY;z=(edge&2)==0?box.minZ:box.maxZ;}else if(edge<8){y=box.minY+(box.maxY-box.minY)*t;x=(edge&1)==0?box.minX:box.maxX;z=(edge&2)==0?box.minZ:box.maxZ;}else{z=box.minZ+(box.maxZ-box.minZ)*t;x=(edge&1)==0?box.minX:box.maxX;y=(edge&2)==0?box.minY:box.maxY;}level.sendParticles(player,dust,true,x,y,z,1,0,0,0,0);
        }}player.displayClientMessage(net.minecraft.network.chat.Component.translatable(GravityTravel.enabled(module)?"dashboard.sablelife.move_gravity_preview":module.value("linearMode")==1&&module.value("moveStep")==1?"dashboard.sablelife.move_step_preview":"dashboard.sablelife.move_preview_"+(clear?"clear":"blocked")),true);
    }
}
