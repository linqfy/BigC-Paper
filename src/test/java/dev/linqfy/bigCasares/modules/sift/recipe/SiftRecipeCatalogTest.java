package dev.linqfy.bigCasares.modules.sift.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.SiftItems;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.CookingKind;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Ingredient;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeCatalog.Reason;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeCatalog.Translation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftRecipeCatalogTest {

    private static final SiftData DATA = SiftData.fromClasspath();

    /** Every vanilla item and item tag exists on the server. */
    private static boolean vanilla(String entry) {
        return entry.startsWith("minecraft:") || entry.startsWith("#minecraft:");
    }

    /**
     * The content items the port registers today that the mod's recipes can use. It is spelled out here, not read
     * from {@link SiftItems}, so the expectations below do not change when more items are added; a test further
     * down checks that every id of this set is really a registered content item.
     */
    private static final Set<String> SIFT_ITEMS = Set.of(
        "the_sift:siftslate", "the_sift:cobbled_siftslate", "the_sift:chiseled_siftslate", "the_sift:siftslate_bricks",
        "the_sift:overgrown_willow_log", "the_sift:overgrown_willow_wood", "the_sift:stripped_overgrown_willow_log",
        "the_sift:stripped_overgrown_willow_wood", "the_sift:overgrown_willow_planks", "the_sift:siftite_nugget",
        "the_sift:siftite_ingot", "the_sift:ichor_shard", "the_sift:raw_sifter_meat", "the_sift:cooked_sifter_meat",
        "the_sift:sculkflower");

    private static final Set<String> SIFTITE_GEAR = Set.of(
        "the_sift:siftite_sword", "the_sift:siftite_pickaxe", "the_sift:siftite_axe", "the_sift:siftite_shovel",
        "the_sift:siftite_hoe", "the_sift:siftite_spear", "the_sift:siftite_helmet", "the_sift:siftite_chestplate",
        "the_sift:siftite_leggings", "the_sift:siftite_boots", "the_sift:siftite_upgrade_smithing_template");

    /** The recipes that work with {@link #SIFT_ITEMS} and vanilla items alone. */
    private static final Set<String> PORTABLE = Set.of(
        "chiseled_siftslate_from_cobbled_siftslate_stonecutting", "chiseled_siftslate_from_siftslate_bricks_stonecutting",
        "chiseled_siftslate_from_siftslate_stonecutting", "cobbled_siftslate_from_siftslate_stonecutting",
        "siftslate_bricks", "siftslate_bricks_from_cobbled_siftslate_stonecutting",
        "siftslate_bricks_from_siftslate_stonecutting",
        "dispenser_from_cobbled_siftslate", "dropper_from_cobbled_siftslate", "lever_from_cobbled_siftslate",
        "observer_from_cobbled_siftslate", "piston_from_cobbled_siftslate",
        "overgrown_willow_planks", "overgrown_willow_wood", "stripped_overgrown_willow_wood",
        "siftite_ingot", "siftite_nugget_from_siftite_ingot", "light_blue_dye_from_sculkflower",
        "sifter_meat_smelting", "sifter_meat_smoking", "sifter_meat_campfire");

    private static Predicate<String> available(Set<String> contentItems) {
        return entry -> vanilla(entry) || contentItems.contains(entry);
    }

    private static Translation translate(Set<String> contentItems) {
        return SiftRecipeCatalog.translate(DATA, available(contentItems));
    }

    private static Map<String, RecipeSpec> byId(Translation translation) {
        Map<String, RecipeSpec> byId = new HashMap<>();
        translation.recipes().forEach(recipe -> byId.put(recipe.id(), recipe));
        return byId;
    }

    @Test
    void recipeListMatchesTheDirectory() throws IOException, URISyntaxException {
        URL directory = SiftData.class.getClassLoader().getResource("the_sift/mielon/recipe");
        assertNotNull(directory, "recipe directory is on the classpath");
        Set<String> onDisk;
        try (Stream<Path> files = Files.list(Path.of(directory.toURI()))) {
            onDisk = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<String> listed = new LinkedHashSet<>(SiftRecipeCatalog.RECIPE_NAMES);
        assertEquals(SiftRecipeCatalog.RECIPE_NAMES.size(), listed.size(), "recipe names are listed once");
        Set<String> notListed = new LinkedHashSet<>(onDisk);
        notListed.removeAll(listed);
        Set<String> notOnDisk = new LinkedHashSet<>(listed);
        notOnDisk.removeAll(onDisk);
        assertTrue(notListed.isEmpty(), "recipe files missing from SiftRecipeCatalog.RECIPE_NAMES: " + notListed);
        assertTrue(notOnDisk.isEmpty(), "SiftRecipeCatalog.RECIPE_NAMES without a recipe file: " + notOnDisk);
        assertEquals(76, listed.size());
    }

    @Test
    void recipeNamesMakeValidUniqueKeys() {
        for (String name : SiftRecipeCatalog.RECIPE_NAMES) {
            assertTrue((SiftRecipeRegistrar.KEY_PREFIX + name).matches("[a-z0-9_]+"), name);
        }
    }

    @Test
    void everyRecipeFileParsesAndCoversEveryImportedType() {
        Translation everything = SiftRecipeCatalog.translate(DATA, entry -> true);

        assertTrue(everything.skipped().isEmpty(), "unexpected skips: " + everything.skipped());
        assertEquals(SiftRecipeCatalog.RECIPE_NAMES, everything.recipes().stream().map(RecipeSpec::id).toList());
        Map<String, Long> types = everything.recipes().stream()
            .collect(Collectors.groupingBy(RecipeSpec::type, Collectors.counting()));
        assertEquals(Map.of(
            "minecraft:crafting_shaped", 33L, "minecraft:crafting_shapeless", 8L, "minecraft:stonecutting", 22L,
            "minecraft:smithing_transform", 10L, "minecraft:smelting", 1L, "minecraft:smoking", 1L,
            "minecraft:campfire_cooking", 1L), types);
    }

    @Test
    void shapedPatternsKeysAndCountsMatchTheFiles() {
        Translation everything = SiftRecipeCatalog.translate(DATA, entry -> true);
        int checked = 0;
        for (RecipeSpec spec : everything.recipes()) {
            JsonObject json = DATA.json("recipe/" + spec.id() + ".json");
            JsonObject result = json.getAsJsonObject("result");
            int count = result.has("count") ? result.get("count").getAsInt() : 1;
            assertEquals(result.get("id").getAsString(), spec.result().id(), spec.id());
            assertEquals(count, spec.result().count(), spec.id());
            if (spec instanceof RecipeSpec.Shaped shaped) {
                checked++;
                List<String> pattern = new ArrayList<>();
                for (JsonElement row : json.getAsJsonArray("pattern")) {
                    pattern.add(row.getAsString());
                }
                assertEquals(pattern, shaped.pattern(), spec.id());
                JsonObject key = json.getAsJsonObject("key");
                assertEquals(key.size(), shaped.key().size(), spec.id());
                key.entrySet().forEach(entry -> assertEquals(Ingredient.of(entry.getValue().getAsString()),
                    shaped.key().get(entry.getKey().charAt(0)), spec.id()));
            }
        }
        assertEquals(33, checked);
    }

    @Test
    void specificShapedRecipesKeepTheirExactShapeAndYield() {
        Map<String, RecipeSpec> all = byId(SiftRecipeCatalog.translate(DATA, entry -> true));

        RecipeSpec.Shaped stairs = assertInstanceOf(RecipeSpec.Shaped.class, all.get("cobbled_siftslate_stairs"));
        assertEquals(List.of("#  ", "## ", "###"), stairs.pattern());
        assertEquals(4, stairs.result().count());

        RecipeSpec.Shaped shelf = assertInstanceOf(RecipeSpec.Shaped.class, all.get("overgrown_willow_shelf"));
        assertEquals(List.of("###", "   ", "###"), shelf.pattern());
        assertEquals(6, shelf.result().count());

        RecipeSpec.Shaped piston = assertInstanceOf(RecipeSpec.Shaped.class, all.get("piston_from_cobbled_siftslate"));
        assertEquals(List.of("TTT", "#X#", "#R#"), piston.pattern());
        assertEquals(Ingredient.of("#minecraft:planks"), piston.key().get('T'));
        assertEquals(Ingredient.of("the_sift:cobbled_siftslate"), piston.key().get('#'));
        assertEquals(Ingredient.of("minecraft:iron_ingot"), piston.key().get('X'));
        assertEquals(new RecipeSpec.Result("minecraft:piston", 1), piston.result());
    }

    @Test
    void keepsExactlyThePortableRecipesForTheCurrentItems() {
        Translation translation = translate(SIFT_ITEMS);

        assertEquals(PORTABLE, translation.recipeIds());
        Set<String> expectedSkipped = new LinkedHashSet<>(SiftRecipeCatalog.RECIPE_NAMES);
        expectedSkipped.removeAll(PORTABLE);
        assertEquals(expectedSkipped, translation.skippedIds());
        assertEquals(76 - PORTABLE.size(), translation.skipped().size());
        assertTrue(translation.skipped().stream().allMatch(skip -> skip.reason() == Reason.MISSING_ITEM),
            "only missing items explain a skip: " + translation.skipped(Reason.INVALID) + translation.skipped(Reason.UNSUPPORTED_TYPE));
    }

    @Test
    void skipsRecipesForItemsWithoutServerEquivalentAndNamesTheMissingItems() {
        Map<String, String> missing = translate(SIFT_ITEMS).skipped().stream()
            .collect(Collectors.toMap(SiftRecipeCatalog.Skipped::id, SiftRecipeCatalog.Skipped::detail));

        // Result has no item (slabs, stairs, walls, ichor glass, doors, boats, signs).
        assertEquals("the_sift:cobbled_siftslate_slab", missing.get("cobbled_siftslate_slab"));
        assertEquals("the_sift:siftslate_brick_wall", missing.get("siftslate_brick_wall_from_siftslate_stonecutting"));
        assertEquals("the_sift:ichor_glass", missing.get("ichor_glass"));
        assertEquals("the_sift:overgrown_willow_boat", missing.get("overgrown_willow_boat"));
        // Ingredient has no item.
        assertEquals("the_sift:siftslate_brick_slab", missing.get("chiseled_siftslate"));
        assertEquals("the_sift:ichor_glass", missing.get("glass_bottle_from_ichor_glass"));
        assertEquals("the_sift:sunburst_plant", missing.get("yellow_dye_from_sunburst_plant"));
        assertEquals("the_sift:overgrown_willow_hanging_sign", missing.get("overgrown_willow_hanging_sign"));
        // Siftite gear waits for its items.
        assertEquals("the_sift:siftite_upgrade_smithing_template, the_sift:siftite_sword", missing.get("siftite_sword_smithing"));
        assertEquals("the_sift:siftite_upgrade_smithing_template", missing.get("siftite_upgrade_smithing_template"));
    }

    @Test
    void siftiteGearRecipesStartWorkingOnceTheItemsExist() {
        assertTrue(translate(SIFT_ITEMS).skippedIds().containsAll(smithingRecipeNames()));

        Set<String> withGear = new HashSet<>(SIFT_ITEMS);
        withGear.addAll(SIFTITE_GEAR);
        Translation translation = translate(withGear);
        Map<String, RecipeSpec> kept = byId(translation);

        assertTrue(translation.recipeIds().containsAll(smithingRecipeNames()));
        assertTrue(translation.recipeIds().contains("siftite_upgrade_smithing_template"));
        RecipeSpec.SmithingTransform sword = assertInstanceOf(RecipeSpec.SmithingTransform.class,
            kept.get("siftite_sword_smithing"));
        assertEquals(Ingredient.of("the_sift:siftite_upgrade_smithing_template"), sword.template());
        assertEquals(Ingredient.of("minecraft:netherite_sword"), sword.base());
        assertEquals(Ingredient.of("the_sift:siftite_ingot"), sword.addition());
        assertEquals(new RecipeSpec.Result("the_sift:siftite_sword", 1), sword.result());
        RecipeSpec.Shaped template = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("siftite_upgrade_smithing_template"));
        assertEquals(List.of("#S#", "#C#", "###"), template.pattern());
        assertEquals(2, template.result().count());
        assertEquals(PORTABLE.size() + 11, translation.recipes().size());
    }

    private static Set<String> smithingRecipeNames() {
        return SiftRecipeCatalog.RECIPE_NAMES.stream().filter(name -> name.endsWith("_smithing")).collect(Collectors.toSet());
    }

    @Test
    void theSculkflowerMakesLightBlueDye() {
        RecipeSpec.Shapeless dye = assertInstanceOf(RecipeSpec.Shapeless.class,
            byId(translate(SIFT_ITEMS)).get("light_blue_dye_from_sculkflower"));
        assertEquals(List.of(Ingredient.of("the_sift:sculkflower")), dye.ingredients());
        assertEquals(new RecipeSpec.Result("minecraft:light_blue_dye", 1), dye.result());
        assertTrue(translate(Set.of()).skippedIds().contains("light_blue_dye_from_sculkflower"), "needs the item");
    }

    @Test
    void siftiteIngotAndNuggetRecipesConvertBothWays() {
        Map<String, RecipeSpec> kept = byId(translate(SIFT_ITEMS));

        RecipeSpec.Shaped ingot = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("siftite_ingot"));
        assertEquals(List.of("###", "###", "###"), ingot.pattern());
        assertEquals(Map.of('#', Ingredient.of("the_sift:siftite_nugget")), ingot.key());
        assertEquals(new RecipeSpec.Result("the_sift:siftite_ingot", 1), ingot.result());

        RecipeSpec.Shapeless nuggets = assertInstanceOf(RecipeSpec.Shapeless.class, kept.get("siftite_nugget_from_siftite_ingot"));
        assertEquals(List.of(Ingredient.of("the_sift:siftite_ingot")), nuggets.ingredients());
        assertEquals(new RecipeSpec.Result("the_sift:siftite_nugget", 9), nuggets.result());
    }

    @Test
    void siftslateBricksAndChiseledSiftslateComeFromStonecuttingAndCrafting() {
        Map<String, RecipeSpec> kept = byId(translate(SIFT_ITEMS));

        RecipeSpec.Shaped bricks = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("siftslate_bricks"));
        assertEquals(List.of("##", "##"), bricks.pattern());
        assertEquals(Map.of('#', Ingredient.of("the_sift:cobbled_siftslate")), bricks.key());
        assertEquals(new RecipeSpec.Result("the_sift:siftslate_bricks", 4), bricks.result());

        RecipeSpec.Stonecutting chiseled = assertInstanceOf(RecipeSpec.Stonecutting.class,
            kept.get("chiseled_siftslate_from_siftslate_bricks_stonecutting"));
        assertEquals(Ingredient.of("the_sift:siftslate_bricks"), chiseled.ingredient());
        assertEquals(new RecipeSpec.Result("the_sift:chiseled_siftslate", 1), chiseled.result());
        assertEquals(Ingredient.of("the_sift:siftslate"),
            assertInstanceOf(RecipeSpec.Stonecutting.class, kept.get("cobbled_siftslate_from_siftslate_stonecutting")).ingredient());
    }

    @Test
    void willowWoodRecipesExpandTheModTagIntoItsContentItems() {
        Map<String, RecipeSpec> kept = byId(translate(SIFT_ITEMS));

        RecipeSpec.Shapeless planks = assertInstanceOf(RecipeSpec.Shapeless.class, kept.get("overgrown_willow_planks"));
        assertEquals(List.of(Ingredient.of("the_sift:overgrown_willow_log", "the_sift:overgrown_willow_wood",
            "the_sift:stripped_overgrown_willow_log", "the_sift:stripped_overgrown_willow_wood")), planks.ingredients());
        assertEquals(new RecipeSpec.Result("the_sift:overgrown_willow_planks", 4), planks.result());
        assertEquals("planks", planks.group());

        RecipeSpec.Shaped wood = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("overgrown_willow_wood"));
        assertEquals(Map.of('#', Ingredient.of("the_sift:overgrown_willow_log")), wood.key());
        assertEquals(3, wood.result().count());
        RecipeSpec.Shaped stripped = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("stripped_overgrown_willow_wood"));
        assertEquals(Map.of('#', Ingredient.of("the_sift:stripped_overgrown_willow_log")), stripped.key());
    }

    @Test
    void tagMembersWithoutItemsAreDroppedAndAnEmptyTagSkipsTheRecipe() {
        Set<String> onlyLogs = Set.of("the_sift:overgrown_willow_log", "the_sift:overgrown_willow_planks");
        RecipeSpec.Shapeless planks = assertInstanceOf(RecipeSpec.Shapeless.class,
            byId(translate(onlyLogs)).get("overgrown_willow_planks"));
        assertEquals(List.of(Ingredient.of("the_sift:overgrown_willow_log")), planks.ingredients());

        Translation noWood = translate(Set.of("the_sift:overgrown_willow_planks"));
        assertTrue(noWood.skippedIds().contains("overgrown_willow_planks"));
        assertEquals("the_sift:overgrown_willow_log, the_sift:overgrown_willow_wood, "
            + "the_sift:stripped_overgrown_willow_log, the_sift:stripped_overgrown_willow_wood",
            noWood.skipped().stream().filter(skip -> skip.id().equals("overgrown_willow_planks")).findFirst().orElseThrow().detail());
    }

    @Test
    void redstoneComponentsFromCobbledSiftslateKeepTheirVanillaIngredients() {
        Map<String, RecipeSpec> kept = byId(translate(SIFT_ITEMS));

        RecipeSpec.Shaped dispenser = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("dispenser_from_cobbled_siftslate"));
        assertEquals(List.of("###", "#X#", "#R#"), dispenser.pattern());
        assertEquals(Ingredient.of("minecraft:bow"), dispenser.key().get('X'));
        assertEquals("minecraft:dispenser", dispenser.result().id());
        RecipeSpec.Shaped observer = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("observer_from_cobbled_siftslate"));
        assertEquals(List.of("###", "RRQ", "###"), observer.pattern());
        assertEquals(Ingredient.of("minecraft:quartz"), observer.key().get('Q'));
        RecipeSpec.Shaped lever = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("lever_from_cobbled_siftslate"));
        assertEquals(List.of("X", "#"), lever.pattern());
        RecipeSpec.Shaped dropper = assertInstanceOf(RecipeSpec.Shaped.class, kept.get("dropper_from_cobbled_siftslate"));
        assertEquals(List.of("###", "# #", "#R#"), dropper.pattern());
    }

    @Test
    void sifterMeatIsCookedInFurnaceSmokerAndCampfire() {
        Map<String, RecipeSpec> kept = byId(translate(SIFT_ITEMS));

        for (Map.Entry<String, CookingKind> entry : Map.of(
            "sifter_meat_smelting", CookingKind.SMELTING, "sifter_meat_smoking", CookingKind.SMOKING,
            "sifter_meat_campfire", CookingKind.CAMPFIRE).entrySet()) {
            RecipeSpec.Cooking cooking = assertInstanceOf(RecipeSpec.Cooking.class, kept.get(entry.getKey()));
            assertEquals(entry.getValue(), cooking.kind());
            assertEquals(Ingredient.of("the_sift:raw_sifter_meat"), cooking.ingredient());
            assertEquals(new RecipeSpec.Result("the_sift:cooked_sifter_meat", 1), cooking.result());
            assertEquals(0.35F, cooking.experience());
            assertEquals("food", cooking.category());
        }
        assertEquals(200, ((RecipeSpec.Cooking) kept.get("sifter_meat_smelting")).cookingTime());
        assertEquals(200, ((RecipeSpec.Cooking) kept.get("sifter_meat_smoking")).cookingTime());
        assertEquals(600, ((RecipeSpec.Cooking) kept.get("sifter_meat_campfire")).cookingTime());
    }

    @Test
    void theSetOfAssumedSiftItemsIsRegisteredContent() {
        Set<String> registered = SiftItems.items().stream().map(item -> item.id().asString()).collect(Collectors.toSet());

        Set<String> unknown = new LinkedHashSet<>(SIFT_ITEMS);
        unknown.removeAll(registered);
        assertTrue(unknown.isEmpty(), "test assumes content items that SiftItems does not register: " + unknown);
    }

    @Test
    void portingMoreItemsOnlyAddsRecipes() {
        Set<String> registered = SiftItems.items().stream().map(ContentItem::id).map(id -> id.asString())
            .collect(Collectors.toSet());

        Translation translation = translate(registered);

        assertTrue(translation.recipeIds().containsAll(PORTABLE), "recipes lost: " + PORTABLE.stream()
            .filter(id -> !translation.recipeIds().contains(id)).toList());
        assertTrue(translation.skipped(Reason.INVALID).isEmpty());
        assertTrue(translation.skipped(Reason.UNSUPPORTED_TYPE).isEmpty());
    }

    @Test
    void ingredientEntriesAreIdsOrMinecraftTagsAfterTranslation() {
        for (RecipeSpec spec : translate(SIFT_ITEMS).recipes()) {
            for (Ingredient ingredient : spec.ingredients()) {
                assertTrue(!ingredient.isEmpty(), spec.id());
                for (String entry : ingredient.entries()) {
                    assertTrue(vanilla(entry) || SIFT_ITEMS.contains(entry), spec.id() + " uses " + entry);
                }
            }
            assertTrue(vanilla(spec.result().id()) || SIFT_ITEMS.contains(spec.result().id()), spec.id());
        }
    }
}
