package dev.linqfy.bigCasares.dimension.persistence;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Versioned YAML persistence for the dimension framework
 * ({@code plugins/BigCasares/dimensions/state.yml}).
 *
 * <p>Holds per-dimension values, player return points and built portals. Writes
 * go to a temporary file and are moved into place, so a crash never leaves a
 * truncated file. A corrupt file is moved aside ({@code state.yml.corrupt-*})
 * and the store starts empty instead of preventing activation.</p>
 *
 * <p>Schema history: v1 is the initial layout. Add a migration to
 * {@link #migrate} whenever the layout changes and bump {@link #SCHEMA_VERSION}.</p>
 */
public final class DimensionDataStore {

    public static final int SCHEMA_VERSION = 1;

    /** A stored position (world identified by name; worlds can be re-created with new UUIDs). */
    public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
        public StoredLocation {
            Objects.requireNonNull(world, "world");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Stored location must be finite");
            }
        }
    }

    /** A built portal frame. */
    public record StoredPortal(
        String id, String definition, String dimension, String world, String axis,
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String linkedPortal
    ) {
        public StoredPortal {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(axis, "axis");
        }
    }

    private final Path file;
    private final Logger logger;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean dirty;

    public DimensionDataStore(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void load() {
        YamlConfiguration loaded = new YamlConfiguration();
        if (Files.isRegularFile(file)) {
            try {
                loaded.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException exception) {
                quarantine(exception);
                loaded = new YamlConfiguration();
            }
        }
        int version = loaded.getInt("schema-version", loaded.getKeys(false).isEmpty() ? SCHEMA_VERSION : 0);
        if (version > SCHEMA_VERSION) {
            throw new IllegalStateException("Dimension state " + file + " has schema " + version
                + ", newer than supported " + SCHEMA_VERSION + "; refusing to overwrite it");
        }
        migrate(loaded, version);
        loaded.set("schema-version", SCHEMA_VERSION);
        this.yaml = loaded;
        this.dirty = version != SCHEMA_VERSION;
    }

    /**
     * Upgrades older layouts in place, one version at a time. Version 0 is a file
     * without {@code schema-version} (e.g. created by hand); its layout equals v1,
     * so it only gets stamped. Future layout changes add a step here.
     */
    static void migrate(YamlConfiguration yaml, int fromVersion) {
        int version = fromVersion;
        if (version < 1) {
            version = 1;
        }
        if (version != SCHEMA_VERSION) {
            throw new IllegalStateException("No migration path from dimension state schema " + fromVersion);
        }
    }

    public synchronized void save() throws IOException {
        if (!dirty && Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    // --- per-dimension values -------------------------------------------------------------

    public synchronized Optional<String> value(String dimension, String key) {
        return Optional.ofNullable(yaml.getString("dimensions." + encode(dimension) + ".values." + encodeKey(key)));
    }

    public synchronized void putValue(String dimension, String key, String value) {
        yaml.set("dimensions." + encode(dimension) + ".values." + encodeKey(key), value);
        dirty = true;
    }

    public synchronized void removeValue(String dimension, String key) {
        yaml.set("dimensions." + encode(dimension) + ".values." + encodeKey(key), null);
        dirty = true;
    }

    // --- datapack ledger ------------------------------------------------------------------

    public synchronized Optional<String> installedPackHash() {
        return Optional.ofNullable(yaml.getString("datapack.installed-hash"));
    }

    public synchronized void recordInstalledPack(String hash, String installedAt) {
        yaml.set("datapack.installed-hash", hash);
        yaml.set("datapack.installed-at", installedAt);
        dirty = true;
    }

    // --- return points --------------------------------------------------------------------

    public synchronized Optional<StoredLocation> returnPoint(UUID player, String dimension) {
        ConfigurationSection section = yaml.getConfigurationSection(
            "players." + player + ".return-points." + encode(dimension));
        if (section == null || section.getString("world") == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredLocation(section.getString("world"),
            section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            (float) section.getDouble("yaw"), (float) section.getDouble("pitch")));
    }

    public synchronized void setReturnPoint(UUID player, String dimension, StoredLocation location) {
        String path = "players." + player + ".return-points." + encode(dimension);
        if (location == null) {
            yaml.set(path, null);
        } else {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("world", location.world());
            values.put("x", location.x());
            values.put("y", location.y());
            values.put("z", location.z());
            values.put("yaw", (double) location.yaw());
            values.put("pitch", (double) location.pitch());
            yaml.createSection(path, values);
        }
        dirty = true;
    }

    public synchronized boolean markVisited(UUID player, String dimension) {
        String path = "players." + player + ".visited";
        List<String> visited = new ArrayList<>(yaml.getStringList(path));
        if (visited.contains(dimension)) {
            return false;
        }
        visited.add(dimension);
        yaml.set(path, visited);
        dirty = true;
        return true;
    }

    public synchronized boolean hasVisited(UUID player, String dimension) {
        return yaml.getStringList("players." + player + ".visited").contains(dimension);
    }

    // --- portals --------------------------------------------------------------------------

    public synchronized List<StoredPortal> portals() {
        ConfigurationSection section = yaml.getConfigurationSection("portals");
        if (section == null) {
            return List.of();
        }
        List<StoredPortal> portals = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            try {
                portals.add(new StoredPortal(id, entry.getString("definition"), entry.getString("dimension"),
                    entry.getString("world"), entry.getString("axis"),
                    entry.getInt("min-x"), entry.getInt("min-y"), entry.getInt("min-z"),
                    entry.getInt("max-x"), entry.getInt("max-y"), entry.getInt("max-z"),
                    entry.getString("linked")));
            } catch (RuntimeException invalid) {
                logger.warning("Ignoring malformed portal entry '" + id + "' in " + file + ": " + invalid.getMessage());
            }
        }
        return portals;
    }

    public synchronized void putPortal(StoredPortal portal) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("definition", portal.definition());
        values.put("dimension", portal.dimension());
        values.put("world", portal.world());
        values.put("axis", portal.axis());
        values.put("min-x", portal.minX());
        values.put("min-y", portal.minY());
        values.put("min-z", portal.minZ());
        values.put("max-x", portal.maxX());
        values.put("max-y", portal.maxY());
        values.put("max-z", portal.maxZ());
        if (portal.linkedPortal() != null) {
            values.put("linked", portal.linkedPortal());
        }
        yaml.createSection("portals." + portal.id(), values);
        dirty = true;
    }

    public synchronized void removePortal(String id) {
        yaml.set("portals." + id, null);
        dirty = true;
    }

    private void quarantine(Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, aside);
            logger.log(Level.SEVERE, "Dimension state was unreadable; moved it to " + aside + " and started empty", cause);
        } catch (IOException moveFailure) {
            logger.log(Level.SEVERE, "Dimension state was unreadable and could not be moved aside", moveFailure);
        }
    }

    /** YAML path segments cannot contain '.' or ':' safely; encode namespaced ids. */
    static String encode(String id) {
        return id.replace(':', '~').replace('.', '_');
    }

    private static String encodeKey(String key) {
        if (!key.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid dimension value key: " + key);
        }
        return key;
    }
}
