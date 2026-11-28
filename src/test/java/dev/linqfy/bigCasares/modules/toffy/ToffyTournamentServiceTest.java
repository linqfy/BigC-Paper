package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyTournamentServiceTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final ToffyStoredLocation SPAWN = new ToffyStoredLocation("world", 1, 64, 2, 90f, 10f);
    private static final ToffyStoredLocation ARENA = new ToffyStoredLocation("arena", 100, 70, 100, 0f, 0f);

    static final class FakeStorage implements ToffyTournamentStorage {
        int saves;
        boolean failing;

        @Override
        public List<ToffyTournament> load() {
            return List.of();
        }

        @Override
        public void save(Collection<ToffyTournament> tournaments) throws IOException {
            if (failing) {
                throw new IOException("disk full");
            }
            saves++;
        }
    }

    private final FakeStorage storage = new FakeStorage();
    private final ToffyTournamentService service = new ToffyTournamentService(storage, new ArrayList<>());

    @Test
    void createsTournamentsOpenAndKeepsTheTypedName() {
        ToffyTournament created = service.create("Copa_1");

        assertEquals("copa_1", created.key());
        assertEquals("Copa_1", created.displayName());
        assertTrue(created.open());
        assertEquals(1, storage.saves);
        assertTrue(service.find("COPA_1").isPresent());
    }

    @Test
    void rejectsDuplicatesAndInvalidNames() {
        service.create("copa");

        assertThrows(IllegalStateException.class, () -> service.create("COPA"));
        for (String bad : new String[] {"", "  ", "con espacio", "ñandu", "a.b", "x".repeat(33)}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(bad), bad);
        }
        assertEquals(1, service.list().size());
        assertTrue(service.find("a b").isEmpty());
    }

    @Test
    void theNamesOfTheAnotarSubcommandsAreReserved() {
        assertThrows(IllegalArgumentException.class, () -> service.create("salir"));
        assertThrows(IllegalArgumentException.class, () -> service.create("Estado"));
        assertTrue(service.list().isEmpty());
        assertEquals("estadio", service.create("estadio").key());
    }

    @Test
    void registerResultsFollowTheTournamentState() {
        assertEquals(ToffyTournamentService.RegisterResult.UNKNOWN_TOURNAMENT, service.register("nada", ANA, "Ana"));
        service.create("copa");

        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED, service.register("copa", ANA, "Ana"));
        assertEquals(ToffyTournamentService.RegisterResult.ALREADY_REGISTERED, service.register("copa", ANA, "Ana"));
        service.close("copa");
        assertEquals(ToffyTournamentService.RegisterResult.CLOSED, service.register("copa", BEN, "Ben"));
        assertEquals(ToffyTournamentService.RegisterResult.ALREADY_REGISTERED, service.register("copa", ANA, "Ana"));
        service.open("copa");
        assertEquals(ToffyTournamentService.RegisterResult.REGISTERED, service.register("copa", BEN, "Ben"));
        assertEquals(List.of(ANA, BEN), List.copyOf(service.find("copa").orElseThrow().participants().keySet()));
    }

    @Test
    void closeAndOpenReportWhatChanged() {
        service.create("copa");

        assertEquals(ToffyTournamentService.Change.CHANGED, service.close("copa"));
        assertEquals(ToffyTournamentService.Change.UNCHANGED, service.close("copa"));
        assertEquals(ToffyTournamentService.Change.CHANGED, service.open("copa"));
        assertEquals(ToffyTournamentService.Change.UNCHANGED, service.open("copa"));
        assertEquals(ToffyTournamentService.Change.UNKNOWN, service.close("otra"));
    }

    @Test
    void deleteAndRemoveParticipant() {
        service.create("copa");
        service.register("copa", ANA, "Ana");

        assertTrue(service.removeParticipant("copa", ANA));
        assertFalse(service.removeParticipant("copa", ANA));
        assertTrue(service.delete("copa"));
        assertFalse(service.delete("copa"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void countsSavedPlacesSoADeleteCanBeRefused() {
        service.create("copa");
        service.register("copa", ANA, "Ana");
        service.register("copa", BEN, "Ben");

        assertEquals(0, service.savedPlaces("copa"));
        service.recordDeparture("copa", ANA, SPAWN);
        assertEquals(1, service.savedPlaces("COPA"));
        assertEquals(0, service.savedPlaces("otra"));
    }

    @Test
    void theFirstSavedLocationIsKept() {
        service.create("copa");

        assertTrue(service.recordDeparture("copa", ANA, SPAWN));
        assertFalse(service.recordDeparture("copa", ANA, ARENA));
        assertEquals(Optional.of(SPAWN), service.savedLocation("copa", ANA));
        assertFalse(service.recordDeparture("otra", ANA, SPAWN));
    }

    @Test
    void takeReturnRemovesTheLocationAndAllowsANewDeparture() {
        service.create("copa");
        service.recordDeparture("copa", ANA, SPAWN);

        assertEquals(Optional.of(SPAWN), service.takeReturn("copa", ANA));
        assertEquals(Optional.empty(), service.takeReturn("copa", ANA));
        assertTrue(service.recordDeparture("copa", ANA, ARENA));
    }

    @Test
    void pendingReturnsNeedASavedLocationAndAreClearedWithIt() {
        service.create("copa");
        service.create("liga");

        assertFalse(service.markPending("copa", ANA));
        service.recordDeparture("copa", ANA, SPAWN);
        service.recordDeparture("liga", ANA, ARENA);
        assertTrue(service.markPending("copa", ANA));
        assertFalse(service.markPending("copa", ANA));
        assertTrue(service.markPending("liga", ANA));
        assertTrue(service.isPending("copa", ANA));

        assertEquals(List.of(new ToffyTournamentService.PendingReturn("copa", SPAWN),
            new ToffyTournamentService.PendingReturn("liga", ARENA)), service.pendingReturns(ANA));
        assertTrue(service.pendingReturns(BEN).isEmpty());

        assertTrue(service.clearReturn("copa", ANA));
        assertFalse(service.isPending("copa", ANA));
        assertEquals(List.of(new ToffyTournamentService.PendingReturn("liga", ARENA)), service.pendingReturns(ANA));
    }

    @Test
    void messageOverridesCanBeSetAndReset() {
        service.create("copa");

        assertTrue(service.setMessage("copa", "anotado", "Hola"));
        assertEquals("Hola", service.find("copa").orElseThrow().message("anotado"));
        assertTrue(service.resetMessage("copa", "anotado"));
        assertFalse(service.resetMessage("copa", "anotado"));
        assertEquals(null, service.find("copa").orElseThrow().message("anotado"));
        assertFalse(service.setMessage("otra", "anotado", "x"));
    }

    @Test
    void everyChangeIsSavedAndAFailureIsReported() {
        service.create("copa");
        int before = storage.saves;
        service.register("copa", ANA, "Ana");
        service.close("copa");
        service.recordDeparture("copa", ANA, SPAWN);
        assertEquals(before + 3, storage.saves);

        storage.failing = true;
        assertThrows(UncheckedIOException.class, () -> service.open("copa"));
    }
}
