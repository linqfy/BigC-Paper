package dev.linqfy.bigCasares.modules.villagermood;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerWorkBatchTest {
    @Test
    void spreadsWorkAcrossTicksWithoutDroppingEntries() {
        VillagerWorkBatch<Integer> batch = new VillagerWorkBatch<>();
        batch.addAll(List.of(1, 2, 3, 4, 5));
        List<Integer> seen = new ArrayList<>();
        batch.drain(2, seen::add);
        assertEquals(List.of(1, 2), seen);
        batch.drain(2, seen::add);
        assertEquals(List.of(1, 2, 3, 4), seen);
        batch.drain(2, seen::add);
        assertEquals(List.of(1, 2, 3, 4, 5), seen);
        assertTrue(batch.isEmpty());
    }
}
