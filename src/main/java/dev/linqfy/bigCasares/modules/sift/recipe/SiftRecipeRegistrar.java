package dev.linqfy.bigCasares.modules.sift.recipe;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Ingredient;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeCatalog.Reason;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeCatalog.Skipped;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeCatalog.Translation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Registers the portable recipes of the mod on the server, owned by a runtime scope so they are removed again
 * when the module is disabled.
 *
 * <p>Content-item ingredients become {@link RecipeChoice.ExactChoice}s (via {@code RecipeChoice.exactChoice}) over the content stack, because their
 * material alone is a vanilla item (a mushroom block, an iron nugget). Vanilla ids and vanilla tags become
 * {@link RecipeChoice.MaterialChoice}s. Recipe keys are {@code <plugin>:sift_<recipe name>}.</p>
 */
public final class SiftRecipeRegistrar {

    public static final String KEY_PREFIX = "sift_";

    private final Plugin plugin;
    private final Logger logger;
    private final ContentItemFactory items;
    private final BukkitRuntimeRegistrations registrations;

    public SiftRecipeRegistrar(Plugin plugin, ContentItemFactory items, BukkitRuntimeRegistrations registrations) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.items = Objects.requireNonNull(items, "items");
        this.registrations = Objects.requireNonNull(registrations, "registrations");
    }

    /**
     * @param registered recipes now active on the server
     * @param failed     translated recipes the server refused or that could not be built
     * @param translation the translation these numbers come from, including the skipped recipes
     */
    public record Summary(int registered, int failed, Translation translation) {
    }

    /** Translates the recipes against the items the server has right now and registers the ones that fit. */
    public Summary register(SiftData data) {
        Translation translation = SiftRecipeCatalog.translate(data, this::isAvailable);
        int registered = 0;
        int failed = 0;
        for (RecipeSpec spec : translation.recipes()) {
            NamespacedKey key = new NamespacedKey(plugin, KEY_PREFIX + spec.id());
            try {
                if (registrations.registerRecipe("sift-recipe-" + spec.id(), key, toRecipe(key, spec))) {
                    registered++;
                } else {
                    failed++;
                    logger.warning("[TheSift] The server did not accept recipe " + key);
                }
            } catch (RuntimeException failure) {
                failed++;
                logger.log(Level.WARNING, "[TheSift] Could not register recipe " + key, failure);
            }
        }
        Summary summary = new Summary(registered, failed, translation);
        logSummary(summary);
        return summary;
    }

    private void logSummary(Summary summary) {
        Translation translation = summary.translation();
        int missing = translation.skipped(Reason.MISSING_ITEM).size();
        int other = translation.skipped().size() - missing;
        StringBuilder message = new StringBuilder("[TheSift] Registered ").append(summary.registered())
            .append(" recipes, skipped ").append(translation.skipped().size())
            .append(" (").append(missing).append(" need items the server does not have");
        if (other > 0) {
            message.append(", ").append(other).append(" unsupported or invalid");
        }
        message.append(")");
        if (summary.failed() > 0) {
            message.append("; ").append(summary.failed()).append(" could not be registered");
        }
        logger.info(message.toString());
        if (!translation.skipped().isEmpty() && logger.isLoggable(Level.FINE)) {
            logger.fine("[TheSift] Skipped recipes: " + translation.skipped().stream()
                .map(skip -> skip.id() + " (" + describe(skip) + ")")
                .collect(Collectors.joining("; ")));
        }
    }

    private static String describe(Skipped skip) {
        return skip.reason() == Reason.MISSING_ITEM ? "missing " + skip.detail() : skip.detail();
    }

    /** Whether the server has the item or item tag: a registered content item, a vanilla item or a known tag. */
    boolean isAvailable(String entry) {
        if (Ingredient.isTag(entry)) {
            return itemTag(entry).isPresent();
        }
        if (items.registry().find(entry).isPresent()) {
            return true;
        }
        Material material = Material.matchMaterial(entry);
        return material != null && material.isItem();
    }

    private Recipe toRecipe(NamespacedKey key, RecipeSpec spec) {
        return switch (spec) {
            case RecipeSpec.Shaped shaped -> shaped(key, shaped);
            case RecipeSpec.Shapeless shapeless -> shapeless(key, shapeless);
            case RecipeSpec.Stonecutting stonecutting ->
                new StonecuttingRecipe(key, stack(spec.result()), choice(stonecutting.ingredient()));
            case RecipeSpec.SmithingTransform smithing -> new SmithingTransformRecipe(key, stack(spec.result()),
                choice(smithing.template()), choice(smithing.base()), choice(smithing.addition()));
            case RecipeSpec.Cooking cooking -> cooking(key, cooking);
        };
    }

    private Recipe shaped(NamespacedKey key, RecipeSpec.Shaped spec) {
        ShapedRecipe recipe = new ShapedRecipe(key, stack(spec.result()));
        recipe.shape(spec.pattern().toArray(String[]::new));
        spec.key().forEach((symbol, ingredient) -> recipe.setIngredient(symbol, choice(ingredient)));
        craftingBook(recipe, spec.group(), spec.category());
        return recipe;
    }

    private Recipe shapeless(NamespacedKey key, RecipeSpec.Shapeless spec) {
        ShapelessRecipe recipe = new ShapelessRecipe(key, stack(spec.result()));
        spec.ingredients().forEach(ingredient -> recipe.addIngredient(choice(ingredient)));
        craftingBook(recipe, spec.group(), spec.category());
        return recipe;
    }

    private Recipe cooking(NamespacedKey key, RecipeSpec.Cooking spec) {
        ItemStack result = stack(spec.result());
        RecipeChoice input = choice(spec.ingredient());
        CookingRecipe<?> recipe = switch (spec.kind()) {
            case SMELTING -> new FurnaceRecipe(key, result, input, spec.experience(), spec.cookingTime());
            case BLASTING -> new BlastingRecipe(key, result, input, spec.experience(), spec.cookingTime());
            case SMOKING -> new SmokingRecipe(key, result, input, spec.experience(), spec.cookingTime());
            case CAMPFIRE -> new CampfireRecipe(key, result, input, spec.experience(), spec.cookingTime());
        };
        recipe.setGroup(spec.group());
        enumValue(CookingBookCategory.class, spec.category()).ifPresent(recipe::setCategory);
        return recipe;
    }

    private static void craftingBook(CraftingRecipe recipe, String group, String category) {
        recipe.setGroup(group);
        enumValue(CraftingBookCategory.class, category).ifPresent(recipe::setCategory);
    }

    private static <E extends Enum<E>> Optional<E> enumValue(Class<E> type, String name) {
        if (name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private ItemStack stack(RecipeSpec.Result result) {
        return items.resolve(result.id(), result.count())
            .orElseThrow(() -> new IllegalStateException("Unknown recipe result " + result.id()));
    }

    /**
     * Content items match exactly (by their persistent content id); with only vanilla entries the choice is a
     * material choice. A mixed ingredient becomes an exact choice, where its vanilla entries only match
     * unmodified vanilla stacks.
     */
    private RecipeChoice choice(Ingredient ingredient) {
        List<String> entries = ingredient.entries();
        boolean content = entries.stream()
            .anyMatch(entry -> !Ingredient.isTag(entry) && items.registry().find(entry).isPresent());
        if (content) {
            List<ItemStack> stacks = new ArrayList<>();
            for (String entry : entries) {
                if (!Ingredient.isTag(entry) && items.registry().find(entry).isPresent()) {
                    stacks.add(items.create(entry, 1).orElseThrow());
                } else {
                    materials(entry).forEach(material -> stacks.add(new ItemStack(material)));
                }
            }
            return new RecipeChoice.ExactChoice(stacks);
        }
        if (entries.size() == 1 && Ingredient.isTag(entries.get(0))) {
            return new RecipeChoice.MaterialChoice(requireTag(entries.get(0)));
        }
        Set<Material> materials = new LinkedHashSet<>();
        entries.forEach(entry -> materials.addAll(materials(entry)));
        return new RecipeChoice.MaterialChoice(List.copyOf(materials));
    }

    private Set<Material> materials(String entry) {
        if (Ingredient.isTag(entry)) {
            return requireTag(entry).getValues();
        }
        Material material = Material.matchMaterial(entry);
        if (material == null || !material.isItem()) {
            throw new IllegalStateException("Unknown item " + entry);
        }
        return Set.of(material);
    }

    private Tag<Material> requireTag(String entry) {
        return itemTag(entry).orElseThrow(() -> new IllegalStateException("Unknown item tag " + entry));
    }

    private static Optional<Tag<Material>> itemTag(String entry) {
        NamespacedKey key = NamespacedKey.fromString(entry.substring(1));
        return key == null ? Optional.empty()
            : Optional.ofNullable(Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class));
    }
}
