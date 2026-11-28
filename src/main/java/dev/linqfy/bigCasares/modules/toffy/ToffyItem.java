package dev.linqfy.bigCasares.modules.toffy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An item reduced to what the model needs: its namespaced key and its enchantment levels.
 */
public record ToffyItem(String key, Map<String, Integer> enchantments) {
    public ToffyItem {
        key = ToffyKeys.normalize(key);
        Objects.requireNonNull(enchantments, "enchantments");
        Map<String, Integer> normalized = new LinkedHashMap<>();
        enchantments.forEach((enchantment, level) -> {
            if (level != null && level > 0) {
                normalized.merge(ToffyKeys.normalize(enchantment), level, Math::max);
            }
        });
        enchantments = Collections.unmodifiableMap(normalized);
    }

    public static ToffyItem of(String key) {
        return new ToffyItem(key, Map.of());
    }

    public static ToffyItem of(String key, Map<String, Integer> enchantments) {
        return new ToffyItem(key, enchantments);
    }
}
