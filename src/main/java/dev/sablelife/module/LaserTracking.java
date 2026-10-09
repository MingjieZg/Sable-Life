package dev.sablelife.module;

/** Fraction of the shortest angular turn allowed in one game tick. */
public final class LaserTracking {
    public static double fraction(double directionDot, double speed) {
        if (speed >= 1) return 1;
        double angle = Math.acos(Math.clamp(directionDot, -1, 1));
        if (angle < 1e-9) return 1;
        return Math.min(1, Math.toRadians(Math.max(.1, speed) * 10) / angle);
    }
}
