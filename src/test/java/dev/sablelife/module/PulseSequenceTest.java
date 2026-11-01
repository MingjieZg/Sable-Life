package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PulseSequenceTest {
    private final PulseSequence.Step output = new PulseSequence.Step(false, 1, 15, 2);
    private final PulseSequence.Step wait = new PulseSequence.Step(true, 1, 15, 1);
    @Test void shortPulseRunsCompleteSequenceWithExactDurations() {
        var clock = new PulseSequence(); var steps = List.of(output, wait);
        assertEquals(output, clock.tick(steps, true, false, false, false));
        assertEquals(output, clock.tick(steps, false, false, false, false));
        assertEquals(wait, clock.tick(steps, false, false, false, false));
        assertNull(clock.tick(steps, false, false, false, false)); assertFalse(clock.running());
    }
    @Test void heldInputDoesNotReplayOneShotAndNewEdgeDoes() {
        var clock = new PulseSequence(); var steps = List.of(wait);
        assertEquals(wait, clock.tick(steps, true, false, false, false));
        for (int i = 0; i < 5; i++) assertNull(clock.tick(steps, true, false, false, false));
        assertNull(clock.tick(steps, false, false, false, false));
        assertEquals(wait, clock.tick(steps, true, false, false, false));
    }
    @Test void resetTakesPriorityAndHeldStartRequiresFreshEdge() {
        var clock = new PulseSequence(); var steps = List.of(output);
        clock.tick(steps, true, false, false, false);
        assertNull(clock.tick(steps, true, true, false, false)); assertFalse(clock.running());
        assertNull(clock.tick(steps, true, false, false, false));
        clock.tick(steps, false, false, false, false);
        assertEquals(output, clock.tick(steps, true, false, false, false)); assertEquals(1, clock.remaining());
    }
    @Test void repeatCompletesCurrentCycleAfterShortInputThenStops() {
        var clock = new PulseSequence(); var steps = List.of(output);
        for (int i = 0; i < 5; i++) assertEquals(output, clock.tick(steps, true, false, true, false));
        assertEquals(output, clock.tick(steps, false, false, true, false));
        assertNull(clock.tick(steps, false, false, true, false));
    }
    @Test void stopOnLowCancelsAndStartsFromFirstStepOnNextEdge() {
        var clock = new PulseSequence(); var steps = List.of(output, wait);
        clock.tick(steps, true, false, false, true);
        assertNull(clock.tick(steps, false, false, false, true));
        assertEquals(output, clock.tick(steps, true, false, false, true)); assertEquals(0, clock.index());
    }
    @Test void emptyAndZeroDurationCannotCreateAnInfiniteLoop() {
        var clock = new PulseSequence(); assertNull(clock.tick(List.of(), true, false, true, false));
        var zero = new PulseSequence.Step(false, 1, 15, 0); assertEquals(1, zero.ticks());
        for (int i = 0; i < 100; i++) assertEquals(zero, clock.tick(List.of(zero), true, false, true, false));
    }
}
