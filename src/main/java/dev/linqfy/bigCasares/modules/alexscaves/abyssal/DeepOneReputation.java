package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mod {@code ACWorldData.deepOneReputations}: each player's reputation with the Deep Ones, between -100 and 100, one
 * value for the whole server (the mod keeps it in the overworld's data). Saved to a small YAML file in the plugin
 * folder, written when it changed (at most every few seconds) and when the module stops.
 */
public final class DeepOneReputation {
    static final String FILE = "alexscaves-deep-one-reputation.yml";

    private static final Map<Plugin, DeepOneReputation> INSTANCES = new WeakHashMap<>();

    private final File file;
    private final Map<UUID, Integer> values = new HashMap<>();
    private boolean dirty;

    DeepOneReputation(File file) {
        this.file = file;
    }

    /** The store of a plugin, loaded on first use. */
    public static synchronized DeepOneReputation of(Plugin plugin) {
        return INSTANCES.computeIfAbsent(plugin, key -> {
            DeepOneReputation store = new DeepOneReputation(new File(key.getDataFolder(), FILE));
            store.load(key.getLogger());
            return store;
        });
    }

    /** Saves and forgets the store of a plugin (module shutdown). */
    public static synchronized void release(Plugin plugin) {
        DeepOneReputation store = INSTANCES.remove(plugin);
        if (store != null) {
            store.save(plugin.getLogger());
        }
    }

    public synchronized int get(UUID player) {
        return player == null ? 0 : values.getOrDefault(player, 0);
    }

    public synchronized void set(UUID player, int reputation) {
        values.put(player, DeepOneReaction.clamp(reputation));
        dirty = true;
    }

    /** Adds to a player's reputation; returns the new value. */
    public synchronized int add(UUID player, int amount) {
        int value = DeepOneReaction.clamp(get(player) + amount);
        set(player, value);
        return value;
    }

    public synchronized DeepOneReaction reactionOf(UUID player) {
        return DeepOneReaction.fromReputation(get(player));
    }

    public synchronized boolean dirty() {
        return dirty;
    }

    synchronized void load(Logger logger) {
        values.clear();
        File backup = new File(file.getPath() + ".bak");
        if (!read(file, logger) && !read(backup, logger)) {
            values.clear();
        }
    }

    /** Reads one file into the map; false when it is missing or does not parse (the caller then tries the backup). */
    private boolean read(File source, Logger logger) {
        if (!source.isFile()) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(source);
        } catch (IOException | InvalidConfigurationException broken) {
            logger.log(Level.WARNING, "[AlexsCaves] No se pudo leer " + source.getName() + " (reputación con los profundos)", broken);
            return false;
        }
        values.clear();
        ConfigurationSection section = yaml.getConfigurationSection("reputation");
        if (section == null) {
            return true;
        }
        for (String key : section.getKeys(false)) {
            try {
                values.put(UUID.fromString(key), DeepOneReaction.clamp(section.getInt(key)));
            } catch (IllegalArgumentException invalid) {
                logger.warning("[AlexsCaves] Reputación con los profundos: jugador inválido " + key);
            }
        }
        return true;
    }

    /** Written to a temporary file that replaces the real one in a single move, keeping the previous file as a backup. */
    public synchronized void save(Logger logger) {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : values.entrySet()) {
            yaml.set("reputation." + entry.getKey(), entry.getValue());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            File temp = new File(file.getPath() + ".tmp");
            yaml.save(temp);
            if (file.isFile()) {
                Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException failure) {
            logger.log(Level.WARNING, "[AlexsCaves] No se pudo guardar la reputación con los profundos", failure);
        }
    }
}
