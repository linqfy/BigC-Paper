package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftTickSettingsTest {

    @Test
    void everythingIsOnByDefaultAtEightSamples() {
        SiftTickSettings settings = SiftTickSettings.defaults();
        assertTrue(settings.randomTicks());
        assertTrue(settings.cropTrampling());
        assertTrue(settings.dynamicLights());
        assertEquals(8, settings.samplesPerSectionPerSecond());
        assertTrue(settings.warnings().isEmpty());
    }

    @Test
    void eachSubstituteSwitchesOffOnItsOwn() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("the-sift:\n  random-ticks:\n    enabled: false\n");
        SiftTickSettings settings = SiftTickSettings.load(config);
        assertFalse(settings.randomTicks());
        assertTrue(settings.cropTrampling());
        assertTrue(settings.dynamicLights());
        config.loadFromString("the-sift:\n  dynamic-lights:\n    enabled: false\n  crop-trampling:\n    enabled: false\n");
        settings = SiftTickSettings.load(config);
        assertTrue(settings.randomTicks());
        assertFalse(settings.cropTrampling());
        assertFalse(settings.dynamicLights());
    }

    @Test
    void theRateIsReadAndClamped() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("the-sift:\n  random-ticks:\n    samples-per-section-per-second: 20\n");
        assertEquals(20, SiftTickSettings.load(config).samplesPerSectionPerSecond());
        for (String bad : new String[]{"0", "61", "-3", "many", "2.5"}) {
            config.loadFromString("the-sift:\n  random-ticks:\n    samples-per-section-per-second: " + bad + "\n");
            SiftTickSettings settings = SiftTickSettings.load(config);
            assertEquals(8, settings.samplesPerSectionPerSecond(), bad);
            assertEquals(1, settings.warnings().size(), bad);
        }
    }

    @Test
    void shippedConfigDeclaresTheSwitchesAndTheSamplerFitsItsBudget() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.isBoolean("the-sift.random-ticks.enabled"));
        assertTrue(config.isBoolean("the-sift.crop-trampling.enabled"));
        assertTrue(config.isBoolean("the-sift.dynamic-lights.enabled"));
        assertEquals(SiftRandomTicks.DEFAULT_SAMPLES_PER_SECTION, config.getInt("the-sift.random-ticks.samples-per-section-per-second"));
        SiftTickSettings settings = SiftTickSettings.load(config);
        assertTrue(settings.warnings().isEmpty());
        assertTrue(settings.randomTicks() && settings.cropTrampling() && settings.dynamicLights());
    }
}
