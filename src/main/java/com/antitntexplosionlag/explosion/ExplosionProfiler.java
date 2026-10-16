package com.antitntexplosionlag.explosion;

import java.util.Arrays;

/**
 * Fixed-capacity ring of nanosecond timing samples with average/percentile.
 * Server-thread confined by convention; synchronized anyway so the
 * {@code /antitnt stats} command can read safely.
 */
public final class ExplosionProfiler {
    private final long[] ring;
    private int head;
    private int count;

    public ExplosionProfiler(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.ring = new long[capacity];
    }

    public synchronized void record(long nanos) {
        ring[head] = nanos;
        head = (head + 1) % ring.length;
        if (count < ring.length) {
            count++;
        }
    }

    public synchronized int count() {
        return count;
    }

    public synchronized double averageMs() {
        if (count == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += ring[i];
        }
        return sum / (count * 1_000_000.0);
    }

    public synchronized double maxMs() {
        if (count == 0) {
            return 0.0;
        }
        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, ring[i]);
        }
        return max / 1_000_000.0;
    }

    /** Nearest-rank percentile in [0,100]. */
    public synchronized double percentileMs(double p) {
        if (count == 0) {
            return 0.0;
        }
        long[] copy = Arrays.copyOf(ring, count);
        Arrays.sort(copy);
        double clamped = Math.min(100.0, Math.max(0.0, p));
        int rank = (int) Math.ceil(clamped / 100.0 * count) - 1;
        rank = Math.min(count - 1, Math.max(0, rank));
        return copy[rank] / 1_000_000.0;
    }

    public synchronized void reset() {
        head = 0;
        count = 0;
    }
}
