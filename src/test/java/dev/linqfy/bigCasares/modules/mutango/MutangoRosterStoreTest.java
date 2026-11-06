package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El roster de cada pelea vive fuera de las entidades: las bajas no se pierden con un sobreviviente descargado. */
class MutangoRosterStoreTest {

    @TempDir
    File folder;

    private final UUID fight = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private File file() {
        return new File(folder, "data/peleas.yml");
    }

    @Test
    void whatWasSavedSurvivesARestart() {
        new MutangoRosterStore(file()).put(fight, List.of(a, b, c), List.of());

        MutangoRosterStore.Record saved = new MutangoRosterStore(file()).get(fight);
        assertEquals(List.of(a, b, c), List.copyOf(saved.members()));
        assertTrue(saved.dead().isEmpty());
    }

    @Test
    void theGhostScenarioCUnloadedThenABDieThenRestartThenCDies() {
        // C se descarga con el roster A/B/C y despues mueren A y B: las bajas se guardan por pelea, no en las entidades
        MutangoRosterStore store = new MutangoRosterStore(file());
        MutangoRoster roster = new MutangoRoster();
        roster.restore(List.of(a, b, c), List.of());
        store.put(fight, roster.members(), roster.dead());
        roster.confirmDeath(a);
        store.put(fight, roster.members(), roster.dead());
        roster.confirmDeath(b);
        store.put(fight, roster.members(), roster.dead());

        // el servidor reinicia: la pelea se arma de lo guardado (C todavia no cargo)
        MutangoRosterStore afterRestart = new MutangoRosterStore(file());
        MutangoRoster rebuilt = new MutangoRoster();
        MutangoRosterStore.Record saved = afterRestart.get(fight);
        rebuilt.restore(saved.members(), saved.dead());
        assertEquals(java.util.Set.of(c), rebuilt.members(), "A y B no vuelven como fantasmas");

        // C carga y muere: no queda nadie, aparece Neguechen
        rebuilt.confirmDeath(c);
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void aFinishedFightIsForgotten() {
        MutangoRosterStore store = new MutangoRosterStore(file());
        store.put(fight, List.of(a), List.of());
        store.remove(fight);

        assertNull(new MutangoRosterStore(file()).get(fight));
        assertEquals(0, new MutangoRosterStore(file()).size());
    }

    @Test
    void eachFightHasItsOwnRoster() {
        UUID other = UUID.randomUUID();
        MutangoRosterStore store = new MutangoRosterStore(file());
        store.put(fight, List.of(a), List.of(b));
        store.put(other, List.of(c), List.of());

        MutangoRosterStore reopened = new MutangoRosterStore(file());
        assertEquals(java.util.Set.of(a), reopened.get(fight).members());
        assertEquals(java.util.Set.of(b), reopened.get(fight).dead());
        assertEquals(java.util.Set.of(c), reopened.get(other).members());
    }

    @Test
    void aBrokenRecordDoesNotStopTheOthers() throws Exception {
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "version: 1\npeleas:\n  - id: no-es-un-uuid\n    miembros: [x]\n  - id: " + fight
            + "\n    miembros: [" + a + ", no-es-uuid]\n    muertos: [" + b + "]\n");

        MutangoRosterStore.Record saved = new MutangoRosterStore(file).get(fight);
        assertEquals(java.util.Set.of(a), saved.members());
        assertEquals(java.util.Set.of(b), saved.dead());
    }
}
