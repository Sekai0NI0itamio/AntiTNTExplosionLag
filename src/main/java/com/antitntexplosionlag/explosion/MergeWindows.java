package com.antitntexplosionlag.explosion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived absorption windows left behind by merged blasts.
 *
 * <p>Problem: a merged blast destroys TNT <em>blocks</em>, and vanilla
 * primes those blocks into new short-fuse entities — a second-by-second
 * chain that undoes the merge. A window covers the merged crater for a few
 * ticks: anything that detonates inside it is absorbed (discarded, counted)
 * instead of exploding again. This is what makes a whole fill go off
 * <em>at once</em> instead of chaining.
 *
 * <p>Pure math, fully unit-tested. Expiry is tick-based so paused servers
 * can't leak windows.
 */
public final class MergeWindows {
    private final List<Entry> entries = new ArrayList<>();

    public record Entry(double x, double y, double z, double radius, long expiryTick) {
    }

    public synchronized void add(double x, double y, double z, double radius, long expiryTick) {
        entries.add(new Entry(x, y, z, radius, expiryTick));
    }

    /**
     * @return true if the point detonates inside a live window (caller should
     *         absorb it: discard + count, no new explosion).
     */
    public synchronized boolean isAbsorbed(long tick, double x, double y, double z) {
        boolean absorbed = false;
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.expiryTick() < tick) {
                it.remove();
                continue;
            }
            double dx = x - e.x();
            double dy = y - e.y();
            double dz = z - e.z();
            if (dx * dx + dy * dy + dz * dz <= e.radius() * e.radius()) {
                absorbed = true;
            }
        }
        return absorbed;
    }

    public synchronized int liveCount(long tick) {
        isAbsorbed(tick, Double.NaN, Double.NaN, Double.NaN);
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
