package dev.linqfy.bigCasares.modules.alexscaves.core.block.potted;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.WaterloggedIndex;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PottedPlantTest {
    @Test
    void tableCoversTheTenPottedBlocksWithVanillaPots() {
        assertEquals(10, PottedPlantTable.all().size());
        PottedPlantTable.all().values().forEach(sub -> {
            assertTrue(sub.potMaterial().startsWith("POTTED_"), sub.potMaterial());
            assertTrue(sub.potMaterial().endsWith(sub.plantMaterial()), sub.potMaterial());
        });
    }

    @Test
    void itemsMapToTheirSubstituteAndBack() {
        assertEquals("POTTED_FERN", PottedPlantTable.forItem("ac_curly_fern").orElseThrow().potMaterial());
        assertEquals("POTTED_DEAD_BUSH", PottedPlantTable.forItem("ac_thornwood_sapling").orElseThrow().potMaterial());
        assertEquals("ac_underweed", PottedPlantTable.itemId(PottedPlantTable.forPlant("underweed").orElseThrow().plant()));
        assertTrue(PottedPlantTable.forItem("ac_limestone").isEmpty());
        assertTrue(PottedPlantTable.forItem(null).isEmpty());
        assertTrue(PottedPlantTable.forPlant("nothing").isEmpty());
    }

    @Test
    void storeRoundTripsAndKeepsPositionsApart() {
        int a = WaterloggedIndex.pack(33, 64, -17);
        int b = WaterloggedIndex.pack(34, -5, 2);
        String saved = PottedPlantStore.with(PottedPlantStore.with(null, a, "cycad"), b, "flytrap");
        assertEquals(Map.of(a, "cycad", b, "flytrap"), PottedPlantStore.decode(saved));
        assertEquals("flytrap", PottedPlantStore.plantAt(saved, b));
        assertEquals(PottedPlantStore.encode(PottedPlantStore.decode(saved)), saved);
    }

    @Test
    void removingTheLastEntryClearsTheKey() {
        int a = WaterloggedIndex.pack(1, 1, 1);
        String saved = PottedPlantStore.with(null, a, "cycad");
        assertNull(PottedPlantStore.without(saved, a));
        assertEquals(saved, PottedPlantStore.without(saved, a + 1));
    }

    @Test
    void damagedEntriesAreDropped() {
        assertEquals(Map.of(5, "cycad"), PottedPlantStore.decode("x:cycad;5:cycad;7:;:a;9:Bad Name"));
        assertTrue(PottedPlantStore.decode("").isEmpty());
        assertTrue(PottedPlantStore.decode(null).isEmpty());
    }

    @Test
    void marksAreStaleWhenTheBlockIsNotTheMatchingPot() {
        int a = WaterloggedIndex.pack(1, 2, 3);
        String saved = PottedPlantStore.with(null, a, "curly_fern");
        assertTrue(!PottedPlantStore.isStale(saved, a, "POTTED_FERN"));
        assertTrue(PottedPlantStore.isStale(saved, a, "FLOWER_POT"));
        assertTrue(PottedPlantStore.isStale(saved, a, "POTTED_DEAD_BUSH"));
        assertTrue(PottedPlantStore.isStale(saved, a, "AIR"));
        assertTrue(!PottedPlantStore.isStale(saved, a + 1, "POTTED_DEAD_BUSH"));
        assertTrue(!PottedPlantStore.isStale(null, a, "POTTED_FERN"));
        assertTrue(PottedPlantStore.isStale(PottedPlantStore.with(null, a, "unknown_plant"), a, "POTTED_FERN"));
    }
}
