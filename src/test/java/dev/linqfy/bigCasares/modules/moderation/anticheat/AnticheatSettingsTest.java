package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnticheatSettingsTest {

    @Test
    void oldConfigWithoutTheSectionUsesDefaults() {
        AnticheatSettings settings = AnticheatSettings.load(new YamlConfiguration(), "moderation-system.");
        assertEquals(AnticheatSettings.defaults(), settings);
        assertTrue(settings.checkEnabled("movement-speed"));
    }

    @Test
    void readsDisabledChecksAndClampsValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation-system.anticheat.disabled-checks", List.of(" Melee-Reach "));
        config.set("moderation-system.anticheat.max-attacks-per-second", 3);
        config.set("moderation-system.anticheat.minimum-tps", 40);
        config.set("moderation-system.anticheat.bypass-permission", "");

        AnticheatSettings settings = AnticheatSettings.load(config, "moderation-system.");

        assertFalse(settings.checkEnabled("melee-reach"));
        assertTrue(settings.checkEnabled("melee-rate"));
        assertEquals(10, settings.maxAttacksPerSecond());
        assertEquals(20.0, settings.minimumTps());
        assertEquals("", settings.bypassPermission());
    }
}
