package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DinosaurHabitsTest {
    @Test
    void leapsOnlyFromThreeToSevenBlocks() {
        assertFalse(DinosaurHabits.leapRange(3.0));
        assertTrue(DinosaurHabits.leapRange(5.0));
        assertFalse(DinosaurHabits.leapRange(7.0));
        assertEquals(5, DinosaurHabits.leapOdds(true));
        assertEquals(10, DinosaurHabits.leapOdds(false));
    }

    @Test
    void leapAddsHalfAStepTowardTheTargetAndLifts() {
        double[] jump = DinosaurHabits.leapVelocity(0.0, 0.0, 0.0, 4.0);
        assertArrayEquals(new double[] {0.0, 0.6, 0.45}, jump, 1.0E-9);
        assertArrayEquals(new double[] {0.1, 0.6, 0.2}, DinosaurHabits.leapVelocity(0.1, 0.2, 0.0, 0.0), 1.0E-9);
    }

    @Test
    void raptorTakesEggsChopsAndMeatOnly() {
        assertTrue(DinosaurHabits.steals(Material.EGG, null));
        assertTrue(DinosaurHabits.steals(Material.BEEF, null));
        assertFalse(DinosaurHabits.steals(Material.BREAD, null));
        assertTrue(DinosaurHabits.steals(Material.NOTE_BLOCK, "ac_dinosaur_chop"));
        assertFalse(DinosaurHabits.steals(Material.NOTE_BLOCK, "ac_vallumraptor_egg"));
    }

    @Test
    void nibbleHeights() {
        assertFalse(DinosaurHabits.nibbleHigh(3, false));
        assertTrue(DinosaurHabits.nibbleHigh(4, false));
        assertFalse(DinosaurHabits.nibbleHigh(7, false));
        assertTrue(DinosaurHabits.nibbleHigh(1, true));
        assertFalse(DinosaurHabits.nibbleHigh(2, true));
    }

    @Test
    void standsFourBlocksBackOnTheSeenSide() {
        assertArrayEquals(new int[] {6, 10}, DinosaurHabits.standAt(10, 10, 0.5, 10.5, false));
        assertArrayEquals(new int[] {10, 14}, DinosaurHabits.standAt(10, 10, 10.5, 20.5, false)[1] == 14
            ? new int[] {10, 14} : new int[] {0, 0});
        assertArrayEquals(new int[] {7, 10}, DinosaurHabits.standAt(10, 10, 0.5, 10.5, true));
    }

    @Test
    void passesPeriods() {
        assertTrue(DinosaurHabits.passes(7, 8, 8));
        assertFalse(DinosaurHabits.passes(8, 10, 8));
        assertTrue(DinosaurHabits.attached(3, 4, 5));
        assertFalse(DinosaurHabits.attached(12, 0, 0));
    }

    @Test
    void legsGoBackInTheSecondHalfOfEveryStride() {
        SauropodLegs legs = new SauropodLegs();
        assertFalse(legs.update(1.0, true));
        assertTrue(legs.update(1.5, true));
        assertFalse(legs.update(1.6, true));
        assertFalse(legs.update(0.0, false));
        assertEquals("leg_back", PrimordialPose.sauropodBase("walk", true));
        assertEquals("idle", PrimordialPose.sauropodBase("idle", true));
        assertEquals("walk", PrimordialPose.sauropodBase("walk", false));
    }

    @Test
    void builtBlocksRuleOutATree() {
        assertTrue(DinosaurHabits.builtMaterial(org.bukkit.Material.OAK_PLANKS));
        assertTrue(DinosaurHabits.builtMaterial(org.bukkit.Material.GLASS_PANE));
        assertTrue(DinosaurHabits.builtMaterial(org.bukkit.Material.STRIPPED_OAK_LOG));
        assertFalse(DinosaurHabits.builtMaterial(org.bukkit.Material.AIR));
        assertFalse(DinosaurHabits.builtMaterial(org.bukkit.Material.DIRT));
        assertFalse(DinosaurHabits.builtMaterial(org.bukkit.Material.OAK_LEAVES));
    }
}
