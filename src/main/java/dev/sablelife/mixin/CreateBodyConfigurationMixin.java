package dev.sablelife.mixin;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Closing right-click access must also reject Create's direct configuration packet path. */
@Mixin(value=BlockEntityConfigurationPacket.class,remap=false)
public abstract class CreateBodyConfigurationMixin {
 @Shadow @Final protected BlockPos pos;
 @Inject(method="handle",at=@At("HEAD"),cancellable=true)
 private void sablelife$interactionPolicy(ServerPlayer player,CallbackInfo ci){
  if(player!=null&&(!player.serverLevel().hasChunkAt(pos)||!dev.sablelife.ownership.BodyInteraction.allowed(player,pos)))ci.cancel();
 }
}
