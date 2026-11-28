package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyGroupServiceTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAT = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final ToffyStoredLocation PLACE = new ToffyStoredLocation("world", 1, 64, 2, 90f, 10f);

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

    private final FakeStorage storage = new FakeStorage();
    private final ToffyTournamentService service = new ToffyTournamentService(storage, new ArrayList<>());

    private void tennis() {
        service.create("tenis");
        service.createGroup("tenis", "potencia", true);
        service.createGroup("tenis", "velocidad", true);
    }

    @Test
    void createsGroupsInOrderAndRejectsDuplicatesAndReservedNames() {
        tennis();

        assertEquals(List.of("potencia", "velocidad"), service.groupKeys("tenis"));
        assertThrows(IllegalStateException.class, () -> service.createGroup("tenis", "POTENCIA", false));
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("tenis", "salir", false));
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("tenis", "con espacio", false));
        assertThrows(IllegalArgumentException.class, () -> service.createGroup("nada", "x", false));
        assertEquals(List.of("potencia", "velocidad"), service.groupKeys("tenis"));
    }

    @Test
    void groupsAreClosedUnlessTheyAreCreatedOpen() {
        service.create("tenis");

        ToffyGroup closed = service.createGroup("tenis", "reservado", false);

        assertFalse(closed.open());
        assertEquals(ToffyTournamentService.Change.CHANGED, service.setGroupOpen("tenis", "reservado", true));
        assertEquals(ToffyTournamentService.Change.UNCHANGED, service.setGroupOpen("tenis", "reservado", true));
        assertEquals(ToffyTournamentService.Change.UNKNOWN, service.setGroupOpen("tenis", "nada", true));
    }

    @Test
    void joinsAnOpenGroupAndSignsThePlayerUpToTheTournament() {
        tennis();

        ToffyTournamentService.Placement placement = service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0,
            ToffyTournamentService.GroupMode.PLAYER);

        assertEquals(ToffyTournamentService.GroupResult.ASSIGNED, placement.result());
        assertTrue(placement.registered());
        assertEquals(List.of("potencia"), service.groupOf("tenis", ANA).stream().toList());
        assertTrue(service.find("tenis").orElseThrow().participants().containsKey(ANA));
    }

    @Test
    void refusesAClosedGroupWithoutSigningThePlayerUp() {
        service.create("tenis");
        service.createGroup("tenis", "staff", false);

        ToffyTournamentService.Placement placement = service.placeInGroup("tenis", "staff", ANA, "Ana", 50.0,
            ToffyTournamentService.GroupMode.PLAYER);

        assertEquals(ToffyTournamentService.GroupResult.GROUP_CLOSED, placement.result());
        assertFalse(placement.registered());
        assertTrue(service.find("tenis").orElseThrow().participants().isEmpty());
    }

    @Test
    void refusesAFullGroupAndAFullGroupDoesNotTakeMoreMembers() {
        tennis();
        service.setGroupCapacity("tenis", "potencia", 1);
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);

        ToffyTournamentService.Placement placement = service.placeInGroup("tenis", "potencia", BEN, "Ben", 50.0,
            ToffyTournamentService.GroupMode.PLAYER);

        assertEquals(ToffyTournamentService.GroupResult.GROUP_FULL, placement.result());
        assertFalse(service.find("tenis").orElseThrow().participants().containsKey(BEN));
        assertEquals(1, service.group("tenis", "potencia").orElseThrow().members().size());
    }

    @Test
    void refusesALevelOutsideTheGroupRangeAndAnUnknownLevel() {
        tennis();
        service.setGroupLevelRange("tenis", "velocidad", 40.0, 80.0);

        assertEquals(ToffyTournamentService.GroupResult.GROUP_LEVEL_OUT_OF_RANGE, service.placeInGroup("tenis",
            "velocidad", ANA, "Ana", 95.0, ToffyTournamentService.GroupMode.PLAYER).result());
        assertEquals(ToffyTournamentService.GroupResult.LEVEL_UNKNOWN, service.placeInGroup("tenis",
            "velocidad", BEN, "Ben", null, ToffyTournamentService.GroupMode.STAFF).result());
        assertEquals(ToffyTournamentService.GroupResult.ASSIGNED, service.placeInGroup("tenis", "velocidad", ANA,
            "Ana", 60.0, ToffyTournamentService.GroupMode.PLAYER).result());
    }

    @Test
    void aPlayerIsInAtMostOneGroupAndMovingSaysSo() {
        tennis();
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);

        ToffyTournamentService.Placement moved = service.placeInGroup("tenis", "velocidad", ANA, "Ana", 50.0,
            ToffyTournamentService.GroupMode.PLAYER);

        assertEquals(ToffyTournamentService.GroupResult.MOVED, moved.result());
        assertFalse(moved.registered());
        assertEquals(0, service.group("tenis", "potencia").orElseThrow().members().size());
        assertEquals(List.of(ANA), List.copyOf(service.group("tenis", "velocidad").orElseThrow().members()));
        assertEquals(ToffyTournamentService.GroupResult.ALREADY_IN_GROUP, service.placeInGroup("tenis", "velocidad",
            ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER).result());
    }

    @Test
    void staffPlacesIgnoreTheOpenFlagButNotTheRulesUnlessForced() {
        tennis();
        service.setGroupOpen("tenis", "potencia", false);
        service.setGroupLevelRange("tenis", "potencia", 0.0, 10.0);

        assertEquals(ToffyTournamentService.GroupResult.GROUP_LEVEL_OUT_OF_RANGE, service.placeInGroup("tenis",
            "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.STAFF).result());
        assertEquals(ToffyTournamentService.GroupResult.ASSIGNED, service.placeInGroup("tenis", "potencia", ANA,
            "Ana", 50.0, ToffyTournamentService.GroupMode.FORCED).result());
        assertTrue(service.find("tenis").orElseThrow().participants().containsKey(ANA));
    }

    @Test
    void theTournamentRulesApplyToNewPlayersOfAGroup() {
        tennis();
        service.setCapacity("tenis", 1);
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);

        ToffyTournamentService.Placement full = service.placeInGroup("tenis", "velocidad", BEN, "Ben", 50.0,
            ToffyTournamentService.GroupMode.STAFF);

        assertEquals(ToffyTournamentService.GroupResult.TOURNAMENT_CLOSED, full.result());
    }

    @Test
    void deletingAGroupKeepsItsMembersSignedUp() {
        tennis();
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);

        ToffyTournamentService.GroupDeletion deletion = service.deleteGroup("tenis", "potencia");

        assertTrue(deletion.deleted());
        assertFalse(deletion.eventCancelled());
        assertEquals(Set.of(ANA), deletion.members());
        ToffyTournament tournament = service.find("tenis").orElseThrow();
        assertTrue(tournament.participants().containsKey(ANA));
        assertTrue(tournament.groupOf(ANA).isEmpty());
        assertFalse(service.deleteGroup("tenis", "potencia").deleted());
    }

    @Test
    void deletingTheGroupOfAScheduledEventCancelsThatEvent() {
        tennis();
        service.scheduleEvent("tenis", "lifesteal", null, 1_900_000_000_000L, "potencia");

        ToffyTournamentService.GroupDeletion other = service.deleteGroup("tenis", "velocidad");
        assertFalse(other.eventCancelled());
        assertEquals("potencia", service.find("tenis").orElseThrow().eventGroup());

        ToffyTournamentService.GroupDeletion deletion = service.deleteGroup("tenis", "potencia");
        assertTrue(deletion.eventCancelled());
        ToffyTournament tournament = service.find("tenis").orElseThrow();
        assertEquals(null, tournament.eventAt());
        assertEquals(null, tournament.eventGroup());
    }

    @Test
    void leavingTheTournamentOrTheGroupUpdatesBoth() {
        tennis();
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        service.placeInGroup("tenis", "potencia", BEN, "Ben", 50.0, ToffyTournamentService.GroupMode.PLAYER);

        assertTrue(service.leaveGroup("tenis", ANA));
        assertFalse(service.leaveGroup("tenis", ANA));
        assertTrue(service.find("tenis").orElseThrow().participants().containsKey(ANA));

        service.removeParticipant("tenis", BEN);
        assertTrue(service.group("tenis", "potencia").orElseThrow().members().isEmpty());
    }

    @Test
    void autoAssignOnlyPlacesUngroupedPlayersUnlessReshuffled() {
        tennis();
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        service.placeInGroup("tenis", "potencia", BEN, "Ben", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        service.placeInGroup("tenis", "velocidad", CAT, "Cat", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        UUID dan = UUID.fromString("00000000-0000-0000-0000-00000000000d");
        service.register("tenis", dan, "Dan", 50.0);
        Map<UUID, Double> levels = Map.of(ANA, 50.0, BEN, 50.0, CAT, 50.0, dan, 50.0);

        ToffyTournamentService.AutoResult result = service.autoAssign("tenis", true, false, levels, new Random(1));

        // potencia has 2 members and velocidad 1: the only ungrouped player goes to the smaller one, and nobody moves
        assertEquals(Map.of(dan, "velocidad"), result.assigned());
        assertEquals(0, result.ungrouped());
        assertEquals(2, service.group("tenis", "potencia").orElseThrow().members().size());
        assertEquals(2, service.group("tenis", "velocidad").orElseThrow().members().size());
    }

    @Test
    void autoAssignWithReshuffleMovesEveryoneWithALevelAndSkipsOfflinePlayers() {
        tennis();
        service.placeInGroup("tenis", "potencia", ANA, "Ana", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        service.placeInGroup("tenis", "potencia", BEN, "Ben", 50.0, ToffyTournamentService.GroupMode.PLAYER);
        Map<UUID, Double> levels = Map.of(ANA, 50.0, BEN, 50.0);

        ToffyTournamentService.AutoResult result = service.autoAssign("tenis", true, true, levels, new Random(7));

        assertEquals(2, result.assigned().size());
        ToffyTournament tournament = service.find("tenis").orElseThrow();
        assertEquals(1, tournament.group("potencia").orElseThrow().members().size());
        assertEquals(1, tournament.group("velocidad").orElseThrow().members().size());
        assertEquals(2, tournament.groups().stream().mapToInt(group -> group.members().size()).sum());
    }

    @Test
    void mandatoryFlagAndPersistenceWriteThroughTheStorage() {
        tennis();
        int before = storage.saves;

        assertTrue(service.setGroupMandatory("tenis", true));
        assertTrue(service.find("tenis").orElseThrow().groupMandatory());
        assertTrue(storage.saves > before);
        assertFalse(service.setGroupMandatory("nada", true));
    }

    @Test
    void groupLevelInvalidRangesAreRejected() {
        tennis();

        assertThrows(IllegalArgumentException.class, () -> service.setGroupLevelRange("tenis", "potencia", 80.0, 40.0));
        assertThrows(IllegalArgumentException.class, () -> service.setGroupCapacity("tenis", "potencia", 0));
    }

    @Test
    void autoAssignLeavesOutThePlayersOutsideTheRangeOfTheTournament() {
        tennis();
        service.register("tenis", ANA, "Ana", 50.0);
        service.register("tenis", BEN, "Ben", 50.0);
        service.setLevelRange("tenis", 40.0, 100.0);
        Map<UUID, Double> levels = Map.of(ANA, 50.0, BEN, 150.0);

        ToffyTournamentService.AutoResult result = service.autoAssign("tenis", true, false, levels, new Random(2));

        assertEquals(1, result.assigned().size());
        assertTrue(result.assigned().containsKey(ANA));
        assertEquals(1, result.outOfTournamentRange());
        assertEquals(1, result.ungrouped());
    }
}
