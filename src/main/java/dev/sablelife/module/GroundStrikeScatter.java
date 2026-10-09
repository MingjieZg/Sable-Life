package dev.sablelife.module;

/** Area-uniform horizontal scatter, independent of terrain projection. */
public final class GroundStrikeScatter {
    public record Offset(double x, double z) {}
    public static Offset sample(double inner, double outer, double radial, double angular) {
        double radius = Math.sqrt(inner * inner + radial * (outer * outer - inner * inner));
        double angle = angular * Math.PI * 2;
        return new Offset(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }
}
