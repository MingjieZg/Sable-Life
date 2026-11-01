package dev.sablelife.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Recheck an already-open device menu when the author changes its interaction policy. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ProtectedBodyContainerClickMixin {
 @Shadow public ServerPlayer player;
 @Inject(method={"handleContainerClick","handleContainerButtonClick"},at=@At("HEAD"),cancellable=true)
 private void sablelife$lockedContainer(CallbackInfo ci){
  // Packet handlers first arrive on the network thread; vanilla schedules them on the server thread.
  if(player.server.isSameThread()&&!dev.sablelife.ownership.BodyInteraction.openMenuAllowed(player)){player.closeContainer();ci.cancel();}
 }
}
