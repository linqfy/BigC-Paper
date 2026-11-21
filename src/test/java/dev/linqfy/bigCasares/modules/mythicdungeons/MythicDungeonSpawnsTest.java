package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Que mob nace en cada mazmorra: las nueve culturas usan sus propios mobs, tengan jefe o no. */
class MythicDungeonSpawnsTest {

    private final MythicMobCatalog catalog = MythicMobCatalog.defaults();

    @Test
    void everyDungeonSpawnsOnlyItsOwnMobsAndCanSpawnAllOfThem() {
        for (MythicCulture culture : MythicCulture.values()) {
            Set<String> seen = new HashSet<>();
            for (int step = 0; step < 1000; step++) {
                MythicMobType picked = MythicDungeonSpawns.pick(catalog, culture, step / 1000.0).orElseThrow();
                assertEquals(culture, picked.culture(), culture.id());
                seen.add(picked.id());
            }
            assertEquals(Set.copyOf(culture.mobIds()), seen, culture.id());
        }
    }

    @Test
    void theElitesOfTheNewDungeonsAreRare() {
        for (MythicCulture culture : MythicCulture.values()) {
            if (culture.hasBoss()) {
                continue;
            }
            int elites = 0;
            for (int step = 0; step < 1000; step++) {
                if (MythicDungeonSpawns.pick(catalog, culture, step / 1000.0).orElseThrow().elite()) {
                    elites++;
                }
            }
            assertTrue(elites > 0 && elites < 150, culture.id() + ": " + elites);
        }
    }
}
