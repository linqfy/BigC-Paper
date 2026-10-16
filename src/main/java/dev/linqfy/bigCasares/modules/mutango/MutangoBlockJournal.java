package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El diario de los bloques que el plugin saco o cambio y todavia tiene que devolver (la grieta y las ondas de las
 * manos): cada uno se anota con como era antes de tocarlo y se tacha cuando se devuelve. Se guarda en un archivo, asi si
 * el servidor se cae con la grieta abierta o las ondas arrancando bloques, al volver a arrancar se sabe que bloques
 * faltan y se pueden devolver (sin esto los huecos quedaban para siempre). Sin Bukkit: los bloques son coordenadas y
 * el texto con que se escribe su estado.
 */
final class MutangoBlockJournal {

    /** Un bloque por devolver: su mundo, su lugar y su estado de antes (el texto de BlockData). */
    record Entry(UUID world, int x, int y, int z, String data) {
        private String id() {
            return MutangoBlockJournal.key(world, x, y, z);
        }
    }

    private static String key(UUID world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean dirty;

    MutangoBlockJournal(File file) {
        this.file = file;
        load();
    }

    /**
     * Anota un bloque antes de tocarlo (con su estado de antes). Si ya estaba anotado se deja el primero, que es el
     * original.
     */
    void record(UUID world, int x, int y, int z, String originalData) {
        if (entries.putIfAbsent(key(world, x, y, z), new Entry(world, x, y, z, originalData)) == null) {
            dirty = true;
        }
    }

    /** Ya se devolvio ese bloque: se tacha. */
    void release(UUID world, int x, int y, int z) {
        if (entries.remove(key(world, x, y, z)) != null) {
            dirty = true;
        }
    }

    /** Lo que falta devolver. */
    List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Escribe el archivo si algo cambio (a un temporal que despues se mueve, asi un corte no lo deja a medias). */
    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries.values()) {
            lines.add(entry.world() + "|" + entry.x() + "|" + entry.y() + "|" + entry.z() + "|" + entry.data());
        }
        yaml.set("bloques", lines);
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            File temp = new File(file.getPath() + ".tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            dirty = true; // se vuelve a intentar en el proximo flush
            throw new IllegalStateException("No se pudo guardar " + file + ": " + failed.getMessage(), failed);
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String line : yaml.getStringList("bloques")) {
            String[] parts = line.split("\\|", 5);
            if (parts.length != 5) {
                continue;
            }
            try {
                Entry entry = new Entry(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4]);
                entries.put(entry.id(), entry);
            } catch (IllegalArgumentException invalid) {
                // una linea rota no impide cargar las demas
            }
        }
    }
}
