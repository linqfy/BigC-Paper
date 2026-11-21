package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedPickerTest {

    private record Option(String name, double weight) {
    }

    private static final List<Option> OPTIONS = List.of(
        new Option("a", 1.0), new Option("b", 0.0), new Option("c", 3.0), new Option("d", Double.NaN));

    @Test
    void rollsMapToTheCumulativeWeights() {
        assertEquals("a", WeightedPicker.pick(OPTIONS, Option::weight, 0.0).orElseThrow().name());
        assertEquals("a", WeightedPicker.pick(OPTIONS, Option::weight, 0.249).orElseThrow().name());
        assertEquals("c", WeightedPicker.pick(OPTIONS, Option::weight, 0.25).orElseThrow().name());
        assertEquals("c", WeightedPicker.pick(OPTIONS, Option::weight, 0.9999).orElseThrow().name());
        assertEquals("c", WeightedPicker.pick(OPTIONS, Option::weight, 1.0).orElseThrow().name());
    }

    @Test
    void zeroOrInvalidWeightsNeverWin() {
        Map<String, Integer> counts = new HashMap<>();
        for (int step = 0; step < 1000; step++) {
            counts.merge(WeightedPicker.pick(OPTIONS, Option::weight, step / 1000.0).orElseThrow().name(), 1,
                Integer::sum);
        }
        assertEquals(250, counts.get("a"));
        assertEquals(750, counts.get("c"));
        assertTrue(!counts.containsKey("b") && !counts.containsKey("d"));
    }

    @Test
    void nothingToPickWhenEveryWeightIsZero() {
        assertTrue(WeightedPicker.pick(List.of(new Option("x", 0.0)), Option::weight, 0.5).isEmpty());
        assertTrue(WeightedPicker.pick(List.<Option>of(), Option::weight, 0.5).isEmpty());
    }

    @Test
    void cultureSpawnPoolsAlwaysYieldOwnMobs() {
        MythicMobCatalog catalog = MythicMobCatalog.defaults();
        for (MythicCulture culture : MythicCulture.values()) {
            for (double roll : new double[] {0.0, 0.5, 0.999}) {
                MythicMobType picked = WeightedPicker.pick(catalog.ofCulture(culture), MythicMobType::weight, roll)
                    .orElseThrow();
                assertEquals(culture, picked.culture());
            }
        }
    }
}
