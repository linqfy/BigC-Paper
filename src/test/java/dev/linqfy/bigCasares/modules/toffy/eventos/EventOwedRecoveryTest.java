package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventOwedRecoveryTest {

    @Test
    void aBrokenFileIsMovedAsideAndTheStoreStartsEmpty(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("owed.yml");
        Files.writeString(file, "players: [unclosed", StandardCharsets.UTF_8);
        EventOwedStore store = new EventOwedStore(file);

        List<String> warnings = store.load();

        assertFalse(warnings.isEmpty());
        assertFalse(Files.exists(file));
        try (Stream<Path> files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("owed.yml.broken-")));
        }
        assertTrue(store.writable());
        assertFalse(store.has(UUID.randomUUID()));
    }

    @Test
    void invalidPlayerKeysAreSkippedOneByOne(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("owed.yml");
        UUID valid = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("players.no-es-uuid.hearts", 3);
        yaml.set("players." + valid + ".hearts", 2);
        Files.writeString(file, yaml.saveToString(), StandardCharsets.UTF_8);
        EventOwedStore store = new EventOwedStore(file);

        List<String> warnings = store.load();

        assertEquals(1, warnings.size());
        assertEquals(2, store.find(valid).orElseThrow().hearts());
    }

    @Test
    void keepPutsBackAnEntryWhoseRemovalCouldNotBeSaved(@TempDir Path directory) {
        EventOwedStore store = new EventOwedStore(directory.resolve("owed.yml"));
        UUID player = UUID.randomUUID();
        store.addHearts(player, 1);
        EventOwedStore.Owed entry = store.take(player).orElseThrow();
        assertFalse(store.has(player));

        store.keep(player, entry);

        assertEquals(1, store.find(player).orElseThrow().hearts());
    }
}
