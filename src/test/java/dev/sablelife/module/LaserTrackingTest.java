package dev.sablelife.module;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LaserTrackingTest {
    @Test void FullSpeedLocksButSlowSpeedCapsAngularTurn() {
        assertEquals(1, LaserTracking.fraction(-1, 1));
        assertEquals(1. / 90, LaserTracking.fraction(0, .1), 1e-9);
        assertEquals(5. / 90, LaserTracking.fraction(0, .5), 1e-9);
        assertEquals(1, LaserTracking.fraction(Math.cos(Math.toRadians(.5)), .1));
    }
    @Test void OppositeAlignedAndRoundedDirectionsRemainFinite() {
        assertEquals(1. / 180, LaserTracking.fraction(-1, .1), 1e-9);
        assertEquals(1, LaserTracking.fraction(1.0000001, .1));
        assertTrue(Double.isFinite(LaserTracking.fraction(-1.0000001, .1)));
    }
}
