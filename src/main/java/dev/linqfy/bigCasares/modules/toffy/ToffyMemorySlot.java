package dev.linqfy.bigCasares.modules.toffy;

import java.util.Locale;
import java.util.Optional;

/**
 * What the memory remembers for each player: one piece per armor slot, the best weapon and the best tool.
 */
public enum ToffyMemorySlot {
    HELMET(ToffyArmorSlot.HELMET),
    CHESTPLATE(ToffyArmorSlot.CHESTPLATE),
    LEGGINGS(ToffyArmorSlot.LEGGINGS),
    BOOTS(ToffyArmorSlot.BOOTS),
    WEAPON(null),
    TOOL(null);

    private final ToffyArmorSlot armorSlot;

    ToffyMemorySlot(ToffyArmorSlot armorSlot) {
        this.armorSlot = armorSlot;
    }

    /**
     * The armor slot this memory slot stands for, or {@code null} for {@code WEAPON} and {@code TOOL}.
     */
    public ToffyArmorSlot armorSlot() {
        return armorSlot;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ToffyMemorySlot> fromConfigKey(String key) {
        for (ToffyMemorySlot slot : values()) {
            if (slot.configKey().equalsIgnoreCase(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
