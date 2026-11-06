package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.modules.tideframe.engine.EngineConfig;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeEngine;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeApi;
import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

class TideframeEmbeddedRuntimeTest {
    @Test
    void engineIsOwnedByBigCasaresNotASecondJavaPlugin() {
        assertFalse(JavaPlugin.class.isAssignableFrom(TideframeEngine.class));
    }

    @Test
    void engineSettingsLiveInBigCasaresConfigAndItsArtIsBundled() throws Exception {
        assertNull(getClass().getResource("/tideframe/engine/config.yml"), "no config file of its own");
        assertNotNull(getClass().getResource("/tideframe/engine/boss-face.png"));
        assertNotNull(getClass().getResource("/tideframe/engine/item-icons.tsv"));
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(input);
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            var engine = config.getConfigurationSection(EngineConfig.PATH);
            assertNotNull(engine, "config.yml has the engine section");
            for (String section : List.of("pack", "display", "sounds", "editor", "cursor", "limits", "demos", "editing",
                "experimental", "actions", "variables", "open", "hud", "boss-bar", "boss-fight")) {
                assertTrue(engine.isConfigurationSection(section), "tideframe-ui.motor." + section);
            }
            assertTrue(engine.getBoolean("pack.send"));
            assertTrue(config.getBoolean("tideframe-ui.missions") && config.getBoolean("tideframe-ui.shop"));
        }
    }

    @Test
    void onePluginDescriptorAndSourceBuiltApiNeedNoExternalTideframeJar() throws Exception {
        try (var input = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(input);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            assertEquals("dev.linqfy.bigCasares.BigCasares", yaml.getString("main"));
            assertFalse(yaml.getStringList("softdepend").contains("Tideframe"));
            assertNotNull(yaml.getConfigurationSection("commands.tideframe"));
        }
        assertEquals(BigCasares.class.getProtectionDomain().getCodeSource().getLocation(),
            TideframeApi.class.getProtectionDomain().getCodeSource().getLocation());
    }
}
