package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.ChainMonitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainMonitorTest {
    @Test
    void countsPerTickAndRollsOver() {
        ChainMonitor m = new ChainMonitor();
        m.onTntTick(100L);
        m.onTntTick(100L);
        m.onExplosion(100L);
        assertEquals(2, m.tntThisTick(100L));
        assertEquals(1, m.explosionsThisTick(100L));
        assertEquals(0, m.tntThisTick(101L));

        m.onTntTick(101L);
        assertEquals(1, m.tntThisTick(101L));
        assertEquals(0, m.explosionsThisTick(101L));
        assertEquals(2, m.maxTntPerTick());
    }

    @Test
    void stormSignal() {
        ChainMonitor m = new ChainMonitor();
        assertFalse(m.isStorm(50L, 3));
        m.onExplosion(50L);
        m.onExplosion(50L);
        assertFalse(m.isStorm(50L, 3));
        m.onExplosion(50L);
        assertTrue(m.isStorm(50L, 3));
        assertFalse(m.isStorm(51L, 1));
    }

    @Test
    void totalsAndReset() {
        ChainMonitor m = new ChainMonitor();
        m.onTntTick(1L);
        m.onTntTick(2L);
        m.onExplosion(2L);
        m.onMerged(25);
        assertEquals(2, m.totalTntTicks());
        assertEquals(1, m.totalExplosions());
        assertEquals(1, m.maxExplosionsPerTick());
        assertEquals(1, m.mergedClusters());
        assertEquals(25, m.mergedTnt());
        m.onAbsorbed(7);
        m.onAbsorbed(3);
        assertEquals(2, m.absorbedBlasts());
        assertEquals(10, m.absorbedTnt());
        m.reset();
        assertEquals(0, m.totalTntTicks());
        assertEquals(0, m.totalExplosions());
        assertEquals(0, m.maxTntPerTick());
        assertEquals(0, m.tntThisTick(2L));
        assertEquals(0, m.mergedClusters());
        assertEquals(0, m.mergedTnt());
        assertEquals(0, m.absorbedBlasts());
        assertEquals(0, m.absorbedTnt());
    }
}
