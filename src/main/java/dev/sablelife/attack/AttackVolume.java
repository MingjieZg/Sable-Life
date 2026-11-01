package dev.sablelife.attack;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** A world-space parallelepiped; no Entity allocation or enclosing-box hit approximation. */
public final class AttackVolume {
    private final Vec3 center;
    private final Vec3[] edges;
    private final Vec3[] normals;
    private final AABB bounds;

    public AttackVolume(Vec3 center,Vec3 x,Vec3 y,Vec3 z) {
        this.center=center;edges=new Vec3[]{x,y,z};
        normals=new Vec3[]{y.cross(z),z.cross(x),x.cross(y)};
        var extent=new Vec3(Math.abs(x.x)+Math.abs(y.x)+Math.abs(z.x),Math.abs(x.y)+Math.abs(y.y)+Math.abs(z.y),Math.abs(x.z)+Math.abs(y.z)+Math.abs(z.z));
        bounds=new AABB(center.subtract(extent),center.add(extent));
    }
    public static AttackVolume box(Vec3 center,Vec3 half,Quaterniond rotation) {
        return new AttackVolume(center,ContactBox.rotate(rotation,new Vec3(half.x,0,0)),ContactBox.rotate(rotation,new Vec3(0,half.y,0)),ContactBox.rotate(rotation,new Vec3(0,0,half.z)));
    }
    public AABB bounds(){return bounds;}
    public Vec3 center(){return center;}
    public boolean valid() {
        if(!Double.isFinite(center.lengthSqr()))return false;
        for(var edge:edges)if(!Double.isFinite(edge.lengthSqr())||edge.lengthSqr()<1e-12||edge.lengthSqr()>128*128)return false;
        return true;
    }
    public boolean intersects(AttackVolume other) {
        if(!bounds.intersects(other.bounds))return false;
        var delta=other.center.subtract(center);
        for(var axis:normals)if(separated(axis,delta,other))return false;
        for(var axis:other.normals)if(separated(axis,delta,other))return false;
        for(var a:edges)for(var b:other.edges)if(separated(a.cross(b),delta,other))return false;
        return true;
    }
    private boolean separated(Vec3 axis,Vec3 delta,AttackVolume other) {
        if(axis.lengthSqr()<1e-18)return false;
        double extent=0;
        for(var edge:edges)extent+=Math.abs(axis.dot(edge));
        for(var edge:other.edges)extent+=Math.abs(axis.dot(edge));
        return Math.abs(axis.dot(delta))>extent+1e-7*Math.sqrt(axis.lengthSqr());
    }
}
