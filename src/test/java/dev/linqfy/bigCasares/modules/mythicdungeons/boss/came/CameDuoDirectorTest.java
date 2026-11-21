package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameDuoDirectorTest {

    @Test
    void everyPatternUsesBothLordsAndWarnsInTime() {
        for (CameDuoDirector.Pattern pattern : CameDuoDirector.Pattern.values()) {
            Set<CameLord> lords = EnumSet.noneOf(CameLord.class);
            for (CameDuoDirector.Beat beat : CameDuoDirector.beats(pattern)) {
                lords.add(beat.lord());
                assertTrue(beat.landAt() - beat.telegraphAt() >= CameDuoDirector.MIN_TELEGRAPH, pattern + " " + beat);
                assertTrue(beat.landAt() < CameDuoDirector.length(pattern));
            }
            assertEquals(EnumSet.allOf(CameLord.class), lords, pattern + " is a duo attack");
        }
        assertThrows(IllegalArgumentException.class,
            () -> new CameDuoDirector.Beat(CameLord.HUN, CameDuoDirector.Move.STRIKE, 0, 10));
    }

    @Test
    void thePincerHitsFromBothSidesAtTheSameTime() {
        List<CameDuoDirector.Beat> beats = CameDuoDirector.beats(CameDuoDirector.Pattern.PINCER);
        assertEquals(beats.get(0).landAt(), beats.get(1).landAt());
        CameVec target = new CameVec(10.0, 64.0, 10.0);
        CameVec axis = new CameVec(1.0, 0.0, 0.0);
        CameVec hun = CameStrikes.pincerStart(target, axis, CameLord.HUN);
        CameVec vucub = CameStrikes.pincerStart(target, axis, CameLord.VUCUB);
        assertEquals(new CameVec(17.0, 64.0, 10.0), hun);
        assertEquals(new CameVec(3.0, 64.0, 10.0), vucub);
        // los dos carriles pasan por el jugador
        assertTrue(CameStrikes.inLane(hun, CameStrikes.dashEnd(hun, target), CameStrikes.DASH_WIDTH, target));
        assertTrue(CameStrikes.inLane(vucub, CameStrikes.dashEnd(vucub, target), CameStrikes.DASH_WIDTH, target));
    }

    @Test
    void thePinComesBeforeTheLeap() {
        List<CameDuoDirector.Beat> beats = CameDuoDirector.beats(CameDuoDirector.Pattern.PIN_AND_STRIKE);
        CameDuoDirector.Beat pin = beats.get(0);
        CameDuoDirector.Beat leap = beats.get(1);
        assertEquals(CameLord.HUN, pin.lord());
        assertEquals(CameDuoDirector.Move.ROOT_SLAM, pin.move());
        assertEquals(CameLord.VUCUB, leap.lord());
        assertEquals(pin.landAt(), leap.telegraphAt(), "Vucub aims where the player was pinned");
    }

    @Test
    void theComboAlternatesLords() {
        List<CameDuoDirector.Beat> beats = CameDuoDirector.beats(CameDuoDirector.Pattern.ALTERNATING);
        assertEquals(4, beats.size());
        for (int index = 1; index < beats.size(); index++) {
            assertNotEquals(beats.get(index - 1).lord(), beats.get(index).lord());
            assertTrue(beats.get(index).landAt() > beats.get(index - 1).landAt());
        }
    }

    @Test
    void startsAfterAGapNeverRepeatsAndReportsBeats() {
        CameDuoDirector director = new CameDuoDirector(0L);
        assertEquals(Optional.empty(), director.maybeStart(CameDuoDirector.FIRST_GAP - 1, true, bound -> 0));
        CameDuoDirector.Pattern first = director.maybeStart(CameDuoDirector.FIRST_GAP, true, bound -> 0).orElseThrow();
        long start = director.startedAt();
        assertTrue(director.running(start));
        List<CameDuoDirector.Due> atStart = director.due(start);
        assertFalse(atStart.isEmpty());
        assertTrue(atStart.stream().allMatch(due -> due.cue() == CameDuoDirector.Cue.TELEGRAPH));
        long end = start + CameDuoDirector.length(first);
        List<CameDuoDirector.Due> all = new ArrayList<>();
        for (long now = start; now < end; now++) {
            all.addAll(director.due(now));
            assertEquals(Optional.empty(), director.maybeStart(now, true, bound -> 0), "one pattern at a time");
        }
        assertEquals(CameDuoDirector.beats(first).size() * 2, all.size());
        assertFalse(director.running(end));
        assertEquals(Optional.empty(), director.maybeStart(end, true, bound -> 0), "rests between patterns");
        CameDuoDirector.Pattern second = director.maybeStart(end + CameDuoDirector.GAP, true, bound -> 0).orElseThrow();
        assertNotEquals(first, second);
    }

    @Test
    void stopsWhileALordIsDownAndResumesAfterTheRevive() {
        CameDuoDirector director = new CameDuoDirector(0L);
        director.maybeStart(CameDuoDirector.FIRST_GAP, true, bound -> 1).orElseThrow();
        director.abort();
        assertFalse(director.running(CameDuoDirector.FIRST_GAP + 1));
        assertTrue(director.due(CameDuoDirector.FIRST_GAP + 1).isEmpty());
        assertEquals(Optional.empty(), director.maybeStart(100_000L, true, bound -> 0));
        director.resume(1000L);
        assertEquals(Optional.empty(), director.maybeStart(1000L + CameDuoDirector.AFTER_REVIVE_GAP - 1, true, b -> 0));
        assertTrue(director.maybeStart(1000L + CameDuoDirector.AFTER_REVIVE_GAP, true, b -> 0).isPresent());
        // nunca sin los dos en pie
        CameDuoDirector other = new CameDuoDirector(0L);
        assertEquals(Optional.empty(), other.maybeStart(5000L, false, bound -> 0));
    }
}
