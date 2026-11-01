package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HealthPhaseTest {
    @Test void exactBoundariesBelongToOnlyOneAdjacentStage() {
        assertTrue(HealthPhase.contains(100, 100, 70, 100));
        assertFalse(HealthPhase.contains(70, 100, 70, 100));
        assertTrue(HealthPhase.contains(70, 100, 40, 70));
        assertFalse(HealthPhase.contains(40, 100, 40, 70));
        assertTrue(HealthPhase.contains(40, 100, 0, 40));
        assertFalse(HealthPhase.contains(0, 100, 0, 40));
    }
    @Test void percentageUsesConfiguredMaximumAndSupportsSkippedStages() {
        assertTrue(HealthPhase.contains(200, 1000, 0, 40));
        assertFalse(HealthPhase.contains(200, 1000, 40, 70));
        assertFalse(HealthPhase.contains(200, 1000, 70, 100));
        assertTrue(HealthPhase.contains(1000, 1000, 70, 100));
    }
    @Test void InvalidRangesNeverEmit() {
        assertFalse(HealthPhase.contains(50, 100, 70, 40));
        assertFalse(HealthPhase.contains(50, 100, 50, 50));
        assertFalse(HealthPhase.contains(50, 0, 0, 100));
        assertFalse(HealthPhase.contains(Double.NaN, 100, 0, 100));
    }
    @Test void RemovingOneProducerPreservesOtherProducerAndStrongestWins() {
        var bus = new ProgramSignals();
        bus.set("phase", Map.of(0, 10, 1, 15));
        bus.set("sequence", Map.of(0, 12, 2, 8));
        assertEquals(Map.of(0, 12, 1, 15, 2, 8), bus.combined());
        bus.set("sequence", Map.of());
        assertEquals(Map.of(0, 10, 1, 15), bus.combined());
        bus.clear(); assertTrue(bus.combined().isEmpty());
    }
}
