package dev.linqfy.bigCasares.modules.nexus;

import java.util.Locale;
import java.util.Optional;

/** Mejoras de base que un equipo instala en su Nexus. El id es estable: se guarda en nexuses.yml. */
public enum NexusUpgrade {
    MOB_REPULSOR("repulsor", "Módulo repulsor", "Aleja a los mobs hostiles e impide que aparezcan cerca del Nexus."),
    SATURATION_AURA("saturacion", "Aura de saturación", "Alimenta poco a poco a tu equipo cerca del Nexus.");

    private final String id;
    private final String displayName;
    private final String description;

    NexusUpgrade(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static Optional<NexusUpgrade> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("ó", "o");
        for (NexusUpgrade upgrade : values()) {
            if (upgrade.id.equals(value) || upgrade.name().equalsIgnoreCase(value)) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }
}
