package com.antitntexplosionlag.explosion;

import java.util.HashMap;
import java.util.Map;

/**
 * Merges cosmetic explosion effects (particles/sounds) across two budgets:
 *
 * <ul>
 *   <li><b>Per-tick cell budget</b>: at most {@code maxPerTick} bursts per
 *   tick per cell. Overflow uses vanilla {@code affectWorld(false)}.</li>
 *   <li><b>Storm budget</b>: at most {@code stormCap} bursts per storm, where
 *   a storm is a run of ticks with explosions separated by gaps no bigger
 *   than {@code stormGapTicks}. A million-TNT event therefore renders (and
 *   sounds) like at most {@code stormCap} blasts — default 40.</li>
 * </ul>
 *
 * <p>Gameplay is untouched: only the cosmetic burst is ever skipped. Block
 * destruction, damage, and sync packets are never suppressed. A
 * non-positive {@code stormCap} disables the storm ceiling.
 */
public final class EffectThrottler {
    private final int maxPerTick;
    private final double cellSize;
    private final int stormCap;
    private final int stormGapTicks;
    private final Map<Long, Bucket> buckets = new HashMap<>();
    private long currentTick = Long.MIN_VALUE;
    private long lastActiveTick = Long.MIN_VALUE;
    private int stormEmitted;
    private long suppressedTotal;

    private static final class Bucket {
        long tick = Long.MIN_VALUE;
        int count;
    }

    public EffectThrottler(int maxPerTick, double cellSize) {
        this(maxPerTick, cellSize, 0, 10);
    }

    public EffectThrottler(int maxPerTick, double cellSize, int stormCap, int stormGapTicks) {
        if (maxPerTick < 1) {
            throw new IllegalArgumentException("maxPerTick must be >= 1");
        }
        if (cellSize < 1.0) {
            throw new IllegalArgumentException("cellSize must be >= 1.0");
        }
        if (stormGapTicks < 1) {
            throw new IllegalArgumentException("stormGapTicks must be >= 1");
        }
        this.maxPerTick = maxPerTick;
        this.cellSize = cellSize;
        this.stormCap = stormCap;
        this.stormGapTicks = stormGapTicks;
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
        if (tick - lastActiveTick > stormGapTicks) {
            stormEmitted = 0;
        }
        lastActiveTick = tick;
        if (stormCap > 0 && stormEmitted >= stormCap) {
            suppressedTotal++;
            return false;
        }
        long cell = cellKey(x, y, z);
        Bucket b = buckets.computeIfAbsent(cell, k -> new Bucket());
        if (b.tick != tick) {
            b.tick = tick;
            b.count = 0;
        }
        b.count++;
        if (b.count <= maxPerTick) {
            stormEmitted++;
            return true;
        }
        suppressedTotal++;
        return false;
    }

    public synchronized long suppressedTotal() {
        return suppressedTotal;
    }

    public synchronized int stormEmitted() {
        return stormEmitted;
    }

    public synchronized void reset() {
        buckets.clear();
        currentTick = Long.MIN_VALUE;
        lastActiveTick = Long.MIN_VALUE;
        stormEmitted = 0;
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
