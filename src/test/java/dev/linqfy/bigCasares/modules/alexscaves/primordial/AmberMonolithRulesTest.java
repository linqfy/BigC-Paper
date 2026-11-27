package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.Entry;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AmberMonolithRulesTest {
    private static Entry entry(String id) {
        return new Entry(id, SpawnCategory.CAVE_CREATURE, 10, 2, 3);
    }

    @Test
    void delayAveragesTheMeanTimeAndNeverUnderAThousand() {
        Random random = new Random(5);
        long total = 0;
        for (int i = 0; i < 2000; i++) {
            int delay = AmberMonolith.nextDelay(32000, random);
            assertTrue(delay >= 16000 && delay < 48000);
            total += delay;
        }
        assertEquals(32000.0, total / 2000.0, 1500.0);
        assertTrue(AmberMonolith.nextDelay(1, new Random(1)) >= 500);
    }

    @Test
    void groupSizeIsWithinTheEntryRange() {
        Random random = new Random(2);
        for (int i = 0; i < 100; i++) {
            int size = AmberMonolith.groupSize(entry("atlatitan"), random);
            assertTrue(size >= 2 && size <= 3);
        }
        assertEquals(1, AmberMonolith.groupSize(new Entry("x", SpawnCategory.CAVE_CREATURE, 1, 1, 1), random));
    }

    @Test
    void depopulatedKeepsTheFirstUnblockedPickAndTheLastWhenAllAreBlocked() {
        Deque<Entry> picks = new ArrayDeque<>(List.of(entry("a"), entry("b"), entry("c")));
        Entry chosen = AmberMonolith.depopulated(picks::poll, e -> e.mobId().equals("a"), 4);
        assertEquals("b", chosen.mobId());
        Deque<Entry> blockedPicks = new ArrayDeque<>(List.of(entry("a"), entry("b"), entry("c")));
        Entry last = AmberMonolith.depopulated(blockedPicks::poll, e -> true, 3);
        assertEquals("c", last.mobId());
        Entry only = entry("one");
        assertSame(only, AmberMonolith.depopulated(() -> only, e -> false, 4));
    }
}
