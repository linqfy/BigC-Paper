package dev.linqfy.bigCasares.modules.alexscaves.candy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandyAnimationsTest {
    @Test
    void mainHandSwingFollowsHandedness() {
        assertEquals("swing_right", CandyAnimations.mainHandSwing(false));
        assertEquals("swing_left", CandyAnimations.mainHandSwing(true));
    }

    @Test
    void randomSwingUsesBothHands() {
        Random random = new Random(7);
        boolean left = false;
        boolean right = false;
        for (int i = 0; i < 50; i++) {
            String swing = CandyAnimations.randomSwing(random);
            left |= swing.equals("swing_left");
            right |= swing.equals("swing_right");
        }
        assertTrue(left && right);
    }

    @Test
    void bearHuntsFishWithinFiveBlocks() {
        assertNull(CandyAnimations.huntAction(5.0));
        assertEquals("fish", CandyAnimations.huntAction(3.0));
        assertEquals("fish", CandyAnimations.huntAction(1.1));
        assertEquals("eat", CandyAnimations.huntAction(1.0));
    }

    @Test
    void fishingPhases() {
        assertTrue(CandyAnimations.fishJumps(14, 16));
        assertFalse(CandyAnimations.fishJumps(16, 18));
        assertTrue(CandyAnimations.fishLunges(16));
        assertFalse(CandyAnimations.fishLunges(21));
        assertTrue(CandyAnimations.fishCatches(20, 1.2));
        assertFalse(CandyAnimations.fishCatches(20, 1.6));
        assertFalse(CandyAnimations.fishCatches(10, 1.0));
    }

    @Test
    void backScratchNeedsDigestingAdult() {
        assertTrue(CandyAnimations.scratches(false, false, false, true));
        assertFalse(CandyAnimations.scratches(true, false, false, true));
        assertFalse(CandyAnimations.scratches(false, false, false, false));
        assertFalse(CandyAnimations.scratches(false, false, true, true));
    }

    @Test
    void onlyLastingPotionsAreDigested() {
        assertTrue(CandyAnimations.digestible(true, false, true));
        assertFalse(CandyAnimations.digestible(true, true, true));
        assertFalse(CandyAnimations.digestible(true, false, false));
        assertFalse(CandyAnimations.digestible(false, false, true));
    }

    @Test
    void weakHitsKeepLimbs() {
        assertEquals(0, CandyAnimations.afterHit(0, 0.5, false, new Random(1)));
    }

    @Test
    void hitsEventuallyCostLimbsButLegsOnlyOneAtATime() {
        Random random = new Random(3);
        int lost = 0;
        for (int i = 0; i < 200; i++) {
            lost = CandyAnimations.afterHit(lost, 2.0, false, random);
        }
        assertTrue((lost & (CandyAnimations.LEFT_ARM | CandyAnimations.RIGHT_ARM)) != 0);
        int legs = lost & (CandyAnimations.LEFT_LEG | CandyAnimations.RIGHT_LEG);
        assertTrue(legs == CandyAnimations.LEFT_LEG || legs == CandyAnimations.RIGHT_LEG || legs == 0);
    }

    @Test
    void lostLimbLoopYieldsToCarryAndSit() {
        assertEquals("lost_limb", CandyFolk.GingerbreadMan.state(false, false, true));
        assertEquals("sit", CandyFolk.GingerbreadMan.state(false, true, true));
        assertEquals("carry_item", CandyFolk.GingerbreadMan.state(true, true, true));
        assertNull(CandyFolk.GingerbreadMan.state(false, false, false));
    }
}
