package dev.linqfy.bigCasares.modules.dupes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DupeRestoreSettingsTest {
    @Test void missingSettingsEnableStringsInsideAnEnabledModule() {
        assertTrue(DupeRestoreSettings.load(new YamlConfiguration()).strings());
    }

    @Test void usesOnlyTheModulesOwnSetting() {
        var config = new YamlConfiguration();
        config.set("string-duplication", false);
        config.set("dupe-restore.string-duplication", true);
        assertTrue(DupeRestoreSettings.load(config).strings());
        config.set("dupe-restore.string-duplication", false);
        assertFalse(DupeRestoreSettings.load(config).strings());
    }

    @Test void invalidSettingDoesNotSilentlyEnableDuplication() {
        var config = new YamlConfiguration();
        config.set("dupe-restore.string-duplication", "true");
        assertThrows(IllegalArgumentException.class, () -> DupeRestoreSettings.load(config));
    }
}
