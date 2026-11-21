package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonBossRegistryTest {

    private final DungeonBounds greek = new DungeonBounds("world", MythicCulture.GREEK, 0, -10, 0, 31, 20, 31);

    @Test
    void aLiveBossBlocksItsDungeonUntilItIsNoLongerActive() {
        DungeonBossRegistry registry = new DungeonBossRegistry();
        Set<UUID> active = new HashSet<>();
        UUID boss = UUID.randomUUID();
        active.add(boss);
        registry.remember(greek.key(), new DungeonBossRegistry.Summon(boss, MythicCulture.GREEK, "world", 5, 1, 5));

        assertTrue(registry.alive(greek.key(), active::contains).isPresent());
        assertTrue(registry.alive("world:norse:0:0:0", active::contains).isEmpty());

        active.remove(boss);
        assertTrue(registry.alive(greek.key(), active::contains).isEmpty());
        assertTrue(registry.snapshot().isEmpty(), "dead bosses are forgotten");
    }

    @Test
    void forgetAndClear() {
        DungeonBossRegistry registry = new DungeonBossRegistry();
        UUID boss = UUID.randomUUID();
        registry.remember("a", new DungeonBossRegistry.Summon(boss, MythicCulture.NORSE, "world", 0, 0, 0));
        registry.remember("b", new DungeonBossRegistry.Summon(UUID.randomUUID(), MythicCulture.NORSE, "w", 0, 0, 0));
        registry.forget(boss);
        assertEquals(Set.of("b"), registry.snapshot().keySet());
        registry.clear();
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void boundsContainTheirBlocksOnlyInTheirWorld() {
        assertTrue(greek.contains("world", 0, -10, 0));
        assertTrue(greek.contains("world", 31.9, 20.9, 31.9));
        assertFalse(greek.contains("world", 32, 0, 0));
        assertFalse(greek.contains("world", 0, -10.1, 0));
        assertFalse(greek.contains("world_nether", 5, 5, 5));
        assertEquals("world:greek:0:-10:0", greek.key());
        assertEquals(16.0, greek.centerX());
        assertThrows(IllegalArgumentException.class,
            () -> new DungeonBounds("world", MythicCulture.GREEK, 5, 0, 0, 4, 0, 0));
    }

    @Test
    void theLocatorDefaultReportsTheCultureOfTheBounds() {
        DungeonLocator fake = location -> Optional.of(greek);
        assertEquals(MythicCulture.GREEK, fake.cultureAt(null).orElseThrow());
    }

    @Test
    void remembersWhoSpentTheSummonerAndFindsTheSummonByItsBoss() {
        DungeonBossRegistry registry = new DungeonBossRegistry();
        UUID boss = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        registry.remember(greek.key(), new DungeonBossRegistry.Summon(boss, MythicCulture.GREEK, "world", 5, 1, 5,
            player));
        registry.remember("admin:x", new DungeonBossRegistry.Summon(UUID.randomUUID(), MythicCulture.NORSE, "w", 0, 0,
            0));

        assertEquals(Optional.of(player), registry.byBoss(boss).flatMap(DungeonBossRegistry.Summon::summoner));
        assertTrue(registry.snapshot().get("admin:x").summoner().isEmpty());
        registry.forget(boss);
        assertTrue(registry.byBoss(boss).isEmpty());
    }
}
