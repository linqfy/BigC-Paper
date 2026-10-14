package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Locale;
import java.util.Optional;

/**
 * What the memory remembers for each player: one piece per armor slot, the best weapon and the best tool.
 */
public enum CosmicNivMemorySlot {
    HELMET(CosmicNivArmorSlot.HELMET),
    CHESTPLATE(CosmicNivArmorSlot.CHESTPLATE),
    LEGGINGS(CosmicNivArmorSlot.LEGGINGS),
    BOOTS(CosmicNivArmorSlot.BOOTS),
    WEAPON(null),
    TOOL(null);

    private final CosmicNivArmorSlot armorSlot;

    CosmicNivMemorySlot(CosmicNivArmorSlot armorSlot) {
        this.armorSlot = armorSlot;
    }

    /**
     * The armor slot this memory slot stands for, or {@code null} for {@code WEAPON} and {@code TOOL}.
     */
    public CosmicNivArmorSlot armorSlot() {
        return armorSlot;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CosmicNivMemorySlot> fromConfigKey(String key) {
        for (CosmicNivMemorySlot slot : values()) {
            if (slot.configKey().equalsIgnoreCase(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
