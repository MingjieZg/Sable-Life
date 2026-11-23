package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions;
import dev.sablelife.compat.photomancy.BlueprintFactionPlacement;
import dev.sablelife.compat.photomancy.PhotomancyApiContract;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlueprintToolServerActions.class, remap = false)
public abstract class BlueprintToolFactionActorMixin {
    @WrapOperation(method = PhotomancyApiContract.TOOL_LOAD,
            at = @At(value = "INVOKE", target = PhotomancyApiContract.BYTES_LOAD_INVOKE))
    private static BlueprintToolResult sablelife$withToolActor(ServerLevel level, Vec3 origin, byte[] bytes,
            String name, Operation<BlueprintToolResult> original, @Local(argsOnly = true) ServerPlayer actor) {
        return BlueprintFactionPlacement.withActor(level, actor.hasPermissions(2) ? actor : null,
                () -> original.call(level, origin, bytes, name));
    }
}
