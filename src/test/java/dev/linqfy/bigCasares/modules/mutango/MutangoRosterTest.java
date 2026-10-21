package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El roster de una pelea: descargar un chunk no mata a nadie ni adelanta fases. */
class MutangoRosterTest {

    private final UUID lef = UUID.randomUUID();
    private final UUID lihuen = UUID.randomUUID();
    private final UUID lican = UUID.randomUUID();

    private MutangoRoster trio() {
        MutangoRoster roster = new MutangoRoster();
        roster.add(lef);
        roster.add(lihuen);
        roster.add(lican);
        return roster;
    }

    @Test
    void unloadingTheTwoAliveOnesDoesNotMakeTheFightLookAllDowned() {
        MutangoRoster roster = trio();
        Set<UUID> downed = new HashSet<>(Set.of(lef));
        assertFalse(roster.allDowned(downed));
        // se descargan Lihuen y Lican: en el roster siguen (antes quedaba 1 miembro y allDowned daba true)
        assertEquals(3, roster.members().size());
        assertFalse(roster.allDowned(downed));
        assertEquals(2, roster.missing(List.of(lef)));
    }

    @Test
    void theFirstPhaseClosesOnlyWhenTheThreeAreDownedLoadedOrNot() {
        MutangoRoster roster = trio();
        assertFalse(roster.allDowned(Set.of(lef, lihuen)));
        assertTrue(roster.allDowned(Set.of(lef, lihuen, lican)));
    }

    @Test
    void anEmptyRosterIsNeverAllDowned() {
        assertFalse(new MutangoRoster().allDowned(Set.of()));
    }

    @Test
    void onlyAConfirmedDeathRemovesAMemberAndTheLastOneIsWhenNoneIsLeft() {
        MutangoRoster roster = trio();
        roster.confirmDeath(lef);
        roster.confirmDeath(lihuen);
        assertFalse(roster.isEmpty(), "Lican puede estar descargado: todavia no se sabe que murio");
        roster.confirmDeath(lican);
        assertTrue(roster.isEmpty());
    }

    @Test
    void anEntityThatLoadsAgainIsReLinkedButADeadOneIsNotRevived() {
        MutangoRoster roster = trio();
        roster.confirmDeath(lican);
        roster.add(lican); // EntitiesLoadEvent con una entidad vieja de ese id
        assertFalse(roster.members().contains(lican));
        roster.add(lef); // uno vivo que vuelve a cargar: sigue una sola vez
        assertEquals(2, roster.members().size());
    }

    @Test
    void afterARestartOnlyOneMemberLoadsButItKnowsWhoElseBelongs() {
        MutangoRoster saved = trio();
        String onTheirSheet = saved.serialize();

        MutangoRoster afterRestart = new MutangoRoster();
        afterRestart.add(lihuen); // el primero que carga
        afterRestart.adopt(MutangoRoster.parse(onTheirSheet));
        assertEquals(3, afterRestart.members().size());
        assertEquals(2, afterRestart.missing(List.of(lihuen)));
        assertFalse(afterRestart.allDowned(Set.of(lihuen)), "un caido de tres no cierra la fase 1");
    }

    @Test
    void aBrokenIdOnTheSheetDoesNotStopTheOthers() {
        List<UUID> parsed = MutangoRoster.parse(lef + ",no-es-un-id," + lican + ", ");
        assertEquals(List.of(lef, lican), parsed);
        assertTrue(MutangoRoster.parse(null).isEmpty());
        assertTrue(MutangoRoster.parse("  ").isEmpty());
    }

    @Test
    void theSerializedRosterKeepsTheOrderTheyAppearedIn() {
        MutangoRoster roster = trio();
        assertEquals(List.of(lef, lihuen, lican), MutangoRoster.parse(roster.serialize()));
    }
}
