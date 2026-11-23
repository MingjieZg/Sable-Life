package dev.sablelife.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatternGeometryTest {
    private static final int SHIELD_FRACTURE = 2;

    @Test
    void shieldOpensWithAClosedOutlineThenGrowsCracks() {
        var outline = PatternGeometry.strokes(SHIELD_FRACTURE, 0, 100, 1, 2);
        assertFalse(outline.isEmpty());
        for (int i = 0; i < outline.size(); i++) {
            assertEquals(outline.get(i).b(), outline.get((i + 1) % outline.size()).a());
        }

        var earlyCracks = PatternGeometry.strokes(SHIELD_FRACTURE, 6, 100, 1, 2);
        var lateCracks = PatternGeometry.strokes(SHIELD_FRACTURE, 19, 100, 1, 2);
        assertTrue(earlyCracks.size() > outline.size());
        assertTrue(lateCracks.size() > earlyCracks.size());
        assertEquals(earlyCracks.getFirst().a(), lateCracks.getFirst().a());
        assertEquals(earlyCracks.get(outline.size()).a(), lateCracks.get(outline.size()).a());
        assertTrue(earlyCracks.get(outline.size()).b().distanceTo(earlyCracks.get(outline.size()).a())
                < lateCracks.get(outline.size()).b().distanceTo(lateCracks.get(outline.size()).a()));
    }

    @Test
    void shieldShardsMoveAndTumbleAtDifferentSpeedsWithoutStretching() {
        var slow = PatternGeometry.strokes(SHIELD_FRACTURE, 50, 100, .1, 2);
        var fast = PatternGeometry.strokes(SHIELD_FRACTURE, 50, 100, 8, 2);
        var later = PatternGeometry.strokes(SHIELD_FRACTURE, 80, 100, 8, 2);
        assertTrue(slow.size() >= 6);
        assertEquals(0, slow.size() % 3);
        assertEquals(slow.size(), fast.size());
        assertEquals(fast.size(), later.size());
        assertNotEquals(fast.getFirst().a(), slow.getFirst().a());
        for (int i = 0; i < slow.size(); i += 3) {
            for (int edge = 0; edge < 3; edge++) {
                var before = fast.get(i + edge);
                var after = later.get(i + edge);
                assertEquals(before.b(), fast.get(i + (edge + 1) % 3).a());
                assertEquals(before.a().distanceTo(before.b()), after.a().distanceTo(after.b()), 1e-5);
            }
        }
        var firstDrift = later.getFirst().a().subtract(fast.getFirst().a());
        var secondDrift = later.get(3).a().subtract(fast.get(3).a());
        assertTrue(firstDrift.distanceTo(secondDrift) > .01);
    }

    @Test
    void shieldProducesBoundedGeometryAtSettingLimitsAndExpires() {
        for (double size : new double[]{.1, 32}) {
            for (double speed : new double[]{.1, 8}) {
                for (int age : new int[]{0, 10, 16, 36, 55, 85, 99}) {
                    var lines = PatternGeometry.strokes(SHIELD_FRACTURE, age, 100, speed, size);
                    assertFalse(lines.isEmpty());
                    assertTrue(lines.size() <= 84);
                    for (var stroke : lines) {
                        assertTrue(stroke.a().length() < 3 * size && stroke.b().length() < 3 * size);
                    }
                }
                assertFalse(PatternGeometry.strokes(SHIELD_FRACTURE, 0, 1, speed, size).isEmpty());
                assertTrue(PatternGeometry.strokes(SHIELD_FRACTURE, 100, 100, speed, size).isEmpty());
            }
        }
    }
}
