package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.MergeWindows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeWindowsTest {
    @Test
    void absorbsInsideLiveWindow() {
        MergeWindows w = new MergeWindows();
        w.add(0.0, 64.0, 0.0, 100.0, 15L);
        assertTrue(w.isAbsorbed(10L, 10.0, 64.0, 10.0));
        assertTrue(w.isAbsorbed(15L, 0.0, 64.0, 0.0));
    }

    @Test
    void outsideWindowIsNotAbsorbed() {
        MergeWindows w = new MergeWindows();
        w.add(0.0, 64.0, 0.0, 10.0, 15L);
        assertFalse(w.isAbsorbed(10L, 500.0, 64.0, 500.0));
    }

    @Test
    void expiredWindowsPrune() {
        MergeWindows w = new MergeWindows();
        w.add(0.0, 64.0, 0.0, 100.0, 15L);
        assertFalse(w.isAbsorbed(16L, 0.0, 64.0, 0.0));
        assertEquals(0, w.liveCount(16L));
    }

    @Test
    void multipleWindowsOverlap() {
        MergeWindows w = new MergeWindows();
        w.add(0.0, 64.0, 0.0, 10.0, 20L);
        w.add(1000.0, 64.0, 1000.0, 10.0, 20L);
        assertTrue(w.isAbsorbed(10L, 1000.0, 64.0, 1000.0));
        assertFalse(w.isAbsorbed(10L, 500.0, 64.0, 500.0));
        assertEquals(2, w.liveCount(10L));
        w.clear();
        assertEquals(0, w.liveCount(10L));
    }
}
