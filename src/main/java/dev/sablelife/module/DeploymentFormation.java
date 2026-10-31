package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

/** Shared hover-projectile/summoned-weapon deployment geometry. */
public final class DeploymentFormation {
    /** NaN spacing retains Homing's original radius/fan-based line width. */
    public static List<Vec3> points(Vec3 center,Vec3 forward,Quaterniond body,int shape,int count,double radius,double fan,double spacing,double roll,double yaw){
        count=Math.clamp(count,1,32);if(count==1)return List.of(center);
        var up=ContactBox.rotate(body,new Vec3(0,1,0));forward=forward.normalize();
        var heading=forward.subtract(up.scale(forward.dot(up))).normalize();
        if(heading.lengthSqr()<.001)heading=ContactBox.rotate(body,new Vec3(0,0,-1));
        var right=heading.cross(up).normalize();
        var rotation=new Quaterniond().rotationAxis(Math.toRadians(roll),heading.x,heading.y,heading.z);
        var vertical=ContactBox.rotate(rotation,shape==2||shape==4?heading:up);
        var horizontal=ContactBox.rotate(rotation,right);
        var yawRotation=new Quaterniond().rotationAxis(Math.toRadians(yaw),up.x,up.y,up.z);
        vertical=ContactBox.rotate(yawRotation,vertical);horizontal=ContactBox.rotate(yawRotation,horizontal);
        var points=new ArrayList<Vec3>();
        for(int i=0;i<count;i++){
            double fraction=2*i/(double)(count-1)-1,angle=Math.toRadians(fan)*fraction/2,x,y;
            switch(shape){
                case 1,6,7->{double halfWidth=Double.isFinite(spacing)?spacing*(count-1)/2:radius*Math.sin(Math.toRadians(fan)/2);x=fraction*halfWidth;y=shape==6?-x:shape==7?x:0;if(shape>=6){x/=Math.sqrt(2);y/=Math.sqrt(2);}}
                case 2,3->{double segment=10*i/(double)count;int vertex=(int)Math.floor(segment);double blend=segment-vertex,a0=Math.PI/2-vertex*Math.PI/5,a1=a0-Math.PI/5,r0=vertex%2==0?radius:radius*.38196601125,r1=vertex%2==0?radius*.38196601125:radius;x=Math.cos(a0)*r0*(1-blend)+Math.cos(a1)*r1*blend;y=Math.sin(a0)*r0*(1-blend)+Math.sin(a1)*r1*blend;}
                case 4,5->{double theta=Math.PI/2-2*Math.PI*i/count;x=Math.cos(theta)*radius;y=Math.sin(theta)*radius;}
                default->{x=Math.sin(angle)*radius;y=(Math.cos(angle)-1)*radius;}
            }
            points.add(center.add(horizontal.scale(x)).add(vertical.scale(y)));
        }
        return List.copyOf(points);
    }
    private DeploymentFormation(){}
}
