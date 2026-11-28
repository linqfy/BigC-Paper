package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyTournamentScheduleTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final ToffyStoredLocation PLACE = new ToffyStoredLocation("world", 1, 64, 2, 90f, 10f);
    private static final long MINUTE = 60_000L;
    private static final long T0 = 1_800_000_000_000L;

    static final class ManualClock implements LongSupplier {
        long now = T0;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    static final class FakeStorage implements ToffyTournamentStorage {
        int saves;

        @Override
        public List<ToffyTournament> load() {
            return List.of();
        }

        @Override
        public void save(Collection<ToffyTournament> tournaments) throws IOException {
            saves++;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final FakeStorage storage = new FakeStorage();
    private final List<String> logs = new ArrayList<>();
    private final ToffyTournamentService service =
        new ToffyTournamentService(storage, List.of(), clock, logs::add);

    private void copaWithPlace() {
        service.create("copa");
        assertTrue(service.setPlace("copa", PLACE));
    }

    @Test
    void aScheduleNeedsAPlaceAndAKnownTournament() {
        service.create("copa");

        assertThrows(IllegalStateException.class, () -> service.schedule("copa", T0 + MINUTE));
        assertThrows(IllegalArgumentException.class, () -> service.schedule("otra", T0 + MINUTE));
        assertFalse(service.setPlace("otra", PLACE));
        assertEquals(null, service.find("copa").orElseThrow().scheduledAt());
    }

    @Test
    void theTeleportIsDueAtItsTimeNotBefore() {
        copaWithPlace();
        long at = T0 + 10 * MINUTE;
        service.schedule("copa", at);

        assertTrue(service.dueSchedules(at - 1).isEmpty());
        assertEquals(List.of("copa"), service.dueSchedules(at));
        assertEquals(List.of("copa"), service.dueSchedules(at + MINUTE), "still due until it runs");
        assertTrue(service.markFired("copa"));
        assertTrue(service.dueSchedules(at + MINUTE).isEmpty());
        assertEquals(Optional.empty(), Optional.ofNullable(service.find("copa").orElseThrow().scheduledAt()));
        assertFalse(service.markFired("copa"));
    }

    @Test
    void warningsFiveAndOneMinutesBeforeFireOnceEach() {
        copaWithPlace();
        long at = T0 + 10 * MINUTE;
        service.schedule("copa", at);
        List<ToffyTournamentService.Warning> sent = new ArrayList<>();

        for (long now = T0; now <= at + MINUTE; now += 1_000L) {
            clock.now = now;
            for (ToffyTournamentService.Warning warning : service.dueWarnings(now)) {
                sent.add(warning);
                service.markWarned(warning.tournament(), warning.minutes());
            }
        }

        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 5),
            new ToffyTournamentService.Warning("copa", 1)), sent);
        assertTrue(service.dueWarnings(at - 1).isEmpty());
    }

    @Test
    void aWarningIsNotSentOnceTheTeleportIsDue() {
        copaWithPlace();
        long at = T0 + 3 * MINUTE;
        service.schedule("copa", at);

        assertTrue(service.dueWarnings(at).isEmpty());
        assertTrue(service.dueWarnings(at + 1).isEmpty());
    }

    @Test
    void aScheduleCreatedLessThanFiveMinutesAheadSkipsTheWarningsThatAlreadyPassed() {
        copaWithPlace();
        long at = T0 + 3 * MINUTE;
        service.schedule("copa", at);

        assertTrue(service.dueWarnings(T0).isEmpty(), "the five minute warning would be late");
        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 1)), service.dueWarnings(at - MINUTE));
    }

    @Test
    void replacingAScheduleSaysWhatWasReplacedAndCancellingRemovesIt() {
        copaWithPlace();
        long first = T0 + 10 * MINUTE;
        long second = T0 + 20 * MINUTE;

        assertEquals(Optional.empty(), service.schedule("copa", first));
        assertEquals(Optional.of(first), service.schedule("copa", second));
        assertEquals(second, service.find("copa").orElseThrow().scheduledAt());
        assertTrue(service.dueSchedules(first).isEmpty());

        assertTrue(service.cancelSchedule("copa"));
        assertFalse(service.cancelSchedule("copa"));
        assertTrue(service.dueSchedules(second + MINUTE).isEmpty());
        assertFalse(service.cancelSchedule("otra"));
    }

    @Test
    void replacingAScheduleWarnsAgainForTheNewTime() {
        copaWithPlace();
        service.schedule("copa", T0 + 10 * MINUTE);
        service.markWarned("copa", 5);

        service.schedule("copa", T0 + 30 * MINUTE);

        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 5)),
            service.dueWarnings(T0 + 25 * MINUTE));
    }

    @Test
    void clearingThePlaceOrDeletingTheTournamentRemovesTheSchedule() {
        copaWithPlace();
        service.schedule("copa", T0 + 10 * MINUTE);

        assertTrue(service.clearPlace("copa"));
        assertFalse(service.clearPlace("copa"));
        assertEquals(null, service.find("copa").orElseThrow().scheduledAt());
        assertTrue(service.dueSchedules(T0 + 20 * MINUTE).isEmpty());

        service.setPlace("copa", PLACE);
        service.schedule("copa", T0 + 10 * MINUTE);
        assertTrue(service.delete("copa"));
        assertTrue(service.dueSchedules(T0 + 20 * MINUTE).isEmpty());
        assertTrue(service.list().isEmpty());
    }

    @Test
    void schedulesMoreThanFiveMinutesLateAreDroppedOnLoadWithAWarning() {
        ToffyTournament late = new ToffyTournament("tarde", "Tarde");
        late.setPlaces(List.of(PLACE));
        late.setScheduledAt(T0 - 6 * MINUTE);
        ToffyTournament almost = new ToffyTournament("casi", "Casi");
        almost.setPlaces(List.of(PLACE));
        almost.setScheduledAt(T0 - 4 * MINUTE);
        FakeStorage saved = new FakeStorage();

        ToffyTournamentService loaded = new ToffyTournamentService(saved, List.of(late, almost), clock, logs::add);

        assertEquals(null, loaded.find("tarde").orElseThrow().scheduledAt());
        assertEquals(List.of("casi"), loaded.dueSchedules(T0));
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("Tarde"));
        assertEquals(1, saved.saves, "the dropped schedule is saved");
    }

    @Test
    void aScheduleLoadedWithLessThanFiveMinutesLeftOnlyWarnsForTheRemainingMarks() {
        ToffyTournament copa = new ToffyTournament("copa", "Copa");
        copa.setPlaces(List.of(PLACE));
        copa.setScheduledAt(T0 + 3 * MINUTE);

        ToffyTournamentService loaded = new ToffyTournamentService(new FakeStorage(), List.of(copa), clock,
            logs::add);

        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 1)),
            loaded.dueWarnings(T0 + 2 * MINUTE));
        assertTrue(logs.isEmpty());
    }

    @Test
    void theSavedPlaceAndPendingStateDoNotChangeWhenASchedulePasses() {
        copaWithPlace();
        service.recordDeparture("copa", ANA, PLACE);
        service.schedule("copa", T0 + MINUTE);

        service.markFired("copa");

        assertEquals(List.of(PLACE), service.find("copa").orElseThrow().places());
        assertEquals(PLACE, service.savedLocation("copa", ANA).orElseThrow());
    }
}
