package dev.linqfy.bigCasares.modules.jeremy;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeremyPluginContractTest {

    @Test
    void productionConfigurationUsesSevenMinuteHuntAndOneRealHourRest() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        JeremyConfigLoadResult result = JeremySettingsLoader.load(yaml);

        assertTrue(result.valid(), () -> String.join(", ", result.errors()));
        assertEquals(420_000L, result.settings().timing().huntMillis());
        assertEquals(3_600_000L, result.settings().timing().restMillis());
        assertEquals(0.345, result.settings().movement().effectiveSpeed(), 0.00001);
    }

    @Test
    void pluginDeclaresCommandPermissionsAndModuleRegistration() throws Exception {
        YamlConfiguration plugin = pluginDescriptor();
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");

        assertEquals("bigcasares.jeremy.admin", plugin.getString("commands/jeremy/permission"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.jeremy.admin"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.jeremy.start"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.jeremy.stop"));
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/jeremy/JeremyModule", "<init>"));
    }

    private static YamlConfiguration pluginDescriptor() throws Exception {
        // permission names contain dots, so plugin.yml is read with '/' as the path separator
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        return plugin;
    }

    @Test
    void exposesStableModuleId() {
        assertEquals("jeremy", new JeremyModule(null).getId());
    }
}
