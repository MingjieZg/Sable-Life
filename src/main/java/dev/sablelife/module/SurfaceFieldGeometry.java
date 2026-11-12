package dev.sablelife.module;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** A surface-local footprint. Sampling is capped and never requests unloaded chunks. */
public final class SurfaceFieldGeometry {
    public record Frame(Vec3 center, Vec3 normal, Vec3 forward, Vec3 right, double length, double width, boolean circle, boolean ahead) {
        public boolean contains(Vec3 point) {
            var delta=point.subtract(center);double x=delta.dot(right),z=delta.dot(forward);
            if(circle)return x*x+z*z<=length*length+1e-6;
            return Math.abs(x)<=width/2+1e-6 && (ahead?z>=-1e-6&&z<=length+1e-6:Math.abs(z)<=length/2+1e-6);
        }
        public Vec3 point(double x,double z){return center.add(right.scale(x)).add(forward.scale(z));}
        public Vec3 midpoint(){return ahead&&!circle?center.add(forward.scale(length/2)):center;}
        public AABB footprintBounds(){
            double halfWidth=circle?length:width/2, halfLength=circle?length:length/2;
            var extent=new Vec3(Math.abs(right.x)*halfWidth+Math.abs(forward.x)*halfLength,
                    Math.abs(right.y)*halfWidth+Math.abs(forward.y)*halfLength,
                    Math.abs(right.z)*halfWidth+Math.abs(forward.z)*halfLength);
            var mid=midpoint();
            return new AABB(mid.subtract(extent),mid.add(extent));
        }
    }
    public static Frame frame(VirtualModule m,Vec3 center,Vec3 normal,Vec3 incoming){
        normal=normal.lengthSqr()<.5?new Vec3(0,1,0):normal.normalize();
        var forward=incoming.subtract(normal.scale(incoming.dot(normal)));
        if(forward.lengthSqr()<1e-6){var fallback=Math.abs(normal.y)<.9?new Vec3(0,1,0):new Vec3(0,0,1);forward=fallback.subtract(normal.scale(fallback.dot(normal)));}
        forward=forward.normalize();var right=normal.cross(forward).normalize();double angle=Math.toRadians(m.value("fieldAngle"));
        forward=forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();right=normal.cross(forward).normalize();
        boolean circle=m.value("fieldShape")==0;
        return new Frame(center,normal,forward,right,circle?m.value("fieldRadius"):m.value("fieldLength"),m.value("fieldWidth"),circle,m.value("fieldPlacement")==1);
    }
    public static Vec3 surface(ServerLevel level,Vec3 point,Vec3 normal){
        var hit=surfaceHit(level,point,normal);
        return hit==null?null:hit.getLocation().add(normal.scale(.035));
    }
    static BlockHitResult surfaceHit(ServerLevel level,Vec3 point,Vec3 normal){
        if(!level.hasChunkAt(BlockPos.containing(point.add(normal.scale(.12))))||!level.hasChunkAt(BlockPos.containing(point.subtract(normal.scale(.12)))))return null;
        // Prefer the actual nearby surface. A long outward probe can start inside the emitter
        // when its nozzle is close to a wall, hiding the wall behind the moving body.
        var near=level.clip(new ClipContext(point.add(normal.scale(.12)),point.subtract(normal.scale(.12)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
        if(near.getType()==HitResult.Type.BLOCK&&!near.isInside()&&near.getDirection()==net.minecraft.core.Direction.getNearest(normal.x,normal.y,normal.z)&&dev.ryanhcode.sable.Sable.HELPER.getContaining(level,near.getBlockPos())==null)return near;
        var from=point.add(normal.scale(2.05));var to=point.subtract(normal.scale(2.05));
        // Guard every touched column rather than letting a ray load an adjacent chunk.
        for(int i=0;i<=9;i++)if(!level.hasChunkAt(BlockPos.containing(from.lerp(to,i/9.))))return null;
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
        if(hit.getType()!=HitResult.Type.BLOCK || !hit.getDirection().equals(net.minecraft.core.Direction.getNearest(normal.x,normal.y,normal.z)))return null;
        // Do not attach a world-fixed field to a moving Sable body.
        if(dev.ryanhcode.sable.Sable.HELPER.getContaining(level,hit.getBlockPos())!=null)return null;
        return hit;
    }
    public static List<Vec3> samples(ServerLevel level,Frame frame){
        double span=frame.circle?frame.length*2:frame.length,w=frame.circle?span:frame.width;
        double step=Math.max(.65,Math.sqrt(span*w/600));int nx=Math.max(1,(int)Math.ceil(w/step)),nz=Math.max(1,(int)Math.ceil(span/step));
        var result=new ArrayList<Vec3>();
        for(int x=0;x<nx;x++)for(int z=0;z<nz;z++){
            var point=frame.point(-w/2+(x+.5)*w/nx,(frame.ahead&&!frame.circle?0:-span/2)+(z+.5)*span/nz);
            if(!frame.contains(point))continue;var surface=surface(level,point,frame.normal);if(surface!=null)result.add(surface);
        }
        return List.copyOf(result);
    }
    private SurfaceFieldGeometry(){}
}
