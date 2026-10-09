package dev.sablelife.module;

/** Change only velocity toward the point; preserve lateral movement for dodging. */
public final class PullMotion {
    private PullMotion() {}
    public static double acceleration(double distance, double inwardSpeed, double strength, double speedLimit, double stopDistance) {
        if (!Double.isFinite(distance) || !Double.isFinite(inwardSpeed) || !Double.isFinite(strength)
                || !Double.isFinite(speedLimit) || !Double.isFinite(stopDistance)) return 0;
        double desired = Math.min(Math.max(0, speedLimit), Math.max(0, distance - Math.max(0, stopDistance)));
        return Math.min(Math.max(0, strength), desired - inwardSpeed);
    }
}
