package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegendaryCarrierCombatTest {
    @Test
    void aSecondAttackFinishesThePreviousSmashAndItsOldCallbackCannotFinishTheNextOne() {
        var callbacks = new ArrayList<Runnable>();
        var combat = new LegendaryCarrierCombat(null, null, callbacks::add);
        var player = UUID.randomUUID();
        var completions = new ArrayList<Integer>();
        combat.deferSmash(player, () -> completions.add(1));
        assertEquals(List.of(), completions);
        combat.finishSmash(player);
        assertEquals(List.of(1), completions);
        combat.deferSmash(player, () -> completions.add(2));
        callbacks.get(0).run();
        assertEquals(List.of(1), completions);
        callbacks.get(1).run();
        callbacks.get(1).run();
        assertEquals(List.of(1, 2), completions);
    }

    @Test void smashKeepsTheNativeFallThresholdAndExcludesGlidingAndOtherWeapons() {
        assertFalse(LegendaryCarrierCombat.smashEligible(LegendaryWeapon.WINDWEAVER, 1.5, false));
        assertTrue(LegendaryCarrierCombat.smashEligible(LegendaryWeapon.WINDWEAVER, 1.5001, false));
        assertFalse(LegendaryCarrierCombat.smashEligible(LegendaryWeapon.WINDWEAVER, 20, true));
        assertFalse(LegendaryCarrierCombat.smashEligible(LegendaryWeapon.THUNDER_SPEAR, 20, false));
    }

    @Test void onlyFullyChargedGroundedSwordAttacksSweep() {
        assertTrue(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.BLOODLUST, 1, false, false, true));
        assertTrue(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.SHADOW_BLADE, 1, false, false, true));
        assertFalse(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.BLOODLUST, .9F, false, false, true));
        assertFalse(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.BLOODLUST, 1, true, false, true));
        assertFalse(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.BLOODLUST, 1, false, true, true));
        assertFalse(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.BLOODLUST, 1, false, false, false));
        assertFalse(LegendaryCarrierCombat.sweepEligible(LegendaryWeapon.EARTHBREAKER, 1, false, false, true));
    }
}
