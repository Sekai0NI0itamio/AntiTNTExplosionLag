package com.antitntexplosionlag.explosion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure, allocation-conscious explosion math helpers.
 *
 * <p>These mirror the <em>shape</em> of vanilla's explosion sampling (ray grid
 * over a bounding box, duplicate-prone affected-position collection) without
 * depending on Minecraft classes, so every optimization here is covered by
 * vanilla-equivalence unit tests. The live mixins call only
 * {@link #deduplicatePreservingOrder} and the precomputed direction table.
 */
public final class ExplosionMath {
    private ExplosionMath() {
    }

    // ------------------------------------------------------------------
    // Packed block positions (internal use only, NOT vanilla packing).
    // 26 bits per axis is plenty for loaded regions; reversible and fast.
    // ------------------------------------------------------------------

    private static final int BITS = 26;
    private static final long MASK = (1L << BITS) - 1;
    private static final int BIAS = 1 << (BITS - 1);

    public static long packLocal(int x, int y, int z) {
        return (((long) (x + BIAS) & MASK) << (BITS * 2))
                | (((long) (y + BIAS) & MASK) << BITS)
                | ((long) (z + BIAS) & MASK);
    }

    public static int unpackX(long packed) {
        return (int) ((packed >>> (BITS * 2)) & MASK) - BIAS;
    }

    public static int unpackY(long packed) {
        return (int) ((packed >>> BITS) & MASK) - BIAS;
    }

    public static int unpackZ(long packed) {
        return (int) (packed & MASK) - BIAS;
    }

    // ------------------------------------------------------------------
    // Duplicate removal. Vanilla ray-marching can reach the same block from
    // several rays; destroying it twice has the same end state as destroying
    // it once, so order-preserving dedup only shrinks the apply loop.
    // ------------------------------------------------------------------

    /**
     * Removes duplicate entries in place, preserving first-seen order.
     *
     * @return number of entries removed
     */
    public static int deduplicatePreservingOrder(List<Long> packed) {
        if (packed.size() < 2) {
            return 0;
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>(packed.size() * 2);
        seen.addAll(packed);
        int removed = packed.size() - seen.size();
        if (removed > 0) {
            packed.clear();
            packed.addAll(seen);
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Exposure sampling. Naive version allocates per sample (like an
    // unoptimized path); fast version reuses a precomputed direction table
    // and primitive math. Both must agree exactly (see ExposureEquivalence).
    // ------------------------------------------------------------------

    /** Functional obstruction test: is the ray blocked at fraction t? */
    public interface ObstructionFn {
        boolean blocked(double dx, double dy, double dz, double t);
    }

    /** Precomputed unit directions of a cube shell; flat xyz triplets. */
    public static float[] buildShellDirections(int resolution) {
        if (resolution < 2) {
            throw new IllegalArgumentException("resolution must be >= 2");
        }
        List<Float> dirs = new ArrayList<>();
        double step = 2.0 / (resolution - 1);
        for (int ix = 0; ix < resolution; ix++) {
            for (int iy = 0; iy < resolution; iy++) {
                for (int iz = 0; iz < resolution; iz++) {
                    boolean shell = ix == 0 || iy == 0 || iz == 0
                            || ix == resolution - 1 || iy == resolution - 1 || iz == resolution - 1;
                    if (!shell) {
                        continue;
                    }
                    double dx = -1.0 + ix * step;
                    double dy = -1.0 + iy * step;
                    double dz = -1.0 + iz * step;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dirs.add((float) (dx / len));
                    dirs.add((float) (dy / len));
                    dirs.add((float) (dz / len));
                }
            }
        }
        float[] out = new float[dirs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = dirs.get(i);
        }
        return out;
    }

    /** Shared 16-resolution shell, built once and never reallocated per entity. */
    public static final float[] SHELL_16 = buildShellDirections(16);

    /** Naive reference: one heap object per direction (intentionally wasteful). */
    public static float exposureNaive(float[] dirs, ObstructionFn fn) {
        int rays = dirs.length / 3;
        int open = 0;
        for (int r = 0; r < rays; r++) {
            // Deliberate per-sample allocation: the cost the fast path avoids.
            double[] d = new double[]{dirs[r * 3], dirs[r * 3 + 1], dirs[r * 3 + 2]};
            if (!fn.blocked(d[0], d[1], d[2], 1.0)) {
                open++;
            }
        }
        return rays == 0 ? 0.0f : (float) open / rays;
    }

    /** Optimized: identical verdicts, primitives only, no per-sample allocation. */
    public static float exposureFast(float[] dirs, ObstructionFn fn) {
        int rays = dirs.length / 3;
        int open = 0;
        for (int r = 0, i = 0; r < rays; r++, i += 3) {
            float dx = dirs[i];
            float dy = dirs[i + 1];
            float dz = dirs[i + 2];
            if (!fn.blocked(dx, dy, dz, 1.0)) {
                open++;
            }
        }
        return rays == 0 ? 0.0f : (float) open / rays;
    }
}
