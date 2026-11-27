package dev.linqfy.bigCasares.modules.alexscaves.core.spawn;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.Entry;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeSpawnTableTest {

    @Test
    void picksFollowWeights() {
        BiomeSpawnTable table = new BiomeSpawnTable(List.of(
            new Entry("boundroid", SpawnCategory.MONSTER, 350, 1, 2),
            new Entry("magnetron", SpawnCategory.MONSTER, 80, 1, 1)
        ));
        SplittableRandom random = new SplittableRandom(1);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 43000; i++) {
            counts.merge(table.pick(random).orElseThrow().mobId(), 1, Integer::sum);
        }
        double ratio = counts.get("boundroid") / (double) counts.get("magnetron");
        assertTrue(Math.abs(ratio - 350.0 / 80.0) < 0.3, "ratio " + ratio);
    }

    @Test
    void groupSizesStayInRange() {
        Entry entry = new Entry("vallumraptor", SpawnCategory.CAVE_CREATURE, 6, 6, 7);
        SplittableRandom random = new SplittableRandom(3);
        for (int i = 0; i < 500; i++) {
            int size = entry.groupSize(random);
            assertTrue(size == 6 || size == 7);
        }
    }

    @Test
    void emptyTablePicksNothingAndInvalidEntriesFail() {
        assertTrue(new BiomeSpawnTable(List.of()).pick(new SplittableRandom()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Entry("x", SpawnCategory.MONSTER, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Entry("x", SpawnCategory.MONSTER, 1, 3, 2));
    }

    @Test
    void caveCreatureCapUsesTheModMultiplier() {
        SpawnBudget budget = SpawnBudget.modDefaults(1.75, 1.0);
        assertEquals(17, budget.cap(SpawnCategory.CAVE_CREATURE));
        assertEquals(70, budget.cap(SpawnCategory.MONSTER));
        assertEquals(7, budget.remaining(SpawnCategory.CAVE_CREATURE, 10));
        assertEquals(0, budget.remaining(SpawnCategory.MONSTER, 99));
        assertEquals(35, SpawnBudget.modDefaults(1.75, 0.5).cap(SpawnCategory.MONSTER));
    }
}
