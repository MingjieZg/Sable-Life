package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import java.util.*;

/** Horizontal drive with a brief synchronized whole-machine step; ordinary height and rotation remain physical. */
public final class GravityTravel {
    private static final class Contact {long ready;double remaining;int ticks,clearTicks;boolean armed=true;Vec3 lastStepPosition;}
    public static final class Session {
        private Contact contact=new Contact();
        public void clear(){contact=new Contact();}
    }
    public static boolean enabled(VirtualModule m){return m.value("linearMode")==1&&m.value("moveGravity")==1;}
    public static Vec3 horizontal(Vec3 v){return new Vec3(v.x,0,v.z);}
    public static Vector3d driveDelta(Vector3d velocity,Vec3 direction,double speed){return new Vector3d(direction.x*speed-velocity.x,0,direction.z*speed-velocity.z);}
    /** Null means the bounded probe could not finish; never interpret it as a collision. */
    private static List<AABB> shapes(ServerLevel level,AABB area,CollisionWorkBudget.Budget budget){
        int x0=(int)Math.floor(area.minX),x1=(int)Math.floor(area.maxX),y0=(int)Math.floor(area.minY),y1=(int)Math.floor(area.maxY),z0=(int)Math.floor(area.minZ),z1=(int)Math.floor(area.maxZ);
        long volume=((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1);
        if(volume<1||volume>4096||!budget.spend((int)volume))return null;
        var result=new ArrayList<AABB>();
        for(var p:BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1)){
            if(!level.hasChunkAt(p))return null;
            var state=level.getBlockState(p);if(state.isAir())continue;
            for(var shape:state.getCollisionShape(level,p).toAabbs()){var box=shape.move(p);if(box.intersects(area))result.add(box);}
        }
        return result;
    }
    /** Lower part bounds are probes, not a teleport destination or a promise of headroom. */
    public static double obstacle(ServerLevel level,LinearMovementGeometry.Body body,Vec3 direction,double speed,double maxHeight,CollisionWorkBudget.Budget budget){
        return obstacle(level,body,direction,speed,maxHeight,budget,true);
    }
    private static double obstacle(ServerLevel level,LinearMovementGeometry.Body body,Vec3 direction,double speed,double maxHeight,CollisionWorkBudget.Budget budget,boolean requireGround){
        var box=body.box();double bottom=box.minY-.05;
        var ground=shapes(level,new AABB(box.minX,bottom-.16,box.minZ,box.maxX,bottom+.025,box.maxZ),budget);
        if(ground==null||requireGround&&body.body().latestLinearVelocity.y>.5)return -1;
        double support=ground.stream().filter(b->b.maxY<=bottom+.08).mapToDouble(b->b.maxY).max().orElse(Double.NaN);if(!Double.isFinite(support)){if(requireGround)return -1;support=bottom;}
        double reach=Math.min(1,speed/20+.15);AABB area;
        if(Math.abs(direction.x)>=Math.abs(direction.z)){double edge=direction.x>0?box.maxX:box.minX;area=new AABB(edge+(direction.x>0?-.02:-reach),bottom+.08,box.minZ,edge+(direction.x>0?reach:.02),bottom+Math.min(.45,maxHeight),box.maxZ);}
        else {double edge=direction.z>0?box.maxZ:box.minZ;area=new AABB(box.minX,bottom+.08,edge+(direction.z>0?-.02:-reach),box.maxX,bottom+Math.min(.45,maxHeight),edge+(direction.z>0?reach:.02));}
        var obstacles=shapes(level,area,budget);if(obstacles==null)return -1;
        double height=0;var center=box.getCenter();
        for(var hit:obstacles){var delta=hit.getCenter().subtract(center);double rise=hit.maxY-bottom;
            if(delta.x*direction.x+delta.z*direction.z>0&&rise>.05&&hit.maxY>support+.05&&rise<=maxHeight+.08)height=Math.max(height,rise);
        }
        return height;
    }
    public record StepResult(boolean valid,double lift){}
    /** Shared step controller, usable by both route-controlled and gravity-driven Travel. */
    public static StepResult step(ServerLevel level,VirtualModule m,List<LinearMovementGeometry.Body> bodies,Vec3 direction,double speed,Session session){
        var physics=SubLevelPhysicsSystem.get(level);if(physics==null||!level.getServer().isRunning()||bodies.isEmpty())return new StepResult(false,0);
        long now=level.getGameTime();double lift=0;var contact=session.contact;
        if(bodies.stream().anyMatch(b->b.body().isRemoved()))return new StepResult(false,0);
        if(m.value("moveStep")==1&&direction.lengthSqr()>1e-8){
            boolean routeContinuation=!enabled(m)&&contact.lastStepPosition!=null;
            var position=new Vec3(bodies.getFirst().position().x,bodies.getFirst().position().y,bodies.getFirst().position().z);
            var budget=CollisionWorkBudget.begin(level);boolean blocked=false,clearGround=false;
            for(var body:bodies){double rise=obstacle(level,body,direction,speed,m.value("moveStepHeight"),budget,!routeContinuation);blocked|=rise>0;clearGround|=rise==0;}
            // One contact latch for the entire machine: another limb cannot start a second overlapping lift.
            // A held-height route may never touch ground again. Rear limbs can start a later step only
            // after real forward progress, so a stationary wall cannot repeatedly raise the machine.
            if(routeContinuation){
                if(contact.ticks==0)contact.armed=horizontal(position.subtract(contact.lastStepPosition)).dot(direction)>=.25;
            }else if(!blocked&&clearGround&&!CollisionWorkBudget.exhausted(level)&&contact.ticks==0){if(++contact.clearTicks>=4)contact.armed=true;}else contact.clearTicks=0;
            if(blocked&&contact.armed&&contact.ticks==0&&now>=contact.ready){contact.remaining=m.value("moveStepHeight");contact.ticks=6;contact.armed=false;contact.lastStepPosition=position;contact.ready=now+(long)m.value("moveStepCooldown");}
            if(contact.ticks>0)lift=contact.remaining/contact.ticks;
        }else {contact.remaining=0;contact.ticks=0;}
        List<LinearMovementGeometry.Body> raised=bodies;
        if(lift>0){
            raised=LinearMovementGeometry.forcedDestination(bodies,bodies,new Vec3(0,lift,0));
            if(!LinearMovementGeometry.loadedDestination(level,raised))return new StepResult(false,0);
        }
        if(lift>0)for(var body:raised){
            physics.getPipeline().teleport(body.body(),body.position(),body.rotation());
            physics.getPipeline().addLinearAndAngularVelocity(body.body(),new Vector3d(0,-physics.getPipeline().getLinearVelocity(body.body(),new Vector3d()).y,0),new Vector3d());
        }
        if(lift>0){contact.remaining=Math.max(0,contact.remaining-lift);contact.ticks--;dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);}
        return new StepResult(true,lift);
    }
    public static boolean drive(ServerLevel level,HeartBlockEntity heart,VirtualModule m,List<LinearMovementGeometry.Body> bodies,Vec3 direction,double speed,Session session){
        var physics=SubLevelPhysicsSystem.get(level);if(physics==null||!level.getServer().isRunning()||bodies.isEmpty())return false;
        if(!LinearMovementGeometry.loadedDestination(level,LinearMovementGeometry.forcedDestination(bodies,bodies,direction.scale(speed/20))))return false;
        var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,heart.getBlockPos());
        var reference=bodies.stream().filter(b->b.body()==root).findFirst().orElse(bodies.getFirst());
        var common=driveDelta(new Vector3d(reference.body().latestLinearVelocity),direction,speed);
        boolean clearing=heart.terrainBreaker().advance(heart,bodies,direction.scale(speed/20));
        if(heart.terrainBreaker().brokeThisTick(level.getGameTime()))session.clear();
        if(!clearing&&!step(level,m,bodies,direction,speed,session).valid())return false;
        for(var body:bodies)physics.getPipeline().addLinearAndAngularVelocity(body.body(),common,new Vector3d());
        return true;
    }
    public static void stop(ServerLevel level,List<LinearMovementGeometry.Body> bodies){
        var physics=SubLevelPhysicsSystem.get(level);if(physics==null||!level.getServer().isRunning())return;
        for(var body:bodies)if(!body.body().isRemoved()){var v=body.body().latestLinearVelocity;physics.getPipeline().addLinearAndAngularVelocity(body.body(),new Vector3d(-v.x,0,-v.z),new Vector3d());}
    }
    private GravityTravel(){}
}
