package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemapChunkIndexTest {
    private static final UUID OVERWORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();

    @Test
    void markAndUnmarkPerWorld() {
        RemapChunkIndex index = new RemapChunkIndex();
        assertFalse(index.mayContain(234, 85));
        assertTrue(index.mark(OVERWORLD, 234, 85));
        assertFalse(index.mark(OVERWORLD, 234, 85), "already marked");
        assertTrue(index.mayContain(234, 85));
        assertTrue(index.contains(OVERWORLD, 234, 85));
        assertFalse(index.contains(NETHER, 234, 85));
        assertFalse(index.mayContain(234, 86));
        index.mark(NETHER, 234, 85);
        index.unmark(OVERWORLD, 234, 85);
        assertTrue(index.mayContain(234, 85), "still marked in the other world");
        index.unmark(NETHER, 234, 85);
        assertFalse(index.mayContain(234, 85));
        index.unmark(NETHER, 234, 85);
        assertEquals(0, index.size());
    }

    @Test
    void negativeCoordinatesDoNotCollide() {
        RemapChunkIndex index = new RemapChunkIndex();
        index.mark(OVERWORLD, -1, 0);
        assertFalse(index.mayContain(0, -1));
        assertFalse(index.mayContain(-1, -1));
        assertTrue(index.mayContain(-1, 0));
        assertNotEquals(RemapChunkIndex.key(-1, 0), RemapChunkIndex.key(0, -1));
        assertNotEquals(RemapChunkIndex.key(1, -2), RemapChunkIndex.key(-2, 1));
    }

    @Test
    void worldUnloadAndClear() {
        RemapChunkIndex index = new RemapChunkIndex();
        index.mark(OVERWORLD, 1, 1);
        index.mark(OVERWORLD, 2, 2);
        index.mark(NETHER, 2, 2);
        assertEquals(3, index.size());
        index.clearWorld(OVERWORLD);
        assertFalse(index.mayContain(1, 1));
        assertTrue(index.mayContain(2, 2));
        assertEquals(1, index.size());
        index.clear();
        assertFalse(index.mayContain(2, 2));
        assertEquals(0, index.size());
    }

    @Test
    void concurrentMarksFromNettyThreadsKeepCountsRight() throws InterruptedException {
        RemapChunkIndex index = new RemapChunkIndex();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int thread = 0; thread < 4; thread++) {
            pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    index.mark(OVERWORLD, i % 50, 0);
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(50, index.size());
        for (int i = 0; i < 50; i++) {
            index.unmark(OVERWORLD, i, 0);
            assertFalse(index.mayContain(i, 0));
        }
    }
}
