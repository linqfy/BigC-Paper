package dev.linqfy.bigCasares.content;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Keeps content items out of recipes that do not name them. Content items reuse
 * vanilla materials (a Siftite nugget is an iron nugget, a Siftite ingot a
 * netherite ingot), so vanilla recipes would otherwise accept them: nine nuggets
 * would craft an iron ingot and the ingot would upgrade gear to netherite. A
 * recipe accepts a content item only through an exact choice that matches it
 * ({@link RecipeChoice#exactChoice}).
 *
 * <p>Crafting tables and furnaces retry the recipe they used last before a new
 * lookup, so a vanilla recipe can win even though Paper otherwise prefers plugin
 * recipes; the guard then switches to the recipe a fresh lookup finds, or blocks
 * the result when no recipe names the item.</p>
 */
public final class ContentRecipeGuard implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (accepts(event.getRecipe(), matrix)) {
            return;
        }
        event.getInventory().setResult(replacement(matrix, event.getView().getPlayer().getWorld()).orElse(null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        BlockState state = event.getBlock().getState(false);
        if (!(state instanceof Crafter crafter)) {
            return;
        }
        ItemStack[] matrix = crafter.getInventory().getContents();
        if (accepts(event.getRecipe(), matrix)) {
            return;
        }
        Optional<ItemStack> result = replacement(matrix, event.getBlock().getWorld());
        if (result.isPresent()) {
            event.setResult(result.get());
        } else {
            event.setCancelled(true);
        }
    }

    /** Smithing trims keep the base item, so a trimmed content item stays what it was. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        Recipe recipe = inventory.getRecipe();
        ItemStack template = inventory.getInputTemplate();
        ItemStack base = inventory.getInputEquipment();
        ItemStack addition = inventory.getInputMineral();
        boolean allowed = recipe instanceof SmithingTrimRecipe
            ? accepts(recipe, new ItemStack[] {template, addition})
            : accepts(recipe, new ItemStack[] {template, base, addition});
        if (!allowed) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        ItemStack source = event.getSource();
        CookingRecipe<?> recipe = event.getRecipe();
        if (accepts(recipe, new ItemStack[] {source})) {
            return;
        }
        Optional<CookingRecipe<?>> replacement = recipe == null ? Optional.empty() : cookingRecipe(kind(recipe), source);
        if (replacement.isPresent()) {
            event.setResult(replacement.get().getResult().clone());
        } else {
            event.setCancelled(true);
        }
    }

    static boolean accepts(Recipe recipe, ItemStack[] inputs) {
        return accepts(Arrays.asList(inputs), input -> ContentItemFactory.idOf(input).isPresent(), choices(recipe),
            (choice, input) -> choice instanceof RecipeChoice.ExactChoice && choice.test(input));
    }

    /**
     * Whether every content input is matched exactly by one of the recipe's choices
     * (inputs without content items are always accepted).
     */
    static <I, C> boolean accepts(List<I> inputs, Predicate<I> isContent, List<C> choices, BiPredicate<C, I> exactly) {
        for (I input : inputs) {
            if (input != null && isContent.test(input) && choices.stream().noneMatch(choice -> exactly.test(choice, input))) {
                return false;
            }
        }
        return true;
    }

    /** Ingredient choices of the recipe kinds plugins can define; special recipes have none. */
    private static List<RecipeChoice> choices(Recipe recipe) {
        List<RecipeChoice> choices = switch (recipe) {
            case ShapedRecipe shaped -> new ArrayList<>(shaped.getChoiceMap().values());
            case ShapelessRecipe shapeless -> shapeless.getChoiceList();
            case CookingRecipe<?> cooking -> Arrays.asList(cooking.getInputChoice());
            case StonecuttingRecipe stonecutting -> Arrays.asList(stonecutting.getInputChoice());
            case SmithingTransformRecipe transform ->
                Arrays.asList(transform.getTemplate(), transform.getBase(), transform.getAddition());
            case SmithingTrimRecipe trim -> Arrays.asList(trim.getTemplate(), trim.getBase(), trim.getAddition());
            case null, default -> List.of();
        };
        return choices.stream().filter(Objects::nonNull).toList();
    }

    /** The crafting recipe a fresh lookup finds, when it names every content input. */
    private static Optional<ItemStack> replacement(ItemStack[] matrix, World world) {
        Recipe recipe = Bukkit.getCraftingRecipe(matrix, world);
        return recipe != null && accepts(recipe, matrix) ? Optional.of(recipe.getResult().clone()) : Optional.empty();
    }

    /** The Bukkit recipe type of the block that cooks (furnace, smoker, blast furnace, campfire). */
    private static Class<?> kind(CookingRecipe<?> recipe) {
        return switch (recipe) {
            case BlastingRecipe ignored -> BlastingRecipe.class;
            case SmokingRecipe ignored -> SmokingRecipe.class;
            case CampfireRecipe ignored -> CampfireRecipe.class;
            default -> FurnaceRecipe.class;
        };
    }

    private static Optional<CookingRecipe<?>> cookingRecipe(Class<?> kind, ItemStack source) {
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (recipes.next() instanceof CookingRecipe<?> cooking && kind.isInstance(cooking)
                && accepts(cooking, new ItemStack[] {source})) {
                return Optional.of(cooking);
            }
        }
        return Optional.empty();
    }
}
