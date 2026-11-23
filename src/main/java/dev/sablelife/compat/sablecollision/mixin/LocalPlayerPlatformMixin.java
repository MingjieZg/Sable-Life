package dev.sablelife.compat.sablecollision.mixin;

import dev.sablelife.compat.sablecollision.EntityPassThroughTracking;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPlatformMixin {
    @Inject(method = "sendPosition()V", at = @At("HEAD"))
    private void sablelife$releaseBeforeSendingPlatformPosition(CallbackInfo ci) {
        EntityPassThroughTracking.detach((LocalPlayer) (Object) this);
    }
}
