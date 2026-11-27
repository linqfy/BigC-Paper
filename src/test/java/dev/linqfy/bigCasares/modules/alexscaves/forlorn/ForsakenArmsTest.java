package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForsakenRules.Anim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForsakenArmsTest {
    @Test
    void sideAnimationsRaiseTheirOwnArm() {
        assertTrue(ForsakenRules.raisingArm(Anim.LEFT_PICKUP, 20, true));
        assertFalse(ForsakenRules.raisingArm(Anim.LEFT_PICKUP, 20, false));
        assertTrue(ForsakenRules.raisingArm(Anim.RIGHT_SLASH, 5, false));
        assertFalse(ForsakenRules.raisingArm(Anim.RIGHT_SLASH, 5, true));
        assertTrue(ForsakenRules.raisingArm(Anim.SUMMON, 1, true));
        assertTrue(ForsakenRules.raisingArm(Anim.GROUND_SMASH, 1, false));
        assertFalse(ForsakenRules.raisingArm(Anim.BITE, 1, true));
        assertFalse(ForsakenRules.raisingArm(null, 0, true));
    }

    @Test
    void armDropsInTheLastFiveTicks() {
        int last = Anim.LEFT_PICKUP.duration - 5;
        assertTrue(ForsakenRules.raisingArm(Anim.LEFT_PICKUP, last, true));
        assertFalse(ForsakenRules.raisingArm(Anim.LEFT_PICKUP, last + 1, true));
    }

    @Test
    void progressRisesAndFallsOneATickWithinZeroAndTen() {
        assertEquals(3.0F, ForsakenRules.armProgress(0.0F, true, 3));
        assertEquals(10.0F, ForsakenRules.armProgress(8.0F, true, 5));
        assertEquals(7.0F, ForsakenRules.armProgress(10.0F, false, 3));
        assertEquals(0.0F, ForsakenRules.armProgress(2.0F, false, 6));
        assertEquals(0.0F, ForsakenRules.armProgress(0.0F, false, 1));
    }

    @Test
    void pickupKeepsTheArmShownUntilJustAfterItEnds() {
        float progress = 0.0F;
        int shownTicks = 0;
        for (int tick = 0; tick < Anim.LEFT_PICKUP.duration + 12; tick++) {
            boolean raising = ForsakenRules.raisingArm(tick < Anim.LEFT_PICKUP.duration ? Anim.LEFT_PICKUP : null, tick, true);
            progress = ForsakenRules.armProgress(progress, raising, 1);
            if (progress >= ForsakenRules.ARM_RAISE_SHOWN) {
                shownTicks++;
            }
        }
        assertEquals(0.0F, progress);
        assertTrue(shownTicks > 30 && shownTicks < Anim.LEFT_PICKUP.duration, "shown for " + shownTicks);
    }
}
