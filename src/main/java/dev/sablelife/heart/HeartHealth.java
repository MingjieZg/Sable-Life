package dev.sablelife.heart;

import java.util.Objects;

/** Server-owned health rules, deliberately independent of Minecraft for deterministic verification. */
public final class HeartHealth {
    public static final float MAX_CONFIGURED_HEALTH = 1_000_000.0F;
    public static final int MAX_DEATH_DELAY = 1_200;
    private final float maximum;
    private final int deathDelay;
    private float current;
    private HeartState state;
    private int remainingDeathTicks;

    public HeartHealth(float maximum, int deathDelay) {
        requireMaximum(maximum);
        if (deathDelay < 0 || deathDelay > MAX_DEATH_DELAY) throw new IllegalArgumentException("Invalid death delay");
        this.maximum = maximum;
        this.deathDelay = deathDelay;
        this.current = maximum;
        this.state = HeartState.DORMANT;
    }

    public static HeartHealth restore(float maximum, float current, int deathDelay, HeartState state, int remaining) {
        HeartHealth result = new HeartHealth(maximum, deathDelay);
        Objects.requireNonNull(state);
        if (!Float.isFinite(current) || current < 0 || current > maximum) throw new IllegalArgumentException("Invalid health");
        if (remaining < 0 || remaining > deathDelay) throw new IllegalArgumentException("Invalid death countdown");
        if (state == HeartState.ALIVE && current <= 0) {
            throw new IllegalArgumentException("Living core must have positive health");
        }
        if ((state == HeartState.DEAD || state == HeartState.DYING) && current != 0) {
            throw new IllegalArgumentException("Dead core must have zero health");
        }
        result.current = current;
        result.state = state;
        result.remainingDeathTicks = state == HeartState.DYING ? remaining : 0;
        return result;
    }

    public boolean activate() {
        if (state != HeartState.DORMANT || current <= 0) return false;
        state = HeartState.ALIVE;
        return true;
    }

    public boolean hurt(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || state != HeartState.ALIVE) return false;
        current = Math.max(0, current - amount);
        if (current == 0) {
            state = HeartState.DYING;
            remainingDeathTicks = deathDelay;
        }
        return true;
    }

    public boolean heal(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || state != HeartState.ALIVE || current >= maximum) return false;
        current = Math.min(maximum, current + amount); return true;
    }

    /** @return true exactly once, when the death delay finishes. */
    public boolean tick() {
        if (state != HeartState.DYING) return false;
        if (remainingDeathTicks > 0) remainingDeathTicks--;
        if (remainingDeathTicks != 0) return false;
        state = HeartState.DEAD;
        return true;
    }

    public int comparatorSignal() {
        return state == HeartState.ALIVE ? Math.max(1, (int) Math.ceil(15.0 * current / maximum)) : 0;
    }

    private static void requireMaximum(float value) {
        if (!Float.isFinite(value) || value <= 0 || value > MAX_CONFIGURED_HEALTH) {
            throw new IllegalArgumentException("Invalid maximum health");
        }
    }

    public float maximum() { return maximum; }
    public float current() { return current; }
    public int deathDelay() { return deathDelay; }
    public HeartState state() { return state; }
    public int remainingDeathTicks() { return remainingDeathTicks; }
}

