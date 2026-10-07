package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads {@code celular/videos.yml}, written by {@code tools/celular/convertir_videos.py}. */
public final class CelularVideosLoader {

    public static final String RESOURCE = "celular/videos.yml";

    private CelularVideosLoader() {
    }

    public static List<CelularVideo> load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        List<CelularVideo> videos = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("videos")) {
            Object id = entry.get("id");
            Object title = entry.get("titulo");
            if (id == null || title == null || !(entry.get("cuadros") instanceof Number frames)) {
                throw new IllegalArgumentException("Malformed video in " + RESOURCE + ": " + entry);
            }
            videos.add(new CelularVideo(id.toString(), title.toString(), frames.intValue()));
        }
        if (videos.isEmpty()) {
            throw new IllegalArgumentException(RESOURCE + " has no videos");
        }
        return List.copyOf(videos);
    }
}
