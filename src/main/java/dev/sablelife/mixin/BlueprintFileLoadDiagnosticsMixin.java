package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.sablelife.compat.photomancy.PhotomancyApiContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the original file-load exception when the command reports only its message. */
@Mixin(value = BlueprintToolService.class, remap = false)
public abstract class BlueprintFileLoadDiagnosticsMixin {
    @WrapOperation(method = PhotomancyApiContract.FILE_LOAD,
            at = @At(value = "INVOKE", target = "Ljava/lang/Exception;getMessage()Ljava/lang/String;"))
    private static String sablelife$logFileLoadFailure(Exception failure, Operation<String> original,
            @Local(argsOnly = true) String name) {
        LogUtils.getLogger().warn("Photomancy blueprint file load failed for '{}'", name, failure);
        return original.call(failure);
    }
}
