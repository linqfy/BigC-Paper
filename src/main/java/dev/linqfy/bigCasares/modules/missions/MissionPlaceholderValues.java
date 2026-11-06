package dev.linqfy.bigCasares.modules.missions;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Values of the {@code %bigc_...%} placeholders (PlaceholderAPI). Scoreboards may ask from any thread, so they read
 * snapshots the server thread refreshes, never the mission state itself.
 * <ul>
 *   <li>{@code misiones_listas}: missions ready to claim;</li>
 *   <li>{@code misiones_premio}: what Reclamar would pay;</li>
 *   <li>{@code tienda_ultima}: the player's last shop purchase or sale.</li>
 * </ul>
 */
public final class MissionPlaceholderValues {
    public static final String IDENTIFIER = "bigc";

    record Snapshot(int ready, String reward) {
    }

    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Function<UUID, Optional<String>> lastShopOperation;

    MissionPlaceholderValues(Function<UUID, Optional<String>> lastShopOperation) {
        this.lastShopOperation = lastShopOperation;
    }

    /** Server thread: the player's current numbers. */
    void update(UUID playerId, int ready, String reward) {
        snapshots.put(playerId, new Snapshot(ready, reward));
    }

    void forget(UUID playerId) {
        snapshots.remove(playerId);
    }

    void clear() {
        snapshots.clear();
    }

    /** Any thread: the value of {@code key} (without the {@code bigc_} prefix), or "" when unknown. */
    public String resolve(UUID playerId, String key) {
        if (playerId == null || key == null) {
            return "";
        }
        Snapshot snapshot = snapshots.get(playerId);
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "misiones_listas" -> snapshot == null ? "0" : Integer.toString(snapshot.ready());
            case "misiones_premio" -> snapshot == null ? "" : snapshot.reward();
            case "tienda_ultima" -> lastShopOperation.apply(playerId).orElse("");
            default -> "";
        };
    }
}
