package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything both configuration files say. {@code cosmicniv_lib.yml}: output, weights, searches, curve and
 * references. {@code cosmicniv_valores.yml}: armor values, weapon and tool families with their values,
 * enchantment values and custom items.
 */
public record CosmicNivSettings(
    double maximum,
    int decimals,
    double armorWeight,
    double weaponWeight,
    double toolWeight,
    CosmicNivSearch search,
    CosmicNivCurve curve,
    CosmicNivReference zeroReference,
    CosmicNivReference maxReference,
    Map<String, Double> armorValues,
    Map<String, Map<String, Double>> weaponFamilies,
    Map<String, Map<String, Double>> toolFamilies,
    Map<String, CosmicNivEnchantmentValue> enchantmentValues,
    List<CosmicNivCustomItem> customItems,
    CosmicNivMemorySettings memory
) {
    public CosmicNivSettings {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(zeroReference, "zeroReference");
        Objects.requireNonNull(maxReference, "maxReference");
        armorValues = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(armorValues, "armorValues")));
        weaponFamilies = copyFamilies(Objects.requireNonNull(weaponFamilies, "weaponFamilies"));
        toolFamilies = copyFamilies(Objects.requireNonNull(toolFamilies, "toolFamilies"));
        enchantmentValues = Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(enchantmentValues, "enchantmentValues")));
        customItems = List.copyOf(Objects.requireNonNull(customItems, "customItems"));
        Objects.requireNonNull(memory, "memory");
    }

    public Map<String, Map<String, Double>> families(CosmicNivItemCategory category) {
        return category == CosmicNivItemCategory.TOOL ? toolFamilies : weaponFamilies;
    }

    public double weight(CosmicNivItemCategory category) {
        return switch (category) {
            case ARMOR -> armorWeight;
            case WEAPON -> weaponWeight;
            case TOOL -> toolWeight;
        };
    }

    public CosmicNivSettings withCurve(CosmicNivCurve newCurve) {
        return new CosmicNivSettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, newCurve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public CosmicNivSettings withZeroReference(CosmicNivReference newZeroReference) {
        return new CosmicNivSettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, curve,
            newZeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public CosmicNivSettings withDecimals(int newDecimals) {
        return new CosmicNivSettings(maximum, newDecimals, armorWeight, weaponWeight, toolWeight, search, curve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public CosmicNivSettings withWeights(double newArmorWeight, double newWeaponWeight, double newToolWeight) {
        return new CosmicNivSettings(maximum, decimals, newArmorWeight, newWeaponWeight, newToolWeight, search,
            curve, zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues,
            customItems, memory);
    }

    public CosmicNivSettings withSearch(CosmicNivSearch newSearch) {
        return new CosmicNivSettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, newSearch, curve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public CosmicNivSettings withMemory(CosmicNivMemorySettings newMemory) {
        return new CosmicNivSettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, curve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems,
            newMemory);
    }

    private static Map<String, Map<String, Double>> copyFamilies(Map<String, Map<String, Double>> families) {
        Map<String, Map<String, Double>> copy = new LinkedHashMap<>();
        families.forEach((family, members) ->
            copy.put(family, Collections.unmodifiableMap(new LinkedHashMap<>(members))));
        return Collections.unmodifiableMap(copy);
    }
}
