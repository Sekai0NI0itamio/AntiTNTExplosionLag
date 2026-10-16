package com.antitntexplosionlag;

import com.antitntexplosionlag.explosion.StagedProcessor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedProcessorTest {
    @Test
    void chunksPreserveVanillaOrder() {
        List<Long> order = new ArrayList<>(List.of(9L, 1L, 5L, 3L, 7L, 2L, 8L));
        StagedProcessor p = new StagedProcessor(order, 3);
        assertEquals(List.of(9L, 1L, 5L), p.nextChunk());
        assertFalse(p.isComplete());
        assertEquals(List.of(3L, 7L, 2L), p.nextChunk());
        assertEquals(List.of(8L), p.nextChunk());
        assertTrue(p.isComplete());
        assertTrue(p.nextChunk().isEmpty());
        assertEquals(0, p.remaining());
        assertEquals(7, p.total());
    }

    @Test
    void ticksRequiredMath() {
        assertEquals(0, new StagedProcessor(List.of(), 10).ticksRequired());
        assertEquals(1, new StagedProcessor(List.of(1L), 10).ticksRequired());
        assertEquals(1, new StagedProcessor(List.of(1L, 2L), 2).ticksRequired());
        assertEquals(2, new StagedProcessor(List.of(1L, 2L, 3L), 2).ticksRequired());
        assertEquals(4, new StagedProcessor(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), 2).ticksRequired());
    }

    @Test
    void emptyListCompletesImmediately() {
        StagedProcessor p = new StagedProcessor(List.of(), 64);
        assertTrue(p.isComplete());
        assertTrue(p.nextChunk().isEmpty());
        assertEquals(0, p.remaining());
    }

    @Test
    void constructorRejectsBadArgs() {
        try {
            new StagedProcessor(List.of(1L), 0);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            new StagedProcessor(null, 8);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
