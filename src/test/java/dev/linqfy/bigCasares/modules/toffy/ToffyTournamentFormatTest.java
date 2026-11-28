package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToffyTournamentFormatTest {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final long NOW = Instant.parse("2026-11-25T10:00:00Z").toEpochMilli();

    @Test
    void theConfiguredFormatsParseAndShow() {
        long at = ToffyTournamentTime.resolve("18:30", "25/12/2030", NOW, ZONE, "HH:mm", "dd/MM/yyyy");

        assertEquals(Instant.parse("2030-12-25T21:30:00Z").toEpochMilli(), at);
        assertEquals("25/12/2030 18:30", ToffyTournamentTime.dateTime(at, ZONE, "HH:mm", "dd/MM/yyyy"));
    }

    @Test
    void aDayWithoutYearIsStillAcceptedWhenTheFormatHasOne() {
        long at = ToffyTournamentTime.resolve("10:00", "24/12", NOW, ZONE, "HH:mm", "dd/MM/yyyy");

        assertEquals(Instant.parse("2026-12-24T13:00:00Z").toEpochMilli(), at);
    }

    @Test
    void theDefaultFormatsKeepAcceptingOneDigitHours() {
        long at = ToffyTournamentTime.resolve("9:05", null, NOW, ZONE);

        assertEquals(Instant.parse("2026-11-25T12:05:00Z").toEpochMilli(), at);
    }

    @Test
    void impossibleDatesAndHoursAreRefusedInTheConfiguredFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> ToffyTournamentTime.resolve("10:00", "31/02/2030", NOW, ZONE, "HH:mm", "dd/MM/yyyy"));
        assertThrows(IllegalArgumentException.class,
            () -> ToffyTournamentTime.resolve("24:00", null, NOW, ZONE, "HH:mm", "dd/MM/yyyy"));
    }
}
