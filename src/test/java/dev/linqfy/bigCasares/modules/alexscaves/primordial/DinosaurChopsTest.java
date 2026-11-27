package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DinosaurChopsTest {
    @Test
    void bitesAreTheMods() {
        assertEquals(new DinosaurChops.Bite(3, 0.2f), DinosaurChops.bite(true, 0));
        assertEquals(new DinosaurChops.Bite(7, 0.35f), DinosaurChops.bite(false, 0));
        // Primordial armor: one more food and 0.125 more modifier per piece, raw chops only.
        assertEquals(new DinosaurChops.Bite(6, 0.2f + 3 * 0.125f), DinosaurChops.bite(true, 3));
        assertEquals(new DinosaurChops.Bite(7, 0.35f), DinosaurChops.bite(false, 3));
    }

    @Test
    void eatingFollowsVanillaFoodData() {
        assertArrayEquals(new float[] {13, 1.2f + 1.0f}, DinosaurChops.eat(10, 1.0f, DinosaurChops.bite(true, 0)), 1.0e-5f);
        // Food capped at 20, saturation at the new food level.
        assertArrayEquals(new float[] {20, 20}, DinosaurChops.eat(18, 19.0f, DinosaurChops.bite(false, 0)), 1.0e-5f);
    }

    @Test
    void lastBiteLeavesABoneAlongTheChop() {
        assertEquals(3, DinosaurChops.MAX_BITES);
        assertEquals(0, DinosaurChops.axis(BlockFace.EAST));
        assertEquals(0, DinosaurChops.axis(BlockFace.WEST));
        assertEquals(1, DinosaurChops.axis(BlockFace.UP));
        assertEquals(2, DinosaurChops.axis(BlockFace.NORTH));
    }

    @Test
    void everyBiteHasItsDisplayModel() {
        for (String chop : new String[] {"dinosaur_chop", "cooked_dinosaur_chop"}) {
            for (int bites = 0; bites <= DinosaurChops.MAX_BITES; bites++) {
                assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/alexscaves/items",
                    DinosaurChops.displayModel(chop, bites) + ".json")), chop + " " + bites);
            }
        }
    }
}
