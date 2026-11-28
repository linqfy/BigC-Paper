package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyModuleWiringTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("toffy-complementaries", new ToffyModule(null).getId());
        assertEquals("toffy-complementaries", ToffyModule.MODULE_ID);
    }

    @Test
    void isUnavailableUntilEnabled() {
        ToffyModule module = new ToffyModule(null);
        module.onEnable();

        assertFalse(module.isAvailable());
        assertThrows(IllegalStateException.class, () -> module.evaluate(ToffyLoadout.empty()));
        assertThrows(IllegalStateException.class, module::maximum);
        module.onDisable();
    }

    @Test
    void allBundledFilesShipInsideTheJar() throws Exception {
        for (String name : new String[] {ToffyModule.CONFIG_FILE, ToffyModule.VALUES_FILE, ToffyModule.MESSAGES_FILE,
            ToffyModule.EVENTS_FILE}) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(stream, name);
            }
        }
        assertEquals("toffy_complementaries.yml", ToffyModule.CONFIG_FILE);
        assertEquals("toffy_valores.yml", ToffyModule.VALUES_FILE);
        assertEquals("toffy_mensajes.yml", ToffyModule.MESSAGES_FILE);
        assertEquals("toffy_eventos.yml", ToffyModule.EVENTS_FILE);
    }

    @Test
    void theStaffEventsLiveInsideTheToffyModuleOnly() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        String main = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/BigCasares.java"));

        assertFalse(config.contains("staff-events"));
        assertFalse(config.contains("modules.staff-events"));
        assertFalse(main.contains("StaffEventsModule"));
        assertFalse(main.contains("getStaffEventsModule"));
        assertFalse(Files.exists(Path.of("src/main/java/dev/linqfy/bigCasares/modules/staffevents")));
        assertTrue(main.contains("toffyModule.protectsDeath(playerId)"));
    }

    @Test
    void theEventsFileDeclaresTheEventKeysAtTheTop() {
        YamlConfiguration events = YamlConfiguration.loadConfiguration(new File("src/main/resources/toffy_eventos.yml"));

        assertTrue(events.contains("arena-dimension"));
        assertTrue(events.contains("lobby-seconds"));
        assertTrue(events.contains("lifesteal.duration-minutes"));
        assertTrue(events.contains("spleef.rewards.commands"));
        assertFalse(events.contains("staff-events"));
    }

    @Test
    void protectsNoDeathUntilTheModuleRuns() {
        assertFalse(new ToffyModule(null).protectsDeath(UUID.randomUUID()));
    }

    @Test
    void theEventCommandsUseTheToffyPermissions() {
        assertEquals("bigcasares.toffy.evento.admin", ToffyEvents.ADMIN_PERMISSION);
        assertEquals("bigcasares.toffy.anotar", ToffyAnotarCommand.PERMISSION);
    }

    @Test
    void moduleIsEnabledInTheProductionConfig() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));

        assertTrue(config.getBoolean("modules.toffy-complementaries.enabled", false));
    }

    @Test
    void pluginDeclaresCommandPermissionsAndModuleRegistration() throws Exception {
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        String main = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/BigCasares.java"));

        assertTrue(plugin.isConfigurationSection("commands/toffy"));
        assertTrue(plugin.getStringList("commands/toffy/aliases").contains("tcomp"));
        assertEquals("true", plugin.getString("permissions/" + ToffyCommand.CHECK_PERMISSION + "/default"));
        assertEquals("op", plugin.getString("permissions/" + ToffyCommand.CHECK_OTHERS_PERMISSION + "/default"));
        assertEquals("op", plugin.getString("permissions/" + ToffyCommand.ADMIN_PERMISSION + "/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/" + ToffyCommand.ADMIN_PERMISSION, false));
        assertTrue(plugin.isConfigurationSection("commands/anotar"));
        assertEquals(ToffyAnotarCommand.PERMISSION, plugin.getString("commands/anotar/permission"));
        assertEquals("bigcasares.toffy.anotar", ToffyAnotarCommand.PERMISSION);
        assertEquals("true", plugin.getString("permissions/" + ToffyAnotarCommand.PERMISSION + "/default"));
        assertEquals("bigcasares.toffy.torneo.admin", ToffyCommand.TORNEO_ADMIN_PERMISSION);
        assertEquals("op", plugin.getString("permissions/" + ToffyCommand.TORNEO_ADMIN_PERMISSION + "/default"));
        assertTrue(plugin.getBoolean("permissions/" + ToffyCommand.ADMIN_PERMISSION + "/children/"
            + ToffyCommand.TORNEO_ADMIN_PERMISSION, false));
        assertTrue(main.contains("new ToffyModule(this,"));
        assertEquals("/<command> [iniciar|detener|tipos|estado|programar|unirse|salir]",
            plugin.getString("commands/evento/usage"));
        assertTrue(plugin.getStringList("commands/evento/aliases").contains("eventos"));
        assertEquals("op", plugin.getString("permissions/bigcasares.events.admin/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.events.admin/children/" + ToffyEvents.ADMIN_PERMISSION, false));
        assertEquals("true", plugin.getString("permissions/bigcasares.events.join/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.events.join/children/" + ToffyAnotarCommand.PERMISSION, false));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/bigcasares.events.admin", false));
        assertEquals("op", plugin.getString("permissions/" + ToffyEvents.ADMIN_PERMISSION + "/default"));
        assertTrue(plugin.getBoolean("permissions/" + ToffyCommand.ADMIN_PERMISSION + "/children/"
            + ToffyEvents.ADMIN_PERMISSION, false));
        assertTrue(plugin.getString("commands/toffy/usage").contains("evento"));
        assertTrue(main.contains("toffyModule.lastValidSettings()"));
        assertTrue(main.contains("moduleManager.register(toffyModule)"));
        assertTrue(main.contains("public ToffyModule getToffyModule()"));
    }

    @Test
    void pluginUsageListsEveryToffySubcommandAndAnotarOptions() {
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        try {
            plugin.load(new File("src/main/resources/plugin.yml"));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }

        assertEquals("/<command> [nivel|ayuda|curva|reload|torneo|tp|evento]",
            plugin.getString("commands/toffy/usage"));
        assertEquals("/<command> [torneo [grupo] | salir <torneo> | estado]", plugin.getString("commands/anotar/usage"));
        assertTrue(plugin.getString("commands/toffy/description").contains("ayuda"));
        assertTrue(plugin.getString("commands/anotar/description").contains("open"));
    }

    @Test
    void theCommandShowsLevelsWithTheConfiguredDecimals() {
        assertEquals("53", ToffyCommand.level(52.5, 0));
        assertEquals("52.5", ToffyCommand.level(52.5, 1));
        assertEquals("52.523", ToffyCommand.level(52.523, 3));
    }

    @Test
    void aNewInstanceStartsFromThePreviousValidSettings() {
        ToffySettings settings = ToffySettingsLoaderTest.bundledSettings();

        assertEquals(settings, new ToffyModule(null, settings).lastValidSettings());
        assertEquals(null, new ToffyModule(null).lastValidSettings());
    }

    @Test
    void familyNamesAreShownInSpanish() {
        assertEquals("espada", ToffyCommand.familyName("sword"));
        assertEquals("hacha", ToffyCommand.familyName("axe"));
        assertEquals("maza", ToffyCommand.familyName("mace"));
        assertEquals("lanza", ToffyCommand.familyName("spear"));
        assertEquals("tridente", ToffyCommand.familyName("trident"));
        assertEquals("pico", ToffyCommand.familyName("pickaxe"));
        assertEquals("cana de pescar", ToffyCommand.familyName("fishing_rod"));
        assertEquals("bow", ToffyCommand.familyName("bow"));
    }
}
