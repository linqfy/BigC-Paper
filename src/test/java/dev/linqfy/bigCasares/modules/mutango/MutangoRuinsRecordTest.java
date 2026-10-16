package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El archivo de estado de las ruinas: con version, y todavia se lee el formato viejo. */
class MutangoRuinsRecordTest {

    @TempDir
    File folder;

    private File file() {
        return new File(folder, "data/ruinas.yml");
    }

    @Test
    void sinArchivoNoHayNada() throws IOException {
        assertTrue(new MutangoRuinsRecord(file()).load().isEmpty());
    }

    @Test
    void loGuardadoSeLeeIgualYEnElMismoOrden() throws IOException {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("uid;3;-2", "hecha");
        status.put("uid;manual;10;20;1;1700000000", "pendiente");
        status.put("uid;0;0", "cueva");
        MutangoRuinsRecord record = new MutangoRuinsRecord(file());
        record.save(status);

        Map<String, String> loaded = new MutangoRuinsRecord(file()).load();
        assertEquals(status, loaded);
        assertEquals(List.copyOf(status.keySet()), List.copyOf(loaded.keySet()));
    }

    @Test
    void elArchivoNuevoLlevaLaVersion() throws IOException {
        new MutangoRuinsRecord(file()).save(Map.of("uid;1;1", "hecha"));
        assertEquals(MutangoRuinsRecord.VERSION, YamlConfiguration.loadConfiguration(file()).getInt("version"));
    }

    @Test
    void elFormatoViejoDeLineasSeSigueLeyendoYSeReescribeEnElNuevo() throws IOException {
        File file = file();
        file.getParentFile().mkdirs();
        YamlConfiguration old = new YamlConfiguration();
        old.set("ruinas", List.of("uid;1;2=hecha", "uid;manual;5;6;0;99=pendiente"));
        old.save(file);

        MutangoRuinsRecord record = new MutangoRuinsRecord(file);
        Map<String, String> loaded = record.load();
        assertEquals("hecha", loaded.get("uid;1;2"));
        assertEquals("pendiente", loaded.get("uid;manual;5;6;0;99"));

        record.save(loaded);
        assertEquals(MutangoRuinsRecord.VERSION, YamlConfiguration.loadConfiguration(file).getInt("version"));
        assertEquals(loaded, new MutangoRuinsRecord(file).load());
    }

    @Test
    void unArchivoDeUnaVersionMasNuevaNoSeToca() throws IOException {
        File file = file();
        file.getParentFile().mkdirs();
        YamlConfiguration future = new YamlConfiguration();
        future.set("version", MutangoRuinsRecord.VERSION + 1);
        future.save(file);
        assertThrows(IOException.class, () -> new MutangoRuinsRecord(file).load());
    }

    @Test
    void unRegistroRotoNoImpideLeerLosDemas() throws IOException {
        File file = file();
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", MutangoRuinsRecord.VERSION);
        yaml.set("ruinas", List.of(Map.of("clave", "uid;1;1", "estado", "hecha"), Map.of("clave", "sin-estado")));
        yaml.save(file);
        assertEquals(Map.of("uid;1;1", "hecha"), new MutangoRuinsRecord(file).load());
    }
}
