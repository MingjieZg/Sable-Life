package dev.sablelife.mixin;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Create's moving contraption uses a separate entity interaction packet. */
@Mixin(value=ContraptionInteractionPacket.class,remap=false)
public abstract class CreateBodyContraptionInteractionMixin {
 @Inject(method="handle",at=@At("HEAD"),cancellable=true)
 private void sablelife$policy(ServerPlayer player,CallbackInfo ci){
  var entity=player.serverLevel().getEntity(((ContraptionInteractionPacket)(Object)this).target());
  if(entity!=null&&!dev.sablelife.ownership.BodyInteraction.entityAllowed(player,entity))ci.cancel();
 }
}
