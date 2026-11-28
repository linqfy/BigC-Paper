package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlToffyMemoryStorageTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @TempDir
    Path directory;

    @Test
    void savesAndLoadsEveryRememberedSlot() throws IOException {
        YamlToffyMemoryStorage storage = new YamlToffyMemoryStorage(directory.resolve("players"));
        ToffyMemory memory = new ToffyMemory(Map.of(
            ToffyMemorySlot.HELMET, new ToffyMemory.Remembered(
                ToffyItem.of("netherite_helmet", Map.of("protection", 4, "mending", 1)), 1_700_000_000_000L),
            ToffyMemorySlot.WEAPON, new ToffyMemory.Remembered(
                ToffyItem.of("custom:sahurs_bat", Map.of("sharpness", 5)), 42L),
            ToffyMemorySlot.TOOL, new ToffyMemory.Remembered(ToffyItem.of("shears"), 7L)));

        storage.save(PLAYER, memory);

        assertTrue(Files.isRegularFile(directory.resolve("players").resolve(PLAYER + ".yml")));
        assertEquals(memory, storage.load(PLAYER));
    }

    @Test
    void aPlayerWithoutAFileHasAnEmptyMemory() throws IOException {
        assertTrue(new YamlToffyMemoryStorage(directory).load(PLAYER).isEmpty());
    }

    @Test
    void savingAnEmptyMemoryClearsTheFile() throws IOException {
        YamlToffyMemoryStorage storage = new YamlToffyMemoryStorage(directory);
        storage.save(PLAYER, new ToffyMemory(Map.of(ToffyMemorySlot.BOOTS,
            new ToffyMemory.Remembered(ToffyItem.of("iron_boots"), 1L))));

        storage.save(PLAYER, ToffyMemory.empty());

        assertTrue(storage.load(PLAYER).isEmpty());
    }

    @Test
    void brokenFilesAreReportedInsteadOfLoadedHalfway() throws IOException {
        YamlToffyMemoryStorage storage = new YamlToffyMemoryStorage(directory);
        Path file = directory.resolve(PLAYER + ".yml");

        Files.writeString(file, "slots:\n  hat:\n    item: minecraft:iron_helmet\n");
        assertThrows(IOException.class, () -> storage.load(PLAYER));
        assertTrue(Files.notExists(file));
        try (var kept = Files.list(directory)) {
            assertTrue(kept.anyMatch(path -> path.getFileName().toString().startsWith(PLAYER + ".yml.broken-")));
        }
        assertTrue(storage.load(PLAYER).isEmpty());

        Files.writeString(file, "slots:\n  helmet:\n    item: minecraft:iron_helmet\n    enchantments: [protection]\n");
        assertThrows(IOException.class, () -> storage.load(PLAYER));

        Files.writeString(file, "slots: [unclosed\n");
        assertThrows(IOException.class, () -> storage.load(PLAYER));
    }
}
