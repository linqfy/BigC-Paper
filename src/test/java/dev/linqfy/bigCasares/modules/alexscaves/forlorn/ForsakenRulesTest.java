package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForsakenRules.Anim;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForsakenRulesTest {
    @Test
    void modStatsAndLoot() {
        assertEquals(250.0, ForlornMobs.FORSAKEN.attribute("max_health", 0));
        assertEquals(10.0, ForlornMobs.FORSAKEN.attribute("attack_damage", 0));
        assertEquals(64.0, ForlornMobs.FORSAKEN.attribute("follow_range", 0));
        var drop = ForlornMobs.FORSAKEN.drops().get(0);
        assertEquals("ac_pure_darkness", drop.item());
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            int amount = drop.roll(random, 3, true);
            assertTrue(amount >= 7 && amount <= 12, "no looting: " + amount);
        }
        assertTrue(ForlornMobs.ALL.contains(ForlornMobs.FORSAKEN));
        // Only the ritual makes it.
        assertTrue(dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables
            .all(dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId.FORLORN_HOLLOWS).stream()
            .noneMatch(entry -> entry.mobId().equals("forsaken")));
    }

    @Test
    void sonicDamage() {
        assertEquals(4.0F, ForsakenRules.sonicDamage(false));
        assertEquals(45.0F, ForsakenRules.sonicDamage(true));
        assertEquals(3.0F, ForsakenRules.blastDamage(4.0F));
        assertEquals(30.0F, ForsakenRules.blastDamage(45.0F));
        assertEquals(1, ForsakenRules.sonicDelay(3.0));
        assertEquals(4, ForsakenRules.sonicDelay(19.0));
        assertEquals(5, ForsakenRules.sonicDelay(20.0));
    }

    @Test
    void meleeChoice() {
        assertEquals(Anim.LEFT_PICKUP, ForsakenRules.melee(0.1F, 0.6, true));
        assertEquals(Anim.RIGHT_PICKUP, ForsakenRules.melee(0.1F, 0.6, false));
        // Too wide to lift.
        assertEquals(Anim.LEFT_SLASH, ForsakenRules.melee(0.1F, 2.0, true));
        assertEquals(Anim.RIGHT_SLASH, ForsakenRules.melee(0.4F, 0.6, false));
        assertEquals(Anim.GROUND_SMASH, ForsakenRules.melee(0.6F, 0.6, true));
        assertEquals(Anim.BITE, ForsakenRules.melee(0.9F, 0.6, true));
        assertTrue(ForsakenRules.sonicBlast(10.0, 0.4F));
        assertTrue(!ForsakenRules.sonicBlast(10.1, 0.9F));
        assertTrue(!ForsakenRules.sonicBlast(5.0, 0.39F));
    }

    @Test
    void animationDurations() {
        assertEquals(50, Anim.SUMMON.duration);
        assertEquals(15, Anim.PREPARE_JUMP.duration);
        assertEquals(33, Anim.LEFT_SLASH.duration);
        assertEquals(30, Anim.GROUND_SMASH.duration);
        assertEquals(35, Anim.SONIC_ATTACK.duration);
        assertEquals(45, Anim.SONIC_BLAST.duration);
        assertEquals(48, Anim.RIGHT_PICKUP.duration);
    }

    @Test
    void tickWindowsAreCoveredWhenRunningEveryTwoTicks() {
        // A hit window of ticks 15..18 is 4 ticks whatever the step.
        int total = 0;
        for (int tick = 0; tick < 33; tick += 2) {
            total += ForsakenRules.ticksIn(tick, 2, 15, 18);
        }
        assertEquals(4, total);
        // The blast: multiples of 5 in 10..30 are 10, 15, 20, 25, 30.
        total = 0;
        for (int tick = 0; tick < 46; tick += 2) {
            total += ForsakenRules.multiplesIn(tick, 2, 5, 10, 30);
        }
        assertEquals(5, total);
        assertEquals(0, ForsakenRules.ticksIn(0, 2, 8, 10));
        assertEquals(1, ForsakenRules.ticksIn(9, 2, 8, 10) - 1);
    }

    @Test
    void perSpanChance() {
        assertEquals(0.1, ForsakenRules.perSpan(0.1, 1), 1e-9);
        assertEquals(0.19, ForsakenRules.perSpan(0.1, 2), 1e-9);
    }

    @Test
    void handPositions() {
        assertEquals(4.0, ForsakenRules.handPos(5, true)[2]);
        assertEquals(1.0, ForsakenRules.handPos(12, true)[1]);
        assertEquals(2.75, ForsakenRules.handPos(20, true)[0], 1e-6);
        assertEquals(-2.75, ForsakenRules.handPos(20, false)[0], 1e-6);
        assertEquals(1.2, ForsakenRules.handPos(30, true)[0], 1e-6);
    }

    @Test
    void darknessHealing() {
        // Above half health there is no darkness.
        assertEquals(0, ForsakenRules.darknessAfter(30, false, 0, 2));
        // In the dark it stays at 30.
        assertEquals(30, ForsakenRules.darknessAfter(5, true, 4, 2));
        // In the light it runs out.
        assertEquals(28, ForsakenRules.darknessAfter(30, true, 5, 2));
        assertEquals(0, ForsakenRules.darknessAfter(1, true, 15, 2));
    }

    @Test
    void golemsHitForHalf() {
        assertEquals(7.5F, ForsakenRules.golemDamage(15.0F));
    }

    @Test
    void skyDarkenFollowsTheHourAndWeather() {
        assertEquals(0, ForsakenRules.skyDarken(6000, false, false));
        assertEquals(11, ForsakenRules.skyDarken(18000, false, false));
        assertEquals(3, ForsakenRules.skyDarken(6000, true, false));
        assertTrue(ForsakenRules.skyDarken(13000, false, false) > 0);
        assertEquals(2, ForsakenRules.localBrightness(2, 9, 11));
        assertEquals(4, ForsakenRules.localBrightness(2, 15, 11));
        assertEquals(0, ForsakenRules.localBrightness(0, 3, 11));
    }
}
