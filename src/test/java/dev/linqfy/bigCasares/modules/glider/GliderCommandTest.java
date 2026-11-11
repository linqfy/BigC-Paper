package dev.linqfy.bigCasares.modules.glider;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GliderCommandTest {

    @Test
    void acceptsNumericRomanAndCatalogTierNames() {
        assertEquals(GliderTier.I, GliderCommand.parseTier("1").orElseThrow());
        assertEquals(GliderTier.III, GliderCommand.parseTier("III").orElseThrow());
        assertEquals(GliderTier.VI, GliderCommand.parseTier("tier-6").orElseThrow());
        assertEquals(GliderTier.IV, GliderCommand.parseTier("glider_tier_4").orElseThrow());
        assertTrue(GliderCommand.parseTier("7").isEmpty());
    }

    @Test
    void pluginDeclaresDedicatedGliderCommandAndPermissions() throws Exception {
        // permission names contain dots, so plugin.yml is read with '/' as the path separator
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));

        assertEquals("bigcasares.glider.give", plugin.getString("commands/glider/permission"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.glider.admin"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.glider.give"));
    }
}
