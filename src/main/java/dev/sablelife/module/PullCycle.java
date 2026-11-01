package dev.sablelife.module;

/** Bounded warning / attraction / cooldown clock, independent of entity movement. */
public final class PullCycle {
    public enum Phase { WAITING, WARNING, PULLING }
    private Phase phase = Phase.WAITING;
    private long fireAt, finishAt, readyAt;
    public Phase phase() { return phase; }
    public long fireAt() { return fireAt; }
    public boolean ready(long now) { return phase == Phase.WAITING && now >= readyAt; }
    public Phase tick(long now, int warning, int duration, int cooldown, boolean canStart) {
        if (phase != Phase.WAITING && now >= finishAt) {
            phase = Phase.WAITING; readyAt = now + Math.max(1, cooldown); return phase;
        }
        if (phase == Phase.WAITING && canStart && now >= readyAt) {
            phase = Phase.WARNING;
            fireAt = now + Math.max(1, warning); finishAt = fireAt + Math.max(1, duration);
        }
        if (phase == Phase.WARNING && now >= fireAt) phase = Phase.PULLING;
        return phase;
    }
}
