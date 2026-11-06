package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.MapPlayerStrings;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionRecordsTest {
    private final MapPlayerStrings store = new MapPlayerStrings();
    private final MissionRecords records = new MissionRecords(store);
    private final FakePlayer juan = new FakePlayer("juan");
    private static final Instant THIS_WEEK = Instant.parse("2026-11-09T00:00:00Z");

    @Test
    void theWeekTotalAddsUpAndStartsAgainNextWeek() {
        assertEquals(0, records.weekTotal(juan.player, THIS_WEEK));
        records.addClaimed(juan.player, THIS_WEEK, 400);
        records.addClaimed(juan.player, THIS_WEEK, 250);
        records.addClaimed(juan.player, THIS_WEEK, 0);
        assertEquals(650, records.weekTotal(juan.player, THIS_WEEK));
        Instant next = THIS_WEEK.plusSeconds(7 * 86_400);
        assertEquals(0, records.weekTotal(juan.player, next), "a new week starts from nothing");
        records.addClaimed(juan.player, next, 100);
        assertEquals(100, records.weekTotal(juan.player, next));
    }

    @Test
    void theStreakGrowsDayByDayAndBreaksAfterAMissedDay() {
        LocalDate monday = LocalDate.parse("2026-11-02");
        assertEquals(0, records.streak(juan.player, monday));
        records.dailiesDone(juan.player, monday);
        records.dailiesDone(juan.player, monday);
        assertEquals(1, records.streak(juan.player, monday), "once per day");
        records.dailiesDone(juan.player, monday.plusDays(1));
        assertEquals(2, records.streak(juan.player, monday.plusDays(1)));
        assertEquals(2, records.streak(juan.player, monday.plusDays(2)), "still alive the next day, before claiming");
        assertEquals(0, records.streak(juan.player, monday.plusDays(3)), "a day missed: gone");
        records.dailiesDone(juan.player, monday.plusDays(3));
        assertEquals(1, records.streak(juan.player, monday.plusDays(3)), "and starts again");
    }

    @Test
    void unreadableValuesCountAsNothing() {
        store.set(juan.player, MissionRecords.WEEK_KEY, "hola|mundo");
        store.set(juan.player, MissionRecords.STREAK_KEY, "ayer|muchos");
        assertEquals(0, records.weekTotal(juan.player, THIS_WEEK));
        assertEquals(0, records.streak(juan.player, LocalDate.parse("2026-11-02")));
        records.dailiesDone(juan.player, LocalDate.parse("2026-11-02"));
        assertEquals(1, records.streak(juan.player, LocalDate.parse("2026-11-02")));
    }
}
