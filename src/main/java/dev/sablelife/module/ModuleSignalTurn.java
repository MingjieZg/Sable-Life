package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** Signal-controlled rigid-machine rotation. Translation wins; active manual input wins over target facing. */
public final class ModuleSignalTurn {
    public static final List<String> DIRECTIONS=List.of("turnLeft","turnRight","turnUp","turnDown","turnClockwise","turnCounterclockwise");
    private final Map<UUID,String> states=new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    private boolean controlled;
    public String state(UUID id){return states.getOrDefault(id,"idle");}
    public void clear(){states.clear();lastTick=Long.MIN_VALUE;controlled=false;}
    public static int input(HeartBlockEntity heart,VirtualModule module,String direction){
        int channel=(int)module.value(direction+"Channel")-1;
        return module.value(direction+"Source")==0?heart.wireless().output(channel):heart.wireless().input(channel);
    }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy){
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()){clear();return false;}
        if(lastTick==level.getGameTime())return controlled;lastTick=level.getGameTime();controlled=false;var keep=new HashSet<UUID>();
        for(var module:heart.modules().all()){
            if(!module.type().equals("signal_turn"))continue;keep.add(module.id());
            if(!HeartModules.triggered(module,heart)){states.put(module.id(),"idle");continue;}
            if(movementBusy||controlled){states.put(module.id(),"busy");continue;}
            double speed=module.value("turnSpeed");double yaw=SignalTurnGeometry.rate(input(heart,module,"turnLeft"),input(heart,module,"turnRight"),speed);
            double pitch=SignalTurnGeometry.rate(input(heart,module,"turnUp"),input(heart,module,"turnDown"),speed),roll=SignalTurnGeometry.rate(input(heart,module,"turnClockwise"),input(heart,module,"turnCounterclockwise"),speed);
            if(yaw==0&&pitch==0&&roll==0){states.put(module.id(),"no_input");continue;}controlled=true;
            var marked=module.anchors().getFirst().body(heart);var bodies=LinearMovementGeometry.capture(heart);var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
            if(marked==null||bodies.isEmpty()||physics==null){states.put(module.id(),"blocked");continue;}
            var pivot=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());var frame=SignalTurnGeometry.frame(new Quaterniond(marked.logicalPose().orientation()),module.anchors().getFirst().face());
            double radius=AutoFacingGeometry.radius(bodies,pivot),scale=1;boolean collision=module.value("turnCollision")==1;
            double magnitude=Math.sqrt(yaw*yaw+pitch*pitch+roll*roll);
            if(collision)scale=Math.min(1,Math.toDegrees(4/radius)*20/magnitude);
            var turn=SignalTurnGeometry.turn(frame,yaw,pitch,roll,scale);boolean partial=false;
            if(collision&&!AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius)){
                // A blocked vertical/roll axis must not stop a clear horizontal rotation.
                turn=new Quaterniond();partial=true;
                for(int axis=0;axis<3;axis++){
                    var step=SignalTurnGeometry.turn(frame,axis==0?yaw:0,axis==1?pitch:0,axis==2?roll:0,scale);
                    var candidate=new Quaterniond(step).mul(turn);
                    if(AutoFacingGeometry.clearTurn(level,bodies,pivot,candidate,radius))turn=candidate;
                }
                if(2*Math.acos(Math.min(1,Math.abs(turn.w)))<1e-6){states.put(module.id(),CollisionWorkBudget.exhausted(level)?"budget":"blocked");continue;}
            }
            for(var body:AutoFacingGeometry.rotated(bodies,pivot,turn)){
                var velocity=new Vector3d(body.body().latestLinearVelocity);physics.getPipeline().resetVelocity(body.body());physics.getPipeline().teleport(body.body(),body.position(),body.rotation());physics.getPipeline().addLinearAndAngularVelocity(body.body(),velocity,new Vector3d());
            }
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);
            states.put(module.id(),partial?"partial":"turning");
        }
        states.keySet().retainAll(keep);return controlled;
    }
}
