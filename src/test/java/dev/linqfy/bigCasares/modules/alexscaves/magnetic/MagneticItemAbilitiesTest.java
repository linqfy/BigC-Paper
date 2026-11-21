package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.EnchantmentGuardTestAccess;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantment;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticItemAbilitiesTest {

    @Test
    void resistorShieldSlamFollowsHeavySlam() {
        assertEquals(6.0, MagneticFormulas.shieldSlamDamage(0));
        assertEquals(15.0, MagneticFormulas.shieldSlamDamage(3));
    }

    @Test
    void gauntletMiningMatchesTheModFormula() {
        assertEquals(6.0 / 3.0 / 10.0, MagneticFormulas.miningProgress(6.0, 3.0, false), 1e-12);
        assertEquals(6.0 / 3.0 / 8.0, MagneticFormulas.miningProgress(6.0, 3.0, true), 1e-12);
        assertEquals(1.0, MagneticFormulas.miningProgress(6.0, 0.0, false));
        assertEquals(6.0, MagneticFormulas.digSpeed("IRON_PICKAXE"));
        assertEquals(9.0, MagneticFormulas.digSpeed("NETHERITE_SHOVEL"));
    }

    @Test
    void seekingRadiusGrowsToTen() {
        assertEquals(3.0, MagneticFormulas.seekRadius(0));
        assertEquals(5.0, MagneticFormulas.seekRadius(8));
        assertEquals(10.0, MagneticFormulas.seekRadius(400));
    }

    @Test
    void moduleEnchantmentsOnlyFitTheirOwnItem() {
        assertTrue(EnchantmentGuardTestAccess.allowedOn(Optional.of("ac_galena_gauntlet"), ModEnchantment.FIELD_EXTENSION));
        assertFalse(EnchantmentGuardTestAccess.allowedOn(Optional.of("ac_resistor_shield"), ModEnchantment.FIELD_EXTENSION));
        assertFalse(EnchantmentGuardTestAccess.allowedOn(Optional.empty(), ModEnchantment.HEAVY_SLAM), "vanilla shields");
        assertTrue(EnchantmentGuardTestAccess.allowedOn(Optional.of("ac_resistor_shield"), ModEnchantment.HEAVY_SLAM));
    }

    @Test
    void magnetPolarity() {
        assertEquals(Polarity.SCARLET, MagneticBlocks.polarityOfMagnet(new ModBlockRegistry.Placed("scarlet_magnet", "up")));
        assertEquals(Polarity.AZURE, MagneticBlocks.polarityOfMagnet(new ModBlockRegistry.Placed("azure_magnet", "down")));
        assertNull(MagneticBlocks.polarityOfMagnet(new ModBlockRegistry.Placed("galena", "default")));
    }
}
