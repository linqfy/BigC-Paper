package dev.linqfy.bigCasares.items.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCatalogLoaderTest {

    @Test
    void loadsShapelessRecipe() throws Exception {
        Path catalog = tempDir.resolve("catalog");
        Files.createDirectories(catalog);
        Files.writeString(catalog.resolve("golden_amethyst_arrow.yml"), """
            id: golden_tipped_amethyst_arrow
            mechanic: golden-amethyst-arrow
            material: ARROW
            item-model: bigcasares:golden_tipped_amethyst_arrow
            display:
              translation-key: item.bigcasares.golden_tipped_amethyst_arrow
              fallback-name: Flecha de amatista con punta de oro
              lore: []
            max-stack-size: 64
            recipe:
              type: shapeless
              key: golden_tipped_amethyst_arrow_recipe
              result-amount: 1
              ingredients: [AMETHYST_SHARD, GOLD_INGOT, ARROW]
            appearance:
              java-item-definition: java/assets/bigcasares/items/golden_tipped_amethyst_arrow.json
              bedrock-texture: bedrock/textures/item/golden_tipped_amethyst_arrow.png
            """);

        ItemRecipeDefinition recipe = new ItemCatalogLoader().load(catalog)
            .require("golden_tipped_amethyst_arrow").recipeDefinition().orElseThrow();

        assertEquals(RecipeType.SHAPELESS, recipe.type());
        assertEquals(List.of("AMETHYST_SHARD", "GOLD_INGOT", "ARROW"), recipe.shapelessIngredients());
    }

    @TempDir
    Path tempDir;

    @Test
    void loadsArmorAndKeepsRevisionsOfItemsWithoutIt() throws Exception {
        Files.writeString(tempDir.resolve("helmet.yml"), """
            id: ac_primordial_helmet
            mechanic: alexs-caves
            material: LEATHER_HELMET
            item-model: alexscaves:primordial_helmet
            display:
              translation-key: item.alexscaves.primordial_helmet
              fallback-name: Casco primordial
              lore: []
            max-stack-size: 1
            components:
              max-damage: 220
              armor:
                slot: head
                defense: 2.0
                equipment-model: alexscaves:primordial
            appearance:
              java-item-definition: java/assets/alexscaves/items/primordial_helmet.json
              bedrock-texture: bedrock/textures/alexscaves/item/primordial_helmet.png
            """);

        ItemArmorDefinition armor = new ItemCatalogLoader().load(tempDir).require("ac_primordial_helmet")
            .armorDefinition().orElseThrow();

        assertEquals("HEAD", armor.slot());
        assertEquals(2.0, armor.defense());
        assertEquals(0.0, armor.toughness(), "toughness defaults to 0");
        assertEquals("alexscaves:primordial", armor.equipmentModel());
        assertThrows(IllegalArgumentException.class, () -> new ItemArmorDefinition("OFFHAND", 1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new ItemArmorDefinition("HEAD", -1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new ItemArmorDefinition("HEAD", 1, 0, null, 1.5, null, 0));
        ItemArmorDefinition diving = new ItemArmorDefinition("LEGS", 5, 0, null, 0.5, "copper_ingot", 25);
        assertEquals("COPPER_INGOT", diving.repairItem());
        assertEquals(0.5, diving.waterEfficiency());
        assertEquals(25, diving.enchantability());
        assertEquals("ItemArmorDefinition[slot=HEAD, defense=2.0, toughness=0.5, equipmentModel=alexscaves:hazmat_suit]",
            new ItemArmorDefinition("HEAD", 2, 0.5, "alexscaves:hazmat_suit").revisionKey());
        assertTrue(diving.revisionKey().contains("repair=COPPER_INGOT"));
    }

    @Test
    void loadsSahursBatCombatValuesWithoutARecipe() throws Exception {
        Files.writeString(tempDir.resolve("sahurs_bat.yml"), """
            id: sahurs_bat
            mechanic: sahurs-bat
            material: IRON_SWORD
            item-model: bigcasares:sahurs_bat
            display:
              translation-key: item.bigcasares.sahurs_bat
              fallback-name: Bate de Sahur
              lore: []
            max-stack-size: 1
            components:
              max-damage: 250
              combat:
                attack-damage: 6.0
                attack-speed: 3.2
            appearance:
              java-item-definition: java/assets/bigcasares/items/sahurs_bat.json
              bedrock-texture: bedrock/textures/item/sahurs_bat.png
            """);

        CustomItemDefinition definition = new ItemCatalogLoader().load(tempDir).require("sahurs_bat");

        assertEquals("IRON_SWORD", definition.material());
        assertEquals(1, definition.maxStackSize());
        assertEquals(250, definition.maxDamage());
        assertEquals(6.0, definition.combatDefinition().orElseThrow().attackDamage());
        assertEquals(3.2, definition.combatDefinition().orElseThrow().attackSpeed());
        assertTrue(definition.recipeDefinition().isEmpty());
    }

    @Test
    void loadsACompleteDefinitionIntoAnImmutableCatalog() throws Exception {
        Files.writeString(tempDir.resolve("copper_apple.yml"), """
            id: copper_apple
            mechanic: copper-apple
            material: APPLE
            item-model: bigcasares:copper_apple
            legacy-custom-model-data: 1001
            display:
              translation-key: item.bigcasares.copper_apple
              fallback-name: Manzana de Cobre
              lore: []
            max-stack-size: 64
            components:
              food:
                nutrition: 4
                saturation: 1.2
                can-always-eat: true
            recipe:
              key: copper_apple_recipe
              result-amount: 1
              shape: [CCC, CAC, CCC]
              ingredients:
                C: COPPER_INGOT
                A: APPLE
            appearance:
              java-item-definition: java/assets/bigcasares/items/copper_apple.json
              bedrock-texture: bedrock/textures/item/copper_apple.png
            """);

        CustomItemCatalog catalog = new ItemCatalogLoader().load(tempDir);
        CustomItemDefinition definition = catalog.require("copper_apple");

        assertEquals(1, catalog.size());
        assertEquals("APPLE", definition.material());
        assertEquals("bigcasares:copper_apple", definition.itemModel());
        assertEquals(1001, definition.legacyCustomModelData().orElseThrow());
        assertEquals(4, definition.foodDefinition().orElseThrow().nutrition());
        assertEquals("CCC", definition.recipeDefinition().orElseThrow().shape().getFirst());
    }

    @Test
    void loadsOnePointMaximumDamageForNukeShot() throws Exception {
        Files.writeString(tempDir.resolve("nuke_shot.yml"), """
            id: nuke_shot
            mechanic: nuke-shot
            material: FISHING_ROD
            item-model: bigcasares:nuke_shot
            display:
              translation-key: item.bigcasares.nuke_shot
              fallback-name: Nuke Shot
              lore: []
            max-stack-size: 1
            components:
              max-damage: 1
            appearance:
              java-item-definition: java/assets/bigcasares/items/nuke_shot.json
              bedrock-texture: bedrock/textures/item/nuke_shot.png
            """);

        CustomItemDefinition definition = new ItemCatalogLoader().load(tempDir).require("nuke_shot");

        assertEquals(1, definition.maxStackSize());
        assertEquals(1, definition.maxDamage());
    }

    @Test
    void rejectsDefinitionsMissingRequiredValues() throws Exception {
        Files.writeString(tempDir.resolve("broken.yml"), """
            id: smoke_bomb
            mechanic: smoke-bomb
            material: SNOWBALL
            """);

        assertThrows(IllegalArgumentException.class, () -> new ItemCatalogLoader().load(tempDir));
    }

    @Test
    void rejectsAnEmptyDefinitionDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ItemCatalogLoader().load(tempDir));
    }

    @Test
    void rejectsDuplicateIdsUnsafeIdsAndIncompleteRecipes() throws Exception {
        Files.writeString(tempDir.resolve("first.yml"), definition("copper_apple", "copper-apple", ""));
        Files.writeString(tempDir.resolve("second.yml"), definition("copper_apple", "copper-apple", ""));

        assertThrows(IllegalArgumentException.class, () -> new ItemCatalogLoader().load(tempDir));

        Files.delete(tempDir.resolve("second.yml"));
        Files.writeString(tempDir.resolve("unsafe.yml"), definition("unsafe id", "copper-apple", ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemCatalogLoader().load(tempDir));

        Files.delete(tempDir.resolve("unsafe.yml"));
        Files.writeString(tempDir.resolve("broken_recipe.yml"), definition("copper_apple", "copper-apple", """
            recipe:
              key: copper_apple_recipe
              result-amount: 1
              shape: ["X"]
              ingredients:
                A: APPLE
            """));
        assertThrows(IllegalArgumentException.class, () -> new ItemCatalogLoader().load(tempDir));
    }

    private static String definition(String id, String mechanic, String extra) {
        return """
            id: %s
            mechanic: %s
            material: APPLE
            item-model: bigcasares:copper_apple
            display:
              translation-key: item.bigcasares.copper_apple
              fallback-name: Manzana de Cobre
              lore: []
            max-stack-size: 64
            appearance:
              java-item-definition: java/assets/bigcasares/items/copper_apple.json
              bedrock-texture: bedrock/textures/item/copper_apple.png
            %s
            """.formatted(id, mechanic, extra);
    }
}
