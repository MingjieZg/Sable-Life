package dev.sablelife.mixin;

import dev.sablelife.client.ParticleTravel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineTravelMixin {
    // Runs after every particle subclass tick, including ones that never call Particle.tick.
    @Inject(method="tickParticle",at=@At("TAIL"))
    private void sablelife$advance(Particle particle,CallbackInfo ci){((ParticleTravel)particle).sablelife$advanceTravel();}
}
