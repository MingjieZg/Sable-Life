package dev.sablelife.compat.sablecollision;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.sablelife.module.EntityPassThroughState;
import net.minecraft.world.entity.Entity;

public final class EntityPassThroughTracking {
    public static void detach(Entity entity) {
        if (!EntityPassThroughState.mayIgnore(entity)) return;
        var tracking = (EntityMovementExtension) entity;
        var support = tracking.sable$getTrackingSubLevel();
        if (support == null || !EntityPassThroughState.ignores(entity, support)) return;
        tracking.sable$setTrackingSubLevel(null);
        if (entity instanceof LivingEntityMovementExtension living) living.sable$getInheritedVelocity().zero();
        entity.setOnGround(false);
    }

    private EntityPassThroughTracking() {}
}
