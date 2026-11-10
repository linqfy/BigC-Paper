package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending arena-event snapshots in {@code data/staff-events/snapshots.yml}. Every change
 * is written immediately (atomic replace) because the file is the only copy of the
 * player's real inventory while they play.
 */
public final class EventSnapshotStore {

    private final Path file;
    private final Map<UUID, EventPlayerSnapshot> snapshots = new LinkedHashMap<>();

    public EventSnapshotStore(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        snapshots.clear();
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
        } catch (InvalidConfigurationException invalid) {
            throw new IOException("Invalid snapshot file " + file, invalid);
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            if (section != null) {
                snapshots.put(playerId, read(playerId, section));
            }
        }
    }

    public synchronized Optional<EventPlayerSnapshot> find(UUID playerId) {
        return Optional.ofNullable(snapshots.get(playerId));
    }

    public synchronized boolean contains(UUID playerId) {
        return snapshots.containsKey(playerId);
    }

    public synchronized List<UUID> playerIds() {
        return List.copyOf(snapshots.keySet());
    }

    public synchronized void put(EventPlayerSnapshot snapshot) throws IOException {
        EventPlayerSnapshot previous = snapshots.put(snapshot.playerId(), snapshot);
        try {
            save();
        } catch (IOException failure) {
            if (previous == null) {
                snapshots.remove(snapshot.playerId());
            } else {
                snapshots.put(snapshot.playerId(), previous);
            }
            throw failure;
        }
    }

    public synchronized void remove(UUID playerId) throws IOException {
        if (snapshots.remove(playerId) != null) {
            save();
        }
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (EventPlayerSnapshot snapshot : snapshots.values()) {
            write(yaml.createSection("players." + snapshot.playerId()), snapshot);
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString());
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void write(ConfigurationSection section, EventPlayerSnapshot snapshot) {
        ItemStack[] contents = snapshot.contents();
        section.set("size", contents.length);
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                section.set("items." + slot, item);
            }
        }
        EventPlayerSnapshot.StoredLocation location = snapshot.location();
        section.set("location.world", location.world());
        section.set("location.x", location.x());
        section.set("location.y", location.y());
        section.set("location.z", location.z());
        section.set("location.yaw", (double) location.yaw());
        section.set("location.pitch", (double) location.pitch());
        section.set("game-mode", snapshot.gameMode());
        section.set("health", snapshot.health());
        section.set("food", snapshot.foodLevel());
        section.set("saturation", (double) snapshot.saturation());
        section.set("level", snapshot.level());
        section.set("exp", (double) snapshot.exp());
        section.set("effects", new ArrayList<>(snapshot.effects()));
    }

    private static EventPlayerSnapshot read(UUID playerId, ConfigurationSection section) {
        ItemStack[] contents = new ItemStack[Math.max(0, section.getInt("size", 0))];
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException invalid) {
                    continue;
                }
                if (slot >= 0 && slot < contents.length) {
                    contents[slot] = items.getItemStack(key);
                }
            }
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (Object value : section.getList("effects", List.of())) {
            if (value instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        return new EventPlayerSnapshot(
            playerId,
            contents,
            new EventPlayerSnapshot.StoredLocation(
                section.getString("location.world", ""),
                section.getDouble("location.x"),
                section.getDouble("location.y"),
                section.getDouble("location.z"),
                (float) section.getDouble("location.yaw"),
                (float) section.getDouble("location.pitch")),
            section.getString("game-mode", "SURVIVAL"),
            section.getDouble("health", 20.0),
            section.getInt("food", 20),
            (float) section.getDouble("saturation", 5.0),
            section.getInt("level", 0),
            (float) section.getDouble("exp", 0.0),
            effects);
    }
}
