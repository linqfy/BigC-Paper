package dev.linqfy.bigCasares.items.catalog;

import dev.linqfy.bigCasares.modules.alexscaves.AlexsCavesItemIds;
import dev.linqfy.bigCasares.modules.resourcepack.JsonSyntaxValidator;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ItemCatalogValidator {

    private static final Map<String, String> EXPECTED_MECHANICS = Map.ofEntries(
        Map.entry("copper_apple", "copper-apple"),
        Map.entry("smoke_bomb", "smoke-bomb"),
        Map.entry("prismarine_arrow", "prismarine-arrow"),
        Map.entry("nexus", "nexus"),
        Map.entry("echo_arrow", "echo-arrow"),
        Map.entry("golden_tipped_amethyst_arrow", "golden-amethyst-arrow"),
        Map.entry("tracker_compass", "tracker-compass"),
        Map.entry("nuke_shot", "nuke-shot"),
        Map.entry("sahurs_bat", "sahurs-bat"),
        Map.entry("potassium_nitrate", "catalog-material"),
        Map.entry("nitric_acid", "catalog-material"),
        Map.entry("grappling_hook_1", "grappling-hook"),
        Map.entry("grappling_hook_2", "grappling-hook"),
        Map.entry("grappling_hook_3", "grappling-hook"),
        Map.entry("grappling_hook_4", "grappling-hook"),
        Map.entry("grappling_hook_5", "grappling-hook"),
        Map.entry("grappling_hook_6", "grappling-hook"),
        Map.entry("glider_tier_1", "glider"),
        Map.entry("glider_tier_2", "glider"),
        Map.entry("glider_tier_3", "glider"),
        Map.entry("glider_tier_4", "glider"),
        Map.entry("glider_tier_5", "glider"),
        Map.entry("glider_tier_6", "glider"),
        Map.entry("keep_inventory_totem", "keep-inventory-totem")
    );

    private final ItemMaterialValidator materials;

    public ItemCatalogValidator() {
        this(material -> material.matches("[A-Z0-9_]+"));
    }

    public ItemCatalogValidator(ItemMaterialValidator materials) {
        this.materials = java.util.Objects.requireNonNull(materials, "materials");
    }

    public void validate(CustomItemCatalog catalog, Path contentPackRoot) {
        Path packRoot = contentPackRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(packRoot)) {
            throw new IllegalArgumentException("content pack directory does not exist: " + packRoot);
        }
        Set<String> models = new HashSet<>();
        Set<Integer> legacyModelData = new HashSet<>();
        Set<String> recipeKeys = new HashSet<>();
        for (CustomItemDefinition definition : catalog.definitions().values()) {
            String expectedMechanic = EXPECTED_MECHANICS.get(definition.id());
            if (expectedMechanic == null && AlexsCavesItemIds.contains(definition.id())) {
                expectedMechanic = AlexsCavesItemIds.MECHANIC;
            }
            if (expectedMechanic == null || !expectedMechanic.equals(definition.mechanic())) {
                throw new IllegalArgumentException("unsupported item mechanic for " + definition.id());
            }
            if (!materials.isItem(definition.material())) {
                throw new IllegalArgumentException("item material is not available: " + definition.material());
            }
            if (!models.add(definition.itemModel().toString())) {
                throw new IllegalArgumentException("duplicate item-model: " + definition.itemModel());
            }
            if (definition.legacyCustomModelData().isPresent()
                && !legacyModelData.add(definition.legacyCustomModelData().getAsInt())) {
                throw new IllegalArgumentException("duplicate legacy custom-model-data: "
                    + definition.legacyCustomModelData().getAsInt());
            }
            definition.recipeDefinition().ifPresent(recipe -> {
                if (!recipeKeys.add(recipe.key())) {
                    throw new IllegalArgumentException("duplicate item recipe key: " + recipe.key());
                }
                if (recipe.resultMaterial() != null && !materials.isItem(recipe.resultMaterial())) {
                    throw new IllegalArgumentException(
                        "recipe result material is not available: " + recipe.resultMaterial());
                }
                for (String material : recipe.ingredients().values()) {
                    validateIngredient(material, catalog);
                }
                for (String material : recipe.shapelessIngredients()) {
                    validateIngredient(material, catalog);
                }
            });
            validateAppearance(packRoot, definition.appearance());
        }
    }

    private void validateIngredient(String value, CustomItemCatalog catalog) {
        for (String part : value.split("\\|")) {
            if (part.contains(":")) {
                // Custom catalog item reference: must resolve inside the same catalog.
                String itemId = part.substring(part.indexOf(':') + 1);
                if (catalog.find(itemId).isEmpty()) {
                    throw new IllegalArgumentException("recipe ingredient references unknown catalog item: " + part);
                }
                continue;
            }
            if (!materials.isItem(part)) {
                throw new IllegalArgumentException("recipe material is not available: " + part);
            }
        }
    }

    private static void validateAppearance(Path packRoot, ItemAppearanceDefinition appearance) {
        Path javaDefinition = validateFile(packRoot, appearance.javaItemDefinition(), ".json");
        Path bedrockTexture = validateFile(packRoot, appearance.bedrockTexture(), ".png");
        try {
            new JsonSyntaxValidator().validate(Files.readString(javaDefinition), appearance.javaItemDefinition());
            if (ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(bedrockTexture))) == null) {
                throw new IllegalArgumentException("unreadable item appearance texture: " + appearance.bedrockTexture());
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("could not validate item appearance assets", exception);
        }
    }

    private static Path validateFile(Path root, String relativePath, String extension) {
        if (!relativePath.endsWith(extension)) {
            throw new IllegalArgumentException("appearance asset has wrong extension: " + relativePath);
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("missing item appearance asset: " + relativePath);
        }
        return candidate;
    }
}
