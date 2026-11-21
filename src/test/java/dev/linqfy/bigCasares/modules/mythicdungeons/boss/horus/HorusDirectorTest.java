package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus.HorusDirector.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorusDirectorTest {

    /** Avanza hasta entrar a {@code stage} (o falla tras {@code limit} ticks), planeando planeos de 20 ticks. */
    private static int runUntil(HorusDirector director, Stage stage, double fraction, int limit) {
        for (int tick = 0; tick < limit; tick++) {
            boolean entered = director.tick(fraction, false, false);
            if (entered && director.stage() == Stage.AIM) {
                director.glidePlanned(20);
            }
            if (director.stage() == stage) {
                return tick;
            }
        }
        throw new AssertionError("never reached " + stage + ", stuck in " + director.stage());
    }

    @Test
    void opensWithATakeoffAndTheFirstTickCountsAsAnEntry() {
        HorusDirector director = new HorusDirector();
        assertTrue(director.tick(1.0, false, false));
        assertEquals(Stage.TAKEOFF, director.stage());
        assertEquals(0, director.ticksInStage());
        assertEquals(1, director.phase());
        assertTrue(director.vulnerable());
        assertEquals(2, director.glidesLeft());
    }

    @Test
    void phaseOneCyclesTwoGlidesThenLandsWithABuffetWindow() {
        HorusDirector director = new HorusDirector();
        director.tick(1.0, false, false);
        runUntil(director, Stage.AIM, 1.0, 100);
        director.glidePlanned(20);
        int glides = 0;
        Stage previous = director.stage();
        for (int tick = 0; tick < 400 && director.stage() != Stage.LANDED; tick++) {
            boolean entered = director.tick(1.0, false, false);
            if (entered && director.stage() == Stage.AIM) {
                director.glidePlanned(20);
            }
            if (entered && director.stage() == Stage.GLIDE) {
                glides++;
                assertEquals(Stage.AIM, previous);
            }
            previous = director.stage();
        }
        assertEquals(Stage.LANDED, director.stage());
        assertEquals(2, glides);

        boolean warned = false;
        boolean struck = false;
        for (int tick = 0; tick < HorusDirector.LANDED_TICKS - 1; tick++) {
            director.tick(1.0, false, false);
            warned |= director.buffetWarning();
            if (director.buffetStrike()) {
                assertTrue(warned, "the buffet is always warned first");
                struck = true;
            }
        }
        assertTrue(struck);
        assertTrue((HorusDirector.BUFFET_HIT_AT - HorusDirector.BUFFET_WARN_AT) / 20.0 >= 0.8);
        assertTrue(director.tick(1.0, false, false));
        assertEquals(Stage.TAKEOFF, director.stage());
    }

    @Test
    void theGlideIsTelegraphedAtLeastEightTenthsOfASecond() {
        assertTrue(HorusDirector.AIM_TICKS / 20.0 >= 0.8);
    }

    @Test
    void woundedHorusGlidesThreeTimes() {
        assertEquals(2, HorusDirector.glidesFor(0.9));
        assertEquals(3, HorusDirector.glidesFor(0.6));
    }

    @Test
    void withoutATargetHeKeepsAimingInsteadOfGliding() {
        HorusDirector director = new HorusDirector();
        director.tick(1.0, false, false);
        for (int tick = 0; tick < HorusDirector.TAKEOFF_TICKS + HorusDirector.AIM_TICKS * 3; tick++) {
            director.tick(1.0, false, false);
            assertTrue(director.stage() == Stage.TAKEOFF || director.stage() == Stage.AIM);
        }
    }

    @Test
    void halfHealthOpensPhaseTwoFromAnyStageAndMakesHimInvulnerable() {
        HorusDirector director = new HorusDirector();
        director.tick(1.0, false, false);
        runUntil(director, Stage.GLIDE, 0.8, 200);
        assertTrue(director.tick(0.5, false, false));
        assertEquals(Stage.ASCEND, director.stage());
        assertEquals(2, director.phase());
        assertFalse(director.vulnerable());
    }

    @Test
    void phaseTwoSummonsWaitsForEveryEyeThenCrumbles() {
        HorusDirector director = new HorusDirector();
        director.tick(1.0, false, false);
        director.tick(0.5, false, false);
        for (int tick = 0; tick < HorusDirector.ASCEND_TICKS; tick++) {
            director.tick(0.5, false, false);
        }
        assertEquals(Stage.SUMMON, director.stage());
        director.tick(0.5, false, false);
        assertEquals(Stage.SUMMON, director.stage());
        director.tick(0.5, true, false);
        assertEquals(Stage.GUARD, director.stage());
        for (int tick = 0; tick < 500; tick++) {
            director.tick(0.3, true, false);
        }
        assertEquals(Stage.GUARD, director.stage(), "he only leaves when every eye is dead");
        director.tick(0.0, true, true);
        assertEquals(Stage.WRATH, director.stage());
        for (int tick = 0; tick < HorusDirector.WRATH_TICKS; tick++) {
            director.tick(0.0, true, true);
        }
        assertEquals(Stage.DONE, director.stage());
        assertFalse(director.tick(0.0, true, true));
        assertEquals(2, director.phase());
    }
}
