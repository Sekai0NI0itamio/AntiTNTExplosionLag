package com.antitntexplosionlag;

import com.antitntexplosionlag.config.AntiTntConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiTntConfigTest {
    @Test
    void defaultsPreserveVanilla() {
        AntiTntConfig.applyRaw(true, true, true, 4, 32.0, false, 2048, false,
                true, 7.0, 3, 48.0f, 2048);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertTrue(cfg.masterEnabled);
        assertTrue(cfg.dedupEnabled);
        assertTrue(cfg.effectThrottleEnabled);
        assertFalse(cfg.stagedProcessingEnabled);
        assertEquals(4, cfg.maxEffectsPerTick);
        assertEquals(32.0, cfg.throttleRadius);
        assertEquals(2048, cfg.stagedBlocksPerTick);
        assertTrue(cfg.clusterDetonationEnabled);
        assertEquals(7.0, cfg.clusterRadius);
        assertEquals(3, cfg.clusterMinSize);
        assertEquals(48.0f, cfg.clusterMaxPower);
        assertEquals(2048, cfg.clusterMaxScan);
    }

    @Test
    void invalidValuesAreClamped() {
        AntiTntConfig.applyRaw(true, true, true, -5, -10.0, true, 1, true,
                true, -5.0, -2, -50.0f, 1);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertEquals(1, cfg.maxEffectsPerTick);
        assertEquals(4.0, cfg.throttleRadius);
        assertEquals(64, cfg.stagedBlocksPerTick);
        assertEquals(2.0, cfg.clusterRadius);
        assertEquals(2, cfg.clusterMinSize);
        assertEquals(8.0f, cfg.clusterMaxPower);
        assertEquals(64, cfg.clusterMaxScan);

        AntiTntConfig.applyRaw(true, true, true, 9999, 9999.0, true, 999999, true,
                true, 999.0, 999, 9999.0f, 99999);
        assertEquals(64, cfg.maxEffectsPerTick);
        assertEquals(256.0, cfg.throttleRadius);
        assertEquals(65536, cfg.stagedBlocksPerTick);
        assertEquals(16.0, cfg.clusterRadius);
        assertEquals(64, cfg.clusterMinSize);
        assertEquals(128.0f, cfg.clusterMaxPower);
        assertEquals(8192, cfg.clusterMaxScan);

        AntiTntConfig.applyRaw(true, true, true, 4, Double.NaN, false, 2048, false,
                true, 7.0, 3, Float.NaN, 2048);
        assertEquals(4.0, cfg.throttleRadius);
        assertEquals(8.0f, cfg.clusterMaxPower);
    }

    @Test
    void killSwitchAndStagedAreHonored() {
        AntiTntConfig.applyRaw(false, false, false, 4, 32.0, true, 512, true,
                false, 7.0, 3, 48.0f, 2048);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertFalse(cfg.masterEnabled);
        assertFalse(cfg.dedupEnabled);
        assertFalse(cfg.effectThrottleEnabled);
        assertTrue(cfg.stagedProcessingEnabled);
        assertEquals(512, cfg.stagedBlocksPerTick);
        assertFalse(cfg.clusterDetonationEnabled);
        // Restore safe defaults for other tests (shared singleton).
        AntiTntConfig.applyRaw(true, true, true, 4, 32.0, false, 2048, false,
                true, 7.0, 3, 48.0f, 2048);
    }
}
