package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NukeScheduleTest {
    @Test
    void bombsAreNumberedFromA1AndMoveOnToTheNextLetterAfterNine() {
        assertEquals("A1", NukeSchedule.label(1));
        assertEquals("A9", NukeSchedule.label(9));
        assertEquals("B1", NukeSchedule.label(10));
        assertEquals("B9", NukeSchedule.label(18));
        assertEquals("Z9", NukeSchedule.label(26 * 9));
        assertEquals("A1", NukeSchedule.label(26 * 9 + 1));
        assertEquals("A1", NukeSchedule.label(0));
    }

    @Test
    void theFuseIsFourteenSecondsAndTheAlertComesInTheLastEight() {
        assertEquals(14 * 20, NukeSchedule.FUSE_TICKS);
        assertEquals(8 * 20, NukeSchedule.ALERT_TICKS_LEFT);
        assertTrue(NukeSchedule.isAlert(160));
        assertTrue(!NukeSchedule.isAlert(159));
    }

    @Test
    void theAlertNamesTheBombAndTheCountdownGoesFiveToOne() {
        List<String> alert = NukeSchedule.linesAt(160, "B3");
        assertTrue(alert.stream().anyMatch(line -> line.contains("¡ALERTA!") && line.contains("B3")
            && line.contains("ha sido activada") && line.contains("que Dios los ampare, corran...")));
        assertEquals(List.of("§eExplosión en 5..."), NukeSchedule.linesAt(100, "A1"));
        assertEquals(List.of("§e4..."), NukeSchedule.linesAt(80, "A1"));
        assertEquals(List.of("§e3..."), NukeSchedule.linesAt(60, "A1"));
        assertEquals(List.of("§e2..."), NukeSchedule.linesAt(40, "A1"));
        assertEquals(List.of("§e1..."), NukeSchedule.linesAt(20, "A1"));
    }

    @Test
    void everyOtherTickIsSilent() {
        int lines = 0;
        for (int left = NukeSchedule.FUSE_TICKS; left >= 0; left--) {
            if (!NukeSchedule.linesAt(left, "A1").isEmpty()) {
                lines++;
            }
        }
        // The alert and five countdown lines.
        assertEquals(6, lines);
        assertTrue(NukeSchedule.linesAt(0, "A1").isEmpty());
        assertTrue(NukeSchedule.linesAt(120, "A1").isEmpty());
    }

    @Test
    void theWhiteRisesAtOnceAndFadesAwayFastInOneSecond() {
        assertEquals(128, NukeSchedule.whiteRise(1));
        assertEquals(255, NukeSchedule.whiteRise(2));
        assertEquals(255, NukeSchedule.whiteRise(500));
        assertEquals(255, NukeSchedule.whiteFade(0));
        assertEquals(0, NukeSchedule.whiteFade(NukeSchedule.WHITE_FADE_TICKS));
        int last = 256;
        for (int tick = 0; tick <= NukeSchedule.WHITE_FADE_TICKS; tick++) {
            int alpha = NukeSchedule.whiteFade(tick);
            assertTrue(alpha < last || alpha == 0, "the fade only goes down");
            last = alpha;
        }
        assertEquals(70, NukeSchedule.WHITE_HOLD_TICKS);
    }

    @Test
    void chunksAreRequestedOverAboutThreeSecondsAndAtLeastOneATick() {
        assertEquals(1, NukeSchedule.preloadPerTick(0));
        assertEquals(1, NukeSchedule.preloadPerTick(60));
        assertEquals(6, NukeSchedule.preloadPerTick(330));
    }
}
