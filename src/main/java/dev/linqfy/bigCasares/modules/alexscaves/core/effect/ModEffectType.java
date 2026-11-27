package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import java.util.Locale;
import java.util.Optional;

/** The eight status effects of the mod, implemented by the module instead of the vanilla registry. */
public enum ModEffectType {
    MAGNETIZING("Magnetizado", false),
    STUNNED("Aturdido", true),
    RAGE("Furia", false),
    IRRADIATED("Irradiado", true),
    BUBBLED("Burbujeado", true),
    DEEPSIGHT("Visión profunda", false),
    DARKNESS_INCARNATE("Oscuridad encarnada", false),
    SUGAR_RUSH("Subidón de azúcar", false);

    private final String displayName;
    private final boolean harmful;

    ModEffectType(String displayName, boolean harmful) {
        this.displayName = displayName;
        this.harmful = harmful;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public boolean harmful() {
        return harmful;
    }

    /**
     * Milk and similar cures do not remove these (mod: Irradiated and Darkness Incarnate have no curative items,
     * Bubbled ignores milk).
     */
    public boolean curableByMilk() {
        return this != IRRADIATED && this != BUBBLED && this != DARKNESS_INCARNATE;
    }

    public static Optional<ModEffectType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ModEffectType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
