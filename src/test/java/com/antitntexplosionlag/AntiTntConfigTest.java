package com.antitntexplosionlag;

import com.antitntexplosionlag.config.AntiTntConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiTntConfigTest {
    private static void defaults() {
        AntiTntConfig.applyRaw(true, true, true, 4, 32.0, false, 2048, false,
                true, 7.0, 3, 48.0f, 64.0, 20000, 10, 8.0, 40, 10, 16384, 8192);
    }

    @Test
    void defaultsPreserveVanilla() {
        defaults();
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
        assertEquals(64.0, cfg.mergeFieldRadius);
        assertEquals(20000, cfg.exactClusterCap);
        assertEquals(10, cfg.mergeWindowTicks);
        assertEquals(8.0, cfg.mergeWindowMargin);
        assertEquals(40, cfg.stormEffectCap);
        assertEquals(10, cfg.stormGapTicks);
        assertEquals(16384, cfg.autoStageThreshold);
        assertEquals(8192, cfg.autoStageBudget);
    }

    @Test
    void invalidValuesAreClamped() {
        AntiTntConfig.applyRaw(true, true, true, -5, -10.0, true, 1, true,
                true, -5.0, -2, -50.0f, -1.0, 1, -5, -1.0, -3, 0, -9, 1);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertEquals(1, cfg.maxEffectsPerTick);
        assertEquals(4.0, cfg.throttleRadius);
        assertEquals(64, cfg.stagedBlocksPerTick);
        assertEquals(2.0, cfg.clusterRadius);
        assertEquals(2, cfg.clusterMinSize);
        assertEquals(8.0f, cfg.clusterMaxPower);
        assertEquals(8.0, cfg.mergeFieldRadius);
        assertEquals(1024, cfg.exactClusterCap);
        assertEquals(0, cfg.mergeWindowTicks);
        assertEquals(0.0, cfg.mergeWindowMargin);
        assertEquals(1, cfg.stormEffectCap);
        assertEquals(2, cfg.stormGapTicks);
        assertEquals(0, cfg.autoStageThreshold);
        assertEquals(64, cfg.autoStageBudget);

        AntiTntConfig.applyRaw(true, true, true, 9999, 9999.0, true, 999999, true,
                true, 999.0, 999, 9999.0f, 9999.0, 999999, 999, 999.0, 9999, 9999, 9999999, 999999);
        assertEquals(64, cfg.maxEffectsPerTick);
        assertEquals(256.0, cfg.throttleRadius);
        assertEquals(65536, cfg.stagedBlocksPerTick);
        assertEquals(16.0, cfg.clusterRadius);
        assertEquals(64, cfg.clusterMinSize);
        assertEquals(128.0f, cfg.clusterMaxPower);
        assertEquals(128.0, cfg.mergeFieldRadius);
        assertEquals(65536, cfg.exactClusterCap);
        assertEquals(100, cfg.mergeWindowTicks);
        assertEquals(64.0, cfg.mergeWindowMargin);
        assertEquals(256, cfg.stormEffectCap);
        assertEquals(100, cfg.stormGapTicks);
        assertEquals(1048576, cfg.autoStageThreshold);
        assertEquals(65536, cfg.autoStageBudget);

        AntiTntConfig.applyRaw(true, true, true, 4, Double.NaN, false, 2048, false,
                true, 7.0, 3, Float.NaN, 64.0, 20000, 10, 8.0, 40, 10, 16384, 8192);
        assertEquals(4.0, cfg.throttleRadius);
        assertEquals(8.0f, cfg.clusterMaxPower);
    }

    @Test
    void killSwitchAndStagedAreHonored() {
        AntiTntConfig.applyRaw(false, false, false, 4, 32.0, true, 512, true,
                false, 7.0, 3, 48.0f, 64.0, 20000, 10, 8.0, 40, 10, 16384, 8192);
        AntiTntConfig cfg = AntiTntConfig.get();
        assertFalse(cfg.masterEnabled);
        assertFalse(cfg.dedupEnabled);
        assertFalse(cfg.effectThrottleEnabled);
        assertTrue(cfg.stagedProcessingEnabled);
        assertEquals(512, cfg.stagedBlocksPerTick);
        assertFalse(cfg.clusterDetonationEnabled);
        // Restore safe defaults for other tests (shared singleton).
        defaults();
    }
}
