package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.attack.ContactBox;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** A stored local gravity axis, rather than an absolute heading. Linear movement owns poses first. */
public final class ModuleGyroscope {
    public static final Vec3 DOWN = new Vec3(0,-1,0);
    private final Map<UUID,String> states = new HashMap<>();
    private final Set<UUID> recovering = new HashSet<>();
    private long lastTick = Long.MIN_VALUE;
    private boolean controlled;
    public void clear() { states.clear(); recovering.clear(); lastTick=Long.MIN_VALUE; controlled=false; }
    public String state(UUID id) { return states.getOrDefault(id,"idle"); }
    public static boolean calibrate(HeartBlockEntity heart,VirtualModule module) {
        if (!(heart.getLevel() instanceof ServerLevel) || module == null || !module.type().equals("gyroscope")
                || !List.of("valid","disabled").contains(HeartModules.status(module,heart))) return false;
        var body=module.anchors().getFirst().body(heart);
        if (body==null || body.isRemoved()) return false;
        var local=new Quaterniond(body.logicalPose().orientation()).normalize().invert().transform(new Vector3d(0,-1,0));
        return module.gyroDown(new Vec3(local.x,local.y,local.z));
    }
    public static Vec3 down(HeartBlockEntity heart,VirtualModule module) {
        if (module.gyroDown()==null || module.anchors().isEmpty()) return null;
        var body=module.anchors().getFirst().body(heart);
        return body==null || body.isRemoved() ? null : ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()),module.gyroDown()).normalize();
    }
    public static double tilt(Vec3 down) { return down==null ? Double.NaN : Math.toDegrees(Math.acos(Math.clamp(down.normalize().dot(DOWN),-1,1))); }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy) {return tick(heart,movementBusy,false);}
    public boolean tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation) {
        return tick(heart,movementBusy,preserveTranslation,null);
    }
    public boolean tick(HeartBlockEntity heart,boolean movementBusy,boolean preserveTranslation,Vec3 rotationPivot) {
        if (!(heart.getLevel() instanceof ServerLevel level) || !heart.operational() || !heart.wireless().master()) { clear(); return false; }
        if (lastTick==level.getGameTime()) return controlled;
        lastTick=level.getGameTime(); controlled=false; var keep=new HashSet<UUID>();
        for (var module:heart.modules().all()) {
            if (!module.type().equals("gyroscope")) continue;
            var id=module.id(); keep.add(id);
            if (!HeartModules.triggered(module,heart)) { recovering.remove(id); states.put(id,"idle"); continue; }
            var down=down(heart,module);
            if (down==null) { recovering.remove(id); states.put(id,"uncalibrated"); continue; }
            if (movementBusy || controlled) { states.put(id,"busy"); continue; }
            double angle=tilt(down),limit=module.value("gyroTilt");
            if (!recovering.contains(id) && angle>limit+.1) recovering.add(id);
            double target=module.value("gyroReturn")==0 ? 0 : Math.max(0,limit-3);
            if (!recovering.contains(id) || angle<=target+.05) { recovering.remove(id); states.put(id,"stable"); continue; }
            controlled=true;
            var bodies=LinearMovementGeometry.capture(heart);var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
            if (bodies.isEmpty() || physics==null) { states.put(id,"blocked"); continue; }
            var pivot=rotationPivot!=null?rotationPivot:SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
            double radius=AutoFacingGeometry.radius(bodies,pivot),degrees=Math.min(module.value("gyroSpeed")/20,angle-target);
            boolean collision=module.value("gyroCollision")==1;
            if (collision) degrees=Math.min(degrees,Math.toDegrees(4/radius));
            var turn=AutoFacingGeometry.turn(down,DOWN,true,degrees);
            if (collision && !AutoFacingGeometry.clearTurn(level,bodies,pivot,turn,radius)) { states.put(id,CollisionWorkBudget.exhausted(level)?"budget":"blocked"); continue; }
            var rotated=AutoFacingGeometry.rotated(bodies,pivot,turn);
            if (!LinearMovementGeometry.loadedDestination(level,rotated)) { states.put(id,"blocked"); continue; }
            for (var body:rotated) {
                var velocity=new Vector3d(body.body().latestLinearVelocity);
                // Remove tipping velocity during recovery, but retain world-vertical spin and translation.
                var angular=new Vector3d(0,body.body().latestAngularVelocity.y,0);
                if(preserveTranslation){
                    // Keep this tick's horizontal drive and step assist, which have already reached the pipeline.
                    physics.getPipeline().teleport(body.body(),body.position(),body.rotation());
                    physics.getPipeline().addLinearAndAngularVelocity(body.body(),new Vector3d(),new Vector3d(-body.body().latestAngularVelocity.x,0,-body.body().latestAngularVelocity.z));
                }else{
                    physics.getPipeline().resetVelocity(body.body());physics.getPipeline().teleport(body.body(),body.position(),body.rotation());
                    physics.getPipeline().addLinearAndAngularVelocity(body.body(),velocity,angular);
                }
            }
            dev.sablelife.ownership.LoadedCoreIndex.geometryChanged(level);states.put(id,"recovering");
        }
        states.keySet().retainAll(keep);recovering.retainAll(keep);return controlled;
    }
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity heart,VirtualModule module) {
        var center=module.anchors().getFirst().world(heart,false);var current=down(heart,module);var level=player.serverLevel();
        var cyan=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.2F,.8F,1F),1.2F);
        var orange=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F,.6F,.2F),1.2F);
        var green=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.2F,1F,.4F),1.2F);
        for(int n=0;n<=20;n++) { var p=center.add(DOWN.scale(n/10.));level.sendParticles(player,cyan,true,p.x,p.y,p.z,1,0,0,0,0);if(current!=null){p=center.add(current.scale(n/10.));level.sendParticles(player,orange,true,p.x,p.y,p.z,1,0,0,0,0);} }
        double angle=Math.toRadians(module.value("gyroTilt")),radius=2*Math.sin(angle),height=-2*Math.cos(angle);
        for(int n=0;n<32;n++) { double a=n*Math.PI*2/32;var p=center.add(Math.cos(a)*radius,height,Math.sin(a)*radius);level.sendParticles(player,green,true,p.x,p.y,p.z,1,0,0,0,0); }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.gyro_preview_help"),true);
    }
}
