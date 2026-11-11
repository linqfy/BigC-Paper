package dev.linqfy.bigCasares.modules.schizophrenia;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchizophreniaPluginContractTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("schizophrenia", new SchizophreniaModule(null).getId());
    }

    @Test
    void pluginDeclaresCommandPermissionAndRegistration() throws Exception {
        // permission names contain dots, so plugin.yml is read with '/' as the path separator
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");

        assertEquals("bigcasares.schizophrenia.admin", plugin.getString("commands/esquizo/permission"));
        assertTrue(plugin.isConfigurationSection("permissions/bigcasares.schizophrenia.admin"));
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/schizophrenia/SchizophreniaModule", "<init>"));
    }

    @Test
    void lobotomizerRecipeIsAThreeByThreeShapeWithAStableId() {
        assertEquals(3, LobotomizerItem.SHAPE.size());
        LobotomizerItem.SHAPE.forEach(row -> assertEquals(3, row.length()));
        assertEquals("lobotomizador", LobotomizerItem.ITEM_ID);
    }

    @Test
    void cleaverKeepsItsStableId() {
        assertEquals("cuchilla", CleaverItem.ITEM_ID);
    }
}
