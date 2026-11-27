package dev.linqfy.bigCasares.items.catalog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;

public final class ItemCatalogLoader {

    public CustomItemCatalog load(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("item catalog directory does not exist: " + root);
        }
        Map<String, CustomItemDefinition> definitions = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(root)) {
            List<Path> sources = files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".yml"))
                .sorted()
                .toList();
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("item catalog directory contains no YAML definitions: " + root);
            }
            for (Path source : sources) {
                CustomItemDefinition definition = parse(source);
                CustomItemDefinition previous = definitions.put(definition.id(), definition);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate item id: " + definition.id());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("could not load item catalog from " + root, exception);
        }
        return new CustomItemCatalog(definitions);
    }

    private static CustomItemDefinition parse(Path source) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source.toFile());
        String id = requiredString(yaml, "id", source);
        String mechanic = requiredString(yaml, "mechanic", source);
        String material = requiredString(yaml, "material", source);
        String itemModel = requiredString(yaml, "item-model", source);
        OptionalInt modelData = yaml.isSet("legacy-custom-model-data")
            ? OptionalInt.of(yaml.getInt("legacy-custom-model-data")) : OptionalInt.empty();
        ConfigurationSection display = requiredSection(yaml, "display", source);
        ItemDisplayDefinition displayDefinition = new ItemDisplayDefinition(
            requiredString(display, "translation-key", source),
            requiredString(display, "fallback-name", source),
            display.getStringList("lore")
        );
        int maxStackSize = yaml.getInt("max-stack-size", 64);
        Integer maxDamage = yaml.isSet("components.max-damage")
            ? yaml.getInt("components.max-damage") : null;
        ItemFoodDefinition food = parseFood(yaml.getConfigurationSection("components.food"), source);
        ItemRecipeDefinition recipe = parseRecipe(yaml.getConfigurationSection("recipe"), source);
        ItemCombatDefinition combat = parseCombat(yaml.getConfigurationSection("components.combat"), source);
        ItemArmorDefinition armor = parseArmor(yaml.getConfigurationSection("components.armor"), source);
        ConfigurationSection appearance = requiredSection(yaml, "appearance", source);
        ItemAppearanceDefinition appearanceDefinition = new ItemAppearanceDefinition(
            requiredString(appearance, "java-item-definition", source),
            requiredString(appearance, "bedrock-texture", source)
        );
        return new CustomItemDefinition(
            id, mechanic, material, itemModel, modelData, displayDefinition, maxStackSize, maxDamage, food, recipe,
            combat,
            appearanceDefinition,
            armor
        );
    }

    private static ItemCombatDefinition parseCombat(ConfigurationSection combat, Path source) {
        if (combat == null) {
            return null;
        }
        if (!combat.isSet("attack-damage") || !combat.isSet("attack-speed")) {
            throw new IllegalArgumentException("combat component is incomplete in " + source);
        }
        return new ItemCombatDefinition(
            combat.getDouble("attack-damage"), combat.getDouble("attack-speed")
        );
    }

    private static ItemArmorDefinition parseArmor(ConfigurationSection armor, Path source) {
        if (armor == null) {
            return null;
        }
        if (!armor.isString("slot") || !armor.isSet("defense")) {
            throw new IllegalArgumentException("armor component is incomplete in " + source);
        }
        String repair = armor.getString("repair-item");
        if (repair != null && !repair.isBlank()) {
            if (!repair.trim().matches("[A-Za-z0-9_:]+")) {
                throw new IllegalArgumentException("armor repair-item is not an item name (" + repair + ") in " + source);
            }
            boolean item;
            try {
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(repair.trim());
                item = material != null && material.isItem();
            } catch (LinkageError | RuntimeException noServer) {
                // Without a server (unit tests) the material registry is not there; the stack factory checks it again.
                item = true;
            }
            if (!item) {
                throw new IllegalArgumentException("armor repair-item is not an item (" + repair + ") in " + source);
            }
        }
        return new ItemArmorDefinition(armor.getString("slot"), armor.getDouble("defense"),
            armor.getDouble("toughness", 0.0), armor.getString("equipment-model"),
            armor.getDouble("water-efficiency", 0.0), armor.getString("repair-item"), armor.getInt("enchantability", 0));
    }

    private static ItemFoodDefinition parseFood(ConfigurationSection food, Path source) {
        if (food == null) {
            return null;
        }
        if (!food.isInt("nutrition") || !food.isSet("saturation") || !food.isBoolean("can-always-eat")) {
            throw new IllegalArgumentException("food component is incomplete in " + source);
        }
        return new ItemFoodDefinition(
            food.getInt("nutrition"), (float) food.getDouble("saturation"), food.getBoolean("can-always-eat")
        );
    }

    private static ItemRecipeDefinition parseRecipe(ConfigurationSection recipe, Path source) {
        if (recipe == null) {
            return null;
        }
        String key = requiredString(recipe, "key", source);
        if (!recipe.isInt("result-amount")) {
            throw new IllegalArgumentException("recipe result-amount is required in " + source);
        }
        String resultMaterial = recipe.getString("result-material");
        String type = recipe.getString("type", "shaped").trim().toUpperCase(java.util.Locale.ROOT);
        if (type.equals("SHAPELESS")) {
            List<String> ingredients = recipe.getStringList("ingredients");
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("shapeless recipe ingredients are required in " + source);
            }
            return ItemRecipeDefinition.shapeless(key, recipe.getInt("result-amount"), ingredients, resultMaterial);
        }
        if (!type.equals("SHAPED")) {
            throw new IllegalArgumentException("unknown recipe type in " + source + ": " + type);
        }
        List<String> shape = recipe.getStringList("shape");
        ConfigurationSection ingredients = requiredSection(recipe, "ingredients", source);
        Map<Character, String> values = new LinkedHashMap<>();
        for (String ingredientKey : ingredients.getKeys(false)) {
            if (ingredientKey.length() != 1) {
                throw new IllegalArgumentException("recipe ingredient keys must be one character in " + source);
            }
            values.put(ingredientKey.charAt(0), requiredString(ingredients, ingredientKey, source));
        }
        return new ItemRecipeDefinition(key, recipe.getInt("result-amount"), type.equals("SHAPELESS")
            ? RecipeType.SHAPELESS : RecipeType.SHAPED, shape, values, List.of(), resultMaterial);
    }

    private static ConfigurationSection requiredSection(
        ConfigurationSection configuration,
        String path,
        Path source
    ) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException(path + " section is required in " + source);
        }
        return section;
    }

    private static String requiredString(ConfigurationSection configuration, String path, Path source) {
        String value = configuration.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " is required in " + source);
        }
        return value;
    }
}
