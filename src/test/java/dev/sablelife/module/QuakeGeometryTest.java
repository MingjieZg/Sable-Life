package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuakeGeometryTest {
    @Test void FastFrontSweepsIntermediateTargetsButStaysInsideTheLine() {
        assertTrue(QuakeGeometry.crossed(false, 3, .9, 3.1, 1, 5, 2, 10));
        assertFalse(QuakeGeometry.crossed(false, 3, 1.1, 3.2, 1, 5, 2, 10));
        assertFalse(QuakeGeometry.crossed(false, -1, 0, 1, 0, 2, 2, 10));
        assertFalse(QuakeGeometry.crossed(false, 11, 0, 11, 8, 12, 2, 10));
    }
    @Test void RingWorksInEveryDirectionButNotBehindThePassedFront() {
        assertTrue(QuakeGeometry.crossed(true, -3, 4, 5, 4, 6, 1, 10));
        assertFalse(QuakeGeometry.crossed(true, 1, 0, 1, 4, 6, 1, 10));
    }
    @Test void LaunchHeightMatchesVanillaDiscreteGravityAcrossTheSettingRange() {
        assertEquals(0, QuakeGeometry.launchSpeed(0));
        for (double height : new double[]{.1, 1, 3, 10, 20}) {
            double speed = QuakeGeometry.launchSpeed(height), y = 0;
            while (speed > 0) { y += speed; speed = (speed - .08) * .98; }
            assertEquals(height, y, 1e-6);
        }
    }
}
