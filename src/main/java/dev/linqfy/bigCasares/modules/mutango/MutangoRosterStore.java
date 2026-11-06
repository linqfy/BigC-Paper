package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El roster de cada pelea guardado FUERA de las entidades (data/mutango/peleas.yml): quienes la forman y quienes ya
 * murieron, por id de pelea. Es el estado autoritativo: una entidad descargada no puede actualizar su propia ficha, asi
 * que si las bajas vivieran en las entidades, despues de un reinicio un sobreviviente que estaba descargado volveria a
 * contar a los muertos y la pelea nunca se daria por terminada.
 */
final class MutangoRosterStore {

    static final int VERSION = 1;

    /** Quienes forman una pelea todavia y quienes ya murieron. */
    record Record(Set<UUID> members, Set<UUID> dead) {
    }

    private final File file;
    private final Map<UUID, Record> fights = new LinkedHashMap<>();

    MutangoRosterStore(File file) {
        this.file = file;
        load();
    }

    /** Lo guardado de esa pelea, o null si no se conoce. */
    Record get(UUID fight) {
        return fights.get(fight);
    }

    /** Guarda el roster de esa pelea (se escribe ya: pasa pocas veces, al aparecer el trio y en cada muerte). */
    void put(UUID fight, Collection<UUID> members, Collection<UUID> dead) {
        fights.put(fight, new Record(new LinkedHashSet<>(members), new LinkedHashSet<>(dead)));
        save();
    }

    /** La pelea termino (o la sacaron): ya no hace falta recordarla. */
    void remove(UUID fight) {
        if (fights.remove(fight) != null) {
            save();
        }
    }

    int size() {
        return fights.size();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yaml.getMapList("peleas")) {
            try {
                UUID id = UUID.fromString(String.valueOf(raw.get("id")));
                fights.put(id, new Record(ids(raw.get("miembros")), ids(raw.get("muertos"))));
            } catch (IllegalArgumentException invalid) {
                // un registro roto no impide cargar las demas peleas
            }
        }
    }

    private static Set<UUID> ids(Object raw) {
        Set<UUID> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> list) {
            for (Object item : list) {
                try {
                    out.add(UUID.fromString(String.valueOf(item)));
                } catch (IllegalArgumentException invalid) {
                    // un id roto no impide leer los demas
                }
            }
        }
        return out;
    }

    /** Escribe a un temporal y lo mueve (un corte a mitad de guardar no deja el archivo a medias). */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        List<Map<String, Object>> list = new ArrayList<>();
        fights.forEach((id, record) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id.toString());
            entry.put("miembros", record.members().stream().map(UUID::toString).toList());
            entry.put("muertos", record.dead().stream().map(UUID::toString).toList());
            list.add(entry);
        });
        yaml.set("peleas", list);
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            File temp = new File(file.getPath() + ".tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            throw new IllegalStateException("No se pudo guardar " + file + ": " + failed.getMessage(), failed);
        }
    }
}
