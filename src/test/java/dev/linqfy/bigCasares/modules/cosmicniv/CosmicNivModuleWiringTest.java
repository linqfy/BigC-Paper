package dev.linqfy.bigCasares.modules.cosmicniv;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivModuleWiringTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("cosmicniv-lib", new CosmicNivModule(null).getId());
        assertEquals("cosmicniv-lib", CosmicNivModule.MODULE_ID);
    }

    @Test
    void isUnavailableUntilEnabled() {
        CosmicNivModule module = new CosmicNivModule(null);
        module.onEnable();

        assertFalse(module.isAvailable());
        assertThrows(IllegalStateException.class, () -> module.evaluate(CosmicNivLoadout.empty()));
        assertThrows(IllegalStateException.class, module::maximum);
        module.onDisable();
    }

    @Test
    void bothBundledFilesShipInsideTheJar() throws Exception {
        for (String name : new String[] {CosmicNivModule.CONFIG_FILE, CosmicNivModule.VALUES_FILE}) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(stream, name);
            }
        }
        assertEquals("cosmicniv_lib.yml", CosmicNivModule.CONFIG_FILE);
        assertEquals("cosmicniv_valores.yml", CosmicNivModule.VALUES_FILE);
    }

    @Test
    void moduleIsEnabledInTheProductionConfig() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));

        assertTrue(config.getBoolean("modules.cosmicniv-lib.enabled", false));
    }

    @Test
    void pluginDeclaresCommandPermissionsAndModuleRegistration() throws Exception {
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        String main = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/BigCasares.java"));

        assertTrue(plugin.isConfigurationSection("commands/cosmicniv"));
        assertTrue(plugin.getStringList("commands/cosmicniv/aliases").contains("cniv"));
        assertEquals("true", plugin.getString("permissions/" + CosmicNivCommand.CHECK_PERMISSION + "/default"));
        assertEquals("op", plugin.getString("permissions/" + CosmicNivCommand.CHECK_OTHERS_PERMISSION + "/default"));
        assertEquals("op", plugin.getString("permissions/" + CosmicNivCommand.ADMIN_PERMISSION + "/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/" + CosmicNivCommand.ADMIN_PERMISSION, false));
        assertTrue(main.contains("new CosmicNivModule(this,"));
        assertTrue(main.contains("cosmicNivModule.lastValidSettings()"));
        assertTrue(main.contains("moduleManager.register(cosmicNivModule)"));
        assertTrue(main.contains("public CosmicNivModule getCosmicNivModule()"));
    }

    @Test
    void theCommandShowsLevelsWithTheConfiguredDecimals() {
        assertEquals("53", CosmicNivCommand.level(52.5, 0));
        assertEquals("52.5", CosmicNivCommand.level(52.5, 1));
        assertEquals("52.523", CosmicNivCommand.level(52.523, 3));
    }

    @Test
    void aNewInstanceStartsFromThePreviousValidSettings() {
        CosmicNivSettings settings = CosmicNivSettingsLoaderTest.bundledSettings();

        assertEquals(settings, new CosmicNivModule(null, settings).lastValidSettings());
        assertEquals(null, new CosmicNivModule(null).lastValidSettings());
    }

    @Test
    void familyNamesAreShownInSpanish() {
        assertEquals("espada", CosmicNivCommand.familyName("sword"));
        assertEquals("hacha", CosmicNivCommand.familyName("axe"));
        assertEquals("maza", CosmicNivCommand.familyName("mace"));
        assertEquals("lanza", CosmicNivCommand.familyName("spear"));
        assertEquals("tridente", CosmicNivCommand.familyName("trident"));
        assertEquals("pico", CosmicNivCommand.familyName("pickaxe"));
        assertEquals("cana de pescar", CosmicNivCommand.familyName("fishing_rod"));
        assertEquals("bow", CosmicNivCommand.familyName("bow"));
    }
}
