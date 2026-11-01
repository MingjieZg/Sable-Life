package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PullTest {
    @Test void WarningAllowsDodgingBeforeCaptureAndPullHasExactDuration() {
        var clock = new PullCycle();
        assertEquals(PullCycle.Phase.WAITING, clock.tick(0, 3, 2, 4, false));
        assertEquals(PullCycle.Phase.WARNING, clock.tick(1, 3, 2, 4, true));
        assertEquals(PullCycle.Phase.WARNING, clock.tick(3, 3, 2, 4, false));
        assertEquals(PullCycle.Phase.PULLING, clock.tick(4, 3, 2, 4, false));
        assertEquals(PullCycle.Phase.PULLING, clock.tick(5, 3, 2, 4, false));
        assertEquals(PullCycle.Phase.WAITING, clock.tick(6, 3, 2, 4, true));
        assertFalse(clock.ready(9)); assertTrue(clock.ready(10));
        assertEquals(PullCycle.Phase.WARNING, clock.tick(10, 3, 2, 4, true));
    }
    @Test void CancelledRuntimeRequiresFullNewWarning() {
        var clock = new PullCycle(); clock.tick(0, 30, 40, 60, true);
        // Removing runtime on signal loss never carries an old warning into the next activation.
        clock = new PullCycle();
        assertEquals(PullCycle.Phase.WARNING, clock.tick(100, 30, 40, 60, true));
        assertEquals(130, clock.fireAt());
    }
    @Test void AccelerationPreservesOutwardMotionAndCapsOnlyInwardSpeed() {
        assertEquals(.12, PullMotion.acceleration(10, -.4, .12, .5, .8), 1e-9);
        assertEquals(.05, PullMotion.acceleration(10, .45, .12, .5, .8), 1e-9);
        assertEquals(-.3, PullMotion.acceleration(10, .8, .12, .5, .8), 1e-9);
    }
    @Test void StoppingDistanceBrakesInwardMotionWithoutInstantlyReversingOutwardMomentum() {
        assertEquals(-.3, PullMotion.acceleration(.8, .3, .12, .5, .8), 1e-9);
        assertEquals(.12, PullMotion.acceleration(.8, -.3, .12, .5, .8), 1e-9);
        assertEquals(-.2, PullMotion.acceleration(.9, .3, .12, .5, .8), 1e-9);
        assertEquals(0, PullMotion.acceleration(Double.NaN, 0, .12, .5, .8));
    }
}
