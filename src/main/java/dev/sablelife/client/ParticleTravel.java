package dev.sablelife.client;

import net.minecraft.world.phys.Vec3;

/** Implemented by client particles; their native sprite/animation is retained. */
public interface ParticleTravel {
    void sablelife$travel(Vec3 from,Vec3 to,int ticks,int life);
    void sablelife$advanceTravel();
    void sablelife$jet(Vec3 from,Vec3 velocity,double axialSpeed,double range,double phase,int life);
}
