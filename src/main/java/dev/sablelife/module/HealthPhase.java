package dev.sablelife.module;

/** Adjacent (lower, upper] ranges do not overlap at an exact threshold. */
public final class HealthPhase {
    private HealthPhase() {}
    public static boolean contains(double current, double maximum, double lower, double upper) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || !Double.isFinite(lower)
                || !Double.isFinite(upper) || maximum <= 0 || current <= 0 || lower < 0 || upper > 100 || lower >= upper) return false;
        double percent = Math.clamp(current / maximum * 100, 0, 100);
        return percent > lower && percent <= upper;
    }
}
