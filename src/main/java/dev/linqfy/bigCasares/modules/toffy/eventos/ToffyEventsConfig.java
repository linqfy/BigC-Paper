package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;

/** {@code toffy_eventos.yml}: the event settings. */
public final class ToffyEventsConfig {
    public static final String FILE = "toffy_eventos.yml";
    /** Section of {@code config.yml} that held the event settings before they moved to {@link #FILE}. */
    public static final String LEGACY_SECTION = "staff-events";

    private ToffyEventsConfig() {
    }

    /**
     * Copies the values of an old {@code staff-events} section into a freshly created {@link #FILE}, so tuning done
     * before the move is kept. Keys the section does not have keep their bundled values.
     *
     * @return true when at least one value was copied
     */
    public static boolean importLegacy(Path file, ConfigurationSection legacy) throws IOException {
        if (legacy == null || legacy.getKeys(true).stream().noneMatch(key -> !legacy.isConfigurationSection(key))) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        // Keep the comments of the bundled file: they are what explains each key to the admin.
        yaml.options().parseComments(true);
        try {
            yaml.load(file.toFile());
        } catch (InvalidConfigurationException invalid) {
            throw new IOException(FILE + " is not valid YAML: " + invalid.getMessage(), invalid);
        }
        for (String key : legacy.getKeys(true)) {
            if (!legacy.isConfigurationSection(key)) {
                yaml.set(key, legacy.get(key));
            }
        }
        yaml.save(file.toFile());
        return true;
    }
}
