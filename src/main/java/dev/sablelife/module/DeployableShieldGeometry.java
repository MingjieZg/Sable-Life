package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Planar strokes for a shield that opens from its center hinge. */
public final class DeployableShieldGeometry {
    // The upright marked-face frame points down along +Y.
    private static final double FACE_OFFSET = .035;
    private static final List<Vec3> OUTLINE = List.of(
            point(0, -.88), point(.98, -.70), point(.83, .35),
            point(0, 1), point(-.83, .35), point(-.98, -.70));

    public static List<PatternGeometry.Stroke> strokes(double openness, double size) {
        if (!Double.isFinite(openness) || openness <= 0 || openness > 1
                || !Double.isFinite(size) || size < .1 || size > 32) return List.of();

        double halfWidth = size * progress(openness, 0, .72);
        double height = size * (.82 + .18 * progress(openness, 0, .46));
        double width = Math.max(.012, size * .014);
        double rim = progress(openness, .14, .70);
        double ribs = progress(openness, .035, .48);
        double trim = progress(openness, .55, .93);
        var strokes = new ArrayList<PatternGeometry.Stroke>(17);

        for (int i = 0; i < OUTLINE.size(); i++) {
            var from = OUTLINE.get(i);
            var to = OUTLINE.get((i + 1) % OUTLINE.size());
            stroke(strokes, scaled(from, halfWidth, height), scaled(to, halfWidth, height),
                    width * 1.55, rim);
        }

        var upperHinge = point(0, -.82 * height);
        var lowerHinge = point(0, .88 * height);
        double hinge = progress(openness, 0, .24);
        stroke(strokes, upperHinge, lowerHinge, width * (1.15 - .25 * rim),
                hinge * (1 - .26 * rim));

        for (int side = -1; side <= 1; side += 2) {
            double x = side * halfWidth;
            var shoulder = point(.90 * x, -.64 * height);
            var heel = point(.76 * x, .30 * height);
            var upperRib = point(0, -.31 * height);
            var lowerRib = point(0, .29 * height);
            stroke(strokes, upperRib, shoulder, width, ribs * .73);
            stroke(strokes, lowerRib, heel, width, ribs * .73);

            var upperTrim = point(.16 * x, -.71 * height);
            var shoulderTrim = point(.84 * x, -.59 * height);
            var heelTrim = point(.70 * x, .24 * height);
            var lowerTrim = point(.14 * x, .70 * height);
            stroke(strokes, upperTrim, shoulderTrim, width * .85, trim * .42);
            stroke(strokes, shoulderTrim, heelTrim, width * .85, trim * .42);
            stroke(strokes, heelTrim, lowerTrim, width * .85, trim * .42);
        }

        return List.copyOf(strokes);
    }

    private static Vec3 point(double x, double y) {
        return new Vec3(x, y, FACE_OFFSET);
    }

    private static Vec3 scaled(Vec3 point, double width, double height) {
        return point(point.x * width, point.y * height);
    }

    private static void stroke(List<PatternGeometry.Stroke> strokes, Vec3 from, Vec3 to,
                               double width, double alpha) {
        if (alpha > .005 && from.distanceToSqr(to) > 1e-10) {
            strokes.add(new PatternGeometry.Stroke(from, to, (float) width, (float) alpha));
        }
    }

    private static double progress(double openness, double start, double end) {
        double t = Math.clamp((openness - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private DeployableShieldGeometry() {}
}
