package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqfy.bigCasares.modules.alexscaves.primordial.SauropodRules.Attack;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SauropodRulesTest {
    @Test
    void luxDamageStacksTheModFactors() {
        assertEquals(10.0F, SauropodRules.luxDamage(10.0F, false, false, false, false, false), 1e-6);
        assertEquals(3.5F, SauropodRules.luxDamage(10.0F, true, false, false, false, false), 1e-6);
        assertEquals(6.5F, SauropodRules.luxDamage(10.0F, false, true, false, false, false), 1e-4);
        assertEquals(5.0F, SauropodRules.luxDamage(10.0F, false, false, true, false, false), 1e-6);
        assertEquals(2.5F, SauropodRules.luxDamage(10.0F, false, false, false, true, false), 1e-6);
        assertEquals(5.5F, SauropodRules.luxDamage(10.0F, false, false, false, false, true), 1e-6);
        assertEquals(10.0F * 0.35F * 0.65F * 0.5F * 0.25F * 0.55F,
            SauropodRules.luxDamage(10.0F, true, true, true, true, true), 1e-4);
    }

    @Test
    void attackChoiceFollowsTheMeleeGoal() {
        assertEquals(Attack.STOMP, SauropodRules.luxAttack(0.2F, 3.0, 7.0, true));
        assertEquals(Attack.KICK, SauropodRules.luxAttack(0.2F, 3.0, 7.0, false));
        assertEquals(Attack.WHIP, SauropodRules.luxAttack(0.2F, 9.0, 7.0, false));
        assertEquals(Attack.KICK, SauropodRules.luxAttack(0.5F, 7.5, 7.0, true));
        assertEquals(Attack.WHIP, SauropodRules.luxAttack(0.5F, 9.0, 7.0, true));
        assertEquals(Attack.WHIP, SauropodRules.luxAttack(0.9F, 3.0, 7.0, true));
        assertEquals(Attack.KICK, SauropodRules.atlatitanAttack(0.4F, 3.0, 7.0));
        assertEquals(Attack.WHIP, SauropodRules.atlatitanAttack(0.6F, 3.0, 7.0));
        assertEquals(Attack.WHIP, SauropodRules.atlatitanAttack(0.4F, 9.0, 7.0));
    }

    @Test
    void roarAndEnrageNumbers() {
        assertEquals(90, SauropodRules.roarChance(true, false));
        assertEquals(150, SauropodRules.roarChance(false, false));
        assertEquals(290, SauropodRules.roarChance(true, true));
        assertEquals(350, SauropodRules.roarChance(false, true));
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            int roar = SauropodRules.roarCooldown(random);
            assertTrue(roar >= 100 && roar < 300);
            int enraged = SauropodRules.enragedFor(random);
            assertTrue(enraged >= 500 && enraged < 700);
            int flames = SauropodRules.flamesCooldown(random);
            assertTrue(flames >= 200 && flames < 500);
        }
        assertFalse(SauropodRules.calmsDown(10, 600, 600));
        assertTrue(SauropodRules.calmsDown(0, 150, 600));
        assertFalse(SauropodRules.calmsDown(0, 149, 600));
    }

    @Test
    void fissureRingGrowsEachTick() {
        assertEquals(1 + (int) Math.ceil(6 * 2.3F), SauropodRules.fissureWidth(14));
        assertEquals(1 + (int) Math.ceil(20 * 2.3F), SauropodRules.fissureWidth(0));
        assertEquals(1, SauropodRules.fissureWidth(20));
        assertEquals(1, SauropodRules.fissureWidth(25));
        assertEquals(15, SauropodRules.postStopTicks(true));
        assertEquals(50, SauropodRules.postStopTicks(false));
    }

    @Test
    void breathStepsWidenByOneEachStep() {
        double[][] steps = SauropodRules.breathSteps(13.0F);
        assertEquals(4, steps.length);
        assertArrayEquals(new double[] {0.0, 2.0}, steps[0]);
        assertArrayEquals(new double[] {2.0, 3.0}, steps[1]);
        assertArrayEquals(new double[] {5.0, 4.0}, steps[2]);
        assertArrayEquals(new double[] {9.0, 5.0}, steps[3]);
    }

    @Test
    void jumpLiftFadesOverTheFirstTenTicks() {
        double[] early = SauropodRules.jumpVelocity(20, 0, 0, 0, 15);
        double[] late = SauropodRules.jumpVelocity(20, 0, 0, 0, 25);
        assertEquals(20 * 0.155F, early[0], 1e-4);
        assertEquals((10 - 5) * 0.2F + early[0] * 0.3, early[1], 1e-4);
        assertEquals(late[0] * 0.3, late[1], 1e-4);
        assertTrue(early[1] > late[1]);
        double[] far = SauropodRules.jumpVelocity(1000, 0, 1, 0, 20);
        assertEquals(250 * 0.155F + 0.2, far[0], 1e-3);
    }

    @Test
    void stepsCrossTheModsTickEqualityChecks() {
        assertTrue(SauropodRules.crossed(29, 31, 30));
        assertTrue(SauropodRules.crossed(28, 30, 30));
        assertFalse(SauropodRules.crossed(30, 32, 30));
        assertTrue(SauropodRules.perTick(10, 2) > SauropodRules.perTick(10, 1));
        assertEquals(1.0, SauropodRules.perTick(1, 2), 0.0);
    }

    @Test
    void tephraNumbers() {
        assertEquals(3.0F, SauropodRules.tephraRadius(2.0F), 0.0F);
        assertEquals(0.1F + 0.19F, SauropodRules.tephraScale(0.1F, 2.0F), 1e-6);
        assertEquals(1, SauropodRules.tephraDamage(1.0, 1.0, 3.0F));
        assertEquals((int) ((1.0 + 1.0) / 2.0 * 9.0 * 6.0 + 1.0), SauropodRules.tephraDamage(0.0, 1.0, 3.0F));
        assertEquals(1, SauropodRules.tephraDamage(0.0, 0.0, 3.0F));
    }

    @Test
    void blastLatticeStopsAtUnmovableAndLosesPowerThroughStone() {
        Set<Long> open = SauropodRules.tephraBlocks(0.5, 0.5, 0.5, 3.0F, new Random(7), (x, y, z) -> 0.0F);
        Set<Long> stone = SauropodRules.tephraBlocks(0.5, 0.5, 0.5, 3.0F, new Random(7), (x, y, z) -> 6.0F);
        Set<Long> walled = SauropodRules.tephraBlocks(0.5, 0.5, 0.5, 3.0F, new Random(7),
            (x, y, z) -> Math.abs(x) > 1 ? Float.NaN : 0.0F);
        assertTrue(open.size() > stone.size());
        assertTrue(open.size() > walled.size());
        for (long packed : walled) {
            assertTrue(Math.abs(SauropodRules.unpackX(packed)) <= 2);
        }
        assertTrue(open.contains(SauropodRules.pack(0, 0, 0)));
    }

    @Test
    void packedPositionsRoundTripNegatives() {
        long packed = SauropodRules.pack(-1234, -60, 98765);
        assertEquals(-1234, SauropodRules.unpackX(packed));
        assertEquals(-60, SauropodRules.unpackY(packed));
        assertEquals(98765, SauropodRules.unpackZ(packed));
        long other = SauropodRules.pack(30000000, 300, -30000000);
        assertEquals(30000000, SauropodRules.unpackX(other));
        assertEquals(300, SauropodRules.unpackY(other));
        assertEquals(-30000000, SauropodRules.unpackZ(other));
    }

    @Test
    void migrationLastsADayAfterTheFirstDefeat() {
        assertFalse(PrimordialBoss.migration(false, -1L, 100L));
        assertFalse(PrimordialBoss.migration(true, -1L, 100L));
        assertTrue(PrimordialBoss.migration(true, 1000L, 1000L));
        assertTrue(PrimordialBoss.migration(true, 1000L, 24999L));
        assertFalse(PrimordialBoss.migration(true, 1000L, 25000L));
    }

    @Test
    void eruptionAndSummonTimers() {
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            int cooldown = SauropodRules.eruptionCooldown(random);
            assertTrue(cooldown >= 120 && cooldown < 240);
        }
        assertEquals(24000, SauropodRules.BOSS_SPAWN_COOLDOWN);
    }

    @Test
    void atlatitanNibblesTreesByHeight() {
        assertTrue(SauropodRules.nibbleHighEnough(4, false));
        assertTrue(SauropodRules.nibbleHighEnough(19, false));
        assertFalse(SauropodRules.nibbleHighEnough(3, false));
        assertFalse(SauropodRules.nibbleHighEnough(20, false));
        assertTrue(SauropodRules.nibbleHighEnough(2, true), "the young bite low trees");
        assertFalse(SauropodRules.nibbleHighEnough(3, true));
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int cooldown = SauropodRules.nibbleCooldown(random);
            assertTrue(cooldown >= 200 && cooldown < 400);
        }
        assertEquals(13, SauropodRules.standDistance(1.0));
        assertEquals(1, SauropodRules.standDistance(SauropodRules.ATLATITAN_BABY_SCALE));
    }

    @Test
    void cardinalYawSnapsToTheFourSides() {
        assertEquals(0.0F, SauropodRules.cardinalYaw(10.0F), 1e-6);
        assertEquals(90.0F, SauropodRules.cardinalYaw(50.0F), 1e-6);
        assertEquals(180.0F, SauropodRules.cardinalYaw(-170.0F), 1e-6);
        assertEquals(270.0F, SauropodRules.cardinalYaw(-80.0F), 1e-6);
        assertEquals(0.0F, SauropodRules.cardinalYaw(350.0F), 1e-6);
    }

    @Test
    void atlatitanStompMeterFillsInFourHundredTicks() {
        float meter = 0.0F;
        int ticks = 0;
        while (meter < 1.0F) {
            meter = SauropodRules.meterAfter(meter, true, 2);
            ticks += 2;
        }
        assertTrue(ticks >= 398 && ticks <= 404, "about 400 ticks, got " + ticks);
        assertTrue(SauropodRules.canStomp(meter, false));
        assertFalse(SauropodRules.canStomp(meter, true));
        assertFalse(SauropodRules.canStomp(0.5F, false));
        assertEquals(0.0F, SauropodRules.meterAfter(1.0F, false, 2), 1e-6);
    }

    @Test
    void riddenInputIsSlowerBackwardsAndSideways() {
        assertArrayEquals(new double[] {0.0, 0.8}, SauropodRules.riddenInput(1, 0), 1e-9);
        assertArrayEquals(new double[] {0.0, -0.4}, SauropodRules.riddenInput(-1, 0), 1e-9);
        assertArrayEquals(new double[] {0.35, 0.0}, SauropodRules.riddenInput(0, 1), 1e-9);
        assertEquals(12000, SauropodRules.RIDEABLE_TICKS);
    }

    @Test
    void atlatitanBreedsOnTreeStarsAndIsNeverTamedByThem() {
        assertTrue(TamingRules.breedingFood(Atlatitan.ATLATITAN, "ac_tree_star", false));
        assertFalse(TamingRules.breedingFood(Atlatitan.ATLATITAN, "ac_serene_salad", false));
        assertTrue(TamingRules.isBreedingFood(Atlatitan.ATLATITAN, "ac_tree_star"));
        assertFalse(TamingRules.isBreedingFood(Atlatitan.ATLATITAN, ""));
        assertFalse(TamingRules.rideable(Atlatitan.ATLATITAN), "its rider rides it without an owner");
    }

    @Test
    void oldVolcanoCoresAreLookedForInTheirCornerColumn() {
        int[] range = Volcano.coreColumnRange(-64, 320);
        assertEquals(Volcano.CENTER_Y, range[0]);
        assertEquals(Volcano.CENTER_Y + 56, range[1]);
        int[] shallow = Volcano.coreColumnRange(0, 10);
        assertEquals(0, shallow[0]);
        assertEquals(9, shallow[1]);
    }
}
