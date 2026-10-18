package dev.linqfy.bigCasares.content;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentRecipeGuardTest {

    /** Inputs are item ids; content items carry the {@code the_sift} namespace. */
    private static final Predicate<String> CONTENT = id -> id.startsWith("the_sift:");

    /** A choice is "exact:<id>" (exact content choice) or "material:<base>" (any stack of the base). */
    private static final BiPredicate<String, String> EXACTLY = (choice, input) -> choice.equals("exact:" + input);

    @Test
    void vanillaRecipesDoNotTakeContentItems() {
        // Nine Siftite nuggets (iron nuggets underneath) in the vanilla iron ingot recipe.
        List<String> nuggets = Arrays.asList(new String[9]);
        nuggets.replaceAll(ignored -> "the_sift:siftite_nugget");
        assertFalse(ContentRecipeGuard.accepts(nuggets, CONTENT, List.of("material:iron_nugget"), EXACTLY));
        assertTrue(ContentRecipeGuard.accepts(nuggets, CONTENT, List.of("exact:the_sift:siftite_nugget"), EXACTLY));
    }

    @Test
    void everyContentInputNeedsItsOwnExactChoice() {
        List<String> smithing = List.of("the_sift:siftite_upgrade_smithing_template", "minecraft:netherite_sword",
            "the_sift:siftite_ingot");
        assertTrue(ContentRecipeGuard.accepts(smithing, CONTENT, List.of("exact:the_sift:siftite_upgrade_smithing_template",
            "material:netherite_sword", "exact:the_sift:siftite_ingot"), EXACTLY));
        // The vanilla netherite upgrade names the template exactly but takes any netherite ingot.
        assertFalse(ContentRecipeGuard.accepts(smithing, CONTENT, List.of("exact:the_sift:siftite_upgrade_smithing_template",
            "material:netherite_sword", "material:netherite_ingot"), EXACTLY));
    }

    @Test
    void recipesWithoutContentInputsAndEmptySlotsAreUntouched() {
        assertTrue(ContentRecipeGuard.accepts(Arrays.asList("minecraft:iron_nugget", null, "minecraft:stick"), CONTENT,
            List.of("material:iron_nugget"), EXACTLY));
        // Special recipes (repair, dyeing) expose no choices: they never take content items.
        assertFalse(ContentRecipeGuard.accepts(List.of("the_sift:siftite_sword", "the_sift:siftite_sword"), CONTENT,
            List.<String>of(), EXACTLY));
        assertTrue(ContentRecipeGuard.accepts(List.of("minecraft:diamond_sword"), CONTENT, List.<String>of(), EXACTLY));
    }

    /** Found on a live server: a 2x2 inventory craft made the server's recipe lookup throw. */
    @Test
    void theInventoryGridIsLookedUpAsTheTopLeftOfAThreeByThreeGrid() {
        ItemStack a = new ItemStack(Material.STICK);
        ItemStack b = new ItemStack(Material.COAL);
        ItemStack c = new ItemStack(Material.DIRT);
        ItemStack d = new ItemStack(Material.STONE);
        ItemStack[] grid = ContentRecipeGuard.craftingGrid(new ItemStack[] {a, b, c, d});

        assertArrayEquals(new ItemStack[] {a, b, null, c, d, null, null, null, null}, grid);
        ItemStack[] table = new ItemStack[9];
        assertSame(table, ContentRecipeGuard.craftingGrid(table));
    }
}
