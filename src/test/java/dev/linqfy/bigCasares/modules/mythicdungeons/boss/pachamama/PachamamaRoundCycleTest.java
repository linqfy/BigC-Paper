package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaRoundCycleTest {

    /** Ticks (1-based) at which each cue happened during {@code ticks} ticks. */
    private static List<long[]> run(PachamamaRoundCycle cycle, boolean phaseTwo, int ticks) {
        List<long[]> cues = new ArrayList<>();
        for (int tick = 1; tick <= ticks; tick++) {
            for (PachamamaRoundCycle.Cue cue : cycle.advance(phaseTwo)) {
                cues.add(new long[] {tick, cue.ordinal()});
            }
        }
        return cues;
    }

    private static long at(List<long[]> cues, PachamamaRoundCycle.Cue cue, int occurrence) {
        int seen = 0;
        for (long[] entry : cues) {
            if (entry[1] == cue.ordinal() && seen++ == occurrence) {
                return entry[0];
            }
        }
        return -1;
    }

    @Test
    void paintTelegraphsTheStrikeThenPulsesAndClears() {
        PachamamaRoundCycle cycle = new PachamamaRoundCycle(10);
        List<long[]> cues = run(cycle, false, 400);
        long paint = at(cues, PachamamaRoundCycle.Cue.PAINT, 0);
        long strike = at(cues, PachamamaRoundCycle.Cue.STRIKE, 0);
        long clear = at(cues, PachamamaRoundCycle.Cue.CLEAR, 0);
        assertEquals(10, paint);
        assertEquals(paint + PachamamaTuning.PAINT_TICKS_ONE, strike);
        assertTrue(strike - paint >= 16, "the warning lasts at least 0.8 s");
        assertEquals(strike + PachamamaTuning.EFFECT_TICKS_ONE, clear);
        assertEquals(clear + PachamamaTuning.REST_TICKS_ONE, at(cues, PachamamaRoundCycle.Cue.PAINT, 1));
        long pulses = cues.stream().filter(entry -> entry[0] > strike && entry[0] < clear
            && entry[1] == PachamamaRoundCycle.Cue.PULSE.ordinal()).count();
        assertEquals(PachamamaTuning.EFFECT_TICKS_ONE / PachamamaTuning.PULSE_TICKS - 1, pulses);
        assertTrue(cues.stream().noneMatch(entry -> entry[1] == PachamamaRoundCycle.Cue.PULSE.ordinal()
            && entry[0] <= strike), "no pulse before the strike");
    }

    @Test
    void phaseTwoRoundsAreSlower() {
        List<long[]> one = run(new PachamamaRoundCycle(1), false, 1000);
        List<long[]> two = run(new PachamamaRoundCycle(1), true, 1000);
        long countOne = one.stream().filter(entry -> entry[1] == PachamamaRoundCycle.Cue.PAINT.ordinal()).count();
        long countTwo = two.stream().filter(entry -> entry[1] == PachamamaRoundCycle.Cue.PAINT.ordinal()).count();
        assertTrue(countTwo < countOne, countOne + " vs " + countTwo);
    }

    @Test
    void resetDropsAPaintedRound() {
        PachamamaRoundCycle cycle = new PachamamaRoundCycle(1);
        cycle.advance(false);
        assertEquals(PachamamaRoundCycle.State.PAINTED, cycle.state());
        assertTrue(cycle.reset(5));
        assertEquals(PachamamaRoundCycle.State.RESTING, cycle.state());
        assertFalse(cycle.reset(5));
        List<long[]> cues = run(cycle, true, 5);
        assertEquals(5, at(cues, PachamamaRoundCycle.Cue.PAINT, 0));
    }
}
