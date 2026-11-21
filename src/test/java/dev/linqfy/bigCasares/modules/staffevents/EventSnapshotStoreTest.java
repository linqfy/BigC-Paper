package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventSnapshotStoreTest {

    @TempDir
    Path temp;

    @Test
    void snapshotsSurviveARestartUntilRemoved() throws Exception {
        Path file = temp.resolve("data/staff-events/snapshots.yml");
        UUID playerId = UUID.randomUUID();
        EventSnapshotStore store = new EventSnapshotStore(file);
        store.load();
        store.put(new EventPlayerSnapshot(playerId, new ItemStack[41],
            new EventPlayerSnapshot.StoredLocation("world", 10.5, 64, -3.5, 90f, 10f),
            "SURVIVAL", 14.0, 17, 2.5f, 30, 0.25f, List.of()));
        assertTrue(Files.exists(file));

        EventSnapshotStore reloaded = new EventSnapshotStore(file);
        reloaded.load();
        EventPlayerSnapshot snapshot = reloaded.find(playerId).orElseThrow();
        assertEquals(41, snapshot.contents().length);
        assertEquals(new EventPlayerSnapshot.StoredLocation("world", 10.5, 64, -3.5, 90f, 10f), snapshot.location());
        assertEquals("SURVIVAL", snapshot.gameMode());
        assertEquals(14.0, snapshot.health());
        assertEquals(17, snapshot.foodLevel());
        assertEquals(30, snapshot.level());
        assertEquals(0.25f, snapshot.exp());

        reloaded.remove(playerId);
        EventSnapshotStore empty = new EventSnapshotStore(file);
        empty.load();
        assertFalse(empty.contains(playerId));
    }
    @Test
    void anUnsuccessfulRetirementKeepsTheSnapshotAvailable() throws Exception {
        Path file = temp.resolve("snapshots.yml");
        UUID playerId = UUID.randomUUID();
        EventSnapshotStore store = new EventSnapshotStore(file);
        store.put(new EventPlayerSnapshot(playerId, new ItemStack[41],
            new EventPlayerSnapshot.StoredLocation("world", 0, 64, 0, 0, 0),
            "SURVIVAL", 20, 20, 5, 0, 0, List.of()));
        Files.createDirectory(temp.resolve("snapshots.yml.tmp"));

        assertThrows(IOException.class, () -> store.remove(playerId));
        assertTrue(store.contains(playerId));
    }

}
