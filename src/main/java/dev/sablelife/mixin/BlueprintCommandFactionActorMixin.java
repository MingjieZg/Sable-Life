package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.command.SableBlueprintCommands;
import dev.sablelife.compat.photomancy.BlueprintFactionPlacement;
import dev.sablelife.compat.photomancy.PhotomancyApiContract;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SableBlueprintCommands.class, remap = false)
public abstract class BlueprintCommandFactionActorMixin {
    @WrapOperation(method = PhotomancyApiContract.COMMAND_LOAD,
            at = @At(value = "INVOKE", target = PhotomancyApiContract.FILE_LOAD_INVOKE))
    private static BlueprintToolResult sablelife$withCommandActor(MinecraftServer server, ServerLevel level,
            Vec3 origin, String name, Operation<BlueprintToolResult> original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var actor = source.hasPermission(2) && source.source instanceof ServerPlayer player ? player : null;
        return BlueprintFactionPlacement.withActor(level, actor, () -> original.call(server, level, origin, name));
    }
}
