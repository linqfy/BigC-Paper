package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.mythicdungeons.MythicCulture;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicDungeonSettings;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinition;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinitionLoader;
import dev.linqfy.bigCasares.modules.pveboss.BossDamageStanding;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicBossRegistryTest {

    @Test
    void oneBossPerDevelopedCultureInOrder() {
        assertEquals(MythicCulture.developedCultures(),
            MythicBossRegistry.entries().stream().map(MythicBossRegistry.Entry::culture).toList());
        for (MythicBossRegistry.Entry entry : MythicBossRegistry.entries()) {
            assertEquals(entry.culture().requireBoss().id(), entry.bossId());
            assertNotNull(entry.factory().apply(null), entry.bossId());
            assertNotNull(entry.extraModelKeys().get(), entry.bossId());
            assertEquals(entry, MythicBossRegistry.of(entry.culture()));
        }
        assertNotNull(MythicBossRegistry.extraModelKeys());
    }

    @Test
    void everyBossDefinitionLoadsThroughThePveBossLoader() throws Exception {
        for (MythicCulture culture : MythicCulture.developedCultures()) {
            String bossId = culture.requireBoss().id();
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bosses/" + bossId + ".yml")) {
                assertNotNull(stream, "missing bosses/" + bossId + ".yml");
                AbyssGuardianDefinition definition = new AbyssGuardianDefinitionLoader().load(
                    YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                assertEquals(bossId, definition.id());
                assertEquals(culture.requireBoss().modelKey(), definition.javaModelKey());
                assertEquals(0.5, definition.phaseTwoAt());
                assertEquals(0.01, definition.phaseThreeAt());
                assertTrue(definition.maximumHealth() > 0.0);
                assertTrue(!definition.abilities().isEmpty());
                definition.abilities().forEach(ability ->
                    assertNotNull(definition.animationFor(ability.animationId()), ability.id()));
                definition.musicByPhase().values().forEach(track -> {
                    assertTrue(track.customSoundKey().startsWith("minecraft:"), track.id());
                    assertTrue(track.fallbackSoundKey().startsWith("minecraft:"), track.id());
                });
            }
        }
    }

    @Test
    void thePostponedCulturesHaveNoBossDefinition() {
        for (MythicCulture culture : MythicCulture.values()) {
            if (!culture.developed()) {
                for (String old : List.of("myth-enma", "myth-came", "myth-iblis", "myth-pachamama")) {
                    assertNull(getClass().getClassLoader().getResource("bosses/" + old + ".yml"), old);
                }
            }
        }
    }

    @Test
    void rewardsGoToTheTopDamageDealersWithLessXpPerPlace() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        MythicDungeonSettings.BossReward reward = MythicDungeonSettings.defaultReward(MythicCulture.GREEK);
        List<MythicBossRewards.Grant> grants = MythicBossRewards.grants(List.of(
            new BossDamageStanding(third, 10.0, 3),
            new BossDamageStanding(first, 300.0, 1),
            new BossDamageStanding(idle, 0.0, 4),
            new BossDamageStanding(second, 120.0, 2)), reward, 3);

        assertEquals(List.of(first, second, third), grants.stream().map(MythicBossRewards.Grant::playerId).toList());
        assertEquals(List.of(1500, 1125, 750), grants.stream().map(MythicBossRewards.Grant::xp).toList());
        assertEquals(reward.items(), grants.getFirst().items());
        assertEquals(1, MythicBossRewards.grants(List.of(new BossDamageStanding(first, 5.0, 1),
            new BossDamageStanding(second, 4.0, 2)), reward, 1).size());
    }
}
