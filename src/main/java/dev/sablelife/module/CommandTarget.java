package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Temporary order for the core's movement, steering and opted-in attacks. */
public final class CommandTarget {
    public static final int TARGET_ALIGNMENT = 1;
    public static final int AUTO_FACING = 2;
    public static final int LINEAR_MOVEMENT = 4;
    public static final int WEAPON_TARGETING = 8;
    // Orders and wands without an explicit mask keep their original steering defaults.
    public static final int ALL_MODULES = TARGET_ALIGNMENT | AUTO_FACING | LINEAR_MOVEMENT;
    public static final int SUPPORTED_MODULES = ALL_MODULES | WEAPON_TARGETING;

    private Order order;

    public record Aim(Vec3 point, String name) {}

    public record Order(LivingEntity entity, BlockPos destination, UUID lifeId, long expires, int enabledModulesMask) {
        public Order(LivingEntity entity, BlockPos destination, UUID lifeId, long expires) {
            this(entity, destination, lifeId, expires, ALL_MODULES);
        }

        public Order(LivingEntity entity, BlockPos destination, long expires) {
            this(entity, destination, null, expires);
        }

        public Aim resolve(ServerLevel level, boolean includeCreative) {
            if (destination != null) {
                if (!level.hasChunkAt(destination)) return null;
                return new Aim(destination.getCenter(), destination.toShortString());
            }
            if (lifeId != null) {
                var target = activeLife(level, lifeId);
                if (target == null) return null;
                var point = SableBodyProbe.toWorld(level, target.getBlockPos().getCenter());
                return new Aim(point, target.displayName().getString());
            }
            if (entity == null || entity.level() != level || !entity.isAlive() || entity.isSpectator()
                    || entity instanceof Player player && player.isCreative() && !includeCreative) return null;
            var point = SableBodyProbe.toWorld(level, entity.getBoundingBox().getCenter());
            return new Aim(point, entity.getName().getString());
        }
    }

    private static HeartBlockEntity activeLife(ServerLevel level, UUID lifeId) {
        var target = BodyOwnership.get(level).loadedHeart(level, lifeId);
        return target != null && target.health().state() == HeartState.ALIVE ? target : null;
    }

    public void entity(LivingEntity target, long expires) {
        entity(target, expires, ALL_MODULES);
    }

    public void entity(LivingEntity target, long expires, int enabledModulesMask) {
        order = new Order(target, null, null, expires, enabledModulesMask);
    }

    public void destination(BlockPos point, long expires) {
        destination(point, expires, ALL_MODULES);
    }

    public void destination(BlockPos point, long expires, int enabledModulesMask) {
        order = new Order(null, point.immutable(), null, expires, enabledModulesMask & ALL_MODULES);
    }

    public void life(UUID target, long expires) {
        life(target, expires, ALL_MODULES);
    }

    public void life(UUID target, long expires, int enabledModulesMask) {
        order = new Order(null, null, target, expires, enabledModulesMask);
    }

    public Order current(ServerLevel level) {
        if (order != null && !valid(level, order)) clear();
        return order;
    }

    private static boolean valid(ServerLevel level, Order order) {
        return order.expires() > level.getGameTime()
                && (order.entity() == null || order.entity().level() == level
                    && !order.entity().isDeadOrDying() && !order.entity().isRemoved())
                && (order.lifeId() == null || activeLife(level, order.lifeId()) != null);
    }

    public Order current(ServerLevel level, int moduleMask) {
        var current = current(level);
        return current != null && (current.enabledModulesMask() & moduleMask) != 0 ? current : null;
    }

    public UUID weaponTarget(ServerLevel level) {
        var current = order;
        if (current == null || (current.enabledModulesMask() & WEAPON_TARGETING) == 0 || !valid(level, current)) return null;
        return current.entity() == null ? current.lifeId() : current.entity().getUUID();
    }

    public void clear() {
        order = null;
    }
}
