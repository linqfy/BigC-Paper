package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlTombstoneStorageTest {

    @Test
    void roundTripPreservesEmptySlotsInTheInventoryLayout() {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(42, null));
        items.set(0, new ItemStack(Material.STONE));
        items.set(8, new ItemStack(Material.DIRT));
        items.set(40, new ItemStack(Material.SHIELD));
        TombstoneRecord record = new TombstoneRecord(
            UUID.randomUUID(), UUID.randomUUID(), "Jugador", UUID.randomUUID(),
            1.5, 64.0, 2.5, 0.0f, Instant.parse("2099-01-01T00:00:00Z"), items
        );
        Map<Integer, ItemStack> encoded = YamlTombstoneStorage.encodeItems(record.items());
        List<ItemStack> restored = YamlTombstoneStorage.decodeItems(encoded);

        assertEquals(List.of(0, 8, 40), encoded.keySet().stream().toList());
        assertEquals(41, restored.size());
        assertEquals(Material.STONE, restored.get(0).getType());
        assertNull(restored.get(1));
        assertEquals(Material.DIRT, restored.get(8).getType());
        assertEquals(Material.SHIELD, restored.get(40).getType());
    }

    @Test
    void unparsableFileFailsInsteadOfLoadingAsEmptyAndIsBackedUp(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("tombstones.yml");
        Files.writeString(file, "tombstones:\n  broken: [unclosed\n");
        YamlTombstoneStorage storage = new YamlTombstoneStorage(file);

        assertThrows(IllegalStateException.class, storage::loadAll);

        assertEquals("tombstones:\n  broken: [unclosed\n", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("tombstones.yml.unreadable-")));
        }
    }

    @Test
    void malformedEntryIsSkippedAndTheOriginalFileIsBackedUp(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("tombstones.yml");
        Files.writeString(file, "tombstones:\n  not-a-uuid:\n    owner-id: nope\n");
        YamlTombstoneStorage storage = new YamlTombstoneStorage(file);

        assertEquals(List.of(), storage.loadAll());

        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("tombstones.yml.unreadable-")));
        }
    }
}
