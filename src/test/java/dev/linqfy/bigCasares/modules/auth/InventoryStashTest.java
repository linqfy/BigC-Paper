package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryStashTest {

    @TempDir
    Path directory;

    @Test
    void keepsTheStashUntilTheLoadedPlayerDataAcknowledgesItsRevision() throws IOException {
        UUID playerId = UUID.randomUUID();
        UUID revision = UUID.randomUUID();
        Path file = directory.resolve(playerId + ".yml");
        Files.writeString(file, "revision: " + revision + "\n");

        InventoryStash stash = new InventoryStash(directory);
        assertFalse(stash.retireRestored(playerId, null));
        assertFalse(stash.retireRestored(playerId, UUID.randomUUID().toString()));
        assertTrue(stash.exists(playerId));
        assertTrue(stash.retireRestored(playerId, revision.toString()));
        assertFalse(stash.exists(playerId));
    }

    @Test
    void aLegacyStashHasAStableRevisionAcrossRestarts() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path file = directory.resolve(playerId + ".yml");
        Files.writeString(file, "saved-at: 5\nitems: sample\n");

        InventoryStash stash = new InventoryStash(directory);
        String restored = stash.revision(playerId).orElseThrow();
        assertTrue(new InventoryStash(directory).retireRestored(playerId, restored));
        assertFalse(stash.exists(playerId));
    }
}
