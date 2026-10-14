package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Locale;
import java.util.Optional;

/**
 * Where an item counts: {@code ARMOR} only in armor slots, {@code WEAPON} and {@code TOOL} only when held.
 */
public enum CosmicNivItemCategory {
    ARMOR,
    WEAPON,
    TOOL;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CosmicNivItemCategory> fromConfigKey(String key) {
        for (CosmicNivItemCategory category : values()) {
            if (category.configKey().equalsIgnoreCase(key == null ? "" : key.trim())) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
