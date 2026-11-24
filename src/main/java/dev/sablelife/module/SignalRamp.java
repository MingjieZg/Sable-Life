package dev.sablelife.module;

/** A world-time transition driven by the trigger, with optional release from the last published power. */
public final class SignalRamp {
    private enum Phase { IDLE, ACTIVE, RELEASE }

    private Phase phase = Phase.IDLE;
    private long lastTick = Long.MIN_VALUE;
    private long began;
    private int configuredStart, end, duration, releaseDuration;
    private int start, output;

    public int tick(long now, boolean input, int from, int to, int ticks) {
        return tick(now, input, from, to, ticks, 0);
    }

    public int tick(long now, boolean input, int from, int to, int ticks, int releaseTicks) {
        from = Math.clamp(from, 0, 15);
        to = Math.clamp(to, 0, 15);
        ticks = Math.clamp(ticks, 1, 12000);
        releaseTicks = Math.clamp(releaseTicks, 0, 12000);
        if (now < lastTick || configuredStart != from || end != to || duration != ticks
                || phase == Phase.RELEASE && releaseDuration != releaseTicks) {
            reset();
            configuredStart = from;
            end = to;
            duration = ticks;
        }
        releaseDuration = releaseTicks;
        lastTick = now;

        if (input) {
            if (phase != Phase.ACTIVE) {
                boolean resuming = phase == Phase.RELEASE && elapsed(now, releaseDuration) < releaseDuration;
                start = resuming ? output : configuredStart;
                began = now;
                phase = Phase.ACTIVE;
            }
            double progress = (double) elapsed(now, duration) / duration;
            output = (int) Math.round(start + (end - start) * progress);
            return output;
        }

        if (phase == Phase.ACTIVE) {
            if (releaseDuration == 0 || output == 0) {
                reset();
                return 0;
            }
            start = output;
            began = now;
            phase = Phase.RELEASE;
        }
        if (phase == Phase.RELEASE) {
            int remaining = releaseDuration - elapsed(now, releaseDuration);
            if (remaining == 0) {
                reset();
            } else {
                // Round up so the last level of redstone does not vanish before the release ends.
                output = (start * remaining + releaseDuration - 1) / releaseDuration;
            }
        }
        return output;
    }

    void reset() {
        phase = Phase.IDLE;
        output = 0;
    }

    private int elapsed(long now, int limit) {
        long ticks = now - began;
        return ticks < 0 ? limit : (int) Math.min(ticks, limit);
    }
}
