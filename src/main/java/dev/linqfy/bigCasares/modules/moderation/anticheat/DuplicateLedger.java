package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una pasada del escaner de dupes. Cada item valioso lleva un id unico; si en la misma pasada (el mismo tick) el mismo
 * id aparece en dos lugares, el item se duplico: los traspasos legitimos lo mueven, nunca lo dejan en dos lados a la vez.
 */
public final class DuplicateLedger {
    /** Un mismo id duplicado se reporta una vez cada este tiempo. */
    static final long REPORT_COOLDOWN_MILLIS = 10L * 60L * 1000L;

    private final Map<String, Long> reported = new HashMap<>();
    private Map<String, Holder> pass = new HashMap<>();
    private final List<Duplicate> found = new ArrayList<>();

    /** Quien tiene el item: el jugador (si hay) y una descripcion del lugar para la evidencia. */
    public record Holder(UUID playerId, String playerName, String place, boolean sharedContainer) {
        public Holder(UUID playerId, String playerName, String place) {
            this(playerId, playerName, place, false);
        }
    }

    public record Duplicate(String itemId, String item, Holder first, Holder second) {
        public boolean hasSharedContainer() {
            return first.sharedContainer() || second.sharedContainer();
        }
    }

    public void beginPass() {
        pass = new HashMap<>();
        found.clear();
    }

    public void seen(String itemId, String item, Holder holder) {
        Holder previous = pass.putIfAbsent(itemId, holder);
        if (previous != null) {
            found.add(new Duplicate(itemId, item, previous, holder));
        }
    }

    /** Duplicados de esta pasada que no se reportaron hace poco. */
    public List<Duplicate> endPass(long nowMillis) {
        reported.values().removeIf(at -> at < nowMillis - REPORT_COOLDOWN_MILLIS);
        Map<String, Duplicate> byItem = new LinkedHashMap<>();
        for (Duplicate duplicate : found) {
            byItem.merge(duplicate.itemId(), duplicate,
                (previous, current) -> current.hasSharedContainer() ? current : previous);
        }
        List<Duplicate> fresh = new ArrayList<>();
        for (Duplicate duplicate : byItem.values()) {
            if (reported.putIfAbsent(duplicate.itemId(), nowMillis) == null) {
                fresh.add(duplicate);
            }
        }
        found.clear();
        pass = new HashMap<>();
        return fresh;
    }

    public void clear() {
        reported.clear();
        found.clear();
        pass = new HashMap<>();
    }
}
