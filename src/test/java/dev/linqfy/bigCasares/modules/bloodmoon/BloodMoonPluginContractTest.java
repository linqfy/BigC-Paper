package dev.linqfy.bigCasares.modules.bloodmoon;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodMoonPluginContractTest {

    @Test
    void pluginDeclaresCommandPermissionsAndModuleRegistration() throws Exception {
        YamlConfiguration plugin = pluginDescriptor();
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");

        assertEquals("bigcasares.bloodmoon.admin", plugin.getString("commands/bloodmoon/permission"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.bloodmoon.admin"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.bloodmoon.start"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.bloodmoon.stop"));
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/bloodmoon/BloodMoonModule", "<init>"));
    }

    @Test
    void productionConfigurationLoadsEnabledAndSafe() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            new File("src/main/resources/config.yml"));

        BloodMoonConfigLoadResult result = BloodMoonSettingsLoader.load(yaml);

        assertTrue(result.valid(), () -> String.join(", ", result.errors()));
        assertTrue(result.settings().enabled());
        assertEquals(1.5, result.settings().mobs().healthMultiplier());
        assertEquals(1.25, result.settings().mobs().movementSpeedMultiplier());
        assertEquals(1.0, result.settings().mobs().damageMultiplier());
        assertFalse(result.settings().allowWithOtherEvents());
    }

    private static YamlConfiguration pluginDescriptor() throws Exception {
        // permission names contain dots, so plugin.yml is read with '/' as the path separator
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        return plugin;
    }
}
