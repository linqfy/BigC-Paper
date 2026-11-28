package dev.linqfy.bigCasares.modules.toffy;

import java.util.Locale;
import java.util.Optional;

/**
 * Where an item counts: {@code ARMOR} only in armor slots, {@code WEAPON} and {@code TOOL} only when held.
 */
public enum ToffyItemCategory {
    ARMOR,
    WEAPON,
    TOOL;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ToffyItemCategory> fromConfigKey(String key) {
        for (ToffyItemCategory category : values()) {
            if (category.configKey().equalsIgnoreCase(key == null ? "" : key.trim())) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
