package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que un jugador ya gano pero todavia no recibio: las victorias contra Neguechen (si estaba desconectado cuando
 * ganaron) y los premios (Botas de Neguechen y Cruz Andina). Se guarda en un archivo apenas se gana, asi no se pierde
 * si el servidor se apaga o si sacan la pelea antes de que se entreguen; se le da al jugador cuando se entregan los
 * premios, cuando entra al servidor o cuando revive.
 */
final class MutangoPending {

    /** Lo que le falta recibir a un jugador. */
    record Entry(int wins, int rewards) {
        boolean empty() {
            return wins <= 0 && rewards <= 0;
        }
    }

    private final File file;
    private final Map<UUID, int[]> data = new HashMap<>();

    MutangoPending(File file) {
        this.file = file;
        load();
    }

    /** Una victoria mas para este jugador (por si no esta conectado para guardarsela en el mismo momento). */
    void addWin(UUID player) {
        data.computeIfAbsent(player, id -> new int[2])[0]++;
        save();
    }

    /** Un juego de premios mas (Botas y Cruz) para este jugador. */
    void addReward(UUID player) {
        data.computeIfAbsent(player, id -> new int[2])[1]++;
        save();
    }

    boolean has(UUID player) {
        int[] entry = data.get(player);
        return entry != null && (entry[0] > 0 || entry[1] > 0);
    }

    /** Lo que le falta a ese jugador; lo saca del archivo (quien lo pide se lo tiene que dar). */
    Entry take(UUID player) {
        int[] entry = data.remove(player);
        if (entry == null) {
            return new Entry(0, 0);
        }
        save();
        return new Entry(entry[0], entry[1]);
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("pendientes");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                int wins = Math.max(0, section.getInt(key + ".victorias"));
                int rewards = Math.max(0, section.getInt(key + ".premios"));
                if (wins > 0 || rewards > 0) {
                    data.put(id, new int[] {wins, rewards});
                }
            } catch (IllegalArgumentException invalid) {
                // un registro roto no puede impedir cargar los demas
            }
        }
    }

    /** Escribe a un archivo temporal y lo mueve (asi un corte a mitad de guardar no deja el archivo a medias). */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((id, entry) -> {
            yaml.set("pendientes." + id + ".victorias", entry[0]);
            yaml.set("pendientes." + id + ".premios", entry[1]);
        });
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
