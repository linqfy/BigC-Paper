package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cola de adjudicaciones: lo que un jugador ya gano pero todavia no esta confirmado como entregado (victorias contra
 * Neguechen y premios: Botas de Neguechen y Cruz Andina). Cada adjudicacion tiene su id y se guarda en un archivo
 * <b>antes</b> de tocar al jugador; se borra de la cola recien cuando el jugador ya tiene todo guardado y con su recibo
 * (ver MutangoAwardClaim). Asi un corte en cualquier paso no pierde ni duplica un premio: si se cae antes del recibo se
 * reintenta, y si se cae despues el recibo evita darlo de nuevo.
 */
final class MutangoPending {

    static final int VERSION = 2;

    /** Una adjudicacion: cuantas victorias y cuantos juegos de premios le tocan a ese jugador. */
    record Award(UUID id, UUID player, int wins, int rewards) {
    }

    private final File file;
    private final Map<UUID, Award> awards = new LinkedHashMap<>();

    MutangoPending(File file) {
        this.file = file;
        load();
    }

    /**
     * Anota una adjudicacion nueva y la guarda en el archivo antes de devolverla. Si no se puede guardar queda en la cola
     * de esta corrida igual (se va a entregar) y se avisa con IllegalStateException.
     */
    Award add(UUID player, int wins, int rewards) {
        Award award = new Award(UUID.randomUUID(), player, Math.max(0, wins), Math.max(0, rewards));
        awards.put(award.id(), award);
        save();
        return award;
    }

    /** Lo que ese jugador tiene sin confirmar, en el orden en que se anoto. */
    List<Award> of(UUID player) {
        List<Award> list = new ArrayList<>();
        for (Award award : awards.values()) {
            if (award.player().equals(player)) {
                list.add(award);
            }
        }
        return list;
    }

    boolean has(UUID player) {
        return awards.values().stream().anyMatch(award -> award.player().equals(player));
    }

    int size() {
        return awards.size();
    }

    /** Ya esta entregada y guardada en el jugador: sale de la cola (y del archivo). */
    void confirm(UUID awardId) {
        if (awards.remove(awardId) != null) {
            save();
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yaml.getMapList("adjudicaciones")) {
            try {
                UUID id = UUID.fromString(String.valueOf(raw.get("id")));
                UUID player = UUID.fromString(String.valueOf(raw.get("jugador")));
                int wins = Math.max(0, number(raw.get("victorias")));
                int rewards = Math.max(0, number(raw.get("premios")));
                if (wins > 0 || rewards > 0) {
                    awards.put(id, new Award(id, player, wins, rewards));
                }
            } catch (IllegalArgumentException invalid) {
                // un registro roto no puede impedir cargar los demas
            }
        }
        loadLegacy(yaml);
    }

    /**
     * El formato anterior (sin version) guardaba solo cuentas por jugador. Cada una pasa a ser una adjudicacion con un id
     * que sale del jugador (siempre el mismo: si se vuelve a leer antes de guardarse, no se duplica).
     */
    private void loadLegacy(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("pendientes");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID player = UUID.fromString(key);
                int wins = Math.max(0, section.getInt(key + ".victorias"));
                int rewards = Math.max(0, section.getInt(key + ".premios"));
                UUID id = UUID.nameUUIDFromBytes(("mutango-pendiente-viejo:" + player).getBytes(StandardCharsets.UTF_8));
                if (wins + rewards > 0 && !awards.containsKey(id)) {
                    awards.put(id, new Award(id, player, wins, rewards));
                }
            } catch (IllegalArgumentException invalid) {
                // un registro roto no puede impedir cargar los demas
            }
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    /** Escribe a un archivo temporal y lo mueve (asi un corte a mitad de guardar no deja el archivo a medias). */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Award award : awards.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", award.id().toString());
            entry.put("jugador", award.player().toString());
            entry.put("victorias", award.wins());
            entry.put("premios", award.rewards());
            list.add(entry);
        }
        yaml.set("adjudicaciones", list);
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
