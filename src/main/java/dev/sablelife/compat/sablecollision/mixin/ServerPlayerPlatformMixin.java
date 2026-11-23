package dev.sablelife.compat.sablecollision.mixin;

import dev.sablelife.compat.sablecollision.EntityPassThroughTracking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPlatformMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), order = 900)
    private void sablelife$releaseBeforePlatformCarriesPlayer(CallbackInfo ci) {
        EntityPassThroughTracking.detach((ServerPlayer) (Object) this);
    }
}
