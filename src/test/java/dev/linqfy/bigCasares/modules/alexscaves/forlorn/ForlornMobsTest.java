package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForlornMobsTest {
    @Test
    void naturalMobsAreInTheModSpawnTable() {
        for (var type : List.of(ForlornMobs.GLOOMOTH, ForlornMobs.VESPER, ForlornMobs.CORRODENT, ForlornMobs.UNDERZEALOT)) {
            assertTrue(ModSpawnTables.all(CaveBiomeId.FORLORN_HOLLOWS).stream().anyMatch(entry -> entry.mobId().equals(type.id())),
                type.id());
        }
        // The watcher only comes from the ritual.
        assertTrue(ModSpawnTables.all(CaveBiomeId.FORLORN_HOLLOWS).stream().noneMatch(entry -> entry.mobId().equals("watcher")));
    }

    @Test
    void modStatsAndLoot() {
        assertEquals(4.0, ForlornMobs.GLOOMOTH.attribute("max_health", 0));
        assertEquals(16.0, ForlornMobs.VESPER.attribute("max_health", 0));
        assertEquals(52.0, ForlornMobs.VESPER.attribute("follow_range", 0));
        assertEquals(3.0, ForlornMobs.CORRODENT.attribute("attack_damage", 0));
        assertEquals(20.0, ForlornMobs.UNDERZEALOT.attribute("max_health", 0));
        assertEquals(256.0, ForlornMobs.WATCHER.attribute("follow_range", 0));
        MobDrop dagger = ForlornMobs.UNDERZEALOT.drops().get(1);
        assertEquals("ac_desolate_dagger", dagger.item());
        Random random = new Random(1);
        assertEquals(0, dagger.roll(random, 0, false));
        int drops = 0;
        for (int i = 0; i < 100_000; i++) {
            drops += dagger.roll(random, 0, true);
        }
        assertTrue(drops > 2000 && drops < 3000, "about 2.5%: " + drops);
    }

    @Test
    void mothDustLootingAddsUpToTwoALevel() {
        MobDrop dust = ForlornMobs.GLOOMOTH.drops().get(0);
        Random random = new Random(3);
        int most = 0;
        for (int i = 0; i < 10_000; i++) {
            most = Math.max(most, dust.roll(random, 3, true));
        }
        assertEquals(8, most);
        for (int i = 0; i < 1000; i++) {
            assertTrue(dust.roll(random, 0, true) <= 2);
        }
    }

    @Test
    void mothBallsScareByTheirShareOfTheRange() {
        // One ball scares up to (just under) 4 blocks when the range is 20; five balls up to 20.
        assertTrue(ForlornRules.mothBallRepels(1, 20, 3 * 3));
        assertFalse(ForlornRules.mothBallRepels(1, 20, 4 * 4));
        assertTrue(ForlornRules.mothBallRepels(5, 20, 19 * 19));
        assertFalse(ForlornRules.mothBallRepels(5, 20, 20 * 20));
    }

    @Test
    void sacrificeRules() {
        assertTrue(ForlornRules.gloomothSacrificeValid(3));
        assertFalse(ForlornRules.gloomothSacrificeValid(4));
        assertTrue(ForlornRules.vesperSacrificeValid(2, true));
        assertFalse(ForlornRules.vesperSacrificeValid(3, true));
        assertTrue(ForlornRules.vesperSacrificeValid(8, false));
        assertFalse(ForlornRules.surroundedByPrayers(2));
        assertTrue(ForlornRules.surroundedByPrayers(3));
    }

    @Test
    void prayersStandOnACircleOfTwoBlocks() {
        for (int position = 1; position < 5; position++) {
            double[] offset = ForlornRules.ringOffset(position, 5);
            assertEquals(2.0, Math.hypot(offset[0], offset[1]), 1.0E-9);
        }
        double[] last = ForlornRules.ringOffset(4, 5);
        assertEquals(2.0, last[0], 1.0E-9);
    }

    @Test
    void vesperOrbitSpiralsIn() {
        double[] start = ForlornRules.vesperOrbit(0, 80);
        double[] end = ForlornRules.vesperOrbit(79, 80);
        assertEquals(7.0, start[1], 1.0E-6);
        assertEquals(8.0, Math.hypot(start[0], start[2]), 1.0E-6);
        assertTrue(end[1] < 4.2);
        assertTrue(Math.hypot(end[0], end[2]) < 3.2);
    }

    @Test
    void corrodentRules() {
        assertFalse(ForlornRules.corrodentFearsLight(7));
        assertTrue(ForlornRules.corrodentFearsLight(8));
        assertEquals(0.0F, ForlornRules.corrodentFallDamage(4.0F));
        assertEquals(5.0F, ForlornRules.corrodentFallDamage(10.0F));
        assertTrue(ForlornRules.diggable(true, false, false));
        assertFalse(ForlornRules.diggable(true, true, false));
        assertFalse(ForlornRules.diggable(true, false, true));
        assertFalse(ForlornRules.diggable(false, false, false));
    }

    @Test
    void darkSpotsOnly() {
        Random random = new Random(7);
        int dark = 0;
        int lit = 0;
        for (int i = 0; i < 10_000; i++) {
            if (ForlornRules.darkEnough(0, 0, random)) {
                dark++;
            }
            if (ForlornRules.darkEnough(15, 15, random)) {
                lit++;
            }
        }
        assertEquals(10_000, dark);
        assertEquals(0, lit);
    }

    @Test
    void possessionLastsEightSecondsUnlessTheWatcherIsOnTheVictim() {
        ForlornRules.Possession possession = new ForlornRules.Possession();
        int ticks = 0;
        while (!possession.step(10.0)) {
            ticks++;
            assertTrue(ticks < 1000);
        }
        assertEquals(159, ticks);
        assertTrue(new ForlornRules.Possession().step(0.5));
    }

    @Test
    void mothDustPowerRisesToOne() {
        assertEquals(0.0F, ForlornRules.mothDustPower(0));
        assertEquals(1.0F, ForlornRules.mothDustPower(30));
    }

    @Test
    void goalsLetTheHighestPriorityDrive() {
        List<String> log = new ArrayList<>();
        boolean[] high = {false};
        Goals goals = new Goals(List.of(new Goals.Goal() {
            @Override
            public boolean canUse() {
                return high[0];
            }

            @Override
            public void tick() {
                log.add("high");
            }

            @Override
            public void stop() {
                log.add("high stop");
            }
        }, new Goals.Goal() {
            @Override
            public boolean canUse() {
                return true;
            }

            @Override
            public void tick() {
                log.add("low");
            }

            @Override
            public void stop() {
                log.add("low stop");
            }
        }));
        goals.tick();
        high[0] = true;
        goals.tick();
        high[0] = false;
        goals.tick();
        assertEquals(List.of("low", "low stop", "high", "high stop", "low"), log);
    }
}
