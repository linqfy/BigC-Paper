package dev.linqfy.bigCasares.modules.alexscaves.core.enchant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModEnchantmentTest {

    @Test
    void matchesTheModRegistry() {
        assertEquals(51, ModEnchantment.values().length);
        assertEquals("ac_galena_gauntlet", ModEnchantment.FIELD_EXTENSION.itemId());
        assertEquals(4, ModEnchantment.FIELD_EXTENSION.maxLevel());
        assertEquals(ModEnchantment.Rarity.VERY_RARE, ModEnchantment.TSUNAMI.rarity());
        assertEquals(20, ModEnchantment.TSUNAMI.minCost());
    }

    @Test
    void idsAreUniqueAndParse() {
        Set<String> ids = new HashSet<>();
        for (ModEnchantment enchantment : ModEnchantment.values()) {
            assertTrue(ids.add(enchantment.id()));
            assertEquals(Optional.of(enchantment), ModEnchantment.parse(enchantment.key()));
            assertTrue(enchantment.maxLevel() >= 1);
            assertTrue(enchantment.itemId().startsWith("ac_"));
        }
        assertTrue(ModEnchantment.parse("minecraft:sharpness").isEmpty());
    }

    @Test
    void incompatibilitiesAreSymmetric() {
        assertFalse(ModEnchantment.X_RAY.isCompatibleWith(ModEnchantment.GAMMA_RAY));
        assertFalse(ModEnchantment.GAMMA_RAY.isCompatibleWith(ModEnchantment.X_RAY));
        assertFalse(ModEnchantment.PRECISE_VOLLEY.isCompatibleWith(ModEnchantment.RELENTLESS_DARKNESS));
        assertTrue(ModEnchantment.SOLAR.isCompatibleWith(ModEnchantment.X_RAY));
        assertFalse(ModEnchantment.SOLAR.isCompatibleWith(ModEnchantment.SOLAR));
        assertEquals(Set.of("minecraft:mending", "minecraft:unbreaking"), ModEnchantment.TAXING_BELLOW.incompatibleVanilla());
    }

    @Test
    void enchantabilityGrowsPerLevel() {
        assertEquals(1, ModEnchantment.FIELD_EXTENSION.minEnchantability(1));
        assertEquals(19, ModEnchantment.FIELD_EXTENSION.minEnchantability(4));
    }
}
