package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainiacBarrelTest {
    @Test
    void drinksOnlyBelowHalfHealthWithABarrel() {
        assertTrue(BrainiacBarrel.wantsToDrink(19, 40, true, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToDrink(20, 40, true, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToDrink(10, 40, false, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToDrink(10, 40, true, 0.99, 2));
    }

    @Test
    void throwsBelowThreeQuartersWithinTwentyBlocks() {
        assertTrue(BrainiacBarrel.wantsToThrow(29, 40, 19.9, true, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToThrow(30, 40, 10, true, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToThrow(10, 40, 20.0, true, 0.0, 2));
        assertFalse(BrainiacBarrel.wantsToThrow(10, 40, 10, false, 0.0, 2));
    }

    @Test
    void rollOverTicksMatchesPerTickOdds() {
        assertEquals(1.0 / 20.0, BrainiacBarrel.chance(20, 1), 1e-9);
        assertEquals(1.0 - Math.pow(19.0 / 20.0, 2), BrainiacBarrel.chance(20, 2), 1e-9);
    }

    @Test
    void releaseOffsetIsInFrontAndBelowTheEyes() {
        Vector straight = BrainiacBarrel.releaseOffset(0, 0);
        assertEquals(0.65, straight.getX(), 1e-9);
        assertEquals(-0.3, straight.getY(), 1e-9);
        assertEquals(0.9, straight.getZ(), 1e-9);
        assertEquals(Math.hypot(0.65, 0.9), Math.hypot(BrainiacBarrel.releaseOffset(0, 70).getX(),
            BrainiacBarrel.releaseOffset(0, 70).getZ()), 1e-9);
    }

    @Test
    void throwSpeedIsAFifthOfTheDistance() {
        Vector velocity = BrainiacBarrel.throwVelocity(new Vector(0, 1, 0), new Vector(0, 1, 10), 10);
        assertEquals(2.0, velocity.length(), 1e-9);
        assertTrue(velocity.getY() > 0 && velocity.getZ() > 0);
        assertEquals(0.0, velocity.getX(), 1e-9);
    }
}
