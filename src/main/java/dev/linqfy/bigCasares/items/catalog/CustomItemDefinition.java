package dev.linqfy.bigCasares.items.catalog;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record CustomItemDefinition(
    String id,
    String mechanic,
    String material,
    String itemModel,
    OptionalInt legacyCustomModelData,
    ItemDisplayDefinition display,
    int maxStackSize,
    Integer maxDamage,
    ItemFoodDefinition food,
    ItemRecipeDefinition recipe,
    ItemCombatDefinition combat,
    ItemAppearanceDefinition appearance,
    ItemArmorDefinition armor
) {

    /** Items without an armor component. */
    public CustomItemDefinition(String id, String mechanic, String material, String itemModel,
                                OptionalInt legacyCustomModelData, ItemDisplayDefinition display, int maxStackSize,
                                Integer maxDamage, ItemFoodDefinition food, ItemRecipeDefinition recipe,
                                ItemCombatDefinition combat, ItemAppearanceDefinition appearance) {
        this(id, mechanic, material, itemModel, legacyCustomModelData, display, maxStackSize, maxDamage, food, recipe,
            combat, appearance, null);
    }

    public CustomItemDefinition {
        id = normalizeId(id);
        mechanic = normalizeMechanic(mechanic);
        material = normalizeMaterial(material);
        itemModel = normalizeItemModel(itemModel);
        legacyCustomModelData = legacyCustomModelData == null ? OptionalInt.empty() : legacyCustomModelData;
        if (legacyCustomModelData.isPresent() && legacyCustomModelData.getAsInt() < 0) {
            throw new IllegalArgumentException("legacyCustomModelData must not be negative");
        }
        display = Objects.requireNonNull(display, "display");
        if (maxStackSize < 1 || maxStackSize > 64) {
            throw new IllegalArgumentException("maxStackSize must be between 1 and 64");
        }
        if (maxDamage != null && maxDamage < 1) {
            throw new IllegalArgumentException("maxDamage must be positive");
        }
        appearance = Objects.requireNonNull(appearance, "appearance");
    }

    public Optional<ItemFoodDefinition> foodDefinition() {
        return Optional.ofNullable(food);
    }

    public Optional<ItemRecipeDefinition> recipeDefinition() {
        return Optional.ofNullable(recipe);
    }

    public Optional<ItemCombatDefinition> combatDefinition() {
        return Optional.ofNullable(combat);
    }

    public Optional<ItemArmorDefinition> armorDefinition() {
        return Optional.ofNullable(armor);
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNull(value, "id").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("item id is invalid: " + value);
        }
        return normalized;
    }

    private static String normalizeMechanic(String value) {
        String normalized = Objects.requireNonNull(value, "mechanic").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("mechanic is invalid: " + value);
        }
        return normalized;
    }

    private static String normalizeMaterial(String value) {
        String normalized = Objects.requireNonNull(value, "material").trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("material is invalid: " + value);
        }
        return normalized;
    }

    private static String normalizeItemModel(String value) {
        String normalized = Objects.requireNonNull(value, "itemModel").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("itemModel is invalid: " + value);
        }
        return normalized;
    }
}
