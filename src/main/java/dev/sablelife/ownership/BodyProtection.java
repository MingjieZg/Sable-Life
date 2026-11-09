package dev.sablelife.ownership;

import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BodyProtection {
    public static final ThreadLocal<net.minecraft.core.BlockPos> AUTHOR_BREAK = new ThreadLocal<>();
    public static final ThreadLocal<Integer> CLEANING = ThreadLocal.withInitial(() -> 0);
    private record MiningDrop(ServerLevel level, net.minecraft.core.BlockPos pos, java.util.UUID owner) {}
    private static final ThreadLocal<MiningDrop> MINING_DROP = new ThreadLocal<>();
    private record ExplosionHit(ServerLevel level,net.minecraft.core.BlockPos pos){}
    private static final ThreadLocal<ExplosionHit> EXPLOSION_HIT=new ThreadLocal<>();
    private BodyProtection() {}
    public static boolean playerBreak(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos, java.util.function.BooleanSupplier destroy) {
        var level = player.serverLevel();
        var owner = BodyOwnership.get(level).owner(pos);
        var lifecycle = owner == null ? null : LifeLifecycle.get(level.getServer());
        var previous = MINING_DROP.get();
        if (owner != null && !player.isCreative() && !player.isSpectator()
                && lifecycle.breakable(owner) && lifecycle.miningDrops(owner)
                && level.getBlockState(pos).canHarvestBlock(level, pos, player)) {
            MINING_DROP.set(new MiningDrop(level, pos.immutable(), owner));
        } else {
            MINING_DROP.remove();
        }
        try { return destroy.getAsBoolean(); }
        finally {
            if (previous == null) MINING_DROP.remove();
            else MINING_DROP.set(previous);
        }
    }
    public static boolean suppressDrops(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        var owner = BodyOwnership.get(server).owner(pos);
        if (owner == null) return false;
        var mining = MINING_DROP.get();
        return CLEANING.get() != 0 || BodyOwnership.MOVING.get() != 0
                || mining == null || mining.level() != server || !mining.pos().equals(pos) || !mining.owner().equals(owner);
    }
    public static boolean locked(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (BodyOwnership.MOVING.get() != 0 || CLEANING.get() != 0 || pos.equals(AUTHOR_BREAK.get())) return false;
        var hit=EXPLOSION_HIT.get();
        if(hit!=null&&hit.level()==level&&hit.pos().equals(pos))return explosionLocked(level,pos);
        var owner = BodyOwnership.get(level).owner(pos);
        return owner != null && !LifeLifecycle.get(level.getServer()).breakable(owner);
    }
    public static boolean explosionLocked(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var owner = BodyOwnership.get(level).owner(pos);
        return owner != null && !LifeLifecycle.get(level.getServer()).explodable(owner);
    }
    public static void explosionHit(ServerLevel level,net.minecraft.core.BlockPos pos,Runnable action){
        var previous=EXPLOSION_HIT.get();
        EXPLOSION_HIT.set(new ExplosionHit(level,pos));
        try{action.run();}
        finally{if(previous==null)EXPLOSION_HIT.remove();else EXPLOSION_HIT.set(previous);}
    }
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getPlayer().isCreative() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof dev.sablelife.heart.HeartBlock
                ) {
            if (!event.getPlayer().isShiftKeyDown()) event.setCanceled(true);
            return;
        }
        if(event.getLevel() instanceof ServerLevel level){
            var state=level.getBlockState(event.getPos());
            boolean protectedCore=state.getBlock() instanceof dev.sablelife.heart.HeartBlock
                    &&state.getValue(dev.sablelife.heart.HeartBlock.ACTIVE)
                    &&level.getBlockEntity(event.getPos()) instanceof dev.sablelife.heart.HeartBlockEntity core&&!core.isSurvivalCore();
            if(protectedCore||locked(level,event.getPos()))event.setCanceled(true);
        }
    }
    public static void explosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var source = event.getExplosion().getDirectSourceEntity();
            var data = source == null ? null : source.getPersistentData();
            java.util.UUID attacker = data != null && data.hasUUID("SableLifeAttackOwner") ? data.getUUID("SableLifeAttackOwner") : null;
            if (attacker==null && ((dev.sablelife.mixin.ExplosionDamageAccess)event.getExplosion()).sablelife$calculator() instanceof dev.sablelife.module.ModuleTerrainExplosion module) attacker=module.owner();
            final var attackOwner=attacker;
            event.getAffectedBlocks().removeIf(pos -> explosionLocked(level, pos)
                    || attackOwner != null && (attackOwner.equals(BodyOwnership.get(level).owner(pos))
                    || SableBodyProbe.findConnectedHearts(level, pos).stream().anyMatch(core -> !core.acceptsLifeAttack(attackOwner))));
        }
    }
    public static void drops(BlockDropsEvent event) {
        if (suppressDrops(event.getLevel(), event.getPos())) event.setCanceled(true);
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
