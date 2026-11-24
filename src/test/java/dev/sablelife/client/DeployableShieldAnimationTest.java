package dev.sablelife.client;

import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.PatternVisual;
import dev.sablelife.module.ShieldVisualPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.sablelife.module.ShieldVisualPacket.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class DeployableShieldAnimationTest {
    @Test
    void heartbeatsDoNotRestartOpeningOrLimitHoldToPreviewDuration() {
        var visual = visual(1);
        var hold = new ShieldVisualPacket(visual, HOLD);
        var shield = new DeployableShieldAnimation(hold, 0);
        assertEquals(.5, shield.openness(3));
        shield.update(hold, 3);
        assertEquals(.5, shield.openness(3));
        assertEquals(1, shield.openness(6));

        for (int tick = 10; tick <= 120; tick += 10) {
            assertFalse(shield.advance(tick));
            shield.update(hold, tick);
            assertEquals(1, shield.openness(tick));
        }
        assertFalse(shield.advance(155));
        assertTrue(shield.closing());
        assertEquals(.5, shield.openness(158));
        assertTrue(shield.advance(161));
    }

    @Test
    void releaseAndReactivationReverseFromTheCurrentWidth() {
        var visual = visual(30);
        var shield = new DeployableShieldAnimation(new ShieldVisualPacket(visual, HOLD), 0);
        shield.update(new ShieldVisualPacket(visual, CLOSE), 4);
        assertEquals(2.0 / 3, shield.openness(4), 1e-9);
        assertEquals(1.0 / 3, shield.openness(6), 1e-9);
        shield.update(new ShieldVisualPacket(visual, HOLD), 6);
        assertEquals(1.0 / 3, shield.openness(6), 1e-9);
        assertEquals(2.0 / 3, shield.openness(8), 1e-9);
        assertEquals(1, shield.openness(10));
    }

    @Test
    void previewReservesBothTransitionsAndLateViewerStartsOpen() {
        var shortPreview = new DeployableShieldAnimation(new ShieldVisualPacket(visual(1), PREVIEW), 0);
        assertFalse(shortPreview.advance(6));
        assertEquals(1, shortPreview.openness(6));
        assertTrue(shortPreview.advance(12));
        var preview = new DeployableShieldAnimation(new ShieldVisualPacket(visual(30), PREVIEW), 0);
        assertFalse(preview.advance(23));
        assertEquals(1, preview.openness(23));
        assertFalse(preview.advance(27));
        assertEquals(.5, preview.openness(27));
        assertTrue(preview.advance(30));
        var sync = new DeployableShieldAnimation(new ShieldVisualPacket(visual(30), SYNC), 30);
        assertEquals(1, sync.openness(30));
    }

    private static PatternVisual visual(int duration) {
        return new PatternVisual(ResourceLocation.withDefaultNamespace("overworld"), UUID.randomUUID(),
                new ModuleAnchor(UUID.randomUUID(), BlockPos.ZERO, Direction.SOUTH),
                Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, -1, 0), 0, 2, 1,
                duration, 0xFF25D5, ShieldVisualPacket.GEOMETRY_PATTERN, 0, false, 64);
    }
}
