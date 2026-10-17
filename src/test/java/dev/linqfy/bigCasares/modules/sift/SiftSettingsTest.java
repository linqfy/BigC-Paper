package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftSettingsTest {

    @Test
    void sifterSpawningIsOnByDefault() {
        assertTrue(SiftSettings.defaults().sifters());
        assertTrue(SiftSettings.load(new YamlConfiguration()).sifters());
    }

    @Test
    void sifterSpawningCanBeSwitchedOff() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("the-sift:\n  sifters:\n    enabled: false\n");
        SiftSettings settings = SiftSettings.load(config);
        assertFalse(settings.sifters());
        assertTrue(settings.blubs(), "other creature switches are independent");
        assertTrue(settings.rifts());
    }

    @Test
    void shippedConfigEnablesSifters() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.isBoolean("the-sift.sifters.enabled"));
        assertTrue(config.getBoolean("the-sift.sifters.enabled"));
        assertTrue(SiftSettings.load(config).sifters());
    }
}
