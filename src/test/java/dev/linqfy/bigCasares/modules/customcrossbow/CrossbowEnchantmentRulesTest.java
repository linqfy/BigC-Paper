package dev.linqfy.bigCasares.modules.customcrossbow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossbowEnchantmentRulesTest {

    @Test
    void scopeChargesGraduallyAndCapsAtSixHearts() {
        assertEquals(0, CrossbowEnchantmentRules.sniperHearts(29, 20.0));
        assertEquals(1, CrossbowEnchantmentRules.sniperHearts(30, 20.0));
        assertEquals(5, CrossbowEnchantmentRules.sniperHearts(179, 20.0));
        assertEquals(6, CrossbowEnchantmentRules.sniperHearts(180, 20.0));
        assertEquals(6, CrossbowEnchantmentRules.sniperHearts(900, 40.0));
    }

    @Test
    void shotUsesCurrentHealthAndAlwaysLeavesOneHeart() {
        assertEquals(0, CrossbowEnchantmentRules.sniperHearts(180, 3.9));
        assertEquals(1, CrossbowEnchantmentRules.sniperHearts(180, 4.0));
        assertEquals(3, CrossbowEnchantmentRules.sniperHearts(180, 9.0));
        assertEquals(0, CrossbowEnchantmentRules.sniperHearts(-1, 20.0));
        assertEquals(0, CrossbowEnchantmentRules.sniperHearts(90, Double.NaN));
    }

    @Test
    void eachChargedHeartIsExactlyTwoHealthPoints() {
        assertEquals(2.0, CrossbowEnchantmentRules.sniperDamage(1));
        assertEquals(12.0, CrossbowEnchantmentRules.sniperDamage(6));
        assertEquals(12.0, CrossbowEnchantmentRules.sniperDamage(100));
        assertEquals(0.0, CrossbowEnchantmentRules.sniperDamage(-2));
    }

    @Test
    void magazineOnlyReservesAvailableAmmunitionUpToFiveShots() {
        assertEquals(1, CrossbowEnchantmentRules.magazineSize(0));
        assertEquals(3, CrossbowEnchantmentRules.magazineSize(2));
        assertEquals(5, CrossbowEnchantmentRules.magazineSize(4));
        assertEquals(5, CrossbowEnchantmentRules.magazineSize(64));
        assertEquals(1, CrossbowEnchantmentRules.magazineSize(-1));
    }

    @Test
    void specialEnchantmentsCannotStackOrCombineWithMultishot() {
        assertTrue(CrossbowEnchantmentRules.canApply(null, CrossbowEnchantment.SNIPER, false));
        assertTrue(CrossbowEnchantmentRules.canApply(null, CrossbowEnchantment.MULTICHARGE, false));
        assertFalse(CrossbowEnchantmentRules.canApply(CrossbowEnchantment.SNIPER, CrossbowEnchantment.MULTICHARGE, false));
        assertFalse(CrossbowEnchantmentRules.canApply(CrossbowEnchantment.MULTICHARGE, CrossbowEnchantment.SNIPER, false));
        assertFalse(CrossbowEnchantmentRules.canApply(CrossbowEnchantment.SNIPER, CrossbowEnchantment.SNIPER, false));
        assertFalse(CrossbowEnchantmentRules.canApply(null, CrossbowEnchantment.SNIPER, true));
        assertFalse(CrossbowEnchantmentRules.canApply(null, CrossbowEnchantment.MULTICHARGE, true));
    }

    @Test
    void enchantingTableCannotAddMultishotButCanAddOrdinaryUpgrades() {
        assertTrue(CrossbowEnchantmentRules.blocksVanillaEnchantment(CrossbowEnchantment.MULTICHARGE, "multishot"));
        assertTrue(CrossbowEnchantmentRules.blocksVanillaEnchantment(CrossbowEnchantment.SNIPER, "multishot"));
        assertFalse(CrossbowEnchantmentRules.blocksVanillaEnchantment(null, "multishot"));
        assertFalse(CrossbowEnchantmentRules.blocksVanillaEnchantment(CrossbowEnchantment.MULTICHARGE, "quick_charge"));
        assertFalse(CrossbowEnchantmentRules.blocksVanillaEnchantment(CrossbowEnchantment.SNIPER, "unbreaking"));
    }
}
