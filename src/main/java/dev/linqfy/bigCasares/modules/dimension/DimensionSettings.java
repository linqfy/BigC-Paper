package dev.linqfy.bigCasares.modules.dimension;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code dimension-framework} settings.
 *
 * @param installMode     {@code auto} writes the datapack into the level's datapacks folder;
 *                        {@code manual} only writes it to the plugin folder for an operator to copy
 * @param datapackFolder  folder name under {@code <level>/datapacks/}
 * @param autosaveTicks   how often dirty dimension state is flushed to disk
 */
public record DimensionSettings(InstallMode installMode, String datapackFolder, long autosaveTicks, List<String> warnings) {

    public enum InstallMode {
        AUTO, MANUAL
    }

    public static final String DEFAULT_FOLDER = "bigcasares-dimensions";

    public DimensionSettings {
        warnings = List.copyOf(warnings);
    }

    public static DimensionSettings load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("dimension-framework");
        InstallMode mode = InstallMode.AUTO;
        String folder = DEFAULT_FOLDER;
        long autosaveTicks = 6000L;
        if (section != null) {
            String rawMode = section.getString("datapack.install-mode", "auto").trim().toUpperCase(Locale.ROOT);
            try {
                mode = InstallMode.valueOf(rawMode);
            } catch (IllegalArgumentException invalid) {
                warnings.add("Unknown dimension-framework.datapack.install-mode '" + rawMode + "', using auto");
            }
            String rawFolder = section.getString("datapack.folder", DEFAULT_FOLDER).trim();
            if (rawFolder.matches("[a-z0-9_.-]+")) {
                folder = rawFolder;
            } else {
                warnings.add("Invalid dimension-framework.datapack.folder '" + rawFolder + "', using " + DEFAULT_FOLDER);
            }
            int minutes = section.getInt("autosave-minutes", 5);
            if (minutes < 1 || minutes > 120) {
                warnings.add("dimension-framework.autosave-minutes must be within 1..120, using 5");
                minutes = 5;
            }
            autosaveTicks = minutes * 60L * 20L;
        }
        return new DimensionSettings(mode, folder, autosaveTicks, warnings);
    }
}
