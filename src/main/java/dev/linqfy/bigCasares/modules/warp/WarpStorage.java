package dev.linqfy.bigCasares.modules.warp;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WarpStorage {
    private final Path file;
    private final Map<String, Warp> warps = new LinkedHashMap<>();

    public WarpStorage(Path file) {
        this.file = file;
    }

    public void load() {
        warps.clear();
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection warpsSection = config.getConfigurationSection("warps");
        if (warpsSection == null) {
            return;
        }

        for (String id : warpsSection.getKeys(false)) {
            ConfigurationSection section = warpsSection.getConfigurationSection(id);
            if (section == null) continue;

            String name = section.getString("name", id);
            Location location = section.getLocation("location");
            if (location == null) continue;

            String icon = section.getString("icon", "ENDER_PEARL");
            String description = section.getString("description", "");

            warps.put(id, new Warp(id, name, location, icon, description));
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection warpsSection = config.createSection("warps");

        for (Warp warp : warps.values()) {
            ConfigurationSection section = warpsSection.createSection(warp.id());
            section.set("name", warp.name());
            section.set("location", warp.location());
            section.set("icon", warp.icon());
            section.set("description", warp.description());
        }

        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            config.save(file.toFile());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("No se pudo guardar " + file, e);
        }
    }

    public Collection<Warp> getAll() {
        return Collections.unmodifiableCollection(warps.values());
    }

    public Warp get(String id) {
        return warps.get(id);
    }

    public void add(Warp warp) {
        warps.put(warp.id(), warp);
        save();
    }

    public void remove(String id) {
        warps.remove(id);
        save();
    }
}
