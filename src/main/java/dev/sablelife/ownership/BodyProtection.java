package dev.sablelife.ownership;

import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BodyProtection {
    public static final ThreadLocal<net.minecraft.core.BlockPos> AUTHOR_BREAK = new ThreadLocal<>();
    public static final ThreadLocal<Integer> CLEANING = ThreadLocal.withInitial(() -> 0);
    private BodyProtection() {}
    public static boolean locked(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (BodyOwnership.MOVING.get() != 0 || CLEANING.get() != 0 || pos.equals(AUTHOR_BREAK.get())) return false;
        var owner = BodyOwnership.get(level).owner(pos);
        return owner != null && !LifeLifecycle.get(level.getServer()).breakable(owner);
    }
    public static boolean explosionLocked(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var owner = BodyOwnership.get(level).owner(pos);
        return owner != null && (locked(level, pos) || !LifeLifecycle.get(level.getServer()).explodable(owner));
    }
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getPlayer().isCreative() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof dev.sablelife.heart.HeartBlock
                ) {
            if (!event.getPlayer().isShiftKeyDown()) event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && locked(level, event.getPos())) event.setCanceled(true);
    }
    public static void explosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var source = event.getExplosion().getDirectSourceEntity();
            var data = source == null ? null : source.getPersistentData();
            java.util.UUID attacker = data != null && data.hasUUID("SableLifeAttackOwner") ? data.getUUID("SableLifeAttackOwner") : null;
            event.getAffectedBlocks().removeIf(pos -> explosionLocked(level, pos)
                    || attacker != null && (attacker.equals(BodyOwnership.get(level).owner(pos))
                    || SableBodyProbe.findConnectedHearts(level, pos).stream().anyMatch(core -> !core.acceptsLifeAttack(attacker))));
        }
    }
    public static void drops(BlockDropsEvent event) {
        if (BodyOwnership.protects(event.getLevel(), event.getPos())) event.setCanceled(true);
    }
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var data = BodyOwnership.get(level);
        // A new block must never inherit a destroyed block's stale coordinate record.
        data.forget(event.getPos());
        var heart = SableBodyProbe.findActivatedHeart(level, event.getPos());
        if (heart != null) data.bind(event.getPos(), heart.creatureId());
    }
}
