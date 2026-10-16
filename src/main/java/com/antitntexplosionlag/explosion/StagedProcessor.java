package com.antitntexplosionlag.explosion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Opt-in staged application of huge affected-block lists.
 *
 * <p><b>Gameplay trade-off (off by default):</b> spreads block destruction
 * across several server ticks, which delays block updates and changes
 * TNT chain-reaction timing. Order is strictly preserved (vanilla order in,
 * same order out), but simultaneity is not. Enable only if you accept
 * delayed demolition.
 *
 * <p>Live wiring: the mixin truncates the affected list to the first chunk,
 * lets vanilla {@code affectWorld} destroy exactly that chunk (identical
 * per-block semantics, drops, and damage-once behavior), then the server-tick
 * drain applies one further chunk per tick with the cosmetic burst off.
 *
 * <p>Threading: constructed and drained on the server thread only.
 */
public final class StagedProcessor<T> {
    private final List<T> pending;
    private final int budgetPerTick;
    private int cursor;

    public StagedProcessor(List<T> affectedInVanillaOrder, int budgetPerTick) {
        if (budgetPerTick < 1) {
            throw new IllegalArgumentException("budgetPerTick must be >= 1");
        }
        if (affectedInVanillaOrder == null) {
            throw new IllegalArgumentException("affected list must not be null");
        }
        this.pending = new ArrayList<>(affectedInVanillaOrder);
        this.budgetPerTick = budgetPerTick;
        this.cursor = 0;
    }

    /** Next slice in vanilla order; empty when complete. Never null. */
    public List<T> nextChunk() {
        if (isComplete()) {
            return Collections.emptyList();
        }
        int end = Math.min(pending.size(), cursor + budgetPerTick);
        List<T> chunk = new ArrayList<>(pending.subList(cursor, end));
        cursor = end;
        return chunk;
    }

    public boolean isComplete() {
        return cursor >= pending.size();
    }

    public int remaining() {
        return pending.size() - cursor;
    }

    public int total() {
        return pending.size();
    }

    /** Ticks needed to drain at this budget (at least 1 for non-empty lists). */
    public int ticksRequired() {
        if (pending.isEmpty()) {
            return 0;
        }
        return (pending.size() + budgetPerTick - 1) / budgetPerTick;
    }
}
