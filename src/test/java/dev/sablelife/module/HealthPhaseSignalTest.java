package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthPhaseSignalTest {
    @Test void EnteringStartsExactlyOnePulseAndFurtherHitsDoNotRestartIt() {
        var signal = new HealthPhaseSignal();
        assertFalse(signal.tick(false, true, 2));
        assertTrue(signal.tick(true, true, 2));
        assertTrue(signal.tick(true, true, 2));
        for (int i = 0; i < 20; i++) assertFalse(signal.tick(true, true, 2));
        assertEquals(0, signal.remaining());
    }
    @Test void ExitCancelsPulseAndReentryStartsFreshPulse() {
        var signal = new HealthPhaseSignal();
        assertTrue(signal.tick(true, true, 20));
        assertFalse(signal.tick(false, true, 20));
        assertEquals(0, signal.remaining());
        assertTrue(signal.tick(true, true, 20));
        assertEquals(19, signal.remaining());
    }
    @Test void HoldRemainsPoweredAndDoesNotUsePulseDuration() {
        var signal = new HealthPhaseSignal();
        for (int i = 0; i < 50; i++) assertTrue(signal.tick(true, false, 1));
        assertEquals(0, signal.remaining());
        assertFalse(signal.tick(false, false, 1));
    }
    @Test void MinimumPulseEmitsOnceAndRecreatedRuntimeCanActivateAgain() {
        var signal = new HealthPhaseSignal();
        assertTrue(signal.tick(true, true, 0));
        assertFalse(signal.tick(true, true, 0));
        assertTrue(new HealthPhaseSignal().tick(true, true, 1));
    }
}
