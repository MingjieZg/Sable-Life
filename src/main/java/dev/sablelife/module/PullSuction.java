package dev.sablelife.module;
import net.minecraft.world.phys.Vec3;
/** Bounded steering; the caller compensates gravity only for this tick, without mutating entity flags. */
public final class PullSuction {
    public static Vec3 velocity(Vec3 current,Vec3 delta,double strength,double speed,double stop,double gravity){double distance=delta.length();var desired=distance<1e-6?Vec3.ZERO:delta.scale(Math.min(speed,Math.max(0,distance-stop))/distance);var change=desired.subtract(current);if(change.length()>strength)change=change.normalize().scale(strength);var next=current.add(change);if(next.length()>speed)next=next.normalize().scale(speed);return next.add(0,gravity,0);}
    private PullSuction(){}
}
