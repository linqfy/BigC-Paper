package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.primordial.TremorzillaRules.Anim;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TremorzillaRulesTest {
    @Test
    void modStatsAndLoot() {
        assertEquals(500.0, Tremorzilla.TREMORZILLA.attribute("max_health", 0));
        assertEquals(30.0, Tremorzilla.TREMORZILLA.attribute("attack_damage", 0));
        assertEquals(10.0, Tremorzilla.TREMORZILLA.attribute("armor", 0));
        assertEquals(128.0, Tremorzilla.TREMORZILLA.attribute("follow_range", 0));
        assertEquals(2, Tremorzilla.LOOT.size());
        assertEquals(20, Tremorzilla.LOOT.get(0).min());
        assertEquals(40, Tremorzilla.LOOT.get(0).max());
        assertEquals(10, Tremorzilla.LOOT.get(1).min());
        assertEquals(20, Tremorzilla.LOOT.get(1).max());
        assertEquals(11.0, 12.0 * Tremorzilla.GIANT_SCALE, 1.0E-9);
    }

    @Test
    void animationLengthsMatchTheMod() {
        assertEquals(60, Anim.ROAR_1.duration);
        assertEquals(35, Anim.LEFT_SCRATCH.duration);
        assertEquals(40, Anim.RIGHT_TAIL.duration);
        assertEquals(35, Anim.LEFT_STOMP.duration);
        assertEquals(25, Anim.BITE.duration);
        assertEquals(20, Anim.PREPARE_BREATH.duration);
        assertEquals(35, Anim.CHEW.duration);
    }

    @Test
    void hitTicksAreReachedWithTwoTickSteps() {
        assertTrue(TremorzillaRules.crossed(16, 18, 18));
        assertFalse(TremorzillaRules.crossed(18, 20, 18));
        assertTrue(TremorzillaRules.crossed(17, 19, 18));
    }

    @Test
    void spikesLightOneByOne() {
        assertEquals(0.0F, TremorzillaRules.spikesDownAmount(0.0F, 6.0F), 1.0E-6);
        assertEquals(3.0F, TremorzillaRules.spikesDownAmount(0.5F, 6.0F), 1.0E-4);
        assertTrue(TremorzillaRules.chargeBeep(1.0F / 6.0F - 0.001F, 1.0F / 6.0F + 0.004F));
        assertFalse(TremorzillaRules.chargeBeep(0.05F, 0.06F));
        assertTrue(TremorzillaRules.spikesReady(0.95F));
        assertFalse(TremorzillaRules.spikesReady(0.94F));
    }

    @Test
    void beamTimeRunsFasterAwayFromItsTarget() {
        assertEquals(1, TremorzillaRules.beamTimeStep(true, false, 0));
        assertEquals(3, TremorzillaRules.beamTimeStep(false, false, 0));
        assertEquals(8, TremorzillaRules.beamTimeStep(false, true, 150));
        assertEquals(1, TremorzillaRules.beamTimeStep(false, true, 50));
        assertEquals(1.0F, TremorzillaRules.beamAccuracy(0, 200), 1.0E-6);
        assertEquals(0.0F, TremorzillaRules.beamAccuracy(150, 200), 1.0E-6);
        assertEquals(100, TremorzillaRules.maxBeamTime(0));
        assertEquals(249, TremorzillaRules.maxBeamTime(149));
    }

    @Test
    void healsFasterWhenHurt() {
        assertEquals(10, TremorzillaRules.healPeriod(0.1));
        assertEquals(5.0, TremorzillaRules.healAmount(0.1));
        assertEquals(20, TremorzillaRules.healPeriod(0.4));
        assertEquals(3.0, TremorzillaRules.healAmount(0.4));
        assertEquals(100, TremorzillaRules.healPeriod(0.9));
        assertEquals(2.0, TremorzillaRules.healAmount(0.9));
        assertEquals(36.0, TremorzillaRules.irradiatedHeal(2));
    }

    @Test
    void meleeChoicesFollowTheGoal() {
        assertEquals(Anim.LEFT_SCRATCH, TremorzillaRules.meleeChoice(0.1F, true, false, false));
        assertEquals(Anim.RIGHT_STOMP, TremorzillaRules.meleeChoice(0.3F, false, false, false));
        assertEquals(Anim.LEFT_TAIL, TremorzillaRules.meleeChoice(0.6F, true, false, false));
        assertEquals(Anim.BITE, TremorzillaRules.meleeChoice(0.6F, true, true, false));
        assertEquals(Anim.BITE, TremorzillaRules.meleeChoice(0.3F, true, false, true));
        assertEquals(Anim.BITE, TremorzillaRules.meleeChoice(0.9F, true, false, false));
        assertEquals(Anim.RIGHT_STOMP, TremorzillaRules.riderChoice(0.5F, false, false));
        assertEquals(Anim.BITE, TremorzillaRules.riderChoice(0.5F, false, true));
    }

    @Test
    void tamesFromTheFourthDrumOnAThirdOfTries() {
        assertFalse(TremorzillaRules.tames(3, 0));
        assertTrue(TremorzillaRules.tames(4, 0));
        assertFalse(TremorzillaRules.tames(5, 1));
        assertEquals(300, TremorzillaRules.roarCooldown(0));
        assertTrue(TamingRules.breedingFood(Tremorzilla.TREMORZILLA, "ac_nuclear_bomb", false));
        assertFalse(TamingRules.breedingFood(Tremorzilla.TREMORZILLA, "ac_waste_drum", false));
    }

    @Test
    void tailHangsBehindTheBody() {
        Vector first = Tremorzilla.rotateOffset(0, -4.0, -3.5, 0, 0);
        assertEquals(-3.5, first.getZ(), 1.0E-9);
        Vector turned = Tremorzilla.rotateOffset(0, 0, -2.5, 0, 90);
        // Facing west (yaw 90) the tail points east.
        assertEquals(2.5, turned.getX(), 1.0E-9);
        assertEquals(5.0, Tremorzilla.local(0, 0, 5.0, 0).getZ(), 1.0E-9);
        assertEquals(90.0F, Tremorzilla.yawTo(-1, 0), 1.0E-4);
    }
}
