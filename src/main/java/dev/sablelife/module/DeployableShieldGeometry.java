package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A flat hexagonal mesh. The animation reveals its cells from the center without moving them. */
public final class DeployableShieldGeometry {
    private static final double FACE_OFFSET = .035;
    private static final double CELL_SIDE = .5;
    private static final double DENSE_SIZE = 3;
    private static final double MAX_SIZE = 32;
    // At most 252 distinct edges, so 32 maximum-size shields fit the 8192-stroke frame budget.
    private static final double MAX_EXTENT_IN_CELL_SIDES = 8.75;
    private static final double SQRT_3 = Math.sqrt(3);
    private static final int[] CORNER_X = {2, 1, -1, -2, -1, 1};
    private static final int[] CORNER_Y = {0, 1, 1, 0, -1, -1};
    private static final int[] NEIGHBOR_Q = {1, 0, -1, -1, 0, 1};
    private static final int[] NEIGHBOR_R = {0, 1, 1, 0, -1, -1};

    private record Edge(PatternGeometry.Stroke stroke, double revealAt) {}

    private final List<Edge> edges;
    private final List<PatternGeometry.Stroke> held;

    public DeployableShieldGeometry(double size) {
        if (!Double.isFinite(size) || size < .1 || size > MAX_SIZE) {
            edges = List.of();
            held = List.of();
            return;
        }
        double extent = gridExtent(size);
        double cellSide = size <= DENSE_SIZE ? Math.min(CELL_SIDE, size) : size / extent;
        // Keeping whole cells inside the radius gives the shield a tessellated round outline.
        double centerExtent = extent - 1;
        int limit = (int) Math.floor(centerExtent * centerExtent / 3 + 1e-9);
        int reach = (int) Math.ceil(2 * centerExtent / 3);
        double[] reveal = new double[limit + 1];
        double[] strength = new double[limit + 1];
        for (int i = 0; i <= limit; i++) {
            double distance = centerExtent == 0 ? 0 : Math.sqrt(3 * i) / centerExtent;
            reveal[i] = .76 * distance;
            strength[i] = .96 - .26 * distance;
        }

        float width = (float) Math.max(.009, .062 * cellSide);
        var mesh = new ArrayList<Edge>();
        for (int q = -reach; q <= reach; q++) {
            for (int r = -reach; r <= reach; r++) {
                int distance = latticeDistance(q, r);
                if (distance > limit) continue;
                for (int side = 0; side < 6; side++) {
                    int neighbor = latticeDistance(q + NEIGHBOR_Q[side], r + NEIGHBOR_R[side]);
                    // Each shared edge is owned by the cell whose first three sides include it.
                    if (side >= 3 && neighbor <= limit) continue;
                    int first = Math.min(distance, neighbor);
                    int next = (side + 1) % 6;
                    var stroke = new PatternGeometry.Stroke(corner(q, r, side, cellSide),
                            corner(q, r, next, cellSide), width, (float) strength[first]);
                    mesh.add(new Edge(stroke, reveal[first]));
                }
            }
        }
        edges = List.copyOf(mesh);
        held = edges.stream().map(Edge::stroke).toList();
    }

    public static List<PatternGeometry.Stroke> strokes(double openness, double size) {
        return new DeployableShieldGeometry(size).strokes(openness);
    }

    public List<PatternGeometry.Stroke> strokes(double openness) {
        if (!Double.isFinite(openness) || openness <= 0 || openness > 1) return List.of();
        if (openness == 1) return held;
        var strokes = new ArrayList<PatternGeometry.Stroke>();
        for (var edge : edges) {
            if (openness <= edge.revealAt()) continue;
            double progress = Math.min(1, (openness - edge.revealAt()) / .24);
            var stroke = edge.stroke();
            float alpha = (float) (stroke.alpha() * progress * progress * (3 - 2 * progress));
            if (alpha > .005) {
                strokes.add(alpha == stroke.alpha() ? stroke
                        : new PatternGeometry.Stroke(stroke.a(), stroke.b(), stroke.width(), alpha));
            }
        }
        return List.copyOf(strokes);
    }

    private static double gridExtent(double size) {
        if (size <= DENSE_SIZE) return Math.max(1, size / CELL_SIDE);
        // Add more cells at large sizes, spacing them gradually to keep every tile intact.
        double denseExtent = DENSE_SIZE / CELL_SIDE;
        double growth = Math.log(size / DENSE_SIZE) / Math.log(MAX_SIZE / DENSE_SIZE);
        return denseExtent + (MAX_EXTENT_IN_CELL_SIDES - denseExtent) * growth;
    }

    private static int latticeDistance(int q, int r) {
        return q * q + q * r + r * r;
    }

    private static Vec3 corner(int q, int r, int index, double side) {
        // Integer corner coordinates make adjacent cells agree exactly on their endpoints.
        double x = (3 * q + CORNER_X[index]) * .5 * side;
        double y = (2 * r + q + CORNER_Y[index]) * (.5 * SQRT_3) * side;
        return new Vec3(x, y, FACE_OFFSET);
    }
}
