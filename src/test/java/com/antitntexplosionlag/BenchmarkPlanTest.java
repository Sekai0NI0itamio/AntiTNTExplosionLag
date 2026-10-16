package com.antitntexplosionlag;

import com.antitntexplosionlag.bench.BenchmarkPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkPlanTest {
    @Test
    void fiveRepresentativeScenarios() {
        List<BenchmarkPlan.Scenario> scenarios = BenchmarkPlan.scenarios();
        assertEquals(5, scenarios.size());
        assertEquals("small-isolated", scenarios.get(0).name());
        assertEquals("large-detonation", scenarios.get(1).name());
        assertEquals("chain-reaction", scenarios.get(2).name());
        assertEquals("overlapping-confined", scenarios.get(3).name());
        assertEquals("repeated-stress", scenarios.get(4).name());
        for (BenchmarkPlan.Scenario s : scenarios) {
            assertTrue(s.tntCount() > 0);
            assertTrue(s.ticks() > 0);
        }
    }

    @Test
    void syntheticWorkloadIsDeterministic() {
        List<Long> a = BenchmarkPlan.syntheticWorkload(2000, 0.35, 12345L);
        List<Long> b = BenchmarkPlan.syntheticWorkload(2000, 0.35, 12345L);
        assertEquals(a, b);
        // 2000 unique + 700 duplicates = 2700 entries.
        assertEquals(2700, a.size());
        long distinct = a.stream().distinct().count();
        assertEquals(2000, distinct);
    }

    @Test
    void duplicateRatioZeroMeansAllUnique() {
        List<Long> w = BenchmarkPlan.syntheticWorkload(500, 0.0, 7L);
        assertEquals(500, w.size());
        assertEquals(500, w.stream().distinct().count());
    }

    @Test
    void constructorRejectsBadArgs() {
        try {
            BenchmarkPlan.syntheticWorkload(-1, 0.5, 1L);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            BenchmarkPlan.syntheticWorkload(10, 1.5, 1L);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
