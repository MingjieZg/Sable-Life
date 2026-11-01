package dev.sablelife.heart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeartHealthTest {
    @Test void dormantCoreCannotTakeDamageOrEmitHealthSignal() {
        var core = new HeartHealth(100, 60);
        assertFalse(core.hurt(50));
        assertEquals(100, core.current());
        assertEquals(0, core.comparatorSignal());
    }

    @Test void lethalDamageImmediatelyDisablesLivingStateAndFinishesDeathOnlyOnce() {
        var core = new HeartHealth(100, 2);
        assertTrue(core.activate());
        assertTrue(core.hurt(150));
        assertEquals(HeartState.DYING, core.state());
        assertEquals(0, core.comparatorSignal());
        assertFalse(core.hurt(1));
        assertFalse(core.activate());
        assertFalse(core.tick());
        assertTrue(core.tick());
        assertFalse(core.tick());
        assertEquals(HeartState.DEAD, core.state());
        assertFalse(core.activate());
    }

    @Test void reloadPreservesDeathCountdownAndDoesNotRepeatCompletion() {
        var core = HeartHealth.restore(100, 0, 60, HeartState.DYING, 1);
        assertTrue(core.tick());
        var reloaded = HeartHealth.restore(core.maximum(), core.current(), core.deathDelay(), core.state(), core.remainingDeathTicks());
        assertFalse(reloaded.tick());
        assertFalse(reloaded.activate());
    }

    @Test void invalidDamageCannotHealOrCorruptHealth() {
        var core = new HeartHealth(100, 60);
        core.activate();
        core.hurt(40);
        for (float amount : new float[]{-20, 0, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(core.hurt(amount));
            assertEquals(60, core.current());
        }
    }

    @Test void comparatorRemainsPositiveUntilDeath() {
        var core = new HeartHealth(100, 0);
        core.activate();
        assertEquals(15, core.comparatorSignal());
        core.hurt(50);
        assertEquals(8, core.comparatorSignal());
        core.hurt(49.99F);
        assertEquals(1, core.comparatorSignal());
        core.hurt(1);
        assertEquals(0, core.comparatorSignal());
        assertTrue(core.tick());
    }

    @Test void invalidSavedStateIsRejectedInsteadOfResurrected() {
        assertThrows(IllegalArgumentException.class, () -> new HeartHealth(Float.NaN, 60));
        assertThrows(IllegalArgumentException.class, () -> HeartHealth.restore(100, 0, 60, HeartState.ALIVE, 0));
        assertThrows(IllegalArgumentException.class, () -> HeartHealth.restore(100, 1, 60, HeartState.DEAD, 0));
        assertThrows(IllegalArgumentException.class, () -> HeartHealth.restore(100, 0, 60, HeartState.DYING, 61));
    }
}
