package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.sift.SiftFixedLoot.Context;
import dev.linqfy.bigCasares.modules.sift.SiftFixedLoot.Drop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiftFixedLootTest {

    private static final SiftData DATA = SiftData.fromClasspath();

    private static SiftFixedLoot table(String path) {
        return SiftFixedLoot.parse(DATA.json("loot_table/" + path + ".json"));
    }

    /** A random source whose float draws are scripted. */
    private static final class Floats extends Random {
        private final float value;

        Floats(float value) {
            this.value = value;
        }

        @Override
        public float nextFloat() {
            return value;
        }
    }

    @Test
    void theDarkSnifferDigsOneSculkflowerSeedEveryTime() {
        SiftFixedLoot loot = table("gameplay/dark_sniffer_digging");
        for (int roll = 0; roll < 50; roll++) {
            assertEquals(List.of(new Drop("the_sift:sculkflower_seeds", 1)), loot.roll(new Random(roll), Context.none()));
        }
    }

    @Test
    void anInfectedSnifferDigsUpNothing() {
        assertEquals(List.of(), table("gameplay/infected_sniffer_digging").roll(new Random(1), Context.none()));
        assertEquals(List.of(), table("gameplay/infected_sniffer_digging").possibleDrops());
    }

    @Test
    void aDarkSnifferDropsOneSculk() {
        assertEquals(List.of(new Drop("minecraft:sculk", 1)), table("entities/dark_sniffer").roll(new Random(1), Context.none()));
    }

    @Test
    void aSculkflowerDropsItself() {
        SiftFixedLoot loot = table("blocks/sculkflower");
        assertEquals(List.of(new Drop("the_sift:sculkflower", 1)),
            loot.roll(new Random(1), new Context(null, "the_sift:sculkflower", Map.of())));
    }

    @Test
    void aGrowingCropDropsOnlyItsSeedsAtAgesZeroAndOne() {
        SiftFixedLoot loot = table("blocks/sculkflower_crop");
        for (String age : List.of("0", "1")) {
            assertEquals(List.of(new Drop("the_sift:sculkflower_seeds", 1)),
                loot.roll(new Random(1), new Context(null, "the_sift:sculkflower_crop", Map.of("age", age))), "age " + age);
        }
    }

    @Test
    void theMatureCropTableWouldAddTheFlowerAtAgeTwoButOnlyForThatBlock() {
        SiftFixedLoot loot = table("blocks/sculkflower_crop");
        assertEquals(List.of(new Drop("the_sift:sculkflower_seeds", 1), new Drop("the_sift:sculkflower", 1)),
            loot.roll(new Random(1), new Context(null, "the_sift:sculkflower_crop", Map.of("age", "2"))));
        assertEquals(List.of(new Drop("the_sift:sculkflower_seeds", 1)),
            loot.roll(new Random(1), new Context(null, "the_sift:other", Map.of("age", "2"))));
    }

    @Test
    void anExplosionDestroysTheDropWithTheChanceOneOverItsRadius() {
        SiftFixedLoot loot = table("blocks/sculkflower");
        Context context = new Context(0.25F, "the_sift:sculkflower", Map.of());
        assertEquals(1, loot.roll(new Floats(0.25F), context).size(), "survives when the draw is at most the yield");
        assertEquals(0, loot.roll(new Floats(0.2501F), context).size());
        assertEquals(1, loot.roll(new Floats(0.99F), Context.none()).size(), "no explosion, no loss");
    }

    @Test
    void tablesOfAnotherShapeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SiftFixedLoot.parse(JsonParser.parseString(
            "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"x\","
                + "\"modifier\":[{\"type\":\"minecraft:set_count\"}]}]}]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> SiftFixedLoot.parse(JsonParser.parseString(
            "{\"pools\":[{\"rolls\":{\"type\":\"minecraft:uniform\"},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"x\"}]}]}")
            .getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> SiftFixedLoot.parse(JsonParser.parseString(
            "{\"pools\":[{\"rolls\":1,\"condition\":{\"type\":\"minecraft:random_chance\"},"
                + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"x\"}]}]}").getAsJsonObject()).roll(new Random(), Context.none()));
        JsonObject empty = new JsonObject();
        assertThrows(IllegalArgumentException.class, () -> SiftFixedLoot.parse(empty));
    }
}
