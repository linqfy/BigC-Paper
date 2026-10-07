package dev.linqfy.bigCasares.modules.celular;

import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularModuleWiringTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("celular", new CelularModule(null).getId());
        assertEquals(CelularModule.MODULE_ID, new CelularModule(null).getId());
    }

    @Test
    void enableAndDisableWithoutAPluginAreNoOps() {
        CelularModule module = new CelularModule(null);
        assertDoesNotThrow(() -> module.onEnable(new RuntimeRegistrationScope()));
        assertDoesNotThrow(module::onDisable);
        assertEquals(0, module.videoCount());
    }

    @Test
    void moduleIsRegisteredDeclaredAndEnabledByDefault() throws Exception {
        String main = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/BigCasares.java"));
        assertTrue(main.contains("new CelularModule(this)"));
        assertTrue(main.contains("moduleManager.register(celularModule)"));
        assertTrue(main.contains("public CelularModule getCelularModule()"));

        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        assertEquals("bigcasares.celular.admin", plugin.getString("commands.celular.permission"));
        assertEquals("op", plugin.getString("permissions.bigcasares\\.celular\\.admin.default", "op"));
        assertTrue(Files.readString(Path.of("src/main/resources/plugin.yml")).contains("      bigcasares.celular.admin: true"),
            "bigcasares.* has to include the celular admin permission");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.getBoolean("modules.celular.enabled"));
    }
}
