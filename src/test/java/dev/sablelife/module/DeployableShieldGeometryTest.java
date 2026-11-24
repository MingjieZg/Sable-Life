package dev.sablelife.module;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DeployableShieldGeometryTest {
    @Test
    void shapeUnfoldsFromItsCenterBeforeTheTrimAppears() {
        assertTrue(DeployableShieldGeometry.strokes(0, 2).isEmpty());
        var center = DeployableShieldGeometry.strokes(.033, 2);
        var opening = DeployableShieldGeometry.strokes(.30, 2);
        var spread = DeployableShieldGeometry.strokes(.72, 2);
        var held = DeployableShieldGeometry.strokes(1, 2);

        assertEquals(1, center.size());
        assertEquals(0, halfWidth(center), 1e-10);
        assertTrue(halfWidth(opening) > .3 * halfWidth(held));
        assertTrue(halfWidth(opening) < halfWidth(held));
        assertEquals(halfWidth(held), halfWidth(spread), 1e-10);
        assertTrue(opening.size() < held.size());
        assertNotEquals(spread, held);
        assertEquals(held, DeployableShieldGeometry.strokes(1, 2));
    }

    @Test
    void heldShieldHasAConnectedBroadSixSidedRimAndADownwardTip() {
        var held = DeployableShieldGeometry.strokes(1, 2);
        var outline = held.subList(0, 6);
        for (int i = 0; i < outline.size(); i++) {
            assertEquals(outline.get(i).b(), outline.get((i + 1) % outline.size()).a());
        }
        var tip = outline.stream().map(PatternGeometry.Stroke::a)
                .max(Comparator.comparingDouble(point -> point.y)).orElseThrow();
        assertEquals(0, tip.x, 1e-10);
        assertTrue(tip.y > 0);
        assertTrue(outline.stream().map(PatternGeometry.Stroke::a)
                .anyMatch(point -> point.y < 0 && Math.abs(point.x) > 1.9));
    }

    @Test
    void strokesStayWithinOnePlaneAndTheSizeAndRenderingBudgets() {
        for (double size : new double[]{.1, 2, 32}) {
            for (double openness : new double[]{.08, .25, .45, .85, 1}) {
                var strokes = DeployableShieldGeometry.strokes(openness, size);
                assertFalse(strokes.isEmpty());
                assertTrue(strokes.size() <= 32);
                double plane = strokes.getFirst().a().z;
                assertTrue(plane > 0 && plane < .1);
                for (var stroke : strokes) {
                    assertEquals(plane, stroke.a().z);
                    assertEquals(plane, stroke.b().z);
                    assertTrue(Double.isFinite(stroke.a().lengthSqr()) && Double.isFinite(stroke.b().lengthSqr()));
                    assertTrue(stroke.a().distanceToSqr(stroke.b()) > 0);
                    assertTrue(stroke.width() > 0 && stroke.alpha() > 0 && stroke.alpha() <= 1);
                }
                assertTrue(halfWidth(strokes) <= size);
            }
        }
        assertTrue(DeployableShieldGeometry.strokes(1, .09).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1, 33).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1, Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(Double.NaN, 2).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(-.1, 2).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1.1, 2).isEmpty());
    }

    private static double halfWidth(List<PatternGeometry.Stroke> strokes) {
        return strokes.stream().flatMap(stroke -> Stream.of(stroke.a(), stroke.b()))
                .mapToDouble(point -> Math.abs(point.x)).max().orElseThrow();
    }
}
