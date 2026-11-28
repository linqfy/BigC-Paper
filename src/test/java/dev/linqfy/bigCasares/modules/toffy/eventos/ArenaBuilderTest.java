package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaBuilderTest {
    @Test
    void buildUsesBoundedVisitsAndStopsAfterClose() {
        FakeWorld fake = new FakeWorld();
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, false);
        boolean done = false;
        for (int i = 0; i < 5000 && !done; i++) {
            int before = fake.visits;
            done = builder.step(7);
            assertTrue(fake.visits - before <= 7);
        }
        assertTrue(done);
        assertEquals(Material.RED_CONCRETE, fake.blocks.get(new ArenaBuildPlan.Cell(0, 99, 0)));
        builder.close();
        int before = fake.visits;
        assertFalse(builder.step(7));
        assertEquals(before, fake.visits);
        assertEquals(fake.ticketsAdded, fake.ticketsRemoved);
    }

    @Test
    void fallbackRejectsForeignBlocksBeforeWriting() {
        FakeWorld fake = new FakeWorld();
        fake.blocks.put(new ArenaBuildPlan.Cell(0, 99, 0), Material.DIAMOND_BLOCK);
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, true);
        assertThrows(IllegalStateException.class, () -> {
            for (int i = 0; i < 5000; i++) builder.step(7);
        });
        builder.close();
        assertEquals(0, fake.writes);
        assertEquals(Material.DIAMOND_BLOCK, fake.blocks.get(new ArenaBuildPlan.Cell(0, 99, 0)));
        assertEquals(fake.ticketsAdded, fake.ticketsRemoved);
    }

    @Test
    void intactFallbackCanBeRebuiltWithoutChangingExistingBlocks() {
        FakeWorld fake = new FakeWorld();
        ArenaBuilder first = new ArenaBuilder(null, layout(), fake, true);
        assertTrue(finish(first));
        first.close();
        int initialWrites = fake.writes;
        ArenaBuilder second = new ArenaBuilder(null, layout(), fake, true);
        assertTrue(finish(second));
        second.close();
        assertEquals(initialWrites, fake.writes);
    }

    @Test
    void cancelledPendingChunkDoesNotWriteOrAcquireTicket() {
        FakeWorld fake = new FakeWorld();
        fake.pending = new CompletableFuture<>();
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, true);
        assertFalse(builder.step(7));
        builder.close();
        fake.pending.complete(null);
        assertFalse(builder.step(7));
        assertEquals(0, fake.writes);
        assertEquals(0, fake.ticketsAdded);
    }

    @Test
    void completedChunkTicketIsReleasedWhenCancelledBeforePolling() {
        FakeWorld fake = new FakeWorld();
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, false);
        assertFalse(builder.step(7));
        assertEquals(1, fake.ticketsAdded);
        builder.close();
        assertEquals(1, fake.ticketsRemoved);
    }

    @Test
    void fallbackRechecksBlocksChangedAfterPreflight() {
        FakeWorld fake = new FakeWorld();
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, true);
        while (fake.loads < 5) builder.step(7);
        ArenaBuildPlan.Cell changed = new ArenaBuildPlan.Cell(0, 99, 0);
        fake.blocks.put(changed, Material.DIAMOND_BLOCK);
        assertThrows(IllegalStateException.class, () -> finish(builder));
        builder.close();
        assertEquals(Material.DIAMOND_BLOCK, fake.blocks.get(changed));
    }

    @Test
    void unloadedChunkIsReacquiredBeforeMoreBlockAccess() {
        FakeWorld fake = new FakeWorld();
        ArenaBuilder builder = new ArenaBuilder(null, layout(), fake, false);
        builder.step(7);
        builder.step(7);
        fake.available = false;
        assertDoesNotThrow(() -> builder.step(7));
        fake.available = true;
        assertTrue(finish(builder));
        builder.close();
    }

    private static boolean finish(ArenaBuilder builder) {
        for (int i = 0; i < 10000; i++) if (builder.step(7)) return true;
        return false;
    }

    private static ArenaLayout layout() {
        return new ArenaLayout(ToffyEventType.TNT_TAG, 0, 100, 0, 1, 1, 1, 0);
    }

    private static final class FakeWorld implements ArenaBuilder.Access {
        private final Map<ArenaBuildPlan.Cell, Material> blocks = new HashMap<>();
        private int visits, writes, ticketsAdded, ticketsRemoved, loads;
        private CompletableFuture<Boolean> pending;
        private boolean available = true;

        public int minHeight() { return 0; }
        public int maxHeight() { return 256; }
        public CompletableFuture<Boolean> load(int x, int z) {
            loads++;
            if (pending != null) return pending;
            ticketsAdded++;
            return CompletableFuture.completedFuture(true);
        }
        public boolean loaded(int x, int z) { return available; }
        public void removeTicket(int x, int z) { ticketsRemoved++; }
        public Material get(ArenaBuildPlan.Cell cell) { if (!available) throw new IllegalStateException("unloaded"); visits++; return blocks.getOrDefault(cell, Material.AIR); }
        public void set(ArenaBuildPlan.Cell cell, Material material) { writes++; blocks.put(cell, material); }
    }
}
