package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El diario de los bloques que el plugin saco o cambio y todavia tiene que devolver (la grieta y las ondas de las
 * manos): cada uno se anota con como era antes de tocarlo y se tacha cuando se devuelve. Se guarda en un archivo, asi si
 * el servidor se cae con la grieta abierta o las ondas arrancando bloques, al volver a arrancar se sabe que bloques
 * faltan y se pueden devolver (sin esto los huecos quedaban para siempre). Sin Bukkit: los bloques son coordenadas y
 * el texto con que se escribe su estado.
 * <p>
 * El archivo es un <b>log de solo agregar</b>: cada lote se agrega al final (no se reescribe todo el diario por cada
 * lote), una linea por operacion, con tabuladores: {@code A world x y z data} (anota) y {@code R world x y z} (tacha). Al
 * cargar se repiten las operaciones; una linea sin terminar (corte a mitad de escribir) o rota se ignora. Cuando el log
 * acumulo mucho mas de lo que sigue vivo se compacta: se reescribe solo lo vivo a un temporal y se mueve al destino. El
 * formato anterior (una lista YAML sin version) se sigue leyendo y se pasa al nuevo en el primer guardado.
 */
final class MutangoBlockJournal {

    static final String HEADER = "#mutango-journal v2";
    /** Por defecto no se compacta un log de menos lineas que esto. */
    static final int DEFAULT_COMPACT_MIN_LINES = 2000;
    /** Se compacta cuando el log tiene mas de tantas lineas por cada entrada viva. */
    private static final int COMPACT_RATIO = 4;

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
    private final int compactMinLines;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    /** Devueltos al mundo pero todavia anotados: se tachan cuando el mundo se guarda (worldSaved). */
    private final Set<String> awaitingSave = new HashSet<>();
    /** Las operaciones que todavia no estan en el archivo (se agregan al final en el proximo flush). */
    private final List<String> pending = new ArrayList<>();
    /** Cuantas lineas de operaciones tiene el archivo (sin contar el encabezado). */
    private int fileLines;
    /** Hay que reescribir el archivo entero en el nuevo formato (venia del formato anterior o crecio de mas). */
    private boolean rewrite;

    MutangoBlockJournal(File file) {
        this(file, DEFAULT_COMPACT_MIN_LINES);
    }

    MutangoBlockJournal(File file, int compactMinLines) {
        this.file = file;
        this.compactMinLines = compactMinLines;
        load();
    }

    /**
     * Anota un bloque antes de tocarlo (con su estado de antes). Si ya estaba anotado se deja el primero, que es el
     * original. Queda en memoria hasta el proximo flush (o commit, que lo guarda ya).
     */
    void record(UUID world, int x, int y, int z, String originalData) {
        String key = key(world, x, y, z);
        awaitingSave.remove(key); // se vuelve a tocar: ya no se tacha cuando se guarde el mundo
        Entry entry = new Entry(world, x, y, z, originalData);
        if (entries.putIfAbsent(key, entry) == null) {
            pending.add(addLine(entry));
        }
    }

    /**
     * Anota todo un lote de bloques ANTES de cambiar el mundo y lo guarda en el archivo ahora (se agrega al final: un solo
     * guardado por lote, no uno por bloque). Devuelve true si quedo guardado y se puede tocar el mundo; si no se pudo
     * guardar, deshace las anotaciones nuevas del lote y devuelve false: el que llama no cambia ningun bloque de ese lote.
     */
    boolean commit(Collection<Entry> batch) {
        if (batch.isEmpty()) {
            return true;
        }
        int pendingBefore = pending.size();
        List<String> added = new ArrayList<>();
        for (Entry entry : batch) {
            String key = entry.id();
            awaitingSave.remove(key);
            if (entries.putIfAbsent(key, entry) == null) {
                added.add(key);
                pending.add(addLine(entry));
            }
        }
        try {
            flush();
            return true;
        } catch (IllegalStateException failed) {
            added.forEach(entries::remove);
            while (pending.size() > pendingBefore) {
                pending.remove(pending.size() - 1);
            }
            return false;
        }
    }

    /**
     * El bloque ya se devolvio al mundo, pero el mundo todavia no se guardo con el bloque devuelto: sigue anotado hasta
     * worldSaved. Si el servidor se cae antes de guardar, al arrancar se vuelve a devolver.
     */
    void restored(UUID world, int x, int y, int z) {
        String key = key(world, x, y, z);
        if (entries.containsKey(key)) {
            awaitingSave.add(key);
        }
    }

    /** El mundo se guardo: los bloques devueltos hasta ahora ya estan guardados, se tachan (y se compacta si hace falta). */
    void worldSaved(UUID world) {
        boolean any = false;
        for (String key : new ArrayList<>(awaitingSave)) {
            Entry entry = entries.get(key);
            if (entry == null) {
                awaitingSave.remove(key);
            } else if (entry.world().equals(world)) {
                entries.remove(key);
                awaitingSave.remove(key);
                pending.add(removeLine(entry));
                any = true;
            }
        }
        if (any) {
            try {
                flush();
            } catch (IllegalStateException failed) {
                // se reintenta en el proximo flush
            }
        }
    }

    /** Ya se devolvio ese bloque: se tacha. */
    void release(UUID world, int x, int y, int z) {
        Entry removed = entries.remove(key(world, x, y, z));
        if (removed != null) {
            pending.add(removeLine(removed));
        }
    }

    /** Lo que falta devolver. */
    List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Pasa al archivo lo que todavia no esta: agrega las operaciones al final (nada de reescribir todo). Si hay que pasar
     * al formato nuevo, o el log creció mucho mas de lo que sigue vivo, reescribe solo lo vivo (a un temporal que despues
     * se mueve, asi un corte no lo deja a medias).
     */
    void flush() {
        if (pending.isEmpty() && !rewrite) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            int appended = pending.size();
            if (rewrite) {
                compact();
                pending.clear();
                return;
            }
            StringBuilder text = new StringBuilder();
            if (!file.isFile() || file.length() == 0) {
                text.append(HEADER).append('\n');
            }
            pending.forEach(line -> text.append(line).append('\n'));
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
            fileLines += appended;
            pending.clear();
            if (fileLines > compactMinLines && fileLines > COMPACT_RATIO * Math.max(1, entries.size())) {
                compact();
            }
        } catch (IOException failed) {
            throw new IllegalStateException("No se pudo guardar " + file + ": " + failed.getMessage(), failed);
        }
    }

    /** Reescribe el archivo con solo lo que sigue vivo (temporal + mover). */
    private void compact() throws IOException {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Entry entry : entries.values()) {
            text.append(addLine(entry)).append('\n');
        }
        File temp = new File(file.getPath() + ".tmp");
        Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        fileLines = entries.size();
        rewrite = false;
    }

    private static String addLine(Entry entry) {
        return "A\t" + entry.world() + "\t" + entry.x() + "\t" + entry.y() + "\t" + entry.z() + "\t" + entry.data();
    }

    private static String removeLine(Entry entry) {
        return "R\t" + entry.world() + "\t" + entry.x() + "\t" + entry.y() + "\t" + entry.z();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        String text;
        try {
            text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException unreadable) {
            return; // un archivo ilegible no tumba el modulo: se empieza de cero (y el proximo guardado lo pisa)
        }
        if (!text.startsWith(HEADER)) {
            loadLegacy();
            rewrite = !text.isBlank(); // el primer guardado lo pasa al formato nuevo (un archivo vacio solo recibe encabezado)
            return;
        }
        String[] lines = text.split("\n", -1);
        // la ultima "linea" es lo que viene despues del ultimo salto: vacia si el archivo termina bien, o una linea cortada
        // por un corte a mitad de escribir (no se lee)
        for (int i = 1; i < lines.length - 1; i++) {
            fileLines++;
            apply(lines[i]);
        }
        if (fileLines > compactMinLines && fileLines > COMPACT_RATIO * Math.max(1, entries.size())) {
            rewrite = true;
        } else if (!lines[lines.length - 1].isEmpty()) {
            rewrite = true; // habia una linea cortada al final: se reescribe limpio antes de seguir agregando
        }
    }

    /** Una linea del log; una linea rota no impide cargar las demas. */
    private void apply(String line) {
        String[] parts = line.split("\t", 6);
        try {
            if (parts[0].equals("A") && parts.length == 6) {
                Entry entry = new Entry(UUID.fromString(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5]);
                entries.putIfAbsent(entry.id(), entry);
            } else if (parts[0].equals("R") && parts.length == 5) {
                entries.remove(key(UUID.fromString(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])));
            }
        } catch (IllegalArgumentException invalid) {
            // una linea rota no impide cargar las demas
        }
    }

    /** El formato anterior: una lista YAML "bloques" con lineas world|x|y|z|data. */
    private void loadLegacy() {
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
