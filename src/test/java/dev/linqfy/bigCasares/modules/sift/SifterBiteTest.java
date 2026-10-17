package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SifterBite.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SifterBiteTest {

    @Test
    void theModsTimelineCountsTheStartingTickAsTheFirst() {
        SifterBite bite = new SifterBite();
        assertEquals(1, bite.age());
        List<Stage> stages = new ArrayList<>();
        // advance() number n happens on the tick n after the one that started the bite (age n + 1).
        for (int tick = 1; tick <= 19; tick++) {
            stages.add(bite.advance());
        }
        assertEquals(Stage.SOUND, stages.get(7 - 1), "attack sound on age 8, seven ticks after the start");
        assertEquals(Stage.DAMAGE, stages.get(12 - 1), "damage on age 13, twelve ticks after the start");
        assertEquals(Stage.RELEASED, stages.get(14 - 1), "the Sifter moves again from age 15");
        assertEquals(Stage.FINISHED, stages.get(19 - 1), "age 20 ends the melee cooldown");
        for (int index = 0; index < stages.size(); index++) {
            boolean special = List.of(6, 11, 13, 18).contains(index);
            assertEquals(!special, stages.get(index) == Stage.NONE, "stage " + (index + 1) + ": " + stages.get(index));
        }
    }

    @Test
    void theSifterStandsStillForFourteenTicksOnly() {
        SifterBite bite = new SifterBite();
        int frozenTicks = 1; // the starting tick
        while (bite.frozen()) {
            bite.advance();
            if (bite.frozen()) {
                frozenTicks++;
            }
        }
        assertEquals(SifterBite.DURATION_TICKS, frozenTicks, "ages 1 to 14 are frozen, like the mod's 14-tick bite");
        assertFalse(bite.frozen());
    }

    @Test
    void eachStageHappensOnce() {
        SifterBite bite = new SifterBite();
        int sounds = 0;
        int damages = 0;
        int released = 0;
        int finished = 0;
        for (int tick = 0; tick < 40; tick++) {
            switch (bite.advance()) {
                case SOUND -> sounds++;
                case DAMAGE -> damages++;
                case RELEASED -> released++;
                case FINISHED -> finished++;
                default -> { }
            }
            if (tick == 18) {
                assertEquals(1, finished);
            }
        }
        assertEquals(1, sounds);
        assertEquals(1, damages);
        assertEquals(1, released);
        // Callers drop a finished bite; kept advancing, it keeps reporting the end.
        assertTrue(finished >= 1);
    }

    @Test
    void biteReachIsTwoAndAHalfBlocks() {
        assertEquals(2.5 * 2.5, SifterBite.REACH_SQUARED);
    }
}
