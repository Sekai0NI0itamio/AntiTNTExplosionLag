package com.antitntexplosionlag.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server-side configuration. Every field is wired to real behavior (see
 * {@code docs/CONFIG.md}); defaults preserve vanilla gameplay outcomes.
 *
 * <p>Side note: this mod is a server-side performance mod. It works with the
 * mod installed on the server only; clients do not need it. No client install
 * is ever required for the default optimizations.
 */
public final class AntiTntConfig {
    private static final Logger LOG = Logger.getLogger("AntiTNTExplosionLag");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AntiTntConfig INSTANCE = new AntiTntConfig();

    public static AntiTntConfig get() {
        return INSTANCE;
    }

    /** Master kill switch. Off restores baseline behavior (mixins idle). */
    public volatile boolean masterEnabled = true;
    /**
     * Remove duplicate entries from the affected-block list after vanilla
     * collection. Destroying the same block twice has the same end state as
     * destroying it once, so dedup only shrinks the apply loop. Default on.
     */
    public volatile boolean dedupEnabled = true;
    /**
     * Throttle cosmetic explosion effects (particles/sounds) when many
     * explosions land in the same tick inside {@link #throttleRadius}.
     * Implemented through vanilla's own {@code affectWorld(particles)} flag:
     * throttled explosions still destroy blocks and damage entities, they
     * only skip the cosmetic burst. Block/entity sync packets are never
     * suppressed. Default on.
     */
    public volatile boolean effectThrottleEnabled = true;
    /** Max explosions per tick inside the throttle radius before cosmetic merging kicks in. */
    public volatile int maxEffectsPerTick = 4;
    /** Radius (blocks) inside which same-tick explosions share one cosmetic budget. */
    public volatile double throttleRadius = 32.0;
    /**
     * Optional staged application of huge affected-block lists across
     * several server ticks. Explicitly opt-in: it delays block destruction
     * and changes chain-reaction timing. Default off.
     */
    public volatile boolean stagedProcessingEnabled = false;
    /** Max affected blocks applied per tick when staged processing is on. */
    public volatile int stagedBlocksPerTick = 2048;
    /** Diagnostic logging + /antitnt stats counters. */
    public volatile boolean diagnosticsEnabled = false;
    /**
     * Merge connected primed TNT into ONE explosion instead of a sequential
     * chain. When a TNT detonates, all transitively connected primed TNT
     * inside {@link #clusterRadius} detonate together as a single blast with
     * combined power ({@code 4 * cbrt(n)}, capped). This is an intentional
     * gameplay trade-off (one big crater now vs N vanilla chained blasts),
     * enabled because sequential chains are the dominant lag source.
     * Disable to restore vanilla chaining.
     */
    public volatile boolean clusterDetonationEnabled = true;
    /** Connection distance (blocks) for cluster membership, transitive. */
    public volatile double clusterRadius = 7.0;
    /** Minimum cluster size to merge; smaller groups explode vanilla-style. */
    public volatile int clusterMinSize = 3;
    /** Hard cap on merged-blast power. */
    public volatile float clusterMaxPower = 48.0f;
    /** Upper bound on entities visited per cluster scan (perf guard). */
    public volatile int clusterMaxScan = 2048;

    private AntiTntConfig() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("antitntexplosionlag.json");
    }

    /** Loads from disk, clamping invalid values into their documented ranges. */
    public static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try {
            String json = Files.readString(path);
            AntiTntConfig loaded = GSON.fromJson(json, AntiTntConfig.class);
            if (loaded != null) {
                INSTANCE.masterEnabled = loaded.masterEnabled;
                INSTANCE.dedupEnabled = loaded.dedupEnabled;
                INSTANCE.effectThrottleEnabled = loaded.effectThrottleEnabled;
                INSTANCE.maxEffectsPerTick = clamp(loaded.maxEffectsPerTick, 1, 64);
                INSTANCE.throttleRadius = clampDouble(loaded.throttleRadius, 4.0, 256.0);
                INSTANCE.stagedProcessingEnabled = loaded.stagedProcessingEnabled;
                INSTANCE.stagedBlocksPerTick = clamp(loaded.stagedBlocksPerTick, 64, 65536);
                INSTANCE.diagnosticsEnabled = loaded.diagnosticsEnabled;
                INSTANCE.clusterDetonationEnabled = loaded.clusterDetonationEnabled;
                INSTANCE.clusterRadius = clampDouble(loaded.clusterRadius, 2.0, 16.0);
                INSTANCE.clusterMinSize = clamp(loaded.clusterMinSize, 2, 64);
                INSTANCE.clusterMaxPower = clampFloat(loaded.clusterMaxPower, 8.0f, 128.0f);
                INSTANCE.clusterMaxScan = clamp(loaded.clusterMaxScan, 64, 8192);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[AntiTNTExplosionLag] Bad config, keeping defaults: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[AntiTNTExplosionLag] Could not save config: " + e.getMessage());
        }
    }

    /** Applies raw values with the same clamping as {@link #load()} (used by tests/commands). */
    public static void applyRaw(boolean master, boolean dedup, boolean throttle, int maxFx,
                               double radius, boolean staged, int stagedBudget, boolean diag,
                               boolean cluster, double clusterRadius, int clusterMin,
                               float clusterMaxPower, int clusterMaxScan) {
        INSTANCE.masterEnabled = master;
        INSTANCE.dedupEnabled = dedup;
        INSTANCE.effectThrottleEnabled = throttle;
        INSTANCE.maxEffectsPerTick = clamp(maxFx, 1, 64);
        INSTANCE.throttleRadius = clampDouble(radius, 4.0, 256.0);
        INSTANCE.stagedProcessingEnabled = staged;
        INSTANCE.stagedBlocksPerTick = clamp(stagedBudget, 64, 65536);
        INSTANCE.diagnosticsEnabled = diag;
        INSTANCE.clusterDetonationEnabled = cluster;
        INSTANCE.clusterRadius = clampDouble(clusterRadius, 2.0, 16.0);
        INSTANCE.clusterMinSize = clamp(clusterMin, 2, 64);
        INSTANCE.clusterMaxPower = clampFloat(clusterMaxPower, 8.0f, 128.0f);
        INSTANCE.clusterMaxScan = clamp(clusterMaxScan, 64, 8192);
    }

    /** Legacy overload without cluster options (cluster settings untouched). */
    public static void applyRaw(boolean master, boolean dedup, boolean throttle, int maxFx,
                               double radius, boolean staged, int stagedBudget, boolean diag) {
        applyRaw(master, dedup, throttle, maxFx, radius, staged, stagedBudget, diag,
                INSTANCE.clusterDetonationEnabled, INSTANCE.clusterRadius, INSTANCE.clusterMinSize,
                INSTANCE.clusterMaxPower, INSTANCE.clusterMaxScan);
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(max, Math.max(min, v));
    }

    private static double clampDouble(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.min(max, Math.max(min, v));
    }

    private static float clampFloat(float v, float min, float max) {
        if (Float.isNaN(v)) {
            return min;
        }
        return Math.min(max, Math.max(min, v));
    }
}
