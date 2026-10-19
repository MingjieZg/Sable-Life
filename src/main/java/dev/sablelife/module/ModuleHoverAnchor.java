package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** World is frame one: selected linear axes stay world-aligned even while the body swings. */
public final class ModuleHoverAnchor {
    private record Settings(boolean x,boolean y,boolean z,boolean rotation,boolean elastic,double strength,double damping,double offset){
        static Settings of(VirtualModule m){return new Settings(m.value("hoverX")==1,m.value("hoverY")==1,m.value("hoverZ")==1,m.value("hoverRotation")==1,m.value("hoverMode")==1,m.value("hoverStrength"),m.value("hoverDamping"),m.value("hoverOffset"));}
        boolean constrains(){return x||y||z||rotation||damping>0;}
        boolean axis(int i){return i==0?x:i==1?y:z;}
    }
    private UUID moduleId;
    private ModuleAnchor anchor;
    private GenericConstraintHandle joint;
    private ServerLevel level;
    private Vec3 point;
    private Quaterniond reference;
    private Settings settings;
    public boolean active(){return joint!=null;}
    public boolean active(UUID id){return joint!=null && id.equals(moduleId);}
    public boolean locksRotation(){return active() && settings!=null && settings.rotation;}
    /** Rotate around the marked point's current position, preserving elastic/free-axis displacement. */
    public Vec3 rotationPivot(HeartBlockEntity heart){return active() && anchor!=null?anchor.world(heart,false):null;}
    private void removeJoint(){
        // Shutdown destroys the entire physics scene; do not call a retired native scene.
        if(joint!=null && level!=null && level.getServer().isRunning() && joint.isValid()) joint.remove();
        joint=null;
    }
    public void clear(){removeJoint();moduleId=null;anchor=null;level=null;point=null;reference=null;settings=null;}
    public boolean tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel server) || !server.getServer().isRunning()
                || !heart.operational() || !heart.wireless().master()){clear();return false;}
        var chosen=heart.modules().all().stream().filter(m->m.type().equals("hover_anchor") && HeartModules.triggered(m,heart) && Settings.of(m).constrains()).findFirst().orElse(null);
        if(chosen==null){clear();return false;}
        var mark=chosen.anchors().getFirst();var next=Settings.of(chosen);
        if(moduleId!=null && (!chosen.id().equals(moduleId)||!mark.equals(anchor)||joint!=null&&!joint.isValid()))clear();
        if(settings!=null&&!settings.equals(next))removeJoint();
        if(joint==null){
            var body=mark.body(heart);var physics=SubLevelPhysicsSystem.get(server);
            if(!(body instanceof ServerSubLevel sub) || body.isRemoved() || physics==null){clear();return false;}
            if(point==null){point=mark.world(heart,false);reference=new Quaterniond(body.logicalPose().orientation()).normalize().invert();}
            if(point==null)return false;
            var local=mark.position(body).getCenter();var locks=EnumSet.noneOf(ConstraintJointAxis.class);
            for(int i=0;i<3;i++)if(next.axis(i)&&(!next.elastic||next.offset==0))locks.add(ConstraintJointAxis.LINEAR[i]);
            if(next.rotation)locks.addAll(Arrays.asList(ConstraintJointAxis.ANGULAR));
            joint=physics.getPipeline().addConstraint(null,sub,new GenericConstraintConfiguration(
                    new Vector3d(point.x,point.y,point.z),new Vector3d(local.x,local.y,local.z),
                    new Quaterniond(),reference,locks));
            if(joint==null){clear();return false;}
            level=server;moduleId=chosen.id();anchor=mark;settings=next;
            for(int i=0;i<3;i++)if(next.axis(i)&&next.elastic&&next.offset>0){
                joint.setLimit(ConstraintJointAxis.LINEAR[i],-next.offset,next.offset);
                joint.setMotor(ConstraintJointAxis.LINEAR[i],0,next.strength,next.damping,false,0);
            }
            // Zero stiffness damps angular velocity without restoring a preferred angle.
            if(!next.rotation&&next.damping>0)for(var axis:ConstraintJointAxis.ANGULAR)joint.setMotor(axis,0,0,next.damping,false,0);
        }
        return true;
    }
}
