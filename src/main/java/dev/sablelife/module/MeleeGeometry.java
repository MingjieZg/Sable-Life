package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** Offset boxes remain in limb-local space. Sweeps interpolate the limb, not a chord between box centers. */
public final class MeleeGeometry {
    public record Frame(Vec3 position, Quaterniond bodyRotation, Vec3 localCenter, Quaterniond angleRotation, Vec3 half, String signature) {
        public Vec3 center(){return position.add(ContactBox.rotate(bodyRotation,localCenter));}
        public Quaterniond rotation(){return new Quaterniond(bodyRotation).mul(angleRotation);}
    }
    public record Sample(Vec3 center,Quaterniond rotation) {}
    /** Added full dimensions in the marked face's local forward/right/up basis. */
    public static Vec3 expandedHalf(Vec3 base, Vec3 forward, double length, double width, double height){
        var right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);
        right=right.normalize();var up=right.cross(forward).normalize();
        return base.add((Math.abs(forward.x)*length+Math.abs(right.x)*width+Math.abs(up.x)*height)/2,
            (Math.abs(forward.y)*length+Math.abs(right.y)*width+Math.abs(up.y)*height)/2,
            (Math.abs(forward.z)*length+Math.abs(right.z)*width+Math.abs(up.z)*height)/2);
    }
    public static Quaterniond angles(Vec3 forward,double horizontal,double vertical){
        var right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);
        right=right.normalize();var up=right.cross(forward).normalize();
        return new Quaterniond().rotationAxis(-Math.toRadians(horizontal),up.x,up.y,up.z)
            .mul(new Quaterniond().rotationAxis(Math.toRadians(vertical),right.x,right.y,right.z));
    }
    public static Frame frame(HeartBlockEntity heart,VirtualModule module){
        var anchor=module.anchors().getFirst();var body=anchor.body(heart);var a=anchor.position(body).getCenter();var b=module.anchors().get(1).position(body).getCenter();
        var forward=Vec3.atLowerCornerOf(anchor.face().getNormal());var angle=angles(forward,module.value("meleeHorizontal"),module.value("meleeVertical"));
        var rotation=new Quaterniond(body.logicalPose().orientation());var pos=body.logicalPose().position();var position=new Vec3(pos.x(),pos.y(),pos.z());
        var center=body.logicalPose().transformPosition(a.lerp(b,.5));
        var local=ContactBox.rotate(new Quaterniond(rotation).conjugate(),center.subtract(position))
            .add(ContactBox.rotate(angle,forward).scale(module.value("startDistance")+module.value("meleeLength")/2));
        var half=expandedHalf(ModuleCombat.halfSize(heart,module),forward,module.value("meleeLength"),module.value("meleeWidth"),module.value("meleeHeight"));
        String signature=module.anchors().toString()+":"+half+":"+module.value("startDistance")+":"+module.value("meleeHorizontal")+":"+module.value("meleeVertical");
        return new Frame(position,rotation,local,angle,half,signature);
    }
    public static Sample sample(Frame previous,Frame current,double fraction){
        var body=new Quaterniond(previous.bodyRotation).slerp(current.bodyRotation,fraction);
        var center=previous.position.lerp(current.position,fraction).add(ContactBox.rotate(body,previous.localCenter.lerp(current.localCenter,fraction)));
        return new Sample(center,body.mul(current.angleRotation));
    }
    public static double angular(Frame a,Frame b){return 2*Math.acos(Math.min(1,Math.abs(a.bodyRotation.dot(b.bodyRotation))));}
    public static double arcRadius(Frame a,Frame b){return Math.max(a.localCenter.length(),b.localCenter.length());}
    public static int samples(Frame a,Frame b){return Math.clamp((int)Math.ceil((a.position.distanceTo(b.position)+angular(a,b)*(arcRadius(a,b)+b.half.length()))/.2),1,512);}
    private MeleeGeometry(){}
}
