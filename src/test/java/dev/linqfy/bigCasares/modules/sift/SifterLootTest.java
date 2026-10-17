package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.modules.sift.SifterLoot.Drop;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SifterLootTest {

    private static final SiftData DATA = SiftData.fromClasspath();

    private static SifterLoot loot() {
        return SifterLoot.parse(DATA.json("loot_table/entities/sifter.json"));
    }

    /** Scripted draws: counts by bound, one float for the Looting bonus. */
    private static final class ScriptedRandom extends Random {
        private final IntUnaryOperator ints;
        private final float floatValue;

        ScriptedRandom(IntUnaryOperator ints, float floatValue) {
            this.ints = ints;
            this.floatValue = floatValue;
        }

        @Override
        public int nextInt(int bound) {
            return ints.applyAsInt(bound);
        }

        @Override
        public float nextFloat() {
            return floatValue;
        }
    }

    @Test
    void theTableIsOneToTwoRawSifterMeat() {
        SifterLoot loot = loot();
        assertEquals("the_sift:raw_sifter_meat", loot.item());
        assertEquals(SifterLoot.RAW_ITEM, loot.item());
        assertEquals(1, loot.minimumCount());
        assertEquals(2, loot.maximumCount());
    }

    @Test
    void withoutLootingOneOrTwoMeatAreRolled() {
        SifterLoot loot = loot();
        Set<Integer> counts = new HashSet<>();
        Random random = new Random(42);
        for (int roll = 0; roll < 500; roll++) {
            List<Drop> drops = loot.roll(random, 0, false);
            assertEquals(1, drops.size());
            assertEquals(SifterLoot.RAW_ITEM, drops.get(0).item());
            counts.add(drops.get(0).count());
        }
        assertEquals(Set.of(1, 2), counts);
    }

    @Test
    void countIsUniformOverTheInclusiveRange() {
        SifterLoot loot = loot();
        assertEquals(1, loot.roll(new ScriptedRandom(bound -> {
            assertEquals(2, bound);
            return 0;
        }, 0.0F), 0, false).get(0).count());
        assertEquals(2, loot.roll(new ScriptedRandom(bound -> bound - 1, 0.0F), 0, false).get(0).count());
    }

    @Test
    void lootingAddsTheRoundedLevelTimesAUniformBetweenZeroAndOne() {
        SifterLoot loot = loot();
        // 1 base meat; level 3 * 0.5 = 1.5 rounds to 2; level 3 * 0.49 = 1.47 rounds to 1; 0.0 adds nothing.
        assertEquals(3, loot.roll(new ScriptedRandom(bound -> 0, 0.5F), 3, false).get(0).count());
        assertEquals(2, loot.roll(new ScriptedRandom(bound -> 0, 0.49F), 3, false).get(0).count());
        assertEquals(1, loot.roll(new ScriptedRandom(bound -> 0, 0.0F), 3, false).get(0).count());
        assertEquals(2 + 3, loot.roll(new ScriptedRandom(bound -> bound - 1, 0.99999F), 3, false).get(0).count());
        // No Looting, no bonus whatever the float says.
        assertEquals(1, loot.roll(new ScriptedRandom(bound -> 0, 0.99F), 0, false).get(0).count());
    }

    @Test
    void lootingStaysWithinTheBoundsOverManyRolls() {
        SifterLoot loot = loot();
        Random random = new Random(5);
        Set<Integer> counts = new HashSet<>();
        for (int roll = 0; roll < 2000; roll++) {
            int count = loot.roll(random, 3, false).get(0).count();
            assertTrue(count >= 1 && count <= 2 + 3, "count " + count);
            counts.add(count);
        }
        assertEquals(Set.of(1, 2, 3, 4, 5), counts);
    }

    @Test
    void aSifterThatDiesOnFireDropsCookedMeatWithTheSameCount() {
        SifterLoot loot = loot();
        Drop drop = loot.roll(new ScriptedRandom(bound -> bound - 1, 0.5F), 2, true).get(0);
        assertEquals(SifterLoot.COOKED_ITEM, drop.item());
        assertEquals(2 + 1, drop.count());
        assertEquals(SifterLoot.RAW_ITEM, loot.roll(new Random(1), 0, false).get(0).item());
    }

    @Test
    void theCookedItemIsWhatTheModsSmeltingRecipeMakes() {
        JsonObject recipe = DATA.json("recipe/sifter_meat_smelting.json");
        assertEquals("minecraft:smelting", recipe.get("type").getAsString());
        assertEquals(SifterLoot.RAW_ITEM, recipe.get("ingredient").getAsString());
        assertEquals(SifterLoot.COOKED_ITEM, recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void bothMeatsAreContentItemsAndNothingElseCanDrop() {
        Set<String> content = new HashSet<>();
        for (ContentItem item : SiftItems.items()) {
            content.add(item.id().asString());
        }
        assertTrue(content.contains(SifterLoot.RAW_ITEM));
        assertTrue(content.contains(SifterLoot.COOKED_ITEM));
        Random random = new Random(9);
        for (int roll = 0; roll < 200; roll++) {
            for (Drop drop : loot().roll(random, roll % 4, roll % 2 == 0)) {
                assertTrue(drop.item().equals(SifterLoot.RAW_ITEM) || drop.item().equals(SifterLoot.COOKED_ITEM), drop.item());
            }
        }
    }

    @Test
    void aTableOfAnotherShapeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SifterLoot.parse(JsonParser.parseString("""
            {"pools": []}""").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> SifterLoot.parse(JsonParser.parseString("""
            {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:bone",
              "modifier": [{"type": "minecraft:set_nbt"}]}]}]}""").getAsJsonObject()));
    }

    @Test
    void vanillaSpellingOfTheFunctionsKeyIsAccepted() {
        SifterLoot loot = SifterLoot.parse(JsonParser.parseString("""
            {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "the_sift:raw_sifter_meat",
              "functions": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 4}}]}]}]}
            """).getAsJsonObject());
        assertEquals(3, loot.minimumCount());
        assertEquals(4, loot.maximumCount());
    }
}
