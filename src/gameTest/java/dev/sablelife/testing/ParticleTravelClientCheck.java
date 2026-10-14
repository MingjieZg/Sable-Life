package dev.sablelife.testing;

import dev.sablelife.client.ParticleTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Exercise the real engine hook without opening a world. */
public final class ParticleTravelClientCheck {
    private static final class NativeParticle extends Particle {
        NativeParticle(Vec3 from){super(null,from.x,from.y,from.z);friction=.1F;gravity=1;xd=1;lifetime=5;hasPhysics=false;}
        @Override public void tick(){super.tick();x*=.1;z+=3;}
        Vec3 position(){return new Vec3(x,y,z);}
        @Override public void render(VertexConsumer buffer,Camera camera,float partial){}
        @Override public ParticleRenderType getRenderType(){return ParticleRenderType.NO_RENDER;}
    }
    public static void run(Minecraft mc)throws Exception {
        var tick=ParticleEngine.class.getDeclaredMethod("tickParticle",Particle.class);tick.setAccessible(true);
        for(boolean reverse:new boolean[]{false,true}) {
            var from=reverse?new Vec3(8,0,0):Vec3.ZERO;var to=reverse?Vec3.ZERO:new Vec3(8,0,0);
            var particle=new NativeParticle(from);((ParticleTravel)(Object)particle).sablelife$travel(from,to,32,33);
            for(int i=1;i<=32;i++){tick.invoke(mc.particleEngine,particle);if(particle.position().distanceTo(from.lerp(to,i/32.))>1e-9)throw new AssertionError("Particle friction/native tick shortened controlled path");}
            if(!particle.isAlive())throw new AssertionError("Default native lifetime cut the path short");
        }
        var pulse=new dev.sablelife.module.VirtualModule("pulse");pulse.edit("pulseVisual",1,"");pulse.edit("radius",16,"");
        for(int duration:new int[]{1,10,200}){pulse.edit("pulseExpandTime",duration,"");for(var packet:dev.sablelife.module.PulseVisual.particles(pulse,new Vec3(3,4,5))){var particle=new NativeParticle(packet.from());((ParticleTravel)(Object)particle).sablelife$travel(packet.from(),packet.to(),packet.ticks(),packet.life());for(int n=1;n<=packet.ticks();n++){tick.invoke(mc.particleEngine,particle);if(particle.position().distanceTo(packet.from().lerp(packet.to(),n/(double)packet.ticks()))>1e-8)throw new AssertionError("Pulse shell native tick shortened radial expansion");}if(!particle.isAlive())throw new AssertionError("Pulse particle expired before reaching radius");}}
        System.out.println("Pulse outward shell: native engine travel reached Radius for all 64 directions at 1/10/200 ticks");
        var ordinary=new NativeParticle(Vec3.ZERO);tick.invoke(mc.particleEngine,ordinary);
        if(ordinary.position().distanceTo(new Vec3(.25,0,0))<1e-9)throw new AssertionError("Unconfigured particle was changed");
        var jet=new NativeParticle(Vec3.ZERO);((ParticleTravel)(Object)jet).sablelife$jet(Vec3.ZERO,new Vec3(2,0,0),2,8,.25,0);
        if(jet.position().distanceTo(new Vec3(.5,0,0))>1e-9)throw new AssertionError("Jet sub-tick birth phase lost");
        for(int i=1;i<=4;i++){tick.invoke(mc.particleEngine,jet);if(jet.position().distanceTo(new Vec3(Math.min(8,.5+2*i),0,0))>1e-9)throw new AssertionError("Native particle did not retain flame jet speed/range");}
        if(jet.isAlive())throw new AssertionError("Jet remained alive past range");
        System.out.println("Particle engine: 8-block forward/reverse travel and native-particle isolation passed");
    }
}
