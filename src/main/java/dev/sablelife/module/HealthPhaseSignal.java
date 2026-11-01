package dev.sablelife.module;

/** Edge-triggered pulse clock; staying in a phase does not restart its pulse. */
public final class HealthPhaseSignal {
    private boolean previous;
    private int remaining;
    public int remaining() { return remaining; }
    public boolean tick(boolean inRange, boolean pulse, int ticks) {
        boolean entered = inRange && !previous;
        previous = inRange;
        if (!inRange) { remaining = 0; return false; }
        if (!pulse) { remaining = 0; return true; }
        if (entered) remaining = Math.clamp(ticks, 1, 12000);
        if (remaining == 0) return false;
        remaining--; return true;
    }
}
