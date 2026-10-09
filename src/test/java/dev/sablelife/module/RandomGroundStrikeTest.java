package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RandomGroundStrikeTest {
    @Test void SamplesAnnulusUniformlyByAreaAndPreservesOriginHeight() {
        for (int i = 0; i <= 100; i++) for (int j = 0; j < 32; j++) {
            double u = i / 100.;
            var p = GroundStrikeScatter.sample(4, 12, u, j / 32.);
            double squared = p.x() * p.x() + p.z() * p.z();
            assertEquals(16 + u * (144 - 16), squared, 1e-8);
        }
    }
    @Test void CoincidentBoundsGiveRingWithoutNaNAndZeroRadiusGivesCenter() {
        var ring = GroundStrikeScatter.sample(5, 5, .7, .4);
        assertEquals(5, Math.hypot(ring.x(), ring.z()), 1e-8);
        var center = GroundStrikeScatter.sample(0, 0, .7, .4);
        assertEquals(0, Math.hypot(center.x(), center.z()));
    }
}
