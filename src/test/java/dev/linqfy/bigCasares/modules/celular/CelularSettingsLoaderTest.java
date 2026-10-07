package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularSettingsLoaderTest {

    @Test
    void missingSectionUsesDefaults() {
        assertEquals(CelularSettings.defaults(), CelularSettingsLoader.load(new YamlConfiguration()));
        assertEquals(CelularSettings.defaults(), CelularSettingsLoader.load(null));
    }

    @Test
    void readsBothToggles() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("celular.recipe.enabled", false);
        yaml.set("celular.village-loot.enabled", false);
        CelularSettings settings = CelularSettingsLoader.load(yaml);
        assertFalse(settings.recipeEnabled());
        assertFalse(settings.villageLootEnabled());
    }

    @Test
    void productionConfigEnablesRecipeAndVillageLoot() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(yaml.isConfigurationSection("celular"));
        CelularSettings settings = CelularSettingsLoader.load(yaml);
        assertTrue(settings.recipeEnabled());
        assertTrue(settings.villageLootEnabled());
    }
}
