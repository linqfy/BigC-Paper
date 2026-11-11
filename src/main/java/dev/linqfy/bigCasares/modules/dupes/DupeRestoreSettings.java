package dev.linqfy.bigCasares.modules.dupes;

import org.bukkit.configuration.ConfigurationSection;

record DupeRestoreSettings(boolean strings) {
    static DupeRestoreSettings load(ConfigurationSection config) {
        String path = "dupe-restore.string-duplication";
        if (config.contains(path) && !config.isBoolean(path)) {
            throw new IllegalArgumentException(path + " debe ser true o false.");
        }
        return new DupeRestoreSettings(config.getBoolean(path, true));
    }
}
