package dev.sablelife.mixin;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.SableBlueprintIncrementalPlacer;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(value=SableBlueprintIncrementalPlacer.class,remap=false)
public abstract class RespawnBuildDiagnosticsMixin {
 @WrapOperation(method="step",at=@At(value="INVOKE",target="Ljava/lang/RuntimeException;getMessage()Ljava/lang/String;"))
 private static String sablelife$failure(RuntimeException failure,Operation<String> original,@Local(argsOnly=true) SableBlueprint blueprint){
  if(PhotomancyRespawnBridge.trusted(blueprint))com.mojang.logging.LogUtils.getLogger().error("Native Arena blueprint commit failed",failure);
  return original.call(failure);
 }
}
