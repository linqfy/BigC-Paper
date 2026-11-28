package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDataFolderTest {

    @TempDir
    Path data;

    @Test
    void theLegacyFolderMovesToTheNewOneWithItsFiles() throws Exception {
        Path legacy = data.resolve("data/staff-events");
        Files.createDirectories(legacy.resolve("rewards-pending"));
        Files.writeString(legacy.resolve("snapshots.yml"), "players: {}", StandardCharsets.UTF_8);

        EventDataFolder.Resolution result = EventDataFolder.resolve(data);

        Path current = data.resolve("data/toffy-complementaries/eventos");
        assertEquals(current, result.folder());
        assertTrue(result.moved());
        assertEquals(null, result.failure());
        assertFalse(Files.exists(legacy));
        assertEquals("players: {}", Files.readString(current.resolve("snapshots.yml"), StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(current.resolve("rewards-pending")));
    }

    @Test
    void anExistingNewFolderWinsAndTheLegacyOneIsLeftAlone() throws Exception {
        Path legacy = data.resolve("data/staff-events");
        Path current = data.resolve("data/toffy-complementaries/eventos");
        Files.createDirectories(legacy);
        Files.createDirectories(current);
        Files.writeString(legacy.resolve("snapshots.yml"), "old", StandardCharsets.UTF_8);

        EventDataFolder.Resolution result = EventDataFolder.resolve(data);

        assertEquals(current, result.folder());
        assertFalse(result.moved());
        assertTrue(Files.exists(legacy.resolve("snapshots.yml")));
    }

    @Test
    void inventoriesInBothFoldersAreAConflictAndNothingIsMoved() throws Exception {
        Path legacy = data.resolve("data/staff-events");
        Path current = data.resolve("data/toffy-complementaries/eventos");
        Files.createDirectories(legacy);
        Files.createDirectories(current);
        Files.writeString(legacy.resolve("snapshots.yml"), "players: {}", StandardCharsets.UTF_8);

        EventDataFolder.Resolution result = EventDataFolder.resolve(data);

        assertFalse(result.moved());
        assertNotNull(result.conflict());
        assertTrue(result.conflict().contains(legacy.resolve("snapshots.yml").toString()));
        assertTrue(result.conflict().contains(current.toString()));
        assertTrue(Files.exists(legacy.resolve("snapshots.yml")));
    }

    @Test
    void anEmptyLegacySnapshotFileIsNoConflict() throws Exception {
        Path legacy = data.resolve("data/staff-events");
        Files.createDirectories(legacy);
        Files.createDirectories(data.resolve("data/toffy-complementaries/eventos"));
        Files.writeString(legacy.resolve("snapshots.yml"), "", StandardCharsets.UTF_8);

        assertEquals(null, EventDataFolder.resolve(data).conflict());
    }

    @Test
    void nothingToMoveUsesTheNewFolder() {
        EventDataFolder.Resolution result = EventDataFolder.resolve(data);

        assertEquals(data.resolve("data/toffy-complementaries/eventos"), result.folder());
        assertFalse(result.moved());
        assertEquals(null, result.failure());
    }

    @Test
    void aFailedMoveKeepsUsingTheLegacyFolder() throws Exception {
        Path legacy = data.resolve("data/staff-events");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("snapshots.yml"), "inventories", StandardCharsets.UTF_8);
        // A regular file where the new parent folder must go makes the move impossible.
        Files.createDirectories(data.resolve("data"));
        Files.writeString(data.resolve("data/toffy-complementaries"), "not a folder", StandardCharsets.UTF_8);

        EventDataFolder.Resolution result = EventDataFolder.resolve(data);

        assertEquals(legacy, result.folder());
        assertFalse(result.moved());
        assertNotNull(result.failure());
        assertEquals("inventories", Files.readString(legacy.resolve("snapshots.yml"), StandardCharsets.UTF_8));
    }
}
