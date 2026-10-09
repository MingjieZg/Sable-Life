package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** Rotation about the core, keeping every connected limb's relative pose. */
public final class AutoFacingGeometry {
    public static Quaterniond turn(Vec3 front,Vec3 desired,boolean vertical,double maximumDegrees){
        if(!vertical){front=new Vec3(front.x,0,front.z);desired=new Vec3(desired.x,0,desired.z);}
        if(front.lengthSqr()<1e-10||desired.lengthSqr()<1e-10)return new Quaterniond();
        front=front.normalize();desired=desired.normalize();Quaterniond delta;
        if(vertical)delta=new Quaterniond().rotationTo(front.x,front.y,front.z,desired.x,desired.y,desired.z);
        else delta=new Quaterniond().rotateY(Math.atan2(front.cross(desired).y,front.dot(desired)));
        double angle=2*Math.acos(Math.min(1,Math.abs(delta.w)));
        return angle<1e-9?new Quaterniond():new Quaterniond().slerp(delta,Math.min(1,Math.toRadians(maximumDegrees)/angle));
    }
    public static AABB rotatedBox(AABB box,Vec3 pivot,Quaterniond rotation){
        AABB result=null;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
            var v=new Vec3(x==0?box.minX:box.maxX,y==0?box.minY:box.maxY,z==0?box.minZ:box.maxZ);
            var p=pivot.add(ContactBox.rotate(rotation,v.subtract(pivot)));var point=new AABB(p,p);
            result=result==null?point:result.minmax(point);
        }return result;
    }
    public static List<LinearMovementGeometry.Body> rotated(List<LinearMovementGeometry.Body> bodies,Vec3 pivot,Quaterniond turn){
        return bodies.stream().map(b->{var relative=new Vec3(b.position().x-pivot.x,b.position().y-pivot.y,b.position().z-pivot.z);var p=pivot.add(ContactBox.rotate(turn,relative));
            return new LinearMovementGeometry.Body(b.body(),new Vector3d(p.x,p.y,p.z),new Quaterniond(turn).mul(b.rotation()),rotatedBox(b.box(),pivot,turn));}).toList();
    }
    public static double radius(List<LinearMovementGeometry.Body> bodies,Vec3 pivot){
        double radius=1;for(var b:bodies){var box=b.box();for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)radius=Math.max(radius,pivot.distanceTo(new Vec3(x==0?box.minX:box.maxX,y==0?box.minY:box.maxY,z==0?box.minZ:box.maxZ)));}return radius;
    }
    public static boolean clearTurn(net.minecraft.server.level.ServerLevel level,List<LinearMovementGeometry.Body> bodies,Vec3 pivot,Quaterniond turn,double radius){
        double angle=2*Math.acos(Math.min(1,Math.abs(turn.w)));int steps=Math.clamp((int)Math.ceil(angle*radius/.25),1,16);
        for(int i=1;i<=steps;i++)if(!LinearMovementGeometry.clear(level,rotated(bodies,pivot,new Quaterniond().slerp(turn,i/(double)steps)),Vec3.ZERO,Vec3.ZERO,false))return false;
        return true;
    }
    private AutoFacingGeometry(){}
}
