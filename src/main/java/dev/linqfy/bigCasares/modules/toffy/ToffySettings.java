package dev.linqfy.bigCasares.modules.toffy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything both configuration files say. {@code toffy_complementaries.yml}: output, weights, searches, curve and
 * references. {@code toffy_valores.yml}: armor values, weapon and tool families with their values,
 * enchantment values and custom items.
 */
public record ToffySettings(
    double maximum,
    int decimals,
    double armorWeight,
    double weaponWeight,
    double toolWeight,
    ToffySearch search,
    ToffyCurve curve,
    ToffyReference zeroReference,
    ToffyReference maxReference,
    Map<String, Double> armorValues,
    Map<String, Map<String, Double>> weaponFamilies,
    Map<String, Map<String, Double>> toolFamilies,
    Map<String, ToffyEnchantmentValue> enchantmentValues,
    List<ToffyCustomItem> customItems,
    ToffyMemorySettings memory
) {
    public ToffySettings {
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

    public Map<String, Map<String, Double>> families(ToffyItemCategory category) {
        return category == ToffyItemCategory.TOOL ? toolFamilies : weaponFamilies;
    }

    public double weight(ToffyItemCategory category) {
        return switch (category) {
            case ARMOR -> armorWeight;
            case WEAPON -> weaponWeight;
            case TOOL -> toolWeight;
        };
    }

    public ToffySettings withCurve(ToffyCurve newCurve) {
        return new ToffySettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, newCurve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public ToffySettings withZeroReference(ToffyReference newZeroReference) {
        return new ToffySettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, curve,
            newZeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public ToffySettings withDecimals(int newDecimals) {
        return new ToffySettings(maximum, newDecimals, armorWeight, weaponWeight, toolWeight, search, curve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public ToffySettings withWeights(double newArmorWeight, double newWeaponWeight, double newToolWeight) {
        return new ToffySettings(maximum, decimals, newArmorWeight, newWeaponWeight, newToolWeight, search,
            curve, zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues,
            customItems, memory);
    }

    public ToffySettings withSearch(ToffySearch newSearch) {
        return new ToffySettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, newSearch, curve,
            zeroReference, maxReference, armorValues, weaponFamilies, toolFamilies, enchantmentValues, customItems, memory);
    }

    public ToffySettings withMemory(ToffyMemorySettings newMemory) {
        return new ToffySettings(maximum, decimals, armorWeight, weaponWeight, toolWeight, search, curve,
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
