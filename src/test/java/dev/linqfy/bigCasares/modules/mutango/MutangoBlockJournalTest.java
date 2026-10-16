package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
