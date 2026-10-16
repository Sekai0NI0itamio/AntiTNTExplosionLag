package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.ClusterMerge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterMergeTest {
    private static double[] p(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    @Test
    void isolatedPointsStaySeparate() {
        List<double[]> pts = List.of(p(0, 0, 0), p(100, 0, 0), p(0, 100, 0));
        List<List<double[]>> groups = ClusterMerge.components(new ArrayList<>(pts), 7.0);
        assertEquals(3, groups.size());
    }

    @Test
    void touchingPointsMerge() {
        List<double[]> pts = List.of(p(0, 0, 0), p(3, 0, 0), p(100, 0, 0));
        List<List<double[]>> groups = ClusterMerge.components(new ArrayList<>(pts), 7.0);
        assertEquals(2, groups.size());
        int big = groups.stream().mapToInt(List::size).max().orElse(0);
        assertEquals(2, big);
    }

    @Test
    void transitiveClosureMergesChains() {
        // A-B and B-C linked, A-C farther than radius: still one cluster.
        List<double[]> pts = List.of(p(0, 0, 0), p(5, 0, 0), p(10, 0, 0));
        List<List<double[]>> groups = ClusterMerge.components(new ArrayList<>(pts), 7.0);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).size());
    }

    @Test
    void solidFillIsOneCluster() {
        List<double[]> pts = new ArrayList<>();
        for (int x = 0; x < 21; x++) {
            for (int y = 0; y < 21; y++) {
                for (int z = 0; z < 21; z++) {
                    pts.add(p(x, y, z));
                }
            }
        }
        assertEquals(9261, pts.size());
        List<List<double[]>> groups = ClusterMerge.components(pts, 7.0);
        assertEquals(1, groups.size());
        assertEquals(9261, groups.get(0).size());
        double[] center = ClusterMerge.centroid(groups.get(0));
        assertEquals(10.0, center[0], 1e-9);
        assertEquals(10.0, center[1], 1e-9);
        assertEquals(10.0, center[2], 1e-9);
    }

    @Test
    void emptyInputGivesNoGroups() {
        assertTrue(ClusterMerge.components(new ArrayList<>(), 7.0).isEmpty());
    }

    @Test
    void combinedPowerFollowsCubeRootAndCap() {
        assertEquals(4.0f, ClusterMerge.combinedPower(1, 4.0f, 48.0f), 1e-4);
        assertEquals((float) (4.0 * Math.cbrt(8)), ClusterMerge.combinedPower(8, 4.0f, 48.0f), 1e-4);
        assertEquals((float) (4.0 * Math.cbrt(27)), ClusterMerge.combinedPower(27, 4.0f, 48.0f), 1e-4);
        // 9261 TNT would ask for ~84 power: capped.
        assertEquals(48.0f, ClusterMerge.combinedPower(9261, 4.0f, 48.0f), 1e-4);
        assertTrue(ClusterMerge.combinedPower(9261, 4.0f, 128.0f) < 128.0f);
    }

    @Test
    void guardsRejectBadArgs() {
        try {
            ClusterMerge.components(List.of(p(0, 0, 0)), 0.0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            ClusterMerge.centroid(new ArrayList<>());
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            ClusterMerge.combinedPower(0, 4.0f, 48.0f);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
