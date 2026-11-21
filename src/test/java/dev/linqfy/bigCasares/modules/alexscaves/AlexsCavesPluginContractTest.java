package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlexsCavesPluginContractTest {
    private static final String MAIN = "src/main/java/dev/linqfy/bigCasares/BigCasares.java";

    @Test
    void moduleHasAStableIdAndToleratesANullPlugin() {
        AlexsCavesModule module = new AlexsCavesModule(null, null);
        assertEquals("alexs-caves", module.getId());
        assertDoesNotThrow(() -> module.onEnable(new RuntimeRegistrationScope()));
        assertDoesNotThrow(module::onDisable);
    }

    @Test
    void registeredAfterItemsAndBeforeBossesAndGeyser() throws Exception {
        String main = Files.readString(Path.of(MAIN));
        int items = main.indexOf("moduleManager.register(itemCatalogModule)");
        int pack = main.indexOf("moduleManager.register(resourcePackModule)");
        int module = main.indexOf("moduleManager.register(alexsCavesModule)");
        int bosses = main.indexOf("moduleManager.register(pveBossModule)");
        int geyser = main.indexOf("moduleManager.register(geyserIntegrationModule)");
        assertTrue(module > items && module > pack, "needs the item catalog and pack modules first");
        assertTrue(module < bosses && module < geyser, "bosses and Geyser read module content");
        assertTrue(main.contains("new AlexsCavesModule(this, javaModels)"));
        assertTrue(main.contains("alexsCavesModule = null;"), "runtime references must be cleared on reload");
    }

    @Test
    void shippedConfigKeepsTheModuleOffUntilEnabled() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.isSet("modules.alexs-caves.enabled"));
        assertFalse(config.getBoolean("modules.alexs-caves.enabled"));
    }

    @Test
    void commandIsOpenForCreditsAndAdminIsOperatorOnly() {
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        assertTrue(plugin.isConfigurationSection("commands.alexscaves"));
        assertNull(plugin.getString("commands.alexscaves.permission"), "credits must be available to every player");
        assertEquals("op", plugin.getString("permissions.bigcasares.alexscaves.admin.default"));
        assertTrue(plugin.getBoolean("permissions.bigcasares.*.children.bigcasares.alexscaves.admin"));
    }

    @Test
    void creditsNameTheAuthorsAndTheLicence() {
        String all = String.join("\n", AlexsCavesCredits.lines());
        assertTrue(all.contains("Alexthe668"));
        assertTrue(all.contains("LGPL"));
        assertTrue(all.contains("no oficial"));
        assertTrue(AlexsCavesCredits.shortLine().contains("licenses/alexscaves/CREDITS.txt"));
    }
}
