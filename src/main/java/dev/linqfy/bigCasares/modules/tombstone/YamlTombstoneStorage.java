package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.UUID;

public final class YamlTombstoneStorage implements TombstoneStorage {
    private final Path file;

    public YamlTombstoneStorage(Path file) {
        this.file = file;
    }

    @Override
    public List<TombstoneRecord> loadAll() {
        if (!Files.exists(file)) return List.of();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException failure) {
            // loadConfiguration() would hand back an empty file here, and the next save would erase every tombstone.
            backupUnreadable();
            throw new IllegalStateException("Could not read tombstones from " + file, failure);
        }
        ConfigurationSection root = yaml.getConfigurationSection("tombstones");
        if (root == null) return List.of();
        List<TombstoneRecord> records = new ArrayList<>();
        int[] unreadable = {0};
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(rawId);
            if (entry == null) {
                unreadable[0]++;
                continue;
            }
            try {
                List<ItemStack> items = loadItems(entry, unreadable);
                records.add(new TombstoneRecord(
                    UUID.fromString(rawId), UUID.fromString(entry.getString("owner-id")),
                    entry.getString("owner-name", "Desconocido"), UUID.fromString(entry.getString("world-id")),
                    entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                    (float) entry.getDouble("yaw"), Instant.parse(entry.getString("expires-at")), items
                ));
            } catch (RuntimeException ignored) {
                // A malformed entry must not prevent other tombstones from loading.
                unreadable[0]++;
            }
        }
        if (unreadable[0] > 0) backupUnreadable();
        return List.copyOf(records);
    }

    /** The next save rewrites the file without what could not be read, so keep the original for recovery. */
    private void backupUnreadable() {
        Path backup = file.resolveSibling(file.getFileName() + ".unreadable-" + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Loading must go on; the readable tombstones are still restored.
        }
    }

    @Override
    public void saveAll(Collection<TombstoneRecord> records) {
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (TombstoneRecord record : records) {
                String path = "tombstones." + record.id();
                yaml.set(path + ".owner-id", record.ownerId().toString());
                yaml.set(path + ".owner-name", record.ownerName());
                yaml.set(path + ".world-id", record.worldId().toString());
                yaml.set(path + ".x", record.x());
                yaml.set(path + ".y", record.y());
                yaml.set(path + ".z", record.z());
                yaml.set(path + ".yaw", record.yaw());
                yaml.set(path + ".expires-at", record.expiresAt().toString());
                for (Map.Entry<Integer, ItemStack> item : encodeItems(record.items()).entrySet()) {
                    yaml.set(path + ".items-by-slot." + item.getKey(), item.getValue());
                }
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save tombstones", exception);
        }
    }

    static Map<Integer, ItemStack> encodeItems(List<ItemStack> items) {
        Map<Integer, ItemStack> encoded = new LinkedHashMap<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack item = TombstoneItemLayout.cloneOrNull(items.get(slot));
            if (item != null) encoded.put(slot, item);
        }
        return Collections.unmodifiableMap(encoded);
    }

    static List<ItemStack> decodeItems(Map<Integer, ItemStack> encoded) {
        int size = encoded.keySet().stream().filter(slot -> slot >= 0).max(Integer::compareTo)
            .map(slot -> slot + 1).orElse(0);
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(size, null));
        encoded.forEach((slot, item) -> {
            if (slot >= 0 && slot < size) items.set(slot, TombstoneItemLayout.cloneOrNull(item));
        });
        return Collections.unmodifiableList(items);
    }

    private static List<ItemStack> loadItems(ConfigurationSection entry, int[] unreadable) {
        ConfigurationSection slots = entry.getConfigurationSection("items-by-slot");
        if (slots != null) {
            Map<Integer, ItemStack> encoded = new LinkedHashMap<>();
            for (String rawSlot : slots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(rawSlot);
                    ItemStack item = slots.getItemStack(rawSlot);
                    if (slot >= 0 && item != null) encoded.put(slot, item);
                    else unreadable[0]++;
                } catch (NumberFormatException ignored) {
                    // Ignore malformed slot keys while retaining other items.
                    unreadable[0]++;
                }
            }
            return decodeItems(encoded);
        }
        List<ItemStack> legacy = new ArrayList<>();
        for (Object item : entry.getList("items", List.of())) {
            if (item instanceof ItemStack stack) legacy.add(stack);
        }
        return legacy;
    }
}
