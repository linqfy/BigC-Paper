package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An item reduced to what the model needs: its namespaced key and its enchantment levels.
 */
public record CosmicNivItem(String key, Map<String, Integer> enchantments) {
    public CosmicNivItem {
        key = CosmicNivKeys.normalize(key);
        Objects.requireNonNull(enchantments, "enchantments");
        Map<String, Integer> normalized = new LinkedHashMap<>();
        enchantments.forEach((enchantment, level) -> {
            if (level != null && level > 0) {
                normalized.merge(CosmicNivKeys.normalize(enchantment), level, Math::max);
            }
        });
        enchantments = Collections.unmodifiableMap(normalized);
    }

    public static CosmicNivItem of(String key) {
        return new CosmicNivItem(key, Map.of());
    }

    public static CosmicNivItem of(String key, Map<String, Integer> enchantments) {
        return new CosmicNivItem(key, enchantments);
    }
}
