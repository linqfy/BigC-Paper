package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobAnimatorTest {
    @Test
    void stateLoopWinsOverMovement() {
        assertEquals("fly", MobAnimator.baseLoop("fly", true));
        assertEquals("sleep", MobAnimator.baseLoop("sleep", false));
    }

    @Test
    void walksOnlyWhenMoving() {
        assertEquals("walk", MobAnimator.baseLoop(null, true));
        assertEquals("idle", MobAnimator.baseLoop(null, false));
    }

    @Test
    void movementThresholdIsPerTick() {
        assertFalse(MobAnimator.isMoving(0.02, 0.0, 2));
        assertTrue(MobAnimator.isMoving(0.1, 0.0, 2));
        assertTrue(MobAnimator.isMoving(0.06, 0.06, 2));
        assertFalse(MobAnimator.isMoving(1.0, 1.0, 0));
    }
}
