package dev.linqfy.bigCasares.modules.sift.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The recipes imported from the mod ({@code the_sift/mielon/recipe/}) and their translation into
 * {@link RecipeSpec}s for what the server can actually offer.
 *
 * <p>A recipe is kept only when every ingredient and the result exist on the server (a content item or a vanilla
 * item); the mod's slabs, stairs, doors, boats, ichor glass and so on have no counterpart and their recipes are
 * skipped rather than faked. Recipes are resolved when the module registers them, so a recipe that needs an item
 * the port adds later (the Siftite gear) starts working as soon as that item is registered.</p>
 */
public final class SiftRecipeCatalog {

    public static final String NAMESPACE = "the_sift";
    private static final String DIRECTORY = "recipe/";
    private static final int MAX_TAG_DEPTH = 8;

    /**
     * Every recipe file of {@code the_sift/mielon/recipe/}. A classpath directory cannot be listed inside a JAR, so
     * the names are checked in; {@code SiftRecipeCatalogTest} fails when this list and the directory drift apart.
     */
    public static final List<String> RECIPE_NAMES = List.of(
        "chiseled_siftslate", "chiseled_siftslate_from_cobbled_siftslate_stonecutting",
        "chiseled_siftslate_from_siftslate_brick_slab_stonecutting",
        "chiseled_siftslate_from_siftslate_bricks_stonecutting", "chiseled_siftslate_from_siftslate_stonecutting",
        "cobbled_siftslate_from_siftslate_stonecutting", "cobbled_siftslate_slab",
        "cobbled_siftslate_slab_from_cobbled_siftslate_stonecutting",
        "cobbled_siftslate_slab_from_siftslate_stonecutting", "cobbled_siftslate_stairs",
        "cobbled_siftslate_stairs_from_cobbled_siftslate_stonecutting",
        "cobbled_siftslate_stairs_from_siftslate_stonecutting", "cobbled_siftslate_wall",
        "cobbled_siftslate_wall_from_cobbled_siftslate_stonecutting",
        "cobbled_siftslate_wall_from_siftslate_stonecutting", "dispenser_from_cobbled_siftslate",
        "dropper_from_cobbled_siftslate", "glass_bottle_from_ichor_glass", "ichor_glass", "ichor_glass_pane",
        "ichor_snow", "ichor_snow_block", "lever_from_cobbled_siftslate", "light_blue_dye_from_overgrown_lotus",
        "light_blue_dye_from_sculkflower", "light_gray_dye_from_whisperbloom", "observer_from_cobbled_siftslate",
        "overgrown_willow_boat", "overgrown_willow_button", "overgrown_willow_chest_boat", "overgrown_willow_door",
        "overgrown_willow_fence", "overgrown_willow_fence_gate", "overgrown_willow_hanging_sign",
        "overgrown_willow_planks", "overgrown_willow_pressure_plate", "overgrown_willow_shelf",
        "overgrown_willow_sign", "overgrown_willow_slab", "overgrown_willow_stairs", "overgrown_willow_trapdoor",
        "overgrown_willow_wood", "piston_from_cobbled_siftslate", "sifter_meat_campfire", "sifter_meat_smelting",
        "sifter_meat_smoking", "siftite_axe_smithing", "siftite_boots_smithing", "siftite_chestplate_smithing",
        "siftite_helmet_smithing", "siftite_hoe_smithing", "siftite_ingot", "siftite_leggings_smithing",
        "siftite_nugget_from_siftite_ingot", "siftite_pickaxe_smithing", "siftite_shovel_smithing",
        "siftite_spear_smithing", "siftite_sword_smithing", "siftite_upgrade_smithing_template",
        "siftslate_brick_slab", "siftslate_brick_slab_from_cobbled_siftslate_stonecutting",
        "siftslate_brick_slab_from_siftslate_bricks_stonecutting", "siftslate_brick_slab_from_siftslate_stonecutting",
        "siftslate_brick_stairs", "siftslate_brick_stairs_from_cobbled_siftslate_stonecutting",
        "siftslate_brick_stairs_from_siftslate_bricks_stonecutting",
        "siftslate_brick_stairs_from_siftslate_stonecutting", "siftslate_brick_wall",
        "siftslate_brick_wall_from_cobbled_siftslate_stonecutting",
        "siftslate_brick_wall_from_siftslate_bricks_stonecutting", "siftslate_brick_wall_from_siftslate_stonecutting",
        "siftslate_bricks", "siftslate_bricks_from_cobbled_siftslate_stonecutting",
        "siftslate_bricks_from_siftslate_stonecutting", "stripped_overgrown_willow_wood",
        "yellow_dye_from_sunburst_plant");

    private SiftRecipeCatalog() {
    }

    /** Why a recipe was left out. */
    public enum Reason {
        /** An ingredient or the result has no counterpart on the server. */
        MISSING_ITEM,
        /** The recipe type is not one the port can build. */
        UNSUPPORTED_TYPE,
        /** The file is missing or not a well-formed recipe. */
        INVALID
    }

    public record Skipped(String id, Reason reason, String detail) {
    }

    /** The recipes to register and the ones that were left out, both in {@link #RECIPE_NAMES} order. */
    public record Translation(List<RecipeSpec> recipes, List<Skipped> skipped) {

        public Translation {
            recipes = List.copyOf(recipes);
            skipped = List.copyOf(skipped);
        }

        public Set<String> recipeIds() {
            Set<String> ids = new LinkedHashSet<>();
            recipes.forEach(recipe -> ids.add(recipe.id()));
            return ids;
        }

        public Set<String> skippedIds() {
            Set<String> ids = new LinkedHashSet<>();
            skipped.forEach(skip -> ids.add(skip.id()));
            return ids;
        }

        public List<Skipped> skipped(Reason reason) {
            return skipped.stream().filter(skip -> skip.reason() == reason).toList();
        }
    }

    /**
     * Parses every recipe and keeps those whose items all exist.
     *
     * @param available tells whether an item id ({@code the_sift:siftslate}, {@code minecraft:redstone}) or item
     *                  tag ({@code #minecraft:planks}) exists on the server
     */
    public static Translation translate(SiftData data, Predicate<String> available) {
        List<RecipeSpec> recipes = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (String name : RECIPE_NAMES) {
            RecipeSpec parsed;
            try {
                Optional<JsonObject> json = data.optionalJson(DIRECTORY + name + ".json");
                if (json.isEmpty()) {
                    skipped.add(new Skipped(name, Reason.INVALID, "recipe file is missing"));
                    continue;
                }
                parsed = SiftRecipeParser.parse(name, json.get());
            } catch (SiftRecipeParser.UnsupportedRecipeTypeException unsupported) {
                skipped.add(new Skipped(name, Reason.UNSUPPORTED_TYPE, unsupported.getMessage()));
                continue;
            } catch (RuntimeException invalid) {
                skipped.add(new Skipped(name, Reason.INVALID, String.valueOf(invalid.getMessage())));
                continue;
            }
            Set<String> missing = new LinkedHashSet<>();
            RecipeSpec resolved = parsed.withIngredients(ingredient -> resolve(data, ingredient, available, missing));
            if (!available.test(resolved.result().id())) {
                missing.add(resolved.result().id());
            }
            if (missing.isEmpty()) {
                recipes.add(resolved);
            } else {
                skipped.add(new Skipped(name, Reason.MISSING_ITEM, String.join(", ", missing)));
            }
        }
        return new Translation(recipes, skipped);
    }

    /**
     * Expands {@code #the_sift:*} tags into their items and drops the alternatives that do not exist on the
     * server. When no alternative is left, all of its entries are reported in {@code missing} and the ingredient
     * is returned empty.
     */
    private static Ingredient resolve(SiftData data, Ingredient ingredient, Predicate<String> available, Set<String> missing) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String entry : ingredient.entries()) {
            expandInto(data, entry, expanded, 0);
        }
        List<String> present = expanded.stream().filter(available).toList();
        if (present.isEmpty()) {
            missing.addAll(expanded);
        }
        return new Ingredient(present);
    }

    private static void expandInto(SiftData data, String entry, Set<String> into, int depth) {
        if (!entry.startsWith("#" + NAMESPACE + ":")) {
            into.add(entry);
            return;
        }
        Optional<List<String>> members = tagMembers(data, entry.substring(1 + NAMESPACE.length() + 1));
        if (members.isEmpty() || depth >= MAX_TAG_DEPTH) {
            into.add(entry);
            return;
        }
        for (String member : members.get()) {
            expandInto(data, member, into, depth + 1);
        }
    }

    /**
     * Members of a mod tag. The import carries the block tags only; the mod's item tag of the same name holds the
     * same members (the willow logs, for example), so the item tag is preferred and the block tag is the fallback.
     */
    private static Optional<List<String>> tagMembers(SiftData data, String name) {
        for (String kind : List.of("item", "block")) {
            Optional<JsonObject> tag = data.optionalJson("tags/" + kind + "/" + name + ".json");
            if (tag.isPresent() && tag.get().has("values") && tag.get().get("values").isJsonArray()) {
                return Optional.of(values(tag.get().getAsJsonArray("values")));
            }
        }
        return Optional.empty();
    }

    private static List<String> values(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                values.add(element.getAsJsonObject().get("id").getAsString());
            }
        }
        return values;
    }
}
