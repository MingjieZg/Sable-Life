package dev.sablelife.compat.sablecollision.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.compat.sablecollision.EntityPassThroughCandidates;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class EntitySupportCandidatesMixin {
    @WrapOperation(method = "canFallAtleastWithSubLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Lorg/joml/Vector3d;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"))
    private static Iterable<SubLevel> sablelife$supportCandidates(ActiveSableCompanion helper, Level level,
            BoundingBox3dc bounds, Operation<Iterable<SubLevel>> original) {
        // Arrows also use this helper. Only player support queries carry an entity here.
        return EntityPassThroughCandidates.forSupport(original.call(helper, level, bounds));
    }
}
