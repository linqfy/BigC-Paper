package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FragmentDropRollTest {

    private final MythicMobCatalog catalog = MythicMobCatalog.defaults();

    private static FragmentDropRoll rolling(double value) {
        return new FragmentDropRoll(0.02, () -> value);
    }

    private MythicMobType withChance(String id, double chance) {
        MythicMobType base = catalog.require(id);
        return base.withTuning(base.health(), base.damage(), base.speed(), chance, base.weight());
    }

    @Test
    void dropsTheFragmentOfTheMobsOwnCulture() {
        for (MythicMobType mob : catalog.all()) {
            if (!mob.culture().hasBoss()) {
                continue;
            }
            Optional<String> drop = rolling(0.0).roll(mob, true, true, 0);
            assertEquals(mob.culture().fragmentItemId(), drop, mob.id());
            assertTrue(drop.isPresent(), mob.id());
        }
    }

    @Test
    void mobsOfCulturesWithoutABossNeverDropAFragment() {
        int checked = 0;
        for (MythicMobType mob : catalog.all()) {
            if (mob.culture().hasBoss()) {
                continue;
            }
            checked++;
            assertTrue(mob.culture().fragmentItemId().isEmpty(), mob.id());
            MythicMobType sure = withChance(mob.id(), 1.0);
            assertEquals(0.0, rolling(0.0).chance(sure, 3), mob.id());
            assertTrue(rolling(0.0).roll(sure, true, true, 3).isEmpty(), mob.id());
            assertTrue(rolling(0.0).roll(mob, true, true, 0).isEmpty(), mob.id());
        }
        // hoy las nueve culturas tienen jefe: la regla queda cubierta para cuando se agregue una sin el
        assertEquals(0, checked);
    }

    @Test
    void onlyDungeonSpawnsKilledByPlayersDrop() {
        MythicMobType mob = withChance("minotaur", 1.0);
        assertTrue(rolling(0.0).roll(mob, false, true, 0).isEmpty());
        assertTrue(rolling(0.0).roll(mob, true, false, 0).isEmpty());
        assertTrue(rolling(0.0).roll(mob, false, false, 3).isEmpty());
    }

    @Test
    void chanceBoundaries() {
        assertTrue(rolling(0.0).roll(withChance("hel", 0.0), true, true, 3).isEmpty(), "chance 0 never drops");
        assertTrue(rolling(0.999999).roll(withChance("hel", 1.0), true, true, 0).isPresent(), "chance 1 always");
        MythicMobType tenth = withChance("goblin", 0.1);
        assertTrue(rolling(0.0999).roll(tenth, true, true, 0).isPresent());
        assertTrue(rolling(0.1).roll(tenth, true, true, 0).isEmpty());
    }

    @Test
    void lootingAddsASmallCappedBonus() {
        FragmentDropRoll roll = rolling(0.13);
        MythicMobType tenth = withChance("goblin", 0.1);
        assertTrue(roll.roll(tenth, true, true, 1).isEmpty());
        assertTrue(roll.roll(tenth, true, true, 2).isPresent());
        assertEquals(0.16, roll.chance(tenth, 3), 1.0E-9);
        assertEquals(1.0, roll.chance(withChance("goblin", 0.99), 3), 1.0E-9);
        assertEquals(0.1, roll.chance(tenth, -2), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> new FragmentDropRoll(-0.1, () -> 0.0));
    }
}
