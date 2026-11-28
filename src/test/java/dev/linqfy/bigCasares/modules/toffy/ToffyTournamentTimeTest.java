package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToffyTournamentTimeTest {
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long NOW = at("2026-11-25T10:00:00Z");

    private static long at(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    void anHourWithoutDateIsTodayWhenItHasNotPassedAndTomorrowWhenItHas() {
        assertEquals(at("2026-11-25T12:30:00Z"), ToffyTournamentTime.resolve("12:30", null, NOW, ZONE));
        assertEquals(at("2026-11-26T09:00:00Z"), ToffyTournamentTime.resolve("09:00", null, NOW, ZONE));
        assertEquals(at("2026-11-26T10:00:00Z"), ToffyTournamentTime.resolve("10:00", null, NOW, ZONE),
            "the current minute already passed, so it is tomorrow");
        assertEquals(at("2026-11-26T09:05:00Z"), ToffyTournamentTime.resolve("9:05", null, NOW, ZONE),
            "one-digit hours are accepted");
    }

    @Test
    void aDateIsThisYearWhenItHasNotPassedAndNextYearWhenItHas() {
        assertEquals(at("2026-12-24T08:15:00Z"), ToffyTournamentTime.resolve("08:15", "24/12", NOW, ZONE));
        assertEquals(at("2026-11-25T10:30:00Z"), ToffyTournamentTime.resolve("10:30", "25/11", NOW, ZONE));
        assertEquals(at("2027-11-25T08:15:00Z"), ToffyTournamentTime.resolve("08:15", "25/11", NOW, ZONE));
        assertEquals(at("2027-01-03T08:15:00Z"),
            ToffyTournamentTime.resolve("08:15", "3/1", at("2026-12-31T23:00:00Z"), ZONE),
            "a date of this year that is already past goes to the next year");
    }

    @Test
    void theLeapDayGoesToTheNextLeapYearWhenItIsNotALeapYear() {
        assertEquals(at("2028-02-29T00:00:00Z"), ToffyTournamentTime.resolve("00:00", "29/02", NOW, ZONE));
    }

    @Test
    void invalidHoursAndDatesAreRefused() {
        for (String hour : new String[] {"25:00", "12:7x", "24:00", "12:60", "1230", "", "12:5", "12:345"}) {
            assertThrows(IllegalArgumentException.class, () -> ToffyTournamentTime.resolve(hour, null, NOW, ZONE),
                hour);
        }
        assertThrows(IllegalArgumentException.class, () -> ToffyTournamentTime.resolve(null, null, NOW, ZONE));
        for (String day : new String[] {"31/02", "00/05", "1/13", "ab/cd", "2411", "24/12/2026"}) {
            assertThrows(IllegalArgumentException.class, () -> ToffyTournamentTime.resolve("10:00", day, NOW, ZONE),
                day);
        }
    }

    @Test
    void formatsInTheGivenZone() {
        long at = at("2026-03-05T21:07:00Z");

        assertEquals("21:07", ToffyTournamentTime.hour(at, ZONE));
        assertEquals("05/03/2026", ToffyTournamentTime.date(at, ZONE));
        assertEquals("05/03/2026 21:07", ToffyTournamentTime.dateTime(at, ZONE));
        assertEquals("05/03/2026 18:07", ToffyTournamentTime.dateTime(at, ZoneId.of("America/Argentina/Buenos_Aires")));
    }
}
