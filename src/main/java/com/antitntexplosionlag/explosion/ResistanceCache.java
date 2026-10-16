package com.antitntexplosionlag.explosion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded per-explosion blast-resistance cache.
 *
 * <p>Safety contract: the cache is valid for exactly ONE explosion's
 * collection phase. Vanilla collects affected blocks before destroying
 * anything, so world state cannot change mid-collection on the server
 * thread. The mixin calls {@link #beginExplosion} at the head of every
 * collection pass, which drops all entries from any previous explosion.
 * Never shared across ticks, never read on any thread but the server
 * thread.
 */
public final class ResistanceCache {
    private final int capacity;
    private final Map<Integer, Float> map;
    private long sequence = -1;
    private long hits;
    private long misses;

    public ResistanceCache(int capacity) {
        if (capacity < 16) {
            throw new IllegalArgumentException("capacity must be >= 16");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<Integer, Float>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
                return size() > ResistanceCache.this.capacity;
            }
        };
    }

    /** Starts a new collection phase; all previous entries are invalid. */
    public synchronized void beginExplosion(long sequence) {
        if (sequence != this.sequence) {
            this.sequence = sequence;
            map.clear();
        }
    }

    /**
     * Returns the cached resistance, or {@code null} on miss (caller computes
     * the real value and stores it via {@link #put}).
     */
    public synchronized Float get(int stateId, long sequence) {
        if (sequence != this.sequence) {
            return null;
        }
        Float v = map.get(stateId);
        if (v != null) {
            hits++;
        } else {
            misses++;
        }
        return v;
    }

    public synchronized void put(int stateId, long sequence, float resistance) {
        if (sequence != this.sequence) {
            return;
        }
        map.put(stateId, resistance);
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    public synchronized void resetStats() {
        hits = 0;
        misses = 0;
    }
}
