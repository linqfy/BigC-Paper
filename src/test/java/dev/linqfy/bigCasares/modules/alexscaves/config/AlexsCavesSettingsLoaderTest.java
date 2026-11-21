package dev.linqfy.bigCasares.modules.alexscaves.config;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlexsCavesSettingsLoaderTest {

    @Test
    void shippedFileLoadsWithoutProblemsAndMatchesTheMod() throws IOException, InvalidConfigurationException {
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(shipped());
        assertTrue(result.valid(), String.valueOf(result.errors()));
        assertTrue(result.warnings().isEmpty(), String.valueOf(result.warnings()));
        AlexsCavesSettings settings = result.settings();
        assertEquals(300, settings.worldgen().rarity().radius());
        assertEquals(900, settings.worldgen().rarity().separation());
        assertEquals(0.15, settings.worldgen().rarity().widthRandomness());
        assertEquals(0.45, settings.worldgen().rarity().spacingRandomness());
        assertEquals(650, settings.worldgen().minSpawnDistance().get(CaveBiomeId.TOXIC_CAVES));
        assertEquals(6, settings.worldgen().enabledBiomes().size());
        assertEquals(1.75, settings.spawning().caveCreatureMultiplier());
        assertEquals(List.of("world"), settings.worldgen().worlds());
        assertFalse(settings.blocks().packetRemap(), "the packet remap stays off until tested with real clients");
    }

    @Test
    void packetRemapIsReadFromBlocks() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks.packet-remap", true);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid());
        assertTrue(result.settings().blocks().packetRemap());
        assertFalse(AlexsCavesSettings.defaults().blocks().packetRemap());
    }

    @Test
    void possessPlayersIsOffByDefaultAndReadFromForlorn() throws IOException, InvalidConfigurationException {
        assertFalse(AlexsCavesSettings.defaults().forlorn().possessPlayers());
        assertFalse(AlexsCavesSettingsLoader.load(shipped()).settings().forlorn().possessPlayers());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("forlorn.possess-players", true);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid());
        assertTrue(result.settings().forlorn().possessPlayers());
    }

    @Test
    void removedKeysInOldFilesAreIgnoredSilently() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks.custom-textures", false);
        yaml.set("enchantments.in-loot", true);
        yaml.set("effects.sugar-rush-slows-nearby", false);
        yaml.set("worldgen.structure-blocks-per-tick", 7);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid(), String.valueOf(result.errors()));
        assertTrue(result.warnings().isEmpty(), String.valueOf(result.warnings()));
        assertEquals(AlexsCavesSettings.defaults(), result.settings());
    }

    @Test
    void nonNumericStringsAreErrorsNotZero() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.interval-ticks", "abc");
        yaml.set("spawning.cap-scale", "lots");
        yaml.set("worldgen.biome-separation", "far");
        yaml.set("worldgen.biomes.toxic-caves.min-spawn-distance", "near");
        yaml.set("magnetism.max-entities-per-tick", "many");
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertFalse(result.valid());
        assertEquals(5, result.errors().size(), String.valueOf(result.errors()));
        assertFalse(result.settings().spawning().enabled());
        assertFalse(result.settings().worldgen().enabled());
        assertFalse(result.settings().magnetism().enabled());
    }

    @Test
    void numbersOfTheRightTypeStillLoad() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.interval-ticks", 100);
        yaml.set("spawning.cap-scale", 1);
        yaml.set("worldgen.biome-separation", 1200.5);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid(), String.valueOf(result.errors()));
        assertEquals(100, result.settings().spawning().intervalTicks());
        assertEquals(1.0, result.settings().spawning().capScale());
        assertEquals(1200.5, result.settings().worldgen().rarity().separation());
    }

    @Test
    void missingFileUsesDefaultsWithAWarning() {
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(null);
        assertTrue(result.valid());
        assertEquals(1, result.warnings().size());
        assertEquals(AlexsCavesSettings.defaults(), result.settings());
    }

    @Test
    void invalidWorldgenValueOnlyDisablesWorldgen() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worldgen.biome-radius", 5);
        yaml.set("worldgen.width-randomness", 3.0);
        yaml.set("spawning.interval-ticks", 20);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().get(0).contains("biome-radius"));
        assertFalse(result.settings().worldgen().enabled());
        assertTrue(result.settings().spawning().enabled());
        assertEquals(20, result.settings().spawning().intervalTicks());
    }

    @Test
    void invalidSpawningValueOnlyDisablesSpawning() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.radius", 1000);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertFalse(result.valid());
        assertFalse(result.settings().spawning().enabled());
        assertTrue(result.settings().worldgen().enabled());
        assertTrue(result.settings().magnetism().enabled());
    }

    @Test
    void biomesCanBeTurnedOffAndDistancesChanged() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worldgen.biomes.candy-cavity.enabled", false);
        yaml.set("worldgen.biomes.magnetic-caves.min-spawn-distance", 0);
        yaml.set("worldgen.biomes.nether-caves.enabled", true);
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid());
        assertFalse(result.settings().worldgen().enabledBiomes().contains(CaveBiomeId.CANDY_CAVITY));
        assertEquals(0, result.settings().worldgen().minSpawnDistance().get(CaveBiomeId.MAGNETIC_CAVES));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("nether-caves"));
    }

    @Test
    void negativeSpawnDistanceAndBlankWorldAreErrors() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worldgen.biomes.toxic-caves.min-spawn-distance", -1);
        yaml.set("worldgen.worlds", List.of("world", " "));
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertEquals(2, result.errors().size());
        assertFalse(result.settings().worldgen().enabled());
    }

    @Test
    void economyRewardsAreOffByDefaultAndShippedFileMatches() throws IOException, InvalidConfigurationException {
        assertFalse(AlexsCavesSettings.defaults().integration().economyEnabled());
        assertTrue(AlexsCavesSettings.defaults().integration().dangerPlacements());
        AlexsCavesSettings shipped = AlexsCavesSettingsLoader.load(shipped()).settings();
        assertEquals(AlexsCavesSettings.defaults().integration(), shipped.integration());
        assertEquals(500.0, shipped.integration().reward("luxtructosaurus", 1));
        assertEquals(0.0, shipped.integration().reward("luxtructosaurus", 4));
    }

    @Test
    void integrationValuesCanBeChanged() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("integration.danger.placements", false);
        yaml.set("integration.economy.enabled", true);
        yaml.set("integration.economy.rewards.forsaken", List.of(7, 3.5));
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid(), String.valueOf(result.errors()));
        assertFalse(result.settings().integration().dangerPlacements());
        assertTrue(result.settings().integration().economyEnabled());
        assertEquals(3.5, result.settings().integration().reward("forsaken", 2));
        assertEquals(125.0, result.settings().integration().reward("hullbreaker", 3));
    }

    @Test
    void badRewardAmountOnlySwitchesTheEconomyOff() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("integration.economy.enabled", true);
        yaml.set("integration.economy.rewards.forsaken", List.of(-5));
        yaml.set("integration.economy.rewards.hullbreaker", List.of(1, 2, 3, 4));
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertEquals(2, result.errors().size());
        assertFalse(result.settings().integration().economyEnabled());
        assertTrue(result.settings().integration().dangerPlacements());
        assertTrue(result.settings().worldgen().enabled());
    }

    @Test
    void unknownBossAndNonBooleanSwitchesAreWarnedAndConfiguredBossReplacesDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("integration.economy.enabled", "yes");
        yaml.set("integration.danger.placements", "no");
        yaml.set("integration.economy.rewards.dragon", List.of(1));
        yaml.set("integration.economy.rewards.forsaken", List.of(9));
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(yaml);
        assertTrue(result.valid(), String.valueOf(result.errors()));
        assertEquals(3, result.warnings().size(), String.valueOf(result.warnings()));
        assertFalse(result.settings().integration().economyEnabled());
        assertTrue(result.settings().integration().dangerPlacements());
        assertEquals(9.0, result.settings().integration().reward("forsaken", 1));
        assertEquals(0.0, result.settings().integration().reward("forsaken", 2));
        assertEquals(400.0, result.settings().integration().reward("hullbreaker", 1));
    }

    @Test
    void minDamagePercentIsValidated() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("integration.min-damage-percent", 5);
        assertEquals(5.0, AlexsCavesSettingsLoader.load(yaml).settings().integration().minDamagePercent());
        yaml.set("integration.min-damage-percent", 150);
        AlexsCavesConfigLoadResult bad = AlexsCavesSettingsLoader.load(yaml);
        assertEquals(1, bad.errors().size());
        assertEquals(2.0, bad.settings().integration().minDamagePercent());
    }

    private static YamlConfiguration shipped() throws IOException, InvalidConfigurationException {
        try (InputStream input = AlexsCavesSettingsLoaderTest.class.getClassLoader().getResourceAsStream("alexs-caves.yml")) {
            assertNotNull(input, "alexs-caves.yml must be packaged");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
