package dev.sablelife.compat.sablecollision.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelife.compat.sablecollision.EntityPassThroughCandidates;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerSupportContextMixin {
    @WrapMethod(method = "canPlayerFitWithinBlocksAndEntitiesWhen(Lnet/minecraft/world/entity/Pose;)Z")
    private boolean sablelife$checkStandingRoom(Pose pose, Operation<Boolean> original) {
        return EntityPassThroughCandidates.checkSupport((Player) (Object) this, () -> original.call(pose));
    }

    @WrapMethod(method = "canFallAtLeast(DDF)Z")
    private boolean sablelife$checkSneakingSupport(double x, double z, float stepHeight, Operation<Boolean> original) {
        return EntityPassThroughCandidates.checkSupport((Player) (Object) this, () -> original.call(x, z, stepHeight));
    }
}
