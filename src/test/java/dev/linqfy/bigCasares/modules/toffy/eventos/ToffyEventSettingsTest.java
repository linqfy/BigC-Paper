package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyEventSettingsTest {

    @Test
    void defaultsMatchTheEventCard() {
        ToffyEventSettings settings = ToffyEventSettings.load(new YamlConfiguration());
        assertTrue(settings.warnings().isEmpty(), settings.warnings().toString());
        assertEquals(60, settings.lifesteal().durationMinutes());
        assertEquals(3, settings.lifesteal().rules().floorHearts());
        assertEquals(12, settings.lifesteal().rules().permanentRewardThreshold());
        assertEquals(5, settings.koth().knockbackLevel());
        assertEquals(255, settings.spleef().efficiencyLevel());
        assertEquals(List.of("nuke_shot:1"), settings.tntTag().rewards().items());
        assertTrue(settings.arenaDimension());
        assertTrue(settings.enabled());
    }

    @Test
    void theSwitchTurnsTheEventsOff() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", false);

        assertFalse(ToffyEventSettings.load(config).enabled());
    }

    @Test
    void bundledFileLoadsWithoutWarnings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream("toffy_eventos.yml")), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        ToffyEventSettings settings = ToffyEventSettings.load(config);
        assertTrue(settings.warnings().isEmpty(), settings.warnings().toString());
        assertEquals(List.of("nuke_shot:1"), settings.tntTag().rewards().items());
    }

    @Test
    void invalidValuesFallBackWithWarnings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("lifesteal.floor-hearts", 0);
        config.set("koth.knockback-level", 900);
        config.set("spleef.rewards.items", List.of("minecraft:diamond:3", "not valid"));
        config.set("lifesteal.max-hearts", 2);
        ToffyEventSettings settings = ToffyEventSettings.load(config);
        assertEquals(3, settings.lifesteal().rules().floorHearts());
        assertEquals(3, settings.lifesteal().rules().maxHearts());
        assertEquals(5, settings.koth().knockbackLevel());
        assertEquals(List.of("minecraft:diamond:3"), settings.spleef().rewards().items());
        assertEquals(4, settings.warnings().size(), settings.warnings().toString());
    }

    @Test
    void anEmptyRewardListOverridesTheDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tnt-tag.rewards.items", List.of());
        assertFalse(ToffyEventSettings.load(config).tntTag().rewards().items().contains("nuke_shot:1"));
    }
}
