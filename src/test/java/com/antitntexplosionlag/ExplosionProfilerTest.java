package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.ExplosionProfiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplosionProfilerTest {
    @Test
    void emptyReturnsZero() {
        ExplosionProfiler p = new ExplosionProfiler(8);
        assertEquals(0, p.count());
        assertEquals(0.0, p.averageMs());
        assertEquals(0.0, p.percentileMs(95.0));
        assertEquals(0.0, p.maxMs());
    }

    @Test
    void averagePercentileAndMax() {
        ExplosionProfiler p = new ExplosionProfiler(8);
        for (long ms : new long[]{10, 20, 30, 40, 50}) {
            p.record(ms * 1_000_000L);
        }
        assertEquals(5, p.count());
        assertEquals(30.0, p.averageMs(), 1e-9);
        assertEquals(30.0, p.percentileMs(50.0), 1e-9);
        assertEquals(50.0, p.percentileMs(95.0), 1e-9);
        assertEquals(50.0, p.maxMs(), 1e-9);
        assertEquals(10.0, p.percentileMs(0.0), 1e-9);
    }

    @Test
    void ringKeepsNewestSamples() {
        ExplosionProfiler p = new ExplosionProfiler(4);
        for (long ms : new long[]{10, 20, 30, 40, 50}) {
            p.record(ms * 1_000_000L);
        }
        assertEquals(4, p.count());
        assertEquals(35.0, p.averageMs(), 1e-9);
        assertEquals(50.0, p.maxMs(), 1e-9);
    }

    @Test
    void resetAndBadCapacity() {
        ExplosionProfiler p = new ExplosionProfiler(4);
        p.record(1_000_000L);
        p.reset();
        assertEquals(0, p.count());
        assertEquals(0.0, p.averageMs());
        try {
            new ExplosionProfiler(0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
