package dev.linqfy.bigCasares.dimension.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionDataStoreTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path temp;

    @Test
    void everythingSurvivesASaveAndReload() throws IOException {
        Path file = temp.resolve("dimensions/state.yml");
        DimensionDataStore store = new DimensionDataStore(file, LOGGER);
        store.load();
        UUID player = UUID.randomUUID();
        DimensionDataStore.StoredLocation home = new DimensionDataStore.StoredLocation("world", 10.5, 64.0, -3.25, 90.0F, 10.0F);
        store.setReturnPoint(player, "the_sift:the_sift", home);
        assertTrue(store.markVisited(player, "the_sift:the_sift"));
        assertFalse(store.markVisited(player, "the_sift:the_sift"), "second visit is not a first visit");
        store.putValue("the_sift:the_sift", "tide_last", "endure");
        store.recordInstalledPack("abc123", "2026-10-16T12:00:00Z");
        store.putPortal(new DimensionDataStore.StoredPortal("portal-1", "ancient_city", "the_sift:the_sift", "world", "Z",
            1, 2, 3, 1, 7, 22, null));
        store.save();

        DimensionDataStore reloaded = new DimensionDataStore(file, LOGGER);
        reloaded.load();
        assertEquals(home, reloaded.returnPoint(player, "the_sift:the_sift").orElseThrow());
        assertTrue(reloaded.hasVisited(player, "the_sift:the_sift"));
        assertEquals("endure", reloaded.value("the_sift:the_sift", "tide_last").orElseThrow());
        assertEquals("abc123", reloaded.installedPackHash().orElseThrow());
        assertEquals(1, reloaded.portals().size());
        assertEquals("ancient_city", reloaded.portals().getFirst().definition());
        reloaded.removePortal("portal-1");
        assertTrue(reloaded.portals().isEmpty());
    }

    @Test
    void aCorruptFileIsMovedAsideAndTheStoreStartsEmpty() throws IOException {
        Path file = temp.resolve("state.yml");
        Files.writeString(file, "schema-version: [this is: not valid yaml", StandardCharsets.UTF_8);
        DimensionDataStore store = new DimensionDataStore(file, LOGGER);
        store.load();
        assertTrue(store.portals().isEmpty());
        try (Stream<Path> files = Files.list(temp)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("state.yml.corrupt-")));
        }
    }

    @Test
    void aNewerSchemaIsNeverOverwritten() throws IOException {
        Path file = temp.resolve("state.yml");
        Files.writeString(file, "schema-version: 99\n", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> new DimensionDataStore(file, LOGGER).load());
        assertEquals("schema-version: 99\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void anUnversionedFileIsStampedWithTheCurrentSchema() throws IOException {
        Path file = temp.resolve("state.yml");
        Files.writeString(file, "values: {}\n", StandardCharsets.UTF_8);
        DimensionDataStore store = new DimensionDataStore(file, LOGGER);
        store.load();
        assertTrue(store.isDirty());
        store.save();
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("schema-version: " + DimensionDataStore.SCHEMA_VERSION));
    }
}
