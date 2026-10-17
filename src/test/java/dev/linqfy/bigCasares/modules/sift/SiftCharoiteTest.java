package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.modules.sift.SiftCharoite.AnvilInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftCharoiteTest {

    private static final AnvilInput SIFTITE_SWORD = sift("siftite_sword", "NETHERITE_SWORD");
    private static final AnvilInput SIFTITE_INGOT = sift("siftite_ingot", "NETHERITE_INGOT");
    private static final AnvilInput NETHERITE_INGOT = vanilla("NETHERITE_INGOT");
    private static final AnvilInput NETHERITE_SWORD = vanilla("NETHERITE_SWORD");
    private static final AnvilInput BOOK = vanilla("ENCHANTED_BOOK");

    @Test
    void charoiteRaisesTheRequirementByTwentyAndAddsALevel() {
        assertEquals(50, SiftCharoite.cost(30));
        assertEquals(0, SiftCharoite.cost(0), "slots without an offer stay empty");
        assertEquals(30, SiftCharoite.baseCost(50));
        assertEquals(1, SiftCharoite.baseCost(5));
        assertEquals(6, SiftCharoite.level(5, 5), "Sharpness V becomes VI");
        assertEquals(1, SiftCharoite.level(1, 1), "single-level enchantments such as Mending stay");
        assertEquals(-1, SiftCharoite.level(-1, 3), "no clue stays no clue");
    }

    @Test
    void siftiteGearIsRepairedOnlyWithSiftiteIngots() {
        assertTrue(SiftCharoite.anvilAllows(SIFTITE_SWORD, SIFTITE_INGOT));
        assertTrue(SiftCharoite.anvilAllows(SIFTITE_SWORD, SIFTITE_SWORD));
        assertTrue(SiftCharoite.anvilAllows(SIFTITE_SWORD, BOOK));
        assertTrue(SiftCharoite.anvilAllows(SIFTITE_SWORD, AnvilInput.EMPTY), "renaming");
        assertFalse(SiftCharoite.anvilAllows(SIFTITE_SWORD, NETHERITE_INGOT));
        assertFalse(SiftCharoite.anvilAllows(SIFTITE_SWORD, NETHERITE_SWORD));
        assertFalse(SiftCharoite.anvilAllows(NETHERITE_SWORD, SIFTITE_INGOT), "a Siftite ingot is no netherite ingot");
        assertFalse(SiftCharoite.anvilAllows(NETHERITE_SWORD, SIFTITE_SWORD));
        assertTrue(SiftCharoite.anvilAllows(NETHERITE_SWORD, NETHERITE_INGOT), "vanilla repairs stay vanilla");
    }

    @Test
    void charoiteEnchantedItemsNeverCombine() {
        AnvilInput enchanted = new AnvilInput(null, "DIAMOND_SWORD", true);
        assertFalse(SiftCharoite.anvilAllows(enchanted, vanilla("DIAMOND")));
        assertFalse(SiftCharoite.anvilAllows(vanilla("DIAMOND_SWORD"), new AnvilInput(null, "ENCHANTED_BOOK", true)));
        assertFalse(SiftCharoite.anvilAllows(enchanted, AnvilInput.EMPTY), "as in the mod, not even renaming");
    }

    private static AnvilInput sift(String path, String material) {
        return new AnvilInput(NamespacedId.of(SiftBlocks.NS, path), material, false);
    }

    private static AnvilInput vanilla(String material) {
        return new AnvilInput(null, material, false);
    }
}
