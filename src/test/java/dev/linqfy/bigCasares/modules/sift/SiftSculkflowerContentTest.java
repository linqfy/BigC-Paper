package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Sculkflower and the Dark Sniffer need from items, blocks, the datapack and the resource pack. */
class SiftSculkflowerContentTest {

    private static ContentItem item(String path) {
        return SiftItems.items().stream().filter(candidate -> candidate.id().path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void theSeedsAndTheFlowerAreContentItemsOnVanillaPlantables() {
        ContentItem seeds = item("sculkflower_seeds");
        assertEquals("TORCHFLOWER_SEEDS", seeds.material(), "vanilla places the torchflower crop (compostable at 30%, as in the mod)");
        assertEquals("the_sift:sculkflower_seeds", seeds.itemModel().asString());
        assertEquals("item.the_sift.sculkflower_seeds", seeds.nameKey());
        ContentItem flower = item("sculkflower");
        assertEquals("WARPED_FUNGUS", flower.material(), "vanilla places the warped fungus (compostable at 65%, as in the mod)");
        assertEquals("the_sift:sculkflower", flower.itemModel().asString());
        assertEquals(SiftSculkflowers.SEEDS.asString(), seeds.id().asString());
        assertEquals(SiftSculkflowers.FLOWER.asString(), flower.id().asString());
    }

    @Test
    void theResourcePackHasTheirItemModelsTexturesAndNames() throws Exception {
        Path assets = Path.of("resourcepack/java/assets/the_sift");
        for (String path : List.of("sculkflower_seeds", "sculkflower")) {
            assertTrue(Files.isRegularFile(assets.resolve("items/" + path + ".json")), path);
            assertTrue(Files.isRegularFile(assets.resolve("models/item/" + path + ".json")), path);
        }
        assertTrue(Files.isRegularFile(assets.resolve("textures/item/sculkflower_seeds.png")));
        assertTrue(Files.isRegularFile(assets.resolve("textures/block/sculkflower.png")));
        JsonObject lang = JsonParser.parseString(Files.readString(assets.resolve("lang/en_us.json"))).getAsJsonObject();
        assertEquals("Sculkflower Seeds", lang.get("item.the_sift.sculkflower_seeds").getAsString());
        assertEquals("Sculkflower", lang.get("item.the_sift.sculkflower").getAsString());
    }

    @Test
    void theCropStandsInAsATorchflowerCropAndItsThirdAgeIsTheFlower() {
        assertEquals(Optional.of(BlockStateSpec.of("minecraft:torchflower_crop", Map.of("age", "0"))),
            SiftBlocks.remap("sculkflower_crop", Map.of()));
        assertEquals(Optional.of(BlockStateSpec.of("minecraft:torchflower_crop", Map.of("age", "1"))),
            SiftBlocks.remap("sculkflower_crop", Map.of("age", "1")));
        assertEquals(Optional.of(BlockStateSpec.of("minecraft:warped_fungus")), SiftBlocks.remap("sculkflower_crop", Map.of("age", "2")));
        assertEquals(Optional.of(BlockStateSpec.of("minecraft:warped_fungus")), SiftBlocks.remap("sculkflower", Map.of()));
    }

    @Test
    void theStandInsMatchTheMaterialsTheRuntimeLooksFor() {
        assertEquals(org.bukkit.Material.TORCHFLOWER_CROP.getKey().asString(),
            SiftBlocks.remapType("sculkflower_crop").orElseThrow());
        assertEquals(org.bukkit.Material.WARPED_FUNGUS.getKey().asString(), SiftBlocks.remapType("sculkflower").orElseThrow());
    }

    @Test
    void everyDropOfTheSculkflowerAndDarkSnifferTablesIsAnItemTheServerHas() {
        Set<String> content = new HashSet<>();
        SiftItems.items().forEach(candidate -> content.add(candidate.id().asString()));
        SiftData data = SiftData.fromClasspath();
        for (String table : List.of("gameplay/dark_sniffer_digging", "blocks/sculkflower", "blocks/sculkflower_crop", "entities/dark_sniffer")) {
            for (SiftFixedLoot.Drop drop : SiftFixedLoot.parse(data.json("loot_table/" + table + ".json")).possibleDrops()) {
                assertTrue(drop.item().startsWith("minecraft:") || content.contains(drop.item()), table + " drops " + drop.item());
            }
        }
    }

    private static GeneratedDatapack datapack() {
        SiftWorldgen worldgen = new SiftWorldgen(SiftData.fromClasspath(), Logger.getLogger("test"));
        return new DatapackCompiler().compile(List.of(SiftDimension.create(SiftSettings.defaults(), worldgen, List.of()))).datapack();
    }

    private static Set<String> tag(GeneratedDatapack pack, String name) {
        Set<String> values = new HashSet<>();
        JsonArray array = JsonParser.parseString(pack.text("data/minecraft/tags/block/" + name + ".json")).getAsJsonObject()
            .getAsJsonArray("values");
        for (JsonElement value : array) {
            values.add(value.getAsString());
        }
        return values;
    }

    @Test
    void theDatapackLetsTheStandInsStandOnSculkAndTheSoilsAndSniffersDigSculk() {
        GeneratedDatapack pack = datapack();
        assertTrue(tag(pack, "supports_crops").containsAll(Set.of("minecraft:sculk", "minecraft:red_mushroom_block")));
        assertTrue(tag(pack, "supports_warped_fungus").containsAll(Set.of("minecraft:sculk", "minecraft:red_mushroom_block", "minecraft:farmland")));
        assertEquals(Set.of("minecraft:sculk"), tag(pack, "sniffer_diggable_block"));
        // The healthy sculk soils are red mushroom blocks.
        assertEquals("RED_MUSHROOM_BLOCK", SiftBlocks.custom("healthy_sculk").orElseThrow().state().base().material());
        assertEquals("RED_MUSHROOM_BLOCK", SiftBlocks.custom("dry_healthy_sculk").orElseThrow().state().base().material());
    }

    @Test
    void theDimensionDatapackWritesTheDarkSnifferAdvancements() {
        GeneratedDatapack pack = datapack();
        for (String path : List.of("food_chain", "planting_the_dark", "smells_creepy")) {
            assertTrue(pack.file("data/the_sift/advancement/the_sift/" + path + ".json") != null, path);
        }
    }
}
