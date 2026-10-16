package com.antitntexplosionlag.explosion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connected-component grouping for primed TNT positions.
 *
 * <p>A cluster is the transitive closure of "within {@code radius}": A
 * connected to B and B connected to C puts all three in one cluster even if
 * A and C are far apart. This is what makes a solid TNT fill detonate as a
 * single blast instead of thousands of sequential chains.
 *
 * <p>Pure math on {@code double[3]} positions (no Minecraft classes), with a
 * spatial grid so a 9k-block fill groups in milliseconds, not seconds.
 */
public final class ClusterMerge {
    private ClusterMerge() {
    }

    /**
     * Groups positions into connected components. Order of first appearance
     * is preserved across components and within each component.
     */
    public static List<List<double[]>> components(List<double[]> points, double radius) {
        List<List<double[]>> out = new ArrayList<>();
        if (points.isEmpty()) {
            return out;
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        int n = points.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        double cell = radius;
        Map<Long, List<Integer>> grid = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            grid.computeIfAbsent(cellKey(points.get(i), cell), k -> new ArrayList<>()).add(i);
        }
        double r2 = radius * radius;
        for (int i = 0; i < n; i++) {
            double[] a = points.get(i);
            long cx = cellCoord(a[0], cell);
            long cy = cellCoord(a[1], cell);
            long cz = cellCoord(a[2], cell);
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (long dz = -1; dz <= 1; dz++) {
                        List<Integer> bucket = grid.get(packCell(cx + dx, cy + dy, cz + dz));
                        if (bucket == null) {
                            continue;
                        }
                        for (int j : bucket) {
                            if (j <= i) {
                                continue;
                            }
                            if (dist2(a, points.get(j)) <= r2) {
                                union(parent, i, j);
                            }
                        }
                    }
                }
            }
        }
        Map<Integer, List<double[]>> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(points.get(i));
        }
        out.addAll(byRoot.values());
        return out;
    }

    /**
     * Decides exact vs coarse grouping. Above {@code exactCap} members the
     * field is dense enough that transitive precision isn't worth the
     * quadratic pass: the whole field becomes one cluster (centroid + capped
     * power). This is the branch that keeps million-TNT fields instant.
     */
    public static boolean useCoarseGrouping(int memberCount, int exactCap) {
        return memberCount > exactCap;
    }

    /** Centroid of a component (detonation center for the merged blast). */
    public static double[] centroid(List<double[]> component) {
        if (component.isEmpty()) {
            throw new IllegalArgumentException("component must not be empty");
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (double[] p : component) {
            x += p[0];
            y += p[1];
            z += p[2];
        }
        int n = component.size();
        return new double[]{x / n, y / n, z / n};
    }

    /**
     * Combined power for a merged blast of {@code count} TNT (each base
     * power, vanilla TNT = 4). Volume scales with count, so radius (and
     * hence power, which is roughly linear in radius) scales with the cube
     * root. Deliberately an approximation, capped — see docs.
     */
    public static float combinedPower(int count, float basePower, float cap) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        if (basePower <= 0 || cap <= 0) {
            throw new IllegalArgumentException("powers must be > 0");
        }
        return Math.min(cap, (float) (basePower * Math.cbrt(count)));
    }

    private static double dist2(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static long cellCoord(double v, double cell) {
        return (long) Math.floor(v / cell);
    }

    private static long cellKey(double[] p, double cell) {
        return packCell(cellCoord(p[0], cell), cellCoord(p[1], cell), cellCoord(p[2], cell));
    }

    private static long packCell(long x, long y, long z) {
        long bx = (x + (1L << 20)) & ((1L << 21) - 1);
        long by = (y + (1L << 20)) & ((1L << 21) - 1);
        long bz = (z + (1L << 20)) & ((1L << 21) - 1);
        return (bx << 42) | (by << 21) | bz;
    }

    private static int find(int[] parent, int i) {
        int root = i;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[i] != root) {
            int next = parent[i];
            parent[i] = root;
            i = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
