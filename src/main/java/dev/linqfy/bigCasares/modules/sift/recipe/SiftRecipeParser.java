package dev.linqfy.bigCasares.modules.sift.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.CookingKind;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Ingredient;
import dev.linqfy.bigCasares.modules.sift.recipe.RecipeSpec.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads recipe JSON in the Minecraft 26.x data format into {@link RecipeSpec}s. The parser only checks the
 * format; whether the items exist on the server is decided later by {@link SiftRecipeCatalog}.
 */
public final class SiftRecipeParser {

    private SiftRecipeParser() {
    }

    /** The file is not a well-formed recipe of a supported shape. */
    public static class InvalidRecipeException extends RuntimeException {
        public InvalidRecipeException(String message) {
            super(message);
        }
    }

    /** The recipe type has no server counterpart in the port. */
    public static final class UnsupportedRecipeTypeException extends InvalidRecipeException {
        public UnsupportedRecipeTypeException(String type) {
            super("Unsupported recipe type " + type);
        }
    }

    public static RecipeSpec parse(String id, JsonObject json) {
        String type = requiredString(json, "type");
        String group = optionalString(json, "group").orElse("");
        String category = optionalString(json, "category").orElse("");
        return switch (type) {
            case "minecraft:crafting_shaped" -> shaped(id, group, category, json);
            case "minecraft:crafting_shapeless" -> shapeless(id, group, category, json);
            case "minecraft:stonecutting" -> new RecipeSpec.Stonecutting(id, ingredient(json, "ingredient"), result(json));
            case "minecraft:smithing_transform" -> new RecipeSpec.SmithingTransform(id, ingredient(json, "template"),
                ingredient(json, "base"), ingredient(json, "addition"), result(json));
            default -> CookingKind.ofType(type).map(kind -> cooking(id, kind, group, category, json))
                .orElseThrow(() -> new UnsupportedRecipeTypeException(type));
        };
    }

    private static RecipeSpec shaped(String id, String group, String category, JsonObject json) {
        List<String> pattern = new ArrayList<>();
        for (JsonElement row : requiredArray(json, "pattern")) {
            pattern.add(string(row, "pattern row"));
        }
        if (pattern.isEmpty() || pattern.size() > 3) {
            throw new InvalidRecipeException("Pattern needs 1 to 3 rows, not " + pattern.size());
        }
        int width = pattern.get(0).length();
        for (String row : pattern) {
            if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                throw new InvalidRecipeException("Pattern rows must be 1 to 3 characters wide and equally long: " + pattern);
            }
        }
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : requiredObject(json, "key").entrySet()) {
            if (entry.getKey().length() != 1 || entry.getKey().charAt(0) == ' ') {
                throw new InvalidRecipeException("Key symbols are single non-space characters: '" + entry.getKey() + "'");
            }
            key.put(entry.getKey().charAt(0), ingredient(entry.getValue()));
        }
        Set<Character> used = new LinkedHashSet<>();
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ') {
                    used.add(symbol);
                }
            }
        }
        if (used.isEmpty() || !used.equals(key.keySet())) {
            throw new InvalidRecipeException("Pattern symbols " + used + " do not match key symbols " + key.keySet());
        }
        return new RecipeSpec.Shaped(id, group, category, pattern, key, result(json));
    }

    private static RecipeSpec shapeless(String id, String group, String category, JsonObject json) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : requiredArray(json, "ingredients")) {
            ingredients.add(ingredient(element));
        }
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw new InvalidRecipeException("Shapeless recipes take 1 to 9 ingredients, not " + ingredients.size());
        }
        return new RecipeSpec.Shapeless(id, group, category, ingredients, result(json));
    }

    private static RecipeSpec cooking(String id, CookingKind kind, String group, String category, JsonObject json) {
        float experience = json.has("experience") ? number(json.get("experience"), "experience").floatValue() : 0.0F;
        int time = json.has("cookingtime")
            ? number(json.get("cookingtime"), "cookingtime").intValue() : kind.defaultCookingTime();
        try {
            return new RecipeSpec.Cooking(id, kind, group, category, ingredient(json, "ingredient"), result(json), experience, time);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRecipeException(invalid.getMessage());
        }
    }

    private static Result result(JsonObject json) {
        JsonObject result = requiredObject(json, "result");
        if (result.has("components")) {
            throw new InvalidRecipeException("Result components are not supported");
        }
        String item = itemId(requiredString(result, "id"));
        int count = result.has("count") ? number(result.get("count"), "result count").intValue() : 1;
        try {
            return new Result(item, count);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRecipeException(invalid.getMessage());
        }
    }

    private static Ingredient ingredient(JsonObject json, String field) {
        if (!json.has(field)) {
            throw new InvalidRecipeException("Missing field " + field);
        }
        return ingredient(json.get(field));
    }

    /** An ingredient is an item id, an {@code #item_tag} or a list of those alternatives. */
    private static Ingredient ingredient(JsonElement element) {
        List<String> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement alternative : element.getAsJsonArray()) {
                entries.add(entry(alternative));
            }
        } else {
            entries.add(entry(element));
        }
        if (entries.isEmpty()) {
            throw new InvalidRecipeException("Ingredient has no entries");
        }
        return new Ingredient(entries);
    }

    private static String entry(JsonElement element) {
        String raw = string(element, "ingredient");
        return Ingredient.isTag(raw) ? "#" + itemId(raw.substring(1)) : itemId(raw);
    }

    private static String itemId(String raw) {
        try {
            return NamespacedId.parse(raw).asString();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRecipeException("Invalid item id '" + raw + "'");
        }
    }

    private static String string(JsonElement element, String what) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new InvalidRecipeException("Expected a string for " + what + " but found " + element);
        }
        return element.getAsString();
    }

    private static Number number(JsonElement element, String what) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new InvalidRecipeException("Expected a number for " + what + " but found " + element);
        }
        return element.getAsNumber();
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field)) {
            throw new InvalidRecipeException("Missing field " + field);
        }
        return string(json.get(field), field);
    }

    private static Optional<String> optionalString(JsonObject json, String field) {
        if (!json.has(field)) {
            return Optional.empty();
        }
        JsonElement element = json.get(field);
        return element instanceof JsonPrimitive primitive && primitive.isString()
            ? Optional.of(primitive.getAsString()) : Optional.empty();
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray()) {
            throw new InvalidRecipeException("Missing list " + field);
        }
        return json.getAsJsonArray(field);
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonObject()) {
            throw new InvalidRecipeException("Missing object " + field);
        }
        return json.getAsJsonObject(field);
    }
}
