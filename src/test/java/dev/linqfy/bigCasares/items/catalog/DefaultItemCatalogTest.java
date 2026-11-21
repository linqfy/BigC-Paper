package dev.linqfy.bigCasares.items.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultItemCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void packagedDefaultsSeedAndValidateEveryCatalogItem() {
        List<String> names = List.of(
            "copper_apple.yml",
            "smoke_bomb.yml",
            "prismarine_arrow.yml",
            "nexus.yml",
            "echo_arrow.yml",
            "golden_tipped_amethyst_arrow.yml",
            "tracker_compass.yml",
            "nuke_shot.yml",
            "sahurs_bat.yml",
            "potassium_nitrate.yml",
            "nitric_acid.yml",
            "grappling_hook_1.yml",
            "grappling_hook_2.yml",
            "grappling_hook_3.yml",
            "grappling_hook_4.yml",
            "grappling_hook_5.yml",
            "grappling_hook_6.yml",
            "glider_tier_1.yml",
            "glider_tier_2.yml",
            "glider_tier_3.yml",
            "glider_tier_4.yml",
            "glider_tier_5.yml",
            "glider_tier_6.yml",
            "keep_inventory_totem.yml",
            "hate_fragment_greek.yml",
            "boss_summoner_greek.yml",
            "hate_fragment_norse.yml",
            "boss_summoner_norse.yml",
            "hate_fragment_medieval.yml",
            "boss_summoner_medieval.yml",
            "hate_fragment_egyptian.yml",
            "boss_summoner_egyptian.yml",
            "hate_fragment_mesopotamian.yml",
            "boss_summoner_mesopotamian.yml",
            "hate_fragment_japanese.yml",
            "boss_summoner_japanese.yml",
            "hate_fragment_mesoamerican.yml",
            "boss_summoner_mesoamerican.yml",
            "hate_fragment_arabian.yml",
            "boss_summoner_arabian.yml",
            "hate_fragment_andean.yml",
            "boss_summoner_andean.yml",
            "mandinga_guitar.yml"
        );
        ItemCatalogSeeder seeder = new ItemCatalogSeeder(names);
        seeder.seed(name -> resource("content/items/" + name), tempDir.resolve("items"));

        CustomItemCatalog catalog = new ItemCatalogLoader().load(tempDir.resolve("items"));
        new ItemCatalogValidator().validate(catalog, Path.of("resourcepack"));

        assertEquals(43, catalog.size());
        assertTrue(catalog.find("copper_apple").isPresent());
        assertTrue(catalog.find("smoke_bomb").isPresent());
        assertTrue(catalog.find("prismarine_arrow").isPresent());
        assertTrue(catalog.find("nexus").isPresent());
        assertTrue(catalog.find("echo_arrow").isPresent());
        assertTrue(catalog.find("golden_tipped_amethyst_arrow").isPresent());
        assertTrue(catalog.find("tracker_compass").isPresent());
        assertTrue(catalog.find("nuke_shot").isPresent());
        assertTrue(catalog.find("sahurs_bat").isPresent());
        assertTrue(catalog.find("potassium_nitrate").isPresent());
        assertTrue(catalog.find("nitric_acid").isPresent());
        for (int tier = 1; tier <= 6; tier++) {
            assertTrue(catalog.find("grappling_hook_" + tier).isPresent(),
                "grappling_hook_" + tier + " must be seeded with the defaults");
        }
        for (int tier = 1; tier <= 6; tier++) {
            assertTrue(catalog.find("glider_tier_" + tier).isPresent(),
                "glider_tier_" + tier + " must be seeded with the defaults");
        }
        CustomItemDefinition potassium = catalog.require("potassium_nitrate");
        assertEquals("catalog-material", potassium.mechanic());
        assertEquals("GUNPOWDER", potassium.recipeDefinition().orElseThrow().resultMaterial());
        assertEquals(List.of("bigcasares:potassium_nitrate", "SUGAR", "COAL|CHARCOAL"),
            potassium.recipeDefinition().orElseThrow().shapelessIngredients());
        CustomItemDefinition nukeShot = catalog.require("nuke_shot");
        assertEquals(1, nukeShot.maxStackSize());
        assertEquals("FISHING_ROD", nukeShot.material());
        assertEquals("minecraft:fishing_rod", nukeShot.itemModel());
        assertEquals(null, nukeShot.maxDamage());
        assertEquals(List.of("§cUn disparo. Trescientos cincuenta problemas."),
            nukeShot.display().lore());
        CustomItemDefinition bat = catalog.require("sahurs_bat");
        assertEquals(6.0, bat.combatDefinition().orElseThrow().attackDamage());
        assertEquals(5.0, bat.combatDefinition().orElseThrow().attackSpeed());
        assertTrue(bat.recipeDefinition().isEmpty());
    }

    @Test
    void sahursBatItemTexturesExactlyMatchTheUploadedSakurBytes() throws Exception {
        byte[] expected = Files.readAllBytes(Path.of("agent_uploads/boss1/sakur.png"));

        assertArrayEquals(expected, Files.readAllBytes(
            Path.of("resourcepack/java/assets/bigcasares/textures/item/sahurs_bat.png")));
        assertArrayEquals(expected, Files.readAllBytes(
            Path.of("resourcepack/bedrock/textures/item/sahurs_bat.png")));
    }

    private static InputStream resource(String path) {
        InputStream input = DefaultItemCatalogTest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("missing test resource: " + path);
        }
        return input;
    }
}
