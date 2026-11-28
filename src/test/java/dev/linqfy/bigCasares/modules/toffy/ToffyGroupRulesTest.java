package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyGroupRulesTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final ToffyStoredLocation TOURNAMENT_PLACE = new ToffyStoredLocation("world", 0, 64, 0, 0f, 0f);
    private static final ToffyStoredLocation GROUP_PLACE = new ToffyStoredLocation("arena", 9, 70, 9, 0f, 0f);

    private static ToffyGroup group(String key, boolean open) {
        return new ToffyGroup(key, key, open);
    }

    @Test
    void aClosedGroupRefusesPlayersWhoChooseButNotStaff() {
        ToffyGroup closed = group("staff", false);

        assertEquals(ToffyGroupRules.Refusal.CLOSED,
            ToffyGroupRules.refusal(closed, ANA, 50.0, true).orElseThrow());
        assertTrue(ToffyGroupRules.refusal(closed, ANA, 50.0, false).isEmpty());
    }

    @Test
    void aFullGroupRefusesAndAMemberIsNeverRefused() {
        ToffyGroup full = group("velocidad", true);
        full.setCapacity(1);
        full.addMember(ANA);

        assertTrue(ToffyGroupRules.refusal(full, ANA, 50.0, true).isEmpty());
        assertEquals(ToffyGroupRules.Refusal.FULL,
            ToffyGroupRules.refusal(full, BEN, 50.0, true).orElseThrow());
    }

    @Test
    void theLevelRangeOfTheGroupRefusesOnlyWhenThereIsOne() {
        ToffyGroup ranged = group("potencia", true);
        ranged.setLevelRange(40.0, null);

        assertEquals(ToffyGroupRules.Refusal.LEVEL_OUT_OF_RANGE,
            ToffyGroupRules.refusal(ranged, ANA, 10.0, true).orElseThrow());
        assertTrue(ToffyGroupRules.refusal(ranged, ANA, 200.0, true).isEmpty());
        assertEquals(ToffyGroupRules.Refusal.LEVEL_UNKNOWN,
            ToffyGroupRules.refusal(ranged, ANA, null, true).orElseThrow());
        assertTrue(ToffyGroupRules.refusal(group("libre", true), ANA, null, true).isEmpty());
    }

    @Test
    void listsOpenGroupsOnlyWhenTheTournamentIsMandatoryAndHasOneOpen() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.putGroup(group("cerrado", false));

        assertFalse(ToffyGroupRules.mustChooseGroup(tournament));

        tournament.putGroup(group("potencia", true));
        assertFalse(ToffyGroupRules.mustChooseGroup(tournament));

        tournament.setGroupMandatory(true);
        assertTrue(ToffyGroupRules.mustChooseGroup(tournament));
        assertEquals(List.of("potencia"), ToffyGroupRules.openGroups(tournament).stream().map(ToffyGroup::key).toList());
        assertEquals("potencia", ToffyGroupRules.names(ToffyGroupRules.openGroups(tournament)));
    }

    @Test
    void mandatoryWithoutAnyOpenGroupDoesNotBlockSignUps() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.putGroup(group("cerrado", false));
        tournament.setGroupMandatory(true);

        assertFalse(ToffyGroupRules.mustChooseGroup(tournament));
    }

    @Test
    void groupedPlayersTeleportToTheirGroupPlacesAndOthersToTheFallback() {
        assertEquals(List.of(GROUP_PLACE),
            ToffyGroupRules.destinations(false, List.of(GROUP_PLACE), List.of(TOURNAMENT_PLACE)));
        assertEquals(List.of(TOURNAMENT_PLACE),
            ToffyGroupRules.destinations(false, List.of(), List.of(TOURNAMENT_PLACE)));
        assertTrue(ToffyGroupRules.destinations(false, List.<ToffyStoredLocation>of(), List.of()).isEmpty());
    }

    @Test
    void anExplicitDestinationWinsOverTheGroupPlaces() {
        ToffyStoredLocation typed = new ToffyStoredLocation("world", 1, 2, 3, 0f, 0f);

        assertEquals(List.of(typed), ToffyGroupRules.destinations(true, List.of(GROUP_PLACE), List.of(typed)));
        assertEquals(List.of(typed), ToffyGroupRules.destinations(true, List.of(), List.of(typed)));
    }

    @Test
    void reservedKeywordsCannotBeGroupNames() {
        for (String word : List.of("forzar", "grupo", "lugar", "todos", "sin", "agregar", "borrar", "cancelar",
            "abierto", "salir", "estado")) {
            assertThrows(IllegalArgumentException.class, () -> ToffyGroup.keyOf(word), word);
        }
        assertEquals("potencia", ToffyGroup.keyOf("potencia"));
    }

    @Test
    void rangeTextMatchesTheTournamentWording() {
        assertEquals("sin rango", ToffyGroupRules.rangeText(null, null));
        assertEquals("40-*", ToffyGroupRules.rangeText(40.0, null));
        assertEquals("*-120", ToffyGroupRules.rangeText(null, 120.0));
        assertEquals("12.5-80", ToffyGroupRules.rangeText(12.5, 80.0));
    }

    @Test
    void groupNamesFollowTheTournamentRulesAndRefuseTheAnotarWords() {
        assertEquals("velocidad", ToffyGroup.keyOf(" Velocidad "));
        assertThrows(IllegalArgumentException.class, () -> ToffyGroup.keyOf("salir"));
        assertThrows(IllegalArgumentException.class, () -> ToffyGroup.keyOf("Estado"));
        assertThrows(IllegalArgumentException.class, () -> ToffyGroup.keyOf("con espacio"));
        assertThrows(IllegalArgumentException.class, () -> ToffyGroup.keyOf("x".repeat(33)));
    }
}
