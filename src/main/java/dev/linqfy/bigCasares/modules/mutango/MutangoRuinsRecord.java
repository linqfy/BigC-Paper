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

/**
 * El archivo data/ruinas.yml: que pasó con cada ruina (clave de la ruina → estado: pendiente, hecha, agua, cueva...).
 * Tiene versión y un registro con nombre por ruina. La versión 1 (sin campo version) guardaba una lista de lineas
 * "clave=estado"; se sigue leyendo y se reescribe en el formato nuevo la proxima vez que se guarda. Un archivo de una
 * version mas nueva que esta no se toca (se avisa y las ruinas quedan desactivadas, para no pisar datos que no se
 * entienden). Sin Bukkit mas alla de YamlConfiguration.
 */
final class MutangoRuinsRecord {

    static final int VERSION = 2;

    private final File file;

    MutangoRuinsRecord(File file) {
        this.file = file;
    }

    /** Lo guardado, en el orden en que se guardo. Sin archivo: vacio. */
    Map<String, String> load() throws IOException {
        Map<String, String> status = new LinkedHashMap<>();
        if (!file.isFile()) {
            return status;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int version = yaml.getInt("version", 1);
        if (version > VERSION) {
            throw new IOException(file.getName() + " es de una version mas nueva (" + version + ") que la que entiende "
                + "este plugin (" + VERSION + ")");
        }
        if (version < 2) {
            for (String line : yaml.getStringList("ruinas")) {
                int split = line.lastIndexOf('=');
                if (split > 0) {
                    status.put(line.substring(0, split), line.substring(split + 1));
                }
            }
            return status;
        }
        for (Map<?, ?> entry : yaml.getMapList("ruinas")) {
            Object key = entry.get("clave");
            Object state = entry.get("estado");
            if (key != null && state != null) {
                status.put(key.toString(), state.toString());
            }
        }
        return status;
    }

    /** Escribe todo a un temporal que despues se mueve, asi un corte no deja el archivo a medias. */
    void save(Map<String, String> status) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        List<Map<String, Object>> entries = new ArrayList<>();
        status.forEach((key, state) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("clave", key);
            entry.put("estado", state);
            entries.add(entry);
        });
        yaml.set("ruinas", entries);
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temp = new File(file.getPath() + ".tmp");
        yaml.save(temp);
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
