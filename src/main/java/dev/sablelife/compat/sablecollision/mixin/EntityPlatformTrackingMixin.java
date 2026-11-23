package dev.sablelife.compat.sablecollision.mixin;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.compat.sablecollision.EntityPassThroughTracking;
import dev.sablelife.module.EntityPassThroughState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 900)
public abstract class EntityPlatformTrackingMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void sablelife$releaseBeforeReadingInheritedMotion(MoverType mover, Vec3 motion, CallbackInfo ci) {
        // Sable reads its raw tracking field and inherited velocity before it calls the solver.
        EntityPassThroughTracking.detach((Entity) (Object) this);
    }

    @Dynamic("Added by Sable's entity movement mixin")
    @ModifyVariable(method = "sable$setTrackingSubLevel(Ldev/ryanhcode/sable/sublevel/SubLevel;)V", at = @At("HEAD"),
            argsOnly = true, remap = false)
    private SubLevel sablelife$ignoreStalePlatformAttachment(SubLevel support) {
        // Sable can still convert an in-flight packet's local coordinates using the same platform.
        Entity entity = (Entity) (Object) this;
        if (support == null || !EntityPassThroughState.ignores(entity, support)) return support;
        EntityPassThroughTracking.detach(entity);
        return null;
    }
}
