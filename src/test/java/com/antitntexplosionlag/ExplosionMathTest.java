package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.ExplosionMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionMathTest {
    @Test
    void packUnpackRoundTrips() {
        int[][] samples = {{0, 0, 0}, {10, -5, 300}, {-300000, 64, 300000}, {1, 2, 3}};
        for (int[] s : samples) {
            long packed = ExplosionMath.packLocal(s[0], s[1], s[2]);
            assertEquals(s[0], ExplosionMath.unpackX(packed));
            assertEquals(s[1], ExplosionMath.unpackY(packed));
            assertEquals(s[2], ExplosionMath.unpackZ(packed));
        }
    }

    @Test
    void dedupRemovesDuplicatesPreservingOrder() {
        List<Long> list = new ArrayList<>(List.of(5L, 3L, 5L, 7L, 3L, 9L));
        int removed = ExplosionMath.deduplicatePreservingOrder(list);
        assertEquals(2, removed);
        assertEquals(List.of(5L, 3L, 7L, 9L), list);
    }

    @Test
    void dedupNoOpCases() {
        List<Long> empty = new ArrayList<>();
        assertEquals(0, ExplosionMath.deduplicatePreservingOrder(empty));
        assertTrue(empty.isEmpty());

        List<Long> single = new ArrayList<>(List.of(42L));
        assertEquals(0, ExplosionMath.deduplicatePreservingOrder(single));

        List<Long> unique = new ArrayList<>(List.of(1L, 2L, 3L, 4L));
        assertEquals(0, ExplosionMath.deduplicatePreservingOrder(unique));
        assertEquals(List.of(1L, 2L, 3L, 4L), unique);

        List<Long> allSame = new ArrayList<>(List.of(7L, 7L, 7L, 7L));
        assertEquals(3, ExplosionMath.deduplicatePreservingOrder(allSame));
        assertEquals(List.of(7L), allSame);
    }

    @Test
    void shellDirectionsAreNormalized() {
        float[] dirs = ExplosionMath.buildShellDirections(8);
        assertTrue(dirs.length % 3 == 0);
        int rays = dirs.length / 3;
        assertTrue(rays > 100);
        for (int r = 0; r < rays; r++) {
            float dx = dirs[r * 3];
            float dy = dirs[r * 3 + 1];
            float dz = dirs[r * 3 + 2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(1.0, len, 1e-5);
        }
    }

    @Test
    void exposureFastMatchesNaive() {
        float[] dirs = ExplosionMath.buildShellDirections(10);
        // Checkerboard obstruction: blocked when direction sum is positive.
        ExplosionMath.ObstructionFn checker = (dx, dy, dz, t) -> (dx + dy + dz) > 0;
        ExplosionMath.ObstructionFn none = (dx, dy, dz, t) -> false;
        ExplosionMath.ObstructionFn all = (dx, dy, dz, t) -> true;
        ExplosionMath.ObstructionFn bands = (dx, dy, dz, t) -> ((int) (dx * 10 + dy * 10)) % 2 == 0;
        for (ExplosionMath.ObstructionFn fn : new ExplosionMath.ObstructionFn[]{checker, none, all, bands}) {
            assertEquals(ExplosionMath.exposureNaive(dirs, fn), ExplosionMath.exposureFast(dirs, fn), 0.0f);
        }
        assertEquals(1.0f, ExplosionMath.exposureFast(dirs, none), 0.0f);
        assertEquals(0.0f, ExplosionMath.exposureFast(dirs, all), 0.0f);
    }

    @Test
    void sharedShellIsStable() {
        assertTrue(ExplosionMath.SHELL_16.length > 0);
        assertEquals(ExplosionMath.buildShellDirections(16).length, ExplosionMath.SHELL_16.length);
    }
}
