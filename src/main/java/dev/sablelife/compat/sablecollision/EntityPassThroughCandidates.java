package dev.sablelife.compat.sablecollision;

import com.google.common.collect.Iterables;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.module.EntityPassThroughState;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;

public final class EntityPassThroughCandidates {
    private static final ThreadLocal<Entity> SUPPORT_ENTITY = new ThreadLocal<>();

    public static Iterable<SubLevel> forEntity(Iterable<SubLevel> candidates, Entity entity) {
        return entity == null || !EntityPassThroughState.mayIgnore(entity) ? candidates
                : Iterables.filter(candidates, body -> !EntityPassThroughState.ignores(entity, body));
    }

    public static Iterable<SubLevel> forSupport(Iterable<SubLevel> candidates) {
        return forEntity(candidates, SUPPORT_ENTITY.get());
    }

    public static boolean checkSupport(Entity entity, BooleanSupplier original) {
        if (!EntityPassThroughState.mayIgnore(entity)) return original.getAsBoolean();
        Entity previous = SUPPORT_ENTITY.get();
        SUPPORT_ENTITY.set(entity);
        try {
            return original.getAsBoolean();
        } finally {
            if (previous == null) SUPPORT_ENTITY.remove();
            else SUPPORT_ENTITY.set(previous);
        }
    }

    private EntityPassThroughCandidates() {}
}
