package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DonPolloVariant {
    COMUN("comun"),
    GORDITO("gordito"),
    SALSERO("salsero"),
    AURA_67("aura-67"),
    FINO("fino");

    private final String id;

    DonPolloVariant(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<DonPolloVariant> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(variant -> variant.id.equals(normalized)).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(DonPolloVariant::id).toList();
    }
}
