package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilTabletCycleTest {

    private static List<EnlilCue> advance(EnlilTabletCycle cycle, int ticks) {
        List<EnlilCue> all = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            all.addAll(cycle.advance());
        }
        return all;
    }

    @Test
    void tabletsFormAfterTheRageAndShieldHim() {
        EnlilTabletCycle cycle = new EnlilTabletCycle(3, 4);
        assertEquals(List.of(), advance(cycle, EnlilTuning.RAGE_TICKS - 1));
        assertEquals(EnlilTabletCycle.State.RAGING, cycle.state());
        assertEquals(EnlilTabletCycle.Hit.IGNORED, cycle.hit(0));
        assertEquals(List.of(EnlilCue.TABLETS_FORM), cycle.advance());
        assertEquals(EnlilTabletCycle.State.SHIELDED, cycle.state());
        assertEquals(3, cycle.standing());
        assertEquals(1, cycle.cycles());
        assertFalse(cycle.vulnerable());
    }

    @Test
    void breakingAllThreeMakesHimFallThenTheyReform() {
        EnlilTabletCycle cycle = new EnlilTabletCycle(3, 2);
        advance(cycle, EnlilTuning.RAGE_TICKS);
        assertEquals(EnlilTabletCycle.Hit.CRACKED, cycle.hit(0));
        assertEquals(1, cycle.hitsLeft(0));
        assertEquals(EnlilTabletCycle.Hit.BROKEN, cycle.hit(0));
        assertEquals(EnlilTabletCycle.Hit.IGNORED, cycle.hit(0), "a broken tablet takes no more hits");
        assertEquals(2, cycle.standing());
        cycle.hit(1);
        assertEquals(EnlilTabletCycle.Hit.BROKEN, cycle.hit(1));
        cycle.hit(2);
        assertEquals(EnlilTabletCycle.Hit.ALL_BROKEN, cycle.hit(2));
        assertEquals(EnlilTabletCycle.State.FALLING, cycle.state());
        assertFalse(cycle.vulnerable());

        assertEquals(List.of(EnlilCue.FALLEN), advance(cycle, EnlilTuning.FALL_TICKS));
        assertTrue(cycle.vulnerable());
        assertEquals(0.0, cycle.height(), 1.0e-9);
        assertEquals(List.of(EnlilCue.RISE), advance(cycle, EnlilTuning.FALLEN_TICKS));
        assertFalse(cycle.vulnerable());
        assertEquals(List.of(EnlilCue.TABLETS_FORM), advance(cycle, EnlilTuning.RISE_TICKS));
        assertEquals(2, cycle.cycles());
        assertEquals(3, cycle.standing());
        assertEquals(2, cycle.hitsLeft(2));
    }

    @Test
    void whileShieldedHeKeepsCastingGustsAndTornadoes() {
        EnlilTabletCycle cycle = new EnlilTabletCycle(3, 3);
        advance(cycle, EnlilTuning.RAGE_TICKS);
        List<EnlilCue> cues = advance(cycle, 530);
        assertEquals(3, cues.stream().filter(cue -> cue == EnlilCue.GUST_WARN).count());
        assertEquals(3, cues.stream().filter(cue -> cue == EnlilCue.GUST).count());
        assertEquals(2, cues.stream().filter(cue -> cue == EnlilCue.TORNADO_WARN).count());
        assertEquals(2, cues.stream().filter(cue -> cue == EnlilCue.TORNADO).count());
        assertTrue(cues.indexOf(EnlilCue.GUST_WARN) < cues.indexOf(EnlilCue.GUST));
    }

    @Test
    void heightDropsIntoTheWaterAndComesBack() {
        EnlilTabletCycle cycle = new EnlilTabletCycle(1, 1);
        advance(cycle, EnlilTuning.RAGE_TICKS);
        assertEquals(EnlilTuning.SHIELD_HEIGHT, cycle.height(), 1.0e-9);
        cycle.hit(0);
        advance(cycle, EnlilTuning.FALL_TICKS + EnlilTuning.FALLEN_TICKS + EnlilTuning.RISE_TICKS / 2);
        assertEquals(EnlilTuning.SHIELD_HEIGHT / 2.0, cycle.height(), 1.0e-9);
    }

    @Test
    void rejectsEmptyTablets() {
        assertThrows(IllegalArgumentException.class, () -> new EnlilTabletCycle(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EnlilTabletCycle(3, 0));
    }
}
