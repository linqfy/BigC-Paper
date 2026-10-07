package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.configuration.ConfigurationSection;

public final class CelularSettingsLoader {

    public static final String SECTION = "celular";

    private CelularSettingsLoader() {
    }

    public static CelularSettings load(ConfigurationSection root) {
        CelularSettings defaults = CelularSettings.defaults();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(SECTION);
        if (section == null) {
            return defaults;
        }
        return new CelularSettings(
            section.getBoolean("recipe.enabled", defaults.recipeEnabled()),
            section.getBoolean("village-loot.enabled", defaults.villageLootEnabled()));
    }
}
