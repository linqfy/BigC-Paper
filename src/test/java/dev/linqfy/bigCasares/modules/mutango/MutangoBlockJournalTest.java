package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los bloques que faltan devolver sobreviven a una caida del servidor. */
class MutangoBlockJournalTest {

    @TempDir
    File folder;

    private final UUID world = UUID.randomUUID();

    private File file() {
        return new File(folder, "data/bloques.yml");
    }

    @Test
    void sinNadaAnotadoNoHayNadaPorDevolver() {
        assertTrue(new MutangoBlockJournal(file()).isEmpty());
    }

    @Test
    void loAnotadoSobreviveALaCaida() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.record(world, 10, 64, -3, "minecraft:grass_block[snowy=false]");
        journal.record(world, 10, 63, -3, "minecraft:dirt");
        journal.flush();

        MutangoBlockJournal afterCrash = new MutangoBlockJournal(file());
        assertEquals(2, afterCrash.entries().size());
        assertEquals(new MutangoBlockJournal.Entry(world, 10, 64, -3, "minecraft:grass_block[snowy=false]"),
            afterCrash.entries().get(0));
    }

    @Test
    void siEstaAnotadoDosVecesSeQuedaConElOriginal() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.record(world, 1, 2, 3, "minecraft:stone");
        journal.record(world, 1, 2, 3, "minecraft:air");
        assertEquals("minecraft:stone", journal.entries().get(0).data());
    }

    @Test
    void loQueSeDevolvioSeTacha() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.record(world, 1, 2, 3, "minecraft:stone");
        journal.record(world, 4, 5, 6, "minecraft:dirt");
        journal.flush();
        journal.release(world, 1, 2, 3);
        journal.flush();

        MutangoBlockJournal afterRestart = new MutangoBlockJournal(file());
        assertEquals(1, afterRestart.entries().size());
        assertEquals(4, afterRestart.entries().get(0).x());
    }

    @Test
    void devolverTodoDejaElArchivoVacio() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.record(world, 1, 2, 3, "minecraft:stone");
        journal.flush();
        journal.release(world, 1, 2, 3);
        journal.flush();
        assertTrue(new MutangoBlockJournal(file()).isEmpty());
    }

    @Test
    void unaLineaRotaNoImpideCargarLasDemas() throws Exception {
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "bloques:\n- esto no es una linea\n- " + world
            + "|7|8|9|minecraft:oak_log[axis=y]\n- no-uuid|1|2|3|minecraft:stone\n");
        MutangoBlockJournal journal = new MutangoBlockJournal(file);
        assertEquals(1, journal.entries().size());
        assertEquals("minecraft:oak_log[axis=y]", journal.entries().get(0).data());
    }

    // ---- lote: se guarda antes de tocar el mundo ----

    @Test
    void unLoteQuedaEnElArchivoSinNingunFlushAparte() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        boolean saved = journal.commit(List.of(
            new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone"),
            new MutangoBlockJournal.Entry(world, 1, 1, 3, "minecraft:dirt")));

        assertTrue(saved);
        // un corte aca (antes de cambiar el mundo y sin ningun flush posterior) ya encuentra las dos anotaciones
        assertEquals(2, new MutangoBlockJournal(file()).entries().size());
    }

    @Test
    void recordSoloAnotaEnMemoriaHastaElFlush() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.record(world, 1, 2, 3, "minecraft:stone");
        assertEquals(0, new MutangoBlockJournal(file()).entries().size());
        journal.flush();
        assertEquals(1, new MutangoBlockJournal(file()).entries().size());
    }

    @Test
    void siElLoteNoSePuedeGuardarNoQuedaAnotadoNiSeAutorizaTocarElMundo() throws Exception {
        File file = file();
        // un directorio donde va el archivo temporal: guardar falla
        Files.createDirectories(new File(file.getPath() + ".tmp").toPath());
        MutangoBlockJournal journal = new MutangoBlockJournal(file);

        boolean saved = journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone")));

        assertFalse(saved);
        assertTrue(journal.isEmpty(), "el lote abortado no deja anotaciones en memoria");
    }

    @Test
    void unLoteFallidoNoDeshaceLoQueYaEstabaAnotado() throws Exception {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        assertTrue(journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone"))));
        Files.createDirectories(new File(file().getPath() + ".tmp").toPath());

        assertFalse(journal.commit(List.of(
            new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:air"),
            new MutangoBlockJournal.Entry(world, 9, 9, 9, "minecraft:dirt"))));

        assertEquals(1, journal.entries().size());
        assertEquals("minecraft:stone", journal.entries().get(0).data());
    }

    @Test
    void unLoteVacioSiempreSePuede() {
        assertTrue(new MutangoBlockJournal(file()).commit(List.of()));
    }

    // ---- devolver: se tacha recien cuando el mundo se guarda ----

    @Test
    void unBloqueDevueltoSigueAnotadoHastaQueElMundoSeGuarda() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone")));

        journal.restored(world, 1, 2, 3);
        journal.flush();
        // se cae antes de guardar el mundo: el bloque devuelto puede no haberse guardado, sigue anotado
        assertEquals(1, new MutangoBlockJournal(file()).entries().size());

        journal.worldSaved(world);
        assertTrue(new MutangoBlockJournal(file()).isEmpty());
    }

    @Test
    void elGuardadoDeOtroMundoNoTachaLoQueEsperaOtro() {
        UUID other = UUID.randomUUID();
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone"),
            new MutangoBlockJournal.Entry(other, 4, 5, 6, "minecraft:dirt")));
        journal.restored(world, 1, 2, 3);
        journal.restored(other, 4, 5, 6);

        journal.worldSaved(other);

        List<MutangoBlockJournal.Entry> left = new MutangoBlockJournal(file()).entries();
        assertEquals(1, left.size());
        assertEquals(world, left.get(0).world());
    }

    @Test
    void siSeVuelveATocarElBloqueAntesDeGuardarNoSeTachaCuandoSeGuarde() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone")));
        journal.restored(world, 1, 2, 3);

        // otra onda arranca el mismo bloque antes de que el mundo se guarde
        journal.commit(List.of(new MutangoBlockJournal.Entry(world, 1, 2, 3, "minecraft:stone")));
        journal.worldSaved(world);

        assertEquals(1, new MutangoBlockJournal(file()).entries().size());
    }

    @Test
    void restoredDeAlgoQueNoEstabaAnotadoNoHaceNada() {
        MutangoBlockJournal journal = new MutangoBlockJournal(file());
        journal.restored(world, 1, 2, 3);
        journal.worldSaved(world);
        assertTrue(journal.isEmpty());
    }
}
