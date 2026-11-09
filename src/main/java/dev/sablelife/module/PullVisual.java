package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Cosmetic spherical inflow; never changes the frozen capture volume or pull acceleration. */
public final class PullVisual {
    public static final int BOUNDARY_POINTS=64,STREAM_POINTS=32;
    public static Vec3 shell(Vec3 center,double radius,int index,int count,double phase){
        double y=1-2*(index+.5)/count,angle=index*Math.PI*(3-Math.sqrt(5))+phase;
        double r=Math.sqrt(Math.max(0,1-y*y));
        return center.add(Math.cos(angle)*r*radius,y*radius,Math.sin(angle)*r*radius);
    }
    public static List<ParticleTravelPacket> particles(VirtualModule module,Vec3 center,Vec3 destination,long remaining,long time){
        return particles(module,center,destination,remaining,time,null);
    }
    public static List<ParticleTravelPacket> particles(VirtualModule module,Vec3 center,Vec3 destination,long remaining,long time,PullGeometry shape){
        if(remaining<=0)return List.of();
        var entries=module.particleEntries();
        if(entries.isEmpty()){
            var rgb=ModuleColors.vector(module,"pullWarningColor");int color=(Math.round(rgb.x*255)<<16)|(Math.round(rgb.y*255)<<8)|Math.round(rgb.z*255);
            entries=List.of(new ModuleParticles.Entry("minecraft:dust",0,.8F,color));
        }
        double radius=module.value("pullRadius");var result=new ArrayList<ParticleTravelPacket>();
        for(int i=0;i<STREAM_POINTS;i++){
            var entry=entries.get(i%entries.size());var from=shape==null?shell(center,radius,i,STREAM_POINTS,time*.035):shape.boundary((i+.5)/STREAM_POINTS,i*Math.PI*(3-Math.sqrt(5))+time*.035);
            int ticks=Math.clamp((int)Math.ceil(from.distanceTo(destination)*1.25),6,24);
            int life=(int)Math.max(1,Math.min(remaining,entry.ticks()>0?entry.ticks():ticks+1));
            result.add(new ParticleTravelPacket(entry.id(),from,destination,ticks,life,entry.size(),entry.color()));
        }
        return List.copyOf(result);
    }
    private PullVisual(){}
}
