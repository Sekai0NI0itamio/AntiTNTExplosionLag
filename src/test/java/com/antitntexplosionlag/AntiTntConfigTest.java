package com.antitntexplosionlag;

import com.antitntexplosionlag.config.AntiTntConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiTntConfigTest {
    @Test
    void defaultsPreserveVanilla() {
        AntiTntConfig.applyRaw(true, true, true, 4, 32.0, false, 2048, false);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertTrue(cfg.masterEnabled);
        assertTrue(cfg.dedupEnabled);
        assertTrue(cfg.effectThrottleEnabled);
        assertFalse(cfg.stagedProcessingEnabled);
        assertEquals(4, cfg.maxEffectsPerTick);
        assertEquals(32.0, cfg.throttleRadius);
        assertEquals(2048, cfg.stagedBlocksPerTick);
    }

    @Test
    void invalidValuesAreClamped() {
        AntiTntConfig.applyRaw(true, true, true, -5, -10.0, true, 1, true);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertEquals(1, cfg.maxEffectsPerTick);
        assertEquals(4.0, cfg.throttleRadius);
        assertEquals(64, cfg.stagedBlocksPerTick);

        AntiTntConfig.applyRaw(true, true, true, 9999, 9999.0, true, 999999, true);
        assertEquals(64, cfg.maxEffectsPerTick);
        assertEquals(256.0, cfg.throttleRadius);
        assertEquals(65536, cfg.stagedBlocksPerTick);

        AntiTntConfig.applyRaw(true, true, true, 4, Double.NaN, false, 2048, false);
        assertEquals(4.0, cfg.throttleRadius);
    }

    @Test
    void killSwitchAndStagedAreHonored() {
        AntiTntConfig.applyRaw(false, false, false, 4, 32.0, true, 512, true);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertFalse(cfg.masterEnabled);
        assertFalse(cfg.dedupEnabled);
        assertFalse(cfg.effectThrottleEnabled);
        assertTrue(cfg.stagedProcessingEnabled);
        assertEquals(512, cfg.stagedBlocksPerTick);
        // Restore safe defaults for other tests (shared singleton).
        AntiTntConfig.applyRaw(true, true, true, 4, 32.0, false, 2048, false);
    }
}
