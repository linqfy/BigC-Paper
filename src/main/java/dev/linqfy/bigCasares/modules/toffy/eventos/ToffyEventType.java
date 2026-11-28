package dev.linqfy.bigCasares.modules.toffy.eventos;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Events that staff can start by hand. None of them is ever scheduled or started at random. */
public enum ToffyEventType {
    LIFESTEAL("lifesteal", "Lifesteal", false, List.of("robavida")),
    KOTH("koth", "King of the Hill", true, List.of("rey")),
    TNT_TAG("tnttag", "TNT Tag", true, List.of("tnt")),
    SPLEEF("spleef", "Spleef Clásico", true, List.of("nieve"));

    private final String id;
    private final String displayName;
    private final boolean arena;
    private final List<String> aliases;

    ToffyEventType(String id, String displayName, boolean arena, List<String> aliases) {
        this.id = id;
        this.displayName = displayName;
        this.arena = arena;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Arena events take participants to the event dimension with an event inventory;
     * the others happen in the normal worlds with keep inventory.
     */
    public boolean arena() {
        return arena;
    }

    public static Optional<ToffyEventType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (ToffyEventType type : values()) {
            if (type.id.equals(normalized) || type.aliases.contains(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
