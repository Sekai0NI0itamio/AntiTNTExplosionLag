package com.antitntexplosionlag.explosion;

/**
 * Counts primed TNT and explosions per server tick. Observation only: never
 * changes fuse timing or explosion power. Feeds diagnostics and the
 * cosmetic throttle's storm signal.
 */
public final class ChainMonitor {
    private long tick = Long.MIN_VALUE;
    private int tntTicks;
    private int explosions;
    private int maxTntPerTick;
    private int maxExplosionsPerTick;
    private long totalTntTicks;
    private long totalExplosions;

    public synchronized void onTntTick(long tick) {
        roll(tick);
        tntTicks++;
        totalTntTicks++;
        if (tntTicks > maxTntPerTick) {
            maxTntPerTick = tntTicks;
        }
    }

    public synchronized void onExplosion(long tick) {
        roll(tick);
        explosions++;
        totalExplosions++;
        if (explosions > maxExplosionsPerTick) {
            maxExplosionsPerTick = explosions;
        }
    }

    /** True when this tick already looks like a chain-reaction storm. */
    public synchronized boolean isStorm(long tick, int threshold) {
        if (this.tick != tick) {
            return false;
        }
        return explosions >= threshold;
    }

    public synchronized int tntThisTick(long tick) {
        return this.tick == tick ? tntTicks : 0;
    }

    public synchronized int explosionsThisTick(long tick) {
        return this.tick == tick ? explosions : 0;
    }

    public synchronized int maxTntPerTick() {
        return maxTntPerTick;
    }

    public synchronized int maxExplosionsPerTick() {
        return maxExplosionsPerTick;
    }

    public synchronized long totalTntTicks() {
        return totalTntTicks;
    }

    public synchronized long totalExplosions() {
        return totalExplosions;
    }

    public synchronized void reset() {
        tick = Long.MIN_VALUE;
        tntTicks = 0;
        explosions = 0;
        maxTntPerTick = 0;
        maxExplosionsPerTick = 0;
        totalTntTicks = 0;
        totalExplosions = 0;
    }

    private void roll(long tick) {
        if (tick != this.tick) {
            this.tick = tick;
            tntTicks = 0;
            explosions = 0;
        }
    }
}
