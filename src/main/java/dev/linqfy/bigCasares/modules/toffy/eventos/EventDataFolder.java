package dev.linqfy.bigCasares.modules.toffy.eventos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Folder of the event data (snapshots.yml, rewards-pending). Older builds kept it in {@code data/staff-events}; that
 * folder is moved to {@code data/toffy-complementaries/eventos} once. When the move fails the old folder stays in use,
 * so no saved inventory is lost. When both folders hold inventories nothing is moved or merged: the events refuse to
 * start until an admin merges them by hand.
 */
public final class EventDataFolder {
    static final String LEGACY = "data/staff-events";
    static final String CURRENT = "data/toffy-complementaries/eventos";

    /**
     * @param folder   folder to use from now on
     * @param moved    true when the legacy folder was moved to {@code folder} by this call
     * @param failure  why the move failed; the legacy folder is then the one returned
     * @param conflict non-null when both folders hold inventories: the message names both paths and the events must
     *                 not start
     */
    public record Resolution(Path folder, boolean moved, IOException failure, String conflict) {
    }

    private EventDataFolder() {
    }

    /**
     * Moves the legacy folder when it exists and the new one does not. An existing new folder wins, unless the legacy
     * folder still has inventories in it, which is reported as a conflict.
     */
    public static Resolution resolve(Path dataFolder) {
        Path legacy = dataFolder.resolve(LEGACY);
        Path current = dataFolder.resolve(CURRENT);
        if (!Files.isDirectory(legacy)) {
            return new Resolution(current, false, null, null);
        }
        if (Files.exists(current)) {
            Path legacySnapshots = legacy.resolve("snapshots.yml");
            if (hasInventories(legacySnapshots)) {
                return new Resolution(current, false, null, "Hay inventarios de eventos en " + legacySnapshots
                    + " y tambien existe " + current + ". Uni los dos archivos a mano antes de iniciar eventos.");
            }
            return new Resolution(current, false, null, null);
        }
        try {
            Files.createDirectories(current.getParent());
            Files.move(legacy, current);
            return new Resolution(current, true, null, null);
        } catch (IOException failure) {
            return new Resolution(legacy, false, failure, null);
        }
    }

    /** A snapshot file with content, or one that cannot be checked (treated as holding inventories). */
    private static boolean hasInventories(Path snapshots) {
        if (!Files.exists(snapshots)) {
            return false;
        }
        try {
            return Files.size(snapshots) > 0;
        } catch (IOException unreadable) {
            return true;
        }
    }
}
