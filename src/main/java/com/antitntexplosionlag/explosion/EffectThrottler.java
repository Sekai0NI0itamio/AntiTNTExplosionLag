package com.antitntexplosionlag.explosion;

import java.util.HashMap;
import java.util.Map;

/**
 * Merges cosmetic explosion effects (particles/sounds) when many explosions
 * land in the same tick inside one cell.
 *
 * <p>Gameplay is untouched: the mixin only reroutes throttled explosions
 * through vanilla's own {@code affectWorld(particles=false)} path, which
 * still destroys blocks and damages entities. Block/entity synchronization
 * packets are never suppressed. Only the cosmetic burst is skipped, and only
 * when more than {@code maxPerTick} explosions share a tick+cell.
 */
public final class EffectThrottler {
    private final int maxPerTick;
    private final double cellSize;
    private final Map<Long, Bucket> buckets = new HashMap<>();
    private long currentTick = Long.MIN_VALUE;
    private long suppressedTotal;

    private static final class Bucket {
        long tick = Long.MIN_VALUE;
        int count;
    }

    public EffectThrottler(int maxPerTick, double cellSize) {
        if (maxPerTick < 1) {
            throw new IllegalArgumentException("maxPerTick must be >= 1");
        }
        if (cellSize < 1.0) {
            throw new IllegalArgumentException("cellSize must be >= 1.0");
        }
        this.maxPerTick = maxPerTick;
        this.cellSize = cellSize;
    }

    /**
     * @return true if this explosion may emit its cosmetic burst, false if it
     *         should use the particles=false path (gameplay still applied).
     */
    public synchronized boolean admit(long tick, double x, double y, double z) {
        if (tick != currentTick) {
            currentTick = tick;
            buckets.clear();
        }
        long cell = cellKey(x, y, z);
        Bucket b = buckets.computeIfAbsent(cell, k -> new Bucket());
        if (b.tick != tick) {
            b.tick = tick;
            b.count = 0;
        }
        b.count++;
        if (b.count <= maxPerTick) {
            return true;
        }
        suppressedTotal++;
        return false;
    }

    public synchronized long suppressedTotal() {
        return suppressedTotal;
    }

    public synchronized void reset() {
        buckets.clear();
        currentTick = Long.MIN_VALUE;
        suppressedTotal = 0;
    }

    private long cellKey(double x, double y, double z) {
        long cx = (long) Math.floor(x / cellSize);
        long cy = (long) Math.floor(y / cellSize);
        long cz = (long) Math.floor(z / cellSize);
        // 21 bits per axis after bias; collisions across far-apart cells are
        // acceptable (worst case: an extra cosmetic merge, never lost blocks).
        long bx = (cx + (1L << 20)) & ((1L << 21) - 1);
        long by = (cy + (1L << 20)) & ((1L << 21) - 1);
        long bz = (cz + (1L << 20)) & ((1L << 21) - 1);
        return (bx << 42) | (by << 21) | bz;
    }
}
