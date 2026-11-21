package dev.linqfy.bigCasares.modules.duels;

import java.util.Locale;
import java.util.Optional;

/** The three arena sizes; the side length follows chunks (4x4, 2.3x2.3 and 1.5x1.5 chunks). */
enum DuelArenaSize {

    GRANDE("grande", "Grande", 64),
    MEDIANA("media", "Mediana", 37),
    CHICA("chica", "Chica", 24);

    private final String id;
    private final String displayName;
    private final int side;

    DuelArenaSize(String id, String displayName, int side) {
        this.id = id;
        this.displayName = displayName;
        this.side = side;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    /** Side length of the floor in blocks. */
    int side() {
        return side;
    }

    /** Distance from the center to the inner edge of the walls. */
    double halfSide() {
        return side / 2.0;
    }

    static Optional<DuelArenaSize> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "grande", "large", "big", "g" -> Optional.of(GRANDE);
            case "media", "mediana", "medium", "m" -> Optional.of(MEDIANA);
            case "chica", "chico", "small", "c" -> Optional.of(CHICA);
            default -> Optional.empty();
        };
    }
}
