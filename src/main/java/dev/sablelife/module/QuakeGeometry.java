package dev.sablelife.module;

/** Swept fronts prevent fast waves from skipping targets between ticks. Distances are horizontal. */
public final class QuakeGeometry {
    public static boolean crossed(boolean ring, double along, double across, double radial,
                                  double previous, double current, double width, double range) {
        double distance = ring ? radial : along;
        return distance >= 0 && distance <= range && (ring || Math.abs(across) <= width / 2)
                && distance >= previous - width / 2 && distance <= current + width / 2;
    }
    /** Vanilla 0.08 gravity, 0.98 vertical drag; calibrates the configured height without a potion. */
    public static double launchSpeed(double height) {
        if (height <= 0) return 0;
        double low = 0, high = 4;
        for (int i = 0; i < 32; i++) {
            double speed = (low + high) / 2, y = 0, peak = 0;
            for (int t = 0; t < 200 && speed > 0; t++) { y += speed; peak = y; speed = (speed - .08) * .98; }
            if (peak < height) low = (low + high) / 2; else high = (low + high) / 2;
        }
        return (low + high) / 2;
    }
    private QuakeGeometry() {}
}
