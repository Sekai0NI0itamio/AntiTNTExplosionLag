package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.ResistanceCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResistanceCacheTest {
    @Test
    void missThenHitWithinOneExplosion() {
        ResistanceCache cache = new ResistanceCache(64);
        cache.beginExplosion(1L);
        assertNull(cache.get(7, 1L));
        cache.put(7, 1L, 3.5f);
        assertEquals(3.5f, cache.get(7, 1L));
        assertEquals(1, cache.size());
        assertEquals(1, cache.hits());
        assertEquals(1, cache.misses());
    }

    @Test
    void newExplosionInvalidatesEverything() {
        ResistanceCache cache = new ResistanceCache(64);
        cache.beginExplosion(1L);
        cache.put(7, 1L, 3.5f);
        cache.beginExplosion(2L);
        assertEquals(0, cache.size());
        // Stale sequence reads never served.
        assertNull(cache.get(7, 1L));
        assertNull(cache.get(7, 99L));
        assertNull(cache.get(7, 2L));
        cache.put(7, 2L, 9.0f);
        assertEquals(9.0f, cache.get(7, 2L));
    }

    @Test
    void putForStaleSequenceIsIgnored() {
        ResistanceCache cache = new ResistanceCache(64);
        cache.beginExplosion(5L);
        cache.put(1, 4L, 1.0f);
        assertEquals(0, cache.size());
    }

    @Test
    void capacityBoundEvictsEldest() {
        ResistanceCache cache = new ResistanceCache(16);
        cache.beginExplosion(1L);
        for (int i = 0; i < 40; i++) {
            cache.put(i, 1L, (float) i);
        }
        assertEquals(16, cache.size());
    }

    @Test
    void constructorRejectsTinyCapacity() {
        try {
            new ResistanceCache(4);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
