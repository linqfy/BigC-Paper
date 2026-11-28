package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyEventsConfigTest {

    @TempDir
    Path temp;

    @Test
    void theOldStaffEventsValuesAreCopiedIntoTheNewFile() throws Exception {
        Path file = temp.resolve(ToffyEventsConfig.FILE);
        Files.writeString(file, "arena-y: 100\nlobby-seconds: 30\nkoth:\n  capture-seconds: 120\n");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("lobby-seconds", 45);
        legacy.set("koth.capture-seconds", 99);

        assertTrue(ToffyEventsConfig.importLegacy(file, legacy));

        YamlConfiguration imported = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(45, imported.getInt("lobby-seconds"));
        assertEquals(99, imported.getInt("koth.capture-seconds"));
        assertEquals(100, imported.getInt("arena-y"));
    }

    @Test
    void theCommentsOfTheFileSurviveTheImport() throws Exception {
        Path file = temp.resolve(ToffyEventsConfig.FILE);
        Files.writeString(file, "# Segundos de espera antes de la arena.\n"
            + "lobby-seconds: 30\n"
            + "# Zona de captura.\n"
            + "koth:\n"
            + "  capture-seconds: 120\n");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("koth.capture-seconds", 99);

        assertTrue(ToffyEventsConfig.importLegacy(file, legacy));

        String text = Files.readString(file);
        assertTrue(text.contains("# Segundos de espera antes de la arena."), text);
        assertTrue(text.contains("# Zona de captura."), text);
        assertEquals(99, YamlConfiguration.loadConfiguration(file.toFile()).getInt("koth.capture-seconds"));
    }

    @Test
    void nothingIsCopiedWithoutALegacySection() throws Exception {
        Path file = temp.resolve(ToffyEventsConfig.FILE);
        Files.writeString(file, "arena-y: 100\n");

        assertFalse(ToffyEventsConfig.importLegacy(file, null));
        assertFalse(ToffyEventsConfig.importLegacy(file, new YamlConfiguration()));
        assertEquals("arena-y: 100\n", Files.readString(file));
    }
}
