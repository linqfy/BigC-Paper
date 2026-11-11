package dev.linqfy.bigCasares.modules.glider;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GliderModuleWiringTest {

    @Test
    void moduleIsCreatedByThePluginAndEnabledByDefault() throws Exception {
        assertEquals("glider", new GliderModule(null).getId());
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/glider/GliderModule", "<init>"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.getBoolean("modules.glider.enabled"));
    }
}
