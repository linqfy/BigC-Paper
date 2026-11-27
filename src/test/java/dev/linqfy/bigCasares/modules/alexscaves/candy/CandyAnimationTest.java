package dev.linqfy.bigCasares.modules.alexscaves.candy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CandyAnimationTest {
    @Test
    void candicornStatesFollowThePriorityOfTheMod() {
        assertEquals("charge", CandyMobs.Candicorn.state(true, true, true, true, true));
        assertEquals("vehicle", CandyMobs.Candicorn.state(false, true, true, true, true));
        assertEquals("run", CandyMobs.Candicorn.state(false, false, true, true, false));
        assertEquals("saddled", CandyMobs.Candicorn.state(false, false, false, true, false));
        assertNull(CandyMobs.Candicorn.state(false, false, false, true, true), "saddled and walking shows the walk");
        assertNull(CandyMobs.Candicorn.state(false, false, false, false, false));
    }

    @Test
    void candicornIdleAnimationsUseTheModsOdds() {
        assertEquals("buck", CandyMobs.Candicorn.idleAnimation(0.1F, true));
        assertEquals("tail_flick_1", CandyMobs.Candicorn.idleAnimation(0.3F, true));
        assertEquals("tail_flick_2", CandyMobs.Candicorn.idleAnimation(0.3F, false));
        assertEquals("nibble_idle", CandyMobs.Candicorn.idleAnimation(0.9F, true));
        assertEquals(35, CandyMobs.Candicorn.idleTicks("nibble_idle"));
    }

    @Test
    void gumbeeperShootsThenSwellsOnlyWhenOutOfGumballs() {
        assertEquals("shoot", CandyMobs.Gumbeeper.state(3, 4, false));
        assertEquals("explode", CandyMobs.Gumbeeper.state(0, 0, true));
        assertNull(CandyMobs.Gumbeeper.state(0, 4, true), "with gumballs left the fuse is held back");
    }

    @Test
    void caramelCubeJumpsThenSquishesOnLanding() {
        assertEquals("jump", CandyMobs.CaramelCube.state(true, false));
        assertEquals("squish", CandyMobs.CaramelCube.state(false, true));
        assertNull(CandyMobs.CaramelCube.state(false, false));
    }

    @Test
    void gingerbreadIdleAnimationsLicowitchArmsAndBearStand() {
        assertEquals("carry_item", CandyFolk.GingerbreadMan.state(true, true));
        assertEquals("sit", CandyFolk.GingerbreadMan.state(false, true));
        assertEquals("idle_jump", CandyFolk.GingerbreadMan.idleAnimation(0.1F, false));
        assertEquals("idle_fall_over", CandyFolk.GingerbreadMan.idleAnimation(0.4F, false));
        assertEquals("idle_wave_left", CandyFolk.GingerbreadMan.idleAnimation(0.6F, false));
        assertEquals("idle_wave_right", CandyFolk.GingerbreadMan.idleAnimation(0.9F, false));
        assertEquals("idle_wave_left", CandyFolk.GingerbreadMan.idleAnimation(0.1F, true));
        assertEquals("arms_uncrossed", CandyFolk.Licowitch.state(10));
        assertNull(CandyFolk.Licowitch.state(0));
        assertEquals("stand", CandyMobs.GummyBear.state(5));
    }
}
