package dev.linqfy.bigCasares.modules.sift.recipe;

import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.CookingKind;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiftRecipeParserTest {

    private static RecipeSpec parse(String json) {
        return SiftRecipeParser.parse("test", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void parsesAShapedRecipeKeepingPatternGroupAndCount() {
        RecipeSpec.Shaped shaped = assertInstanceOf(RecipeSpec.Shaped.class, parse("""
            {"type":"minecraft:crafting_shaped","category":"building","group":"wooden_stairs",
             "pattern":["#  ","## ","###"],"key":{"#":"the_sift:planks"},
             "result":{"id":"the_sift:stairs","count":4}}"""));

        assertEquals(List.of("#  ", "## ", "###"), shaped.pattern());
        assertEquals(Map.of('#', Ingredient.of("the_sift:planks")), shaped.key());
        assertEquals("wooden_stairs", shaped.group());
        assertEquals("building", shaped.category());
        assertEquals(new RecipeSpec.Result("the_sift:stairs", 4), shaped.result());
        assertEquals("minecraft:crafting_shaped", shaped.type());
    }

    @Test
    void parsesShapelessWithTagsAndAlternativesAndDefaultNamespace() {
        RecipeSpec.Shapeless shapeless = assertInstanceOf(RecipeSpec.Shapeless.class, parse("""
            {"type":"minecraft:crafting_shapeless","ingredients":["#the_sift:logs",["stone","minecraft:cobblestone"]],
             "result":{"id":"minecraft:stick"}}"""));

        assertEquals(List.of(Ingredient.of("#the_sift:logs"), Ingredient.of("minecraft:stone", "minecraft:cobblestone")),
            shapeless.ingredients());
        assertEquals(new RecipeSpec.Result("minecraft:stick", 1), shapeless.result());
    }

    @Test
    void parsesStonecuttingAndSmithingTransform() {
        RecipeSpec.Stonecutting cutting = assertInstanceOf(RecipeSpec.Stonecutting.class, parse("""
            {"type":"minecraft:stonecutting","ingredient":"the_sift:a","result":{"id":"the_sift:b","count":2}}"""));
        assertEquals(Ingredient.of("the_sift:a"), cutting.ingredient());
        assertEquals(2, cutting.result().count());

        RecipeSpec.SmithingTransform smithing = assertInstanceOf(RecipeSpec.SmithingTransform.class, parse("""
            {"type":"minecraft:smithing_transform","template":"the_sift:t","base":"minecraft:netherite_sword",
             "addition":"the_sift:ingot","result":{"id":"the_sift:sword"}}"""));
        assertEquals(List.of(Ingredient.of("the_sift:t"), Ingredient.of("minecraft:netherite_sword"),
            Ingredient.of("the_sift:ingot")), smithing.ingredients());
    }

    @Test
    void cookingRecipesKeepTimeAndExperienceAndFallBackToVanillaTimes() {
        RecipeSpec.Cooking campfire = assertInstanceOf(RecipeSpec.Cooking.class, parse("""
            {"type":"minecraft:campfire_cooking","category":"food","cookingtime":600,"experience":0.35,
             "ingredient":"the_sift:raw","result":{"id":"the_sift:cooked"}}"""));
        assertEquals(CookingKind.CAMPFIRE, campfire.kind());
        assertEquals(600, campfire.cookingTime());
        assertEquals(0.35F, campfire.experience());

        RecipeSpec.Cooking blasting = assertInstanceOf(RecipeSpec.Cooking.class, parse("""
            {"type":"minecraft:blasting","ingredient":"minecraft:iron_ore","result":{"id":"minecraft:iron_ingot"}}"""));
        assertEquals(100, blasting.cookingTime());
        assertEquals(0.0F, blasting.experience());
        assertEquals("minecraft:blasting", blasting.type());

        RecipeSpec.Cooking smelting = assertInstanceOf(RecipeSpec.Cooking.class, parse("""
            {"type":"minecraft:smelting","ingredient":"minecraft:iron_ore","result":{"id":"minecraft:iron_ingot"}}"""));
        assertEquals(200, smelting.cookingTime());
    }

    @Test
    void unknownRecipeTypesAreReportedAsUnsupported() {
        assertThrows(SiftRecipeParser.UnsupportedRecipeTypeException.class, () -> parse("""
            {"type":"minecraft:smithing_trim","template":"a","base":"b","addition":"c"}"""));
        assertThrows(SiftRecipeParser.UnsupportedRecipeTypeException.class, () -> parse("""
            {"type":"minecraft:crafting_transmute","input":"a","material":"b","result":"c"}"""));
    }

    @Test
    void malformedRecipesAreRejected() {
        String head = "{\"type\":\"minecraft:crafting_shaped\",";
        // Symbol in the pattern without a key.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#X\"],\"key\":{\"#\":\"minecraft:stick\"},\"result\":{\"id\":\"minecraft:stick\"}}"));
        // Key symbol the pattern never uses.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#\"],\"key\":{\"#\":\"minecraft:stick\",\"X\":\"minecraft:dirt\"},"
                + "\"result\":{\"id\":\"minecraft:stick\"}}"));
        // Rows of different length, and more than three rows.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"##\",\"#\"],\"key\":{\"#\":\"minecraft:stick\"},\"result\":{\"id\":\"minecraft:stick\"}}"));
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#\",\"#\",\"#\",\"#\"],\"key\":{\"#\":\"minecraft:stick\"},"
                + "\"result\":{\"id\":\"minecraft:stick\"}}"));
        // No result, result components and an out-of-range count.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#\"],\"key\":{\"#\":\"minecraft:stick\"}}"));
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#\"],\"key\":{\"#\":\"minecraft:stick\"},"
                + "\"result\":{\"id\":\"minecraft:stick\",\"components\":{}}}"));
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            head + "\"pattern\":[\"#\"],\"key\":{\"#\":\"minecraft:stick\"},\"result\":{\"id\":\"minecraft:stick\",\"count\":0}}"));
        // Not an item id.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            "{\"type\":\"minecraft:stonecutting\",\"ingredient\":\"Not An Id\",\"result\":{\"id\":\"minecraft:stick\"}}"));
        // Shapeless without ingredients.
        assertThrows(SiftRecipeParser.InvalidRecipeException.class, () -> parse(
            "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"id\":\"minecraft:stick\"}}"));
    }

    @Test
    void withIngredientsReplacesEverySlotAndKeepsTheRest() {
        RecipeSpec.Shaped shaped = assertInstanceOf(RecipeSpec.Shaped.class, parse("""
            {"type":"minecraft:crafting_shaped","pattern":["#X"],"key":{"#":"the_sift:a","X":"minecraft:stick"},
             "result":{"id":"minecraft:lever","count":3}}"""));

        RecipeSpec.Shaped mapped = assertInstanceOf(RecipeSpec.Shaped.class,
            shaped.withIngredients(ingredient -> Ingredient.of("minecraft:dirt")));

        assertEquals(List.of("#X"), mapped.pattern());
        assertEquals(Map.of('#', Ingredient.of("minecraft:dirt"), 'X', Ingredient.of("minecraft:dirt")), mapped.key());
        assertEquals(shaped.result(), mapped.result());
    }
}
