package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeployableShieldGeometryTest {
    @Test
    void largerShieldAddsClosedCellsOnTheSameLattice() {
        var small = DeployableShieldGeometry.strokes(1, 2);
        var large = DeployableShieldGeometry.strokes(1, 3);
        assertEquals(13, boundedFaces(small));
        assertEquals(31, boundedFaces(large));
        assertTrue(edgePositions(large).containsAll(edgePositions(small)));
        for (var line : large) {
            assertEquals(.5, line.a().distanceTo(line.b()), 1e-9);
        }
    }

    @Test
    void openingAndClosingRevealTheSameEdgesFromTheCenter() {
        var mesh = new DeployableShieldGeometry(2);
        assertTrue(mesh.strokes(0).isEmpty());
        var first = mesh.strokes(.20);
        var middle = mesh.strokes(.64);
        var held = mesh.strokes(1);
        assertEquals(6, first.size());
        assertEquals(1, boundedFaces(first));
        assertTrue(first.stream().allMatch(stroke -> stroke.a().length() < .51 && stroke.b().length() < .51));
        assertTrue(first.size() < middle.size() && middle.size() < held.size());
        assertTrue(edgePositions(middle).containsAll(edgePositions(first)));
        assertTrue(edgePositions(held).containsAll(edgePositions(middle)));
        assertEquals(middle, mesh.strokes(.64));
        assertSame(held, mesh.strokes(1));
    }

    @Test
    void allSettingsStayPlanarWithinRadiusAndSharedFrameBudget() {
        int previousCount = 0;
        for (double size : new double[]{.1, .5, 1.5, 2, 2.5, 3, 6, 12, 32}) {
            var held = DeployableShieldGeometry.strokes(1, size);
            assertFalse(held.isEmpty());
            assertTrue(held.size() >= previousCount);
            previousCount = held.size();
            assertTrue(held.size() * PatternVisual.MAX_EFFECTS <= 8192);
            assertEquals(held.size(), edgePositions(held).size());
            for (var line : held) {
                double plane = held.getFirst().a().z;
                assertTrue(plane > 0 && plane < .1);
                assertEquals(plane, line.a().z);
                assertEquals(plane, line.b().z);
                assertTrue(Math.hypot(line.a().x, line.a().y) <= size + 1e-9);
                assertTrue(Math.hypot(line.b().x, line.b().y) <= size + 1e-9);
                assertTrue(line.a().distanceToSqr(line.b()) > 0);
                assertTrue(line.width() > 0 && line.alpha() > 0 && line.alpha() <= 1);
            }
        }
        assertEquals(73, boundedFaces(DeployableShieldGeometry.strokes(1, 32)));
        assertTrue(DeployableShieldGeometry.strokes(1, .09).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1, 33).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1, Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(Double.NaN, 2).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(-.1, 2).isEmpty());
        assertTrue(DeployableShieldGeometry.strokes(1.1, 2).isEmpty());
    }

    private static Set<Set<Vec3>> edgePositions(List<PatternGeometry.Stroke> strokes) {
        var edges = new HashSet<Set<Vec3>>();
        for (var stroke : strokes) edges.add(Set.of(stroke.a(), stroke.b()));
        return edges;
    }

    private static int boundedFaces(List<PatternGeometry.Stroke> strokes) {
        var vertices = new HashSet<Vec3>();
        for (var stroke : strokes) {
            vertices.add(stroke.a());
            vertices.add(stroke.b());
        }
        // Euler's formula for this connected planar grid, excluding the outside face.
        return strokes.size() - vertices.size() + 1;
    }
}
