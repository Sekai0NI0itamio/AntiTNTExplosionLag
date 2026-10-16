package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.EffectThrottler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectThrottlerTest {
    @Test
    void withinBudgetAllEmit() {
        EffectThrottler t = new EffectThrottler(4, 32.0);
        for (int i = 0; i < 4; i++) {
            assertTrue(t.admit(10L, 0.5, 64.0, 0.5));
        }
        assertEquals(0, t.suppressedTotal());
    }

    @Test
    void overflowIsSuppressedButCounted() {
        EffectThrottler t = new EffectThrottler(2, 32.0);
        assertTrue(t.admit(10L, 0.0, 64.0, 0.0));
        assertTrue(t.admit(10L, 1.0, 64.0, 1.0));
        assertFalse(t.admit(10L, 2.0, 64.0, 2.0));
        assertFalse(t.admit(10L, 3.0, 64.0, 3.0));
        assertEquals(2, t.suppressedTotal());
    }

    @Test
    void distantCellsHaveIndependentBudgets() {
        EffectThrottler t = new EffectThrottler(1, 32.0);
        assertTrue(t.admit(10L, 0.0, 64.0, 0.0));
        assertTrue(t.admit(10L, 500.0, 64.0, 500.0));
        assertFalse(t.admit(10L, 1.0, 64.0, 1.0));
    }

    @Test
    void tickRolloverResetsBudgets() {
        EffectThrottler t = new EffectThrottler(1, 32.0);
        assertTrue(t.admit(10L, 0.0, 64.0, 0.0));
        assertFalse(t.admit(10L, 0.0, 64.0, 0.0));
        assertTrue(t.admit(11L, 0.0, 64.0, 0.0));
        assertEquals(1, t.suppressedTotal());
    }

    @Test
    void stormCapCompressesLongStorms() {
        EffectThrottler t = new EffectThrottler(64, 32.0, 3, 10);
        assertTrue(t.admit(100L, 0.0, 64.0, 0.0));
        assertTrue(t.admit(101L, 0.0, 64.0, 0.0));
        assertTrue(t.admit(102L, 0.0, 64.0, 0.0));
        // Storm budget exhausted: further ticks stay silent.
        assertFalse(t.admit(103L, 0.0, 64.0, 0.0));
        assertFalse(t.admit(104L, 0.0, 64.0, 0.0));
        assertEquals(3, t.stormEmitted());
        // A long enough quiet gap ends the storm: budget returns.
        assertTrue(t.admit(200L, 0.0, 64.0, 0.0));
        assertEquals(1, t.stormEmitted());
    }

    @Test
    void disabledStormCapNeverBlocks() {
        EffectThrottler t = new EffectThrottler(64, 32.0, 0, 10);
        for (long tick = 0; tick < 50; tick++) {
            assertTrue(t.admit(tick, 0.0, 64.0, 0.0));
        }
        assertEquals(50, t.stormEmitted());
    }

    @Test
    void resetClearsEverything() {
        EffectThrottler t = new EffectThrottler(1, 32.0);
        t.admit(10L, 0.0, 64.0, 0.0);
        t.admit(10L, 0.0, 64.0, 0.0);
        t.reset();
        assertEquals(0, t.suppressedTotal());
        assertTrue(t.admit(10L, 0.0, 64.0, 0.0));
    }

    @Test
    void constructorRejectsBadArgs() {
        try {
            new EffectThrottler(0, 32.0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            new EffectThrottler(2, 0.5);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            new EffectThrottler(2, 32.0, 40, 0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
