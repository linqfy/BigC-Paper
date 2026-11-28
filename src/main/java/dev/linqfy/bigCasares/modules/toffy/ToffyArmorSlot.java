package dev.linqfy.bigCasares.modules.toffy;

import java.util.Locale;
import java.util.Optional;

public enum ToffyArmorSlot {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The slot a vanilla item key is worn in, inferred from its name ({@code *_helmet}, {@code *_chestplate},
     * {@code *_leggings}, {@code *_boots}, heads, pumpkins and the elytra); empty when it cannot be told.
     */
    public static Optional<ToffyArmorSlot> ofItemKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String path = key.substring(key.indexOf(':') + 1);
        if (path.endsWith("_helmet") || path.endsWith("_head") || path.endsWith("_skull")
            || path.equals("carved_pumpkin")) {
            return Optional.of(HELMET);
        }
        if (path.endsWith("_chestplate") || path.equals("elytra")) {
            return Optional.of(CHESTPLATE);
        }
        if (path.endsWith("_leggings")) {
            return Optional.of(LEGGINGS);
        }
        if (path.endsWith("_boots")) {
            return Optional.of(BOOTS);
        }
        return Optional.empty();
    }

    public static Optional<ToffyArmorSlot> fromConfigKey(String key) {
        for (ToffyArmorSlot slot : values()) {
            if (slot.configKey().equalsIgnoreCase(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
