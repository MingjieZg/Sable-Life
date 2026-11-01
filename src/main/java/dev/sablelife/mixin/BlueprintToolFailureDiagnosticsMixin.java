package dev.sablelife.mixin;
import dev.rew1nd.sableschematicapi.blueprint.tool.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Photomancy's generic red UI omits the exception reason; retain it in the server log. */
@Mixin(value=BlueprintToolService.class,remap=false)
public abstract class BlueprintToolFailureDiagnosticsMixin {
 @Inject(method="loadBytes",at=@At("RETURN"))
 private static void sablelife$failed(ServerLevel level,Vec3 origin,byte[] data,String name,CallbackInfoReturnable<BlueprintToolResult> ci){
  var result=ci.getReturnValue();if(result!=null&&!result.success())
   com.mojang.logging.LogUtils.getLogger().warn("Photomancy blueprint placement failed in {} at {}: {}",level.dimension().location(),origin,result.message());
 }
}
