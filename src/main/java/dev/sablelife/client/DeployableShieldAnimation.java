package dev.sablelife.client;

import dev.sablelife.module.PatternVisual;
import dev.sablelife.module.ShieldVisualPacket;

/** An interruptible transition using the same track for opening, holding and folding. */
final class DeployableShieldAnimation {
    private PatternVisual visual;
    private double from;
    private double target = 1;
    private double changedAt;
    private double travelTicks;
    private double closeAt;

    DeployableShieldAnimation(ShieldVisualPacket packet, double now) {
        visual = packet.visual();
        from = packet.phase() == ShieldVisualPacket.Phase.SYNC ? 1 : 0;
        changedAt = now;
        travelTicks = (1 - from) * ShieldVisualPacket.transitionTicks(visual.speed());
        extend(packet.phase(), now);
    }

    PatternVisual visual() {
        return visual;
    }

    boolean closing() {
        return target == 0;
    }

    double openness(double now) {
        if (travelTicks == 0) return target;
        double progress = Math.clamp((now - changedAt) / travelTicks, 0, 1);
        return from + (target - from) * progress;
    }

    void update(ShieldVisualPacket packet, double now) {
        advance(now);
        if (packet.phase() == ShieldVisualPacket.Phase.CLOSE) {
            retarget(0, now);
        } else {
            visual = packet.visual();
            retarget(1, now);
            extend(packet.phase(), now);
        }
    }

    boolean advance(double now) {
        if (!closing() && now >= closeAt) retarget(0, closeAt);
        return closing() && now >= changedAt + travelTicks;
    }

    private void retarget(double next, double now) {
        if (target == next) return;
        from = openness(now);
        target = next;
        changedAt = now;
        travelTicks = Math.abs(target - from) * ShieldVisualPacket.transitionTicks(visual.speed());
    }

    private void extend(ShieldVisualPacket.Phase phase, double now) {
        double travel = ShieldVisualPacket.transitionTicks(visual.speed());
        closeAt = now + (phase == ShieldVisualPacket.Phase.PREVIEW
                ? Math.max(travel, visual.duration() - travel) : ShieldVisualPacket.LEASE_TICKS);
    }
}
