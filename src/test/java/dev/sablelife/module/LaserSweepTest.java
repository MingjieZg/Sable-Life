package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaserSweepTest {
    @Test void AutoKeepsLegacyTimingAndExplicitSpeedIgnoresBurstLength() {
        assertEquals(0, LaserSweep.fraction(90, 0, -5, 50));
        assertEquals(.5, LaserSweep.fraction(90, 0, 24.5, 50), 1e-9);
        assertEquals(1, LaserSweep.fraction(90, 0, 49, 50));
        assertEquals(.5, LaserSweep.fraction(90, 90, 10, 50), 1e-9);
        assertEquals(.5, LaserSweep.fraction(-90, 90, 10, 200), 1e-9);
        assertEquals(.25, LaserSweep.fraction(180, 90, 10, 50), 1e-9);
    }

    @Test void ACompletedSweepHoldsAndSlowSweepMayFinishPartway() {
        assertEquals(1, LaserSweep.fraction(90, 90, 21, 100));
        assertEquals(1, LaserSweep.fraction(-90, 720, 30, 100));
        assertEquals(.1, LaserSweep.fraction(90, 9, 20, 21), 1e-9);
        assertTrue(Double.isFinite(LaserSweep.fraction(0, 90, 1, 1)));
    }

    @Test void ParticleSpacingRespondsToDistanceAndStaysBounded() {
        assertEquals(1, LaserSweep.particleSamples(0, 4));
        assertEquals(9, LaserSweep.particleSamples(32, 4));
        assertEquals(33, LaserSweep.particleSamples(32, 1));
        assertEquals(2, LaserSweep.particleSamples(.1, 32));
        assertEquals(64, LaserSweep.particleSamples(256, .25));
    }
}
