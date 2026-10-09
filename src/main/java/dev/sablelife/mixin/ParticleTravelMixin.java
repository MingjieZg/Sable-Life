package dev.sablelife.mixin;

import dev.sablelife.client.ParticleTravel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;

@Mixin(Particle.class)
public abstract class ParticleTravelMixin implements ParticleTravel {
    @Shadow protected double xo,yo,zo,xd,yd,zd;
    @Shadow protected float gravity,friction;
    @Shadow protected boolean hasPhysics;
    @Shadow protected net.minecraft.client.multiplayer.ClientLevel level;
    @Shadow public abstract void remove();
    @Shadow public abstract void setPos(double x,double y,double z);
    @Shadow public abstract void setLifetime(int ticks);
    @Unique private Vec3 sablelife$from,sablelife$to;
    @Unique private int sablelife$duration,sablelife$elapsed;
    @Unique private Vec3 sablelife$jetVelocity;
    @Unique private double sablelife$jetSpeed,sablelife$jetRange,sablelife$jetTime;
    @Override public void sablelife$travel(Vec3 from,Vec3 to,int ticks,int life){
        sablelife$jetVelocity=null;
        sablelife$from=from;sablelife$to=to;sablelife$duration=Math.clamp(ticks,1,12800);sablelife$elapsed=0;
        hasPhysics=false;gravity=0;friction=1;xd=yd=zd=0;setLifetime(Math.clamp(life,1,12801));
        setPos(from.x,from.y,from.z);xo=from.x;yo=from.y;zo=from.z;
    }
    @Override public void sablelife$advanceTravel(){
        if(sablelife$from==null)return;
        if(sablelife$jetVelocity!=null){sablelife$advanceJet(1);return;}
        var previous=sablelife$from.lerp(sablelife$to,Math.min(1,sablelife$elapsed/(double)sablelife$duration));
        var next=sablelife$from.lerp(sablelife$to,Math.min(1,++sablelife$elapsed/(double)sablelife$duration));
        setPos(next.x,next.y,next.z);xo=previous.x;yo=previous.y;zo=previous.z;
        xd=yd=zd=0;
    }
    @Override public void sablelife$jet(Vec3 from,Vec3 velocity,double speed,double range,double phase,int life){
        sablelife$travel(from,from,1,life>0?life:(int)Math.ceil(range/speed)+2);
        sablelife$jetVelocity=velocity;sablelife$jetSpeed=speed;sablelife$jetRange=range;sablelife$jetTime=0;
        sablelife$advanceJet(phase);xo=from.x;yo=from.y;zo=from.z;
    }
    @Unique private void sablelife$advanceJet(double fraction){
        var previous=sablelife$from.add(sablelife$jetVelocity.scale(sablelife$jetTime));
        sablelife$jetTime=Math.min(sablelife$jetRange/sablelife$jetSpeed,sablelife$jetTime+fraction);
        var next=sablelife$from.add(sablelife$jetVelocity.scale(sablelife$jetTime));
        if(level!=null){var hit=level.clip(new net.minecraft.world.level.ClipContext(previous,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){next=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,hit.getLocation());remove();}}
        setPos(next.x,next.y,next.z);xo=previous.x;yo=previous.y;zo=previous.z;xd=yd=zd=0;
        if(sablelife$jetTime*sablelife$jetSpeed>=sablelife$jetRange-.00001)remove();
    }
}
