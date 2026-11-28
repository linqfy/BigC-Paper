package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level range, capacity, scheduled close and scheduled event rules of the tournament service (fake clock and levels). */
class ToffyTournamentRulesTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long MINUTE = 60_000L;
    private static final long T0 = 1_800_000_000_000L;
    private static final ToffyStoredLocation PLACE = new ToffyStoredLocation("world", 1, 64, 2, 0f, 0f);

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

    /** Fake level supplier: the level each player has right now. */
    private final Map<UUID, Double> levels = new HashMap<>();
    private final ManualClock clock = new ManualClock();
    private final List<String> logs = new ArrayList<>();
    private final ToffyTournamentService service =
        new ToffyTournamentService(new FakeStorage(), List.of(), clock, logs::add);

    private ToffyTournamentService.RegisterResult signUp(UUID id) {
        return service.register("copa", id, "jugador", levels.getOrDefault(id, 0.0));
    }

    @Test
    void levelRangeRefusesOutsideAndAcceptsTheBoundsInclusive() {
        service.create("copa");
        service.setLevelRange("copa", 40.0, 120.0);
        levels.put(ANA, 39.9);
        levels.put(BEN, 40.0);
        levels.put(CAR, 120.5);

        assertEquals(ToffyTournamentService.RegisterResult.LEVEL_OUT_OF_RANGE, signUp(ANA));
        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED, signUp(BEN));
        assertEquals(ToffyTournamentService.RegisterResult.LEVEL_OUT_OF_RANGE, signUp(CAR));
        levels.put(CAR, 120.0);
        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED, signUp(CAR));
    }

    @Test
    void levelBoundsAreValidatedAndAStarMeansNoLimit() {
        service.create("copa");

        assertThrows(IllegalArgumentException.class, () -> service.setLevelRange("copa", 50.0, 40.0));
        assertThrows(IllegalArgumentException.class, () -> service.setLevelRange("copa", -1.0, 10.0));
        assertTrue(service.setLevelRange("copa", null, 10.0));
        assertEquals(null, service.find("copa").orElseThrow().minLevel());
        assertFalse(service.find("copa").orElseThrow().allowsLevel(11.0));
        assertTrue(service.find("copa").orElseThrow().allowsLevel(0.0));
        assertTrue(service.setLevelRange("copa", null, null));
        assertTrue(service.find("copa").orElseThrow().allowsLevel(9999.0));
        assertFalse(service.setLevelRange("otra", 1.0, 2.0));
    }

    @Test
    void reachingTheCapacityClosesTheSignUpsAndLaterSignUpsAreRefused() {
        service.create("copa");
        service.setCapacity("copa", 2);

        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED, signUp(ANA));
        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED_CLOSED, signUp(BEN));
        assertFalse(service.find("copa").orElseThrow().open());
        assertEquals(ToffyTournamentService.RegisterResult.CLOSED, signUp(CAR));
        assertEquals(ToffyTournamentService.RegisterResult.ALREADY_REGISTERED, signUp(ANA));
    }

    @Test
    void settingACapacityBelowTheCountClosesAndZeroIsRefused() {
        service.create("copa");
        service.register("copa", ANA, "Ana", 0.0);
        service.register("copa", BEN, "Ben", 0.0);

        assertThrows(IllegalArgumentException.class, () -> service.setCapacity("copa", 0));
        assertTrue(service.setCapacity("copa", 2), "the change closed the sign-ups");
        assertFalse(service.find("copa").orElseThrow().open());
        assertFalse(service.setCapacity("copa", null), "no closing without a capacity");
    }

    @Test
    void aScheduledCloseFiresOnceAndClosesTheSignUps() {
        service.create("copa");
        service.scheduleClose("copa", T0 + 10 * MINUTE);

        assertTrue(service.dueCloses(T0 + 10 * MINUTE - 1).isEmpty());
        assertEquals(List.of("copa"), service.dueCloses(T0 + 10 * MINUTE));
        assertTrue(service.markClosed("copa"));
        assertFalse(service.find("copa").orElseThrow().open());
        assertEquals(null, service.find("copa").orElseThrow().closeAt());
        assertFalse(service.markClosed("copa"));
        assertFalse(service.cancelClose("copa"));
    }

    @Test
    void aScheduledCloseCanBeCancelledAndAReplacementReplacesIt() {
        service.create("copa");
        service.scheduleClose("copa", T0 + 10 * MINUTE);
        service.scheduleClose("copa", T0 + 20 * MINUTE);

        assertEquals(Long.valueOf(T0 + 20 * MINUTE), service.find("copa").orElseThrow().closeAt());
        assertTrue(service.cancelClose("copa"));
        assertTrue(service.dueCloses(T0 + 30 * MINUTE).isEmpty());
    }

    @Test
    void aCloseMoreThanFiveMinutesLateClosesTheTournamentOnLoadAndAFewMinutesLateStaysScheduled() {
        ToffyTournament late = new ToffyTournament("tarde", "Tarde");
        late.setCloseAt(T0 - 6 * MINUTE);
        ToffyTournament almost = new ToffyTournament("casi", "Casi");
        almost.setCloseAt(T0 - 4 * MINUTE);

        ToffyTournamentService loaded = new ToffyTournamentService(new FakeStorage(), List.of(late, almost), clock,
            logs::add);

        assertFalse(loaded.find("tarde").orElseThrow().open(), "the missed close still closes the sign-ups");
        assertEquals(null, loaded.find("tarde").orElseThrow().closeAt());
        assertTrue(loaded.find("casi").orElseThrow().open());
        assertEquals(List.of("casi"), loaded.dueCloses(T0));
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("Tarde"));
    }

    @Test
    void markClosedReportsOnlyTheCloseThatReallyClosedAnOpenTournament() {
        service.create("copa");
        service.scheduleClose("copa", T0 + MINUTE);
        assertTrue(service.markClosed("copa"), "an open tournament was closed");

        service.open("copa");
        service.scheduleClose("copa", T0 + MINUTE);
        service.close("copa");
        assertEquals(null, service.find("copa").orElseThrow().closeAt(), "closing by hand clears the schedule");
        assertTrue(service.dueCloses(T0 + 5 * MINUTE).isEmpty());
        assertFalse(service.markClosed("copa"));
    }

    @Test
    void aScheduledCloseOnAnAlreadyClosedTournamentIsClearedWithoutAnnouncement() {
        service.create("copa");
        service.close("copa");
        service.scheduleClose("copa", T0 + MINUTE);

        assertEquals(List.of("copa"), service.dueCloses(T0 + MINUTE));
        assertFalse(service.markClosed("copa"), "nothing was closed, so nothing is announced");
        assertEquals(null, service.find("copa").orElseThrow().closeAt());
    }

    @Test
    void reachingTheCapacityClearsTheScheduledClose() {
        service.create("copa");
        service.setCapacity("copa", 2);
        service.scheduleClose("copa", T0 + 10 * MINUTE);

        signUp(ANA);
        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED_CLOSED, signUp(BEN));
        assertEquals(null, service.find("copa").orElseThrow().closeAt());
        assertTrue(service.dueCloses(T0 + 20 * MINUTE).isEmpty());
    }

    @Test
    void lowerCapacityClosesAndClearsTheScheduledClose() {
        service.create("copa");
        service.register("copa", ANA, "Ana", 0.0);
        service.register("copa", BEN, "Ben", 0.0);
        service.scheduleClose("copa", T0 + 10 * MINUTE);

        assertTrue(service.setCapacity("copa", 2));
        assertEquals(null, service.find("copa").orElseThrow().closeAt());
    }

    @Test
    void aScheduledEventFiresAtItsTimeWithWarningsFiveAndOneMinuteBeforeOnce() {
        service.create("copa");
        long at = T0 + 10 * MINUTE;
        service.scheduleEvent("copa", "koth", 30, at);
        List<ToffyTournamentService.Warning> sent = new ArrayList<>();

        for (long now = T0; now <= at + MINUTE; now += 1_000L) {
            clock.now = now;
            for (ToffyTournamentService.Warning warning : service.dueEventWarnings(now)) {
                sent.add(warning);
                service.markEventWarned(warning.tournament(), warning.minutes());
            }
        }

        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 5),
            new ToffyTournamentService.Warning("copa", 1)), sent);
        assertTrue(service.dueEvents(at - 1).isEmpty());
        assertEquals(List.of("copa"), service.dueEvents(at));
        assertTrue(service.markEventFired("copa"));
        assertTrue(service.dueEvents(at + MINUTE).isEmpty());
        assertEquals(null, service.find("copa").orElseThrow().eventAt());
    }

    @Test
    void aScheduledEventStartedLessThanFiveMinutesAheadSkipsTheWarningThatPassed() {
        service.create("copa");
        long at = T0 + 3 * MINUTE;
        service.scheduleEvent("copa", "spleef", null, at);

        assertTrue(service.dueEventWarnings(T0).isEmpty());
        assertEquals(List.of(new ToffyTournamentService.Warning("copa", 1)), service.dueEventWarnings(at - MINUTE));
    }

    @Test
    void aScheduledEventReplacesCancelsAndLeavesTheTeleportAlone() {
        service.create("copa");
        service.setPlace("copa", PLACE);
        service.schedule("copa", T0 + 5 * MINUTE);

        assertEquals(Optional.empty(), service.scheduleEvent("copa", "koth", 30, T0 + 10 * MINUTE));
        assertEquals(Optional.of(T0 + 10 * MINUTE), service.scheduleEvent("copa", "tnttag", null, T0 + 20 * MINUTE));
        assertEquals("tnttag", service.find("copa").orElseThrow().eventType());
        assertEquals(Long.valueOf(T0 + 5 * MINUTE), service.find("copa").orElseThrow().scheduledAt());
        assertTrue(service.cancelEvent("copa"));
        assertFalse(service.cancelEvent("copa"));
    }

    @Test
    void anEventMoreThanFiveMinutesLateIsDroppedOnLoad() {
        ToffyTournament late = new ToffyTournament("tarde", "Tarde");
        late.setEvent("koth", null, T0 - 6 * MINUTE);
        ToffyTournament almost = new ToffyTournament("casi", "Casi");
        almost.setEvent("koth", 30, T0 - 4 * MINUTE);

        ToffyTournamentService loaded = new ToffyTournamentService(new FakeStorage(), List.of(late, almost), clock,
            logs::add);

        assertEquals(null, loaded.find("tarde").orElseThrow().eventAt());
        assertEquals(List.of("casi"), loaded.dueEvents(T0));
        assertEquals(30, loaded.find("casi").orElseThrow().eventLobby());
    }
}
