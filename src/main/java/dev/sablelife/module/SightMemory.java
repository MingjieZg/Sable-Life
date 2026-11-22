package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Predicate;

/** A server-side lock remembers a point only after a successful sight check. */
public final class SightMemory {
    public static final String FIELD = "sightMemory";
    private static final double MAX_OBSERVATION_BLOCKS = 258;

    public record Target(UUID id, Vec3 point, boolean visible) {}
    private Target last;
    private long expiresAt = Long.MIN_VALUE;

    public UUID id() { return last == null ? null : last.id(); }

    public Target see(UUID id, Vec3 point) {
        expiresAt = Long.MIN_VALUE;
        return last = new Target(id, point, true);
    }

    public boolean obscured() { return expiresAt != Long.MIN_VALUE; }

    public Target lastSeen() { return last; }

    public Target aimAt(long now) {
        if (!obscured()) return last;
        return now < expiresAt ? new Target(last.id(), last.point(), false) : null;
    }

    public boolean keeping(long now, int ticks) {
        if (last == null || ticks <= 0) return false;
        if (!obscured()) expiresAt = now + ticks;
        return now < expiresAt;
    }

    public Target remember(long now, int ticks) {
        return keeping(now, ticks) ? aimAt(now) : null;
    }

    public void clear() {
        last = null;
        expiresAt = Long.MIN_VALUE;
    }

    /** A retained entity may have teleported after its last sample. Keep the recheck bounded. */
    public static boolean canRecheck(Vec3 origin, Vec3 point) {
        return origin.distanceToSqr(point) <= MAX_OBSERVATION_BLOCKS * MAX_OBSERVATION_BLOCKS;
    }

    /** Use the source deadline and keep the last position seen by this consumer. */
    static final class SourceLink {
        private UUID sourceId;
        private Target seen;

        Vec3 follow(UUID origin, Target source, boolean requireSight, Predicate<Vec3> inRange, Predicate<Vec3> visible) {
            if (source == null || !java.util.Objects.equals(sourceId, origin) || seen != null && !seen.id().equals(source.id())) clear();
            sourceId = origin;
            if (source == null) return null;
            if (!requireSight) {
                clear();
                return source.visible() && inRange.test(source.point()) ? source.point() : null;
            }
            if (!source.visible()) {
                if (seen == null || !inRange.test(seen.point())) {
                    clear();
                    return null;
                }
                return seen.point();
            }
            if (!inRange.test(source.point())) {
                clear();
                return null;
            }
            if (!visible.test(source.point())) return null;
            seen = source;
            return seen.point();
        }

        void clear() { seen = null; }
    }

    public static boolean enabled(VirtualModule module) {
        var field = ModuleFields.find(module.type(), "lineOfSight");
        return field != null && field.choices().indexOf("on") == (int) module.value("lineOfSight");
    }

    public static int ticks(VirtualModule module) {
        return enabled(module) ? (int) module.value(FIELD) : 0;
    }

    private static boolean applicable(VirtualModule module) {
        return switch (module.type()) {
            case "laser" -> module.value("laserAim") == 1;
            case "arrow", "gun", "slash" -> module.value("shotAim") == 1;
            case "mortar" -> module.value("aimMode") == 1;
            case "freeze", "pull", "sphere_blast", "pulse" -> module.value("releasePosition") == 1;
            case "quake" -> module.value("quakeOrigin") == 1;
            case "ground_strike" -> module.value("strikeMode") == 2;
            case "summoned_weapon" -> module.value("summonAim") == 1;
            default -> true;
        };
    }

    public static boolean configurable(VirtualModule module) {
        return enabled(module) && applicable(module)
                && !(module.value("facingSource") == 1
                    && (module.type().equals("navigation") || module.type().equals("auto_facing")));
    }

    public static boolean sensorSource(String type) {
        return type.equals("auto_facing") || type.equals("navigation") || type.equals("move_set");
    }
}
