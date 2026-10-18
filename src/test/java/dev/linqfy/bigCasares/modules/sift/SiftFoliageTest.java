package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftFoliageTest {

    private static CustomBlockDefinition definition(String path) {
        return SiftBlocks.definitions().stream().filter(block -> block.id().path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void everyWillowLogAndWoodVariantIsARealBlockAndCountsAsALog() {
        assertEquals(8, SiftFoliage.LOGS.size());
        for (String path : SiftFoliage.LOGS) {
            assertEquals(LeafDecay.Kind.LOG, SiftFoliage.kindOf(path), path);
            definition(path);
        }
    }

    @Test
    void onlyFoliageCarriesTheDistanceAndPlanksStopIt() {
        assertEquals(LeafDecay.Kind.LEAVES, SiftFoliage.kindOf("overgrown_willow_foliage"));
        assertEquals(LeafDecay.Kind.OTHER, SiftFoliage.kindOf("overgrown_willow_planks"));
        assertEquals(LeafDecay.Kind.OTHER, SiftFoliage.kindOf("siftslate"));
    }

    @Test
    void decayDropsWhatTheModsLootTableSaysWhichIsTheFoliageItself() throws Exception {
        CustomBlockDefinition foliage = definition(SiftFoliage.FOLIAGE);
        List<SiftFoliage.Stack> drops = SiftFoliage.drops(foliage, new Random(1));
        assertEquals(List.of(new SiftFoliage.Stack("the_sift:overgrown_willow_foliage", 1)), drops);

        JsonObject table = SiftData.fromClasspath().json("loot_table/blocks/overgrown_willow_foliage.json");
        JsonObject pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals(1, pool.get("rolls").getAsInt());
        assertEquals(1, pool.getAsJsonArray("entries").size());
        assertEquals(drops.get(0).item(), pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString(),
            "the port's drop matches the mod's single entry");
    }

    @Test
    void definitionsWithListedDropsRollTheirCounts() {
        CustomBlockDefinition growth = definition("dry_healthy_sculk_growth");
        List<SiftFoliage.Stack> drops = SiftFoliage.drops(growth, new Random(2));
        assertEquals(List.of(new SiftFoliage.Stack("the_sift:dry_healthy_sculk", 1)), drops);
        assertTrue(SiftFoliage.drops(definition("siftslate"), new Random(3)).stream().allMatch(stack -> stack.count() >= 1));
    }
}
