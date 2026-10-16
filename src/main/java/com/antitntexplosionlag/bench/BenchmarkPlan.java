package com.antitntexplosionlag.bench;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic workload descriptors for the five benchmark scenarios.
 * Pure data + synthetic workload builders (no Minecraft classes) so CI can
 * run them reproducibly. A full live-server benchmark is out of scope for
 * headless CI; see BENCHMARKS.md for that limitation.
 */
public final class BenchmarkPlan {
    public record Scenario(String name, int tntCount, int ticks, String area) {
    }

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario("small-isolated", 1, 100, "single TNT, open flat ground"),
                new Scenario("large-detonation", 64, 40, "64 primed TNT in one cube"),
                new Scenario("chain-reaction", 256, 200, "256 TNT chain with 2-tick fuse stagger"),
                new Scenario("overlapping-confined", 96, 60, "96 explosions in a 9x9 stone box"),
                new Scenario("repeated-stress", 32, 400, "32 TNT re-primed every 20 ticks")
        );
    }

    /**
     * Builds a synthetic affected-position workload with a controlled
     * duplicate ratio, modelling overlapping blasts that revisit blocks.
     * Unique entries are index-derived (collision-free by construction);
     * duplicates and shuffle order come from a deterministic stream, so the
     * same seed always yields the same list.
     */
    public static List<Long> syntheticWorkload(int uniqueBlocks, double duplicateRatio, long seed) {
        if (uniqueBlocks < 0) {
            throw new IllegalArgumentException("uniqueBlocks must be >= 0");
        }
        if (duplicateRatio < 0.0 || duplicateRatio > 1.0) {
            throw new IllegalArgumentException("duplicateRatio must be in [0,1]");
        }
        long state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        List<Long> out = new ArrayList<>();
        List<Long> uniques = new ArrayList<>(uniqueBlocks);
        for (int i = 0; i < uniqueBlocks; i++) {
            // Grid-derived coordinates: unique for i < 96*64*96.
            int x = (i % 96) - 48;
            int y = ((i / 96) % 64) - 32;
            int z = ((i / (96 * 64)) % 96) - 48;
            long packed = pack(x, y, z);
            uniques.add(packed);
            out.add(packed);
        }
        int dups = (int) (uniqueBlocks * duplicateRatio);
        for (int i = 0; i < dups && !uniques.isEmpty(); i++) {
            state = mix(state);
            out.add(uniques.get((int) Math.floorMod(state, uniques.size())));
        }
        // Deterministic Fisher-Yates shuffle with the same stream.
        for (int i = out.size() - 1; i > 0; i--) {
            state = mix(state);
            int j = (int) Math.floorMod(state, i + 1);
            Long tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long pack(int x, int y, int z) {
        long bx = (long) (x + 512) & 1023L;
        long by = (long) (y + 512) & 1023L;
        long bz = (long) (z + 512) & 1023L;
        return (bx << 20) | (by << 10) | bz;
    }
}
