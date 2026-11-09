package dev.sablelife.mixin;

import dev.sablelife.combat.HitboxMobGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class HitboxNavigationMixin {
    @Shadow @Final protected Mob mob;

    @ModifyVariable(method="createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",at=@At("HEAD"),argsOnly=true)
    private Set<BlockPos> sablelife$receiverSurfacePath(Set<BlockPos> targets) {
        return HitboxMobGeometry.pathTargets(mob,targets);
    }
}
