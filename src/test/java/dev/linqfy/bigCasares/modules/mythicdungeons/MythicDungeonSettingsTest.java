package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicDungeonSettingsTest {

    @Test
    void defaultsMatchTheBundledFile() throws Exception {
        MythicDungeonSettings bundled;
        try (InputStream in = Objects.requireNonNull(
            MythicDungeonSettingsTest.class.getResourceAsStream("/" + MythicDungeonsModule.CONFIG_FILE));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            bundled = MythicDungeonSettings.load(YamlConfiguration.loadConfiguration(reader));
        }
        MythicDungeonSettings defaults = MythicDungeonSettings.defaults();

        assertTrue(bundled.warnings().isEmpty(), () -> "warnings: " + bundled.warnings());
        assertEquals(defaults.altarMaterial(), bundled.altarMaterial());
        assertEquals(defaults.bossOffsetY(), bundled.bossOffsetY());
        assertEquals(defaults.spawnerCap(), bundled.spawnerCap());
        assertEquals(defaults.capRadius(), bundled.capRadius());
        assertEquals(defaults.naturalReplaceChance(), bundled.naturalReplaceChance());
        assertEquals(defaults.lootingBonusPerLevel(), bundled.lootingBonusPerLevel());
        assertEquals(defaults.rewardTopPlayers(), bundled.rewardTopPlayers());
        assertEquals(defaults.rewards(), bundled.rewards());
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            assertEquals(MythicDungeonSettings.defaultReward(culture), bundled.rewardFor(culture), culture.id());
        }
    }
}
