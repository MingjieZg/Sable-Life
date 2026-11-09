package dev.sablelife.module;

import net.minecraft.world.phys.*;

/** Shared finite frustum for target acquisition, retention and previews. */
public record PullGeometry(Vec3 start,Vec3 axis,double length,double startRadius,double endRadius) {
    public PullGeometry {if(!Double.isFinite(length)||length<=0||!Double.isFinite(startRadius)||!Double.isFinite(endRadius)||startRadius<0||endRadius<0||!Double.isFinite(axis.lengthSqr())||axis.lengthSqr()<1e-12)throw new IllegalArgumentException("Invalid attraction geometry");axis=axis.normalize();}
    public boolean contains(Vec3 point){var delta=point.subtract(start);double along=delta.dot(axis);if(along< -1e-8||along>length+1e-8)return false;double radius=startRadius+(endRadius-startRadius)*Math.clamp(along,0,length)/length;return delta.subtract(axis.scale(along)).lengthSqr()<=radius*radius+1e-8;}
    public AABB bounds(){double radius=Math.max(startRadius,endRadius);return new AABB(start,start.add(axis.scale(length))).inflate(radius);}
    public Vec3 boundary(double fraction,double angle){var right=axis.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.normalize();var up=right.cross(axis).normalize();double radius=startRadius+(endRadius-startRadius)*fraction;return start.add(axis.scale(length*fraction)).add(right.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));}
}
