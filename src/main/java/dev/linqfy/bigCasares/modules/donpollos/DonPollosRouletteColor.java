package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum DonPollosRouletteColor {
    RED("red", "§c§lROJO"),
    BLACK("black", "§8§lNEGRO"),
    GREEN("green", "§a§lVERDE");

    private final String id;
    private final String displayName;

    DonPollosRouletteColor(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<DonPollosRouletteColor> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(color -> color.id.equals(normalized)).findFirst();
    }
}
