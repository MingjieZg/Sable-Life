package dev.sablelife.module;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

/** Shared immutable capture/preview geometry: upright cylinder or yaw-oriented box. */
public record ControlVolume(Vec3 center,Vec3 forward,double radius,double length,double width,double height,boolean circle) {
    public static ControlVolume frame(VirtualModule m,Vec3 point,Vec3 direction){
        var forward=new Vec3(direction.x,0,direction.z);if(forward.lengthSqr()<1e-8)forward=new Vec3(0,0,1);forward=forward.normalize();
        double angle=Math.atan2(forward.x,forward.z)+Math.toRadians(m.value("fieldAngle"));forward=new Vec3(Math.sin(angle),0,Math.cos(angle));
        boolean circle=m.value("fieldShape")==0;double length=m.value("fieldLength");
        if(!circle&&m.value("fieldPlacement")==1)point=point.add(forward.scale(length/2));
        return new ControlVolume(point,forward,m.value("freezeRadius"),length,m.value("fieldWidth"),m.value("freezeHeight"),circle);
    }
    public Vec3 right(){return new Vec3(forward.z,0,-forward.x);}
    public AABB bounds(){double x=circle?radius:Math.abs(right().x)*width/2+Math.abs(forward.x)*length/2,z=circle?radius:Math.abs(right().z)*width/2+Math.abs(forward.z)*length/2;return new AABB(center.add(-x,-height/2,-z),center.add(x,height/2,z));}
    public boolean intersects(AABB b){
        if(b.maxY<center.y-height/2||b.minY>center.y+height/2)return false;
        if(circle){double x=Math.clamp(center.x,b.minX,b.maxX)-center.x,z=Math.clamp(center.z,b.minZ,b.maxZ)-center.z;return x*x+z*z<=radius*radius+1e-8;}
        return dev.sablelife.attack.ContactBox.intersects(center,new Vec3(width/2,height/2,length/2),new Quaterniond().rotateY(Math.atan2(forward.x,forward.z)),b);
    }
    private Vec3 point(double x,double y,double z){return center.add(right().scale(x)).add(0,y,0).add(forward.scale(z));}
    /** Exactly the same dimensions as intersects; no dependency on a selected warning particle. */
    public List<Vec3> outline(){
        var out=new ArrayList<Vec3>();
        if(circle){for(int layer=-1;layer<=1;layer++)for(int i=0;i<64;i++){double a=i*Math.PI*2/64;out.add(point(Math.cos(a)*radius,layer*height/2,Math.sin(a)*radius));}for(int i=0;i<4;i++){double a=i*Math.PI/2;line(out,point(Math.cos(a)*radius,-height/2,Math.sin(a)*radius),point(Math.cos(a)*radius,height/2,Math.sin(a)*radius));}}
        else for(int axis=0;axis<3;axis++)for(int a:new int[]{-1,1})for(int b:new int[]{-1,1}){double x=width/2,y=height/2,z=length/2;line(out,point(axis==0?-x:a*x,axis==1?-y:axis==0?a*y:b*y,axis==2?-z:b*z),point(axis==0?x:a*x,axis==1?y:axis==0?a*y:b*y,axis==2?z:b*z));}
        return List.copyOf(out);
    }
    private static void line(List<Vec3> out,Vec3 a,Vec3 b){int count=Math.clamp((int)Math.ceil(a.distanceTo(b)*2),2,32);for(int i=0;i<=count;i++)out.add(a.lerp(b,i/(double)count));}
}
