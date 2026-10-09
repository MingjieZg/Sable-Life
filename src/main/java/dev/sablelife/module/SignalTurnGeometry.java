package dev.sablelife.module;
import dev.sablelife.attack.ContactBox;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** Local yaw/pitch/roll axes follow the marked body's face, including vertical front faces. */
public final class SignalTurnGeometry {
    public record Frame(Vec3 front,Vec3 up,Vec3 right){}
    public static Frame frame(Quaterniond pose,Direction face){
        var front=ContactBox.rotate(pose,Vec3.atLowerCornerOf(face.getNormal())).normalize();
        var reference=ContactBox.rotate(pose,face.getAxis()==Direction.Axis.Y?new Vec3(0,0,1):new Vec3(0,1,0));
        var right=front.cross(reference).normalize();return new Frame(front,right.cross(front).normalize(),right);
    }
    public static double rate(int positive,int negative,double speed){return (Math.clamp(positive,0,15)-Math.clamp(negative,0,15))*speed/15.;}
    public static Quaterniond turn(Frame f,double yaw,double pitch,double roll,double scale){
        var velocity=f.up.scale(yaw).add(f.right.scale(pitch)).add(f.front.scale(roll));double magnitude=velocity.length();
        return magnitude<1e-9?new Quaterniond():new Quaterniond().rotationAxis(Math.toRadians(magnitude*scale/20),velocity.x/magnitude,velocity.y/magnitude,velocity.z/magnitude);
    }
    private SignalTurnGeometry(){}
}
