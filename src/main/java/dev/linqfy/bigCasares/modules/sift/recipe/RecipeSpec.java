package dev.linqfy.bigCasares.modules.sift.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Bukkit-free description of one imported crafting recipe: type, ingredients as item ids or {@code #tags},
 * result id and count. {@link SiftRecipeRegistrar} turns specs into server recipes at runtime, which keeps the
 * translation testable without a server registry.
 */
public sealed interface RecipeSpec {

    /** Recipe name: the file name without {@code .json}. */
    String id();

    /** Vanilla recipe type id, e.g. {@code minecraft:crafting_shaped}. */
    String type();

    Result result();

    /** Every ingredient slot of the recipe, in definition order. */
    List<Ingredient> ingredients();

    /** Copy of this recipe whose ingredients were replaced one by one; the result is kept. */
    RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper);

    /**
     * One ingredient slot: alternative entries that each match. An entry is an item id
     * ({@code the_sift:siftslate}, {@code minecraft:redstone}) or an item tag ({@code #minecraft:planks}).
     */
    record Ingredient(List<String> entries) {

        public Ingredient {
            entries = List.copyOf(entries);
        }

        public static Ingredient of(String... entries) {
            return new Ingredient(List.of(entries));
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public static boolean isTag(String entry) {
            return entry.startsWith("#");
        }
    }

    /** Result item id and stack size. */
    record Result(String id, int count) {

        public Result {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || Ingredient.isTag(id)) {
                throw new IllegalArgumentException("Result must be an item id: " + id);
            }
            if (count < 1 || count > 99) {
                throw new IllegalArgumentException("Result count must be within 1..99: " + count);
            }
        }
    }

    /**
     * {@code minecraft:crafting_shaped}. {@code pattern} keeps the rows exactly as imported (a space is an empty
     * slot); every non-space symbol of the pattern has a {@code key} entry and the other way round.
     */
    record Shaped(
        String id, String group, String category, List<String> pattern, Map<Character, Ingredient> key, Result result
    ) implements RecipeSpec {

        public Shaped {
            pattern = List.copyOf(pattern);
            key = Collections.unmodifiableMap(new LinkedHashMap<>(key));
        }

        @Override
        public String type() {
            return "minecraft:crafting_shaped";
        }

        @Override
        public List<Ingredient> ingredients() {
            return List.copyOf(key.values());
        }

        @Override
        public RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper) {
            Map<Character, Ingredient> mapped = new LinkedHashMap<>();
            key.forEach((symbol, ingredient) -> mapped.put(symbol, mapper.apply(ingredient)));
            return new Shaped(id, group, category, pattern, mapped, result);
        }
    }

    /** {@code minecraft:crafting_shapeless}. */
    record Shapeless(
        String id, String group, String category, List<Ingredient> ingredients, Result result
    ) implements RecipeSpec {

        public Shapeless {
            ingredients = List.copyOf(ingredients);
        }

        @Override
        public String type() {
            return "minecraft:crafting_shapeless";
        }

        @Override
        public RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper) {
            List<Ingredient> mapped = new ArrayList<>();
            ingredients.forEach(ingredient -> mapped.add(mapper.apply(ingredient)));
            return new Shapeless(id, group, category, mapped, result);
        }
    }

    /** {@code minecraft:stonecutting}; the server ignores recipe groups for stonecutting since 26.1, so none is kept. */
    record Stonecutting(String id, Ingredient ingredient, Result result) implements RecipeSpec {

        @Override
        public String type() {
            return "minecraft:stonecutting";
        }

        @Override
        public List<Ingredient> ingredients() {
            return List.of(ingredient);
        }

        @Override
        public RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper) {
            return new Stonecutting(id, mapper.apply(ingredient), result);
        }
    }

    /** {@code minecraft:smithing_transform}. */
    record SmithingTransform(
        String id, Ingredient template, Ingredient base, Ingredient addition, Result result
    ) implements RecipeSpec {

        @Override
        public String type() {
            return "minecraft:smithing_transform";
        }

        @Override
        public List<Ingredient> ingredients() {
            return List.of(template, base, addition);
        }

        @Override
        public RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper) {
            return new SmithingTransform(id, mapper.apply(template), mapper.apply(base), mapper.apply(addition), result);
        }
    }

    /** The cooking recipe types, with the vanilla default cooking time in ticks. */
    enum CookingKind {
        SMELTING("minecraft:smelting", 200),
        BLASTING("minecraft:blasting", 100),
        SMOKING("minecraft:smoking", 100),
        CAMPFIRE("minecraft:campfire_cooking", 100);

        private final String type;
        private final int defaultCookingTime;

        CookingKind(String type, int defaultCookingTime) {
            this.type = type;
            this.defaultCookingTime = defaultCookingTime;
        }

        public String type() {
            return type;
        }

        public int defaultCookingTime() {
            return defaultCookingTime;
        }

        public static Optional<CookingKind> ofType(String type) {
            for (CookingKind kind : values()) {
                if (kind.type.equals(type)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** {@code minecraft:smelting}, {@code smoking}, {@code blasting} and {@code campfire_cooking}. */
    record Cooking(
        String id, CookingKind kind, String group, String category, Ingredient ingredient, Result result,
        float experience, int cookingTime
    ) implements RecipeSpec {

        public Cooking {
            Objects.requireNonNull(kind, "kind");
            if (experience < 0 || cookingTime < 1) {
                throw new IllegalArgumentException("Invalid cooking values: experience " + experience + ", time " + cookingTime);
            }
        }

        @Override
        public String type() {
            return kind.type();
        }

        @Override
        public List<Ingredient> ingredients() {
            return List.of(ingredient);
        }

        @Override
        public RecipeSpec withIngredients(UnaryOperator<Ingredient> mapper) {
            return new Cooking(id, kind, group, category, mapper.apply(ingredient), result, experience, cookingTime);
        }
    }
}
