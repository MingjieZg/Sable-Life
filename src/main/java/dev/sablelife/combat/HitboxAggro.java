package dev.sablelife.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;

import java.util.EnumSet;

/** A low-priority target option for existing combat AI, installed only near opted-in receivers. */
final class HitboxAggro extends Goal {
    private static final double MAX_RANGE = 32;
    private final Mob mob;
    private EntityHitbox offered;
    private EntityHitbox engaged;
    private long offeredAt;

    private HitboxAggro(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    static void offer(EntityHitbox box) {
        if (!box.provokesMobs()) return;
        long now = box.level().getGameTime();
        for (var mob : box.level().getEntitiesOfClass(Mob.class, box.getBoundingBox().inflate(MAX_RANGE), HitboxAggro::idle)) {
            if (!inRange(mob, box)) continue;
            var goals = mob.targetSelector.getAvailableGoals();
            HitboxAggro goal = null;
            boolean combat = mob instanceof Enemy;
            for (var wrapped : goals) {
                if (wrapped.getGoal() instanceof HitboxAggro installed) goal = installed;
                if (wrapped.getGoal() instanceof HurtByTargetGoal) combat = true;
            }
            if (!combat || !canTarget(mob, box) || !mob.getSensing().hasLineOfSight(box)) continue;
            if (goal == null) {
                goal = new HitboxAggro(mob);
                mob.targetSelector.addGoal(Integer.MAX_VALUE - 1, goal);
            }
            if (goal.offeredAt != now || goal.offered == null || LifeRetaliation.distance(mob, box) < LifeRetaliation.distance(mob, goal.offered)) {
                goal.offered = box;
                goal.offeredAt = now;
            }
        }
    }

    private static boolean idle(Mob mob) {
        return !(mob instanceof EntityHitbox) && mob.isAlive() && !mob.isNoAi()
                && (mob.getTarget() == null || !mob.getTarget().isAlive());
    }

    private static boolean inRange(Mob mob, EntityHitbox box) {
        double range = Math.min(MAX_RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        return LifeRetaliation.distance(mob, box) <= range * range;
    }

    private static boolean canTarget(Mob mob, EntityHitbox box) {
        return box.provokesMobs() && mob.canAttack(box) && mob.canAttackType(box.getType())
                && !mob.isAlliedTo(box) && mob.isWithinRestriction(box.blockPosition());
    }

    @Override public boolean canUse() {
        if (offered == null) return false;
        if (mob.level().getGameTime() - offeredAt > 20 || !offered.provokesMobs()) {
            offered = null;
            return false;
        }
        return idle(mob) && inRange(mob, offered) && canTarget(mob, offered) && mob.getSensing().hasLineOfSight(offered);
    }

    @Override public void start() {
        engaged = offered;
        offered = null;
        mob.setTarget(engaged);
    }

    @Override public boolean canContinueToUse() {
        return engaged != null && mob.getTarget() == engaged && inRange(mob, engaged) && canTarget(mob, engaged);
    }

    @Override public void stop() {
        if (engaged != null && mob.getTarget() == engaged) mob.setTarget(null);
        engaged = null;
    }
}
