package dev.sablelife.mixin;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
/** Keep the native failed-placement reason visible to an operator diagnosing an existing world. */
@Mixin(value=BlueprintToolServerActions.class,remap=false)
public abstract class BlueprintToolFailureMessageMixin {
 @Inject(method="handleLoadBlueprint",at=@At(value="INVOKE",target="Ldev/rew1nd/sableschematicapi/blueprint/tool/BlueprintToolResult;success()Z",ordinal=0))
 private static void sablelife$reason(ServerPlayer player,CompoundTag data,CallbackInfo ci,@Local BlueprintToolResult result){
  if(!result.success())player.displayClientMessage(Component.literal(result.message()).withStyle(ChatFormatting.RED),false);
 }
}
