package dev.sablelife.module;

/** Sweep timing is independent of damage cadence; visual sampling remains bounded. */
public final class LaserSweep {
    public static double fraction(double angle, double degreesPerSecond, double elapsedTicks, double durationTicks) {
        double progress = degreesPerSecond > 0 && angle != 0
                ? elapsedTicks * degreesPerSecond / (20 * Math.abs(angle))
                : elapsedTicks / Math.max(1, durationTicks - 1);
        return Math.clamp(progress, 0, 1);
    }

    /** Include both ends, at most 64 extra-particle positions per beam per tick. */
    public static int particleSamples(double length, double spacing) {
        if (length <= 0) return 1;
        return (int) Math.clamp(Math.ceil(length / Math.max(.25, spacing)) + 1, 2, 64);
    }

    public static boolean visible(VirtualModule module, String key) {
        return switch (key) {
            case "sweepSpeed" -> module.value("laserAim") == 0 && module.value("sweepAngle") != 0;
            case "verticalSweepSpeed" -> module.value("laserAim") == 0 && module.value("verticalSweepAngle") != 0;
            default -> true;
        };
    }

    private LaserSweep() {}
}
