package dev.linqfy.bigCasares.modules.cosmicniv;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One file per player: {@code <directory>/<uuid>.yml}. Enchantments are stored as a list of {@code key=level}
 * instead of a section, because Bukkit sections split keys on '.'.
 */
final class YamlCosmicNivMemoryStorage implements CosmicNivMemoryStorage {
    private final Path directory;

    YamlCosmicNivMemoryStorage(Path directory) {
        this.directory = directory;
    }

    /**
     * Loads the memory. A file that cannot be read is renamed to {@code <uuid>.yml.broken-<millis>} before the
     * error is thrown, so the next save does not overwrite it.
     */
    @Override
    public CosmicNivMemory load(UUID playerId) throws IOException {
        Path path = file(playerId);
        if (!Files.isRegularFile(path)) {
            return CosmicNivMemory.empty();
        }
        try {
            return parse(path);
        } catch (IOException broken) {
            Path kept = quarantine(path);
            throw new IOException(broken.getMessage() + " (kept as " + kept.getFileName() + ")", broken);
        }
    }

    private static Path quarantine(Path path) throws IOException {
        String base = path.getFileName() + ".broken-" + System.currentTimeMillis();
        Path target = path.resolveSibling(base);
        for (int attempt = 1; Files.exists(target); attempt++) {
            target = path.resolveSibling(base + "-" + attempt);
        }
        Files.move(path, target);
        return target;
    }

    private static CosmicNivMemory parse(Path path) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException invalid) {
            throw new IOException("Invalid memory file " + path + ": " + invalid.getMessage(), invalid);
        }
        Map<CosmicNivMemorySlot, CosmicNivMemory.Remembered> slots = new EnumMap<>(CosmicNivMemorySlot.class);
        ConfigurationSection root = yaml.getConfigurationSection("slots");
        if (root != null) {
            for (String rawSlot : root.getKeys(false)) {
                CosmicNivMemorySlot slot = CosmicNivMemorySlot.fromConfigKey(rawSlot)
                    .orElseThrow(() -> new IOException("Unknown memory slot " + rawSlot + " in " + path));
                ConfigurationSection entry = root.getConfigurationSection(rawSlot);
                if (entry == null || entry.getString("item") == null) {
                    throw new IOException("Memory slot " + rawSlot + " has no item in " + path);
                }
                Map<String, Integer> enchantments = new LinkedHashMap<>();
                for (String raw : entry.getStringList("enchantments")) {
                    int separator = raw.lastIndexOf('=');
                    if (separator <= 0) {
                        throw new IOException("Invalid enchantment " + raw + " in " + path);
                    }
                    try {
                        enchantments.put(raw.substring(0, separator), Integer.parseInt(raw.substring(separator + 1)));
                    } catch (NumberFormatException invalid) {
                        throw new IOException("Invalid enchantment level " + raw + " in " + path, invalid);
                    }
                }
                try {
                    slots.put(slot, new CosmicNivMemory.Remembered(
                        CosmicNivItem.of(entry.getString("item"), enchantments), entry.getLong("last-seen", 0L)));
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid item in memory slot " + rawSlot + " of " + path, invalid);
                }
            }
        }
        return new CosmicNivMemory(slots);
    }

    @Override
    public void save(UUID playerId, CosmicNivMemory memory) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        memory.slots().forEach((slot, remembered) -> {
            String path = "slots." + slot.configKey();
            yaml.set(path + ".item", remembered.item().key());
            yaml.set(path + ".enchantments", remembered.item().enchantments().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList());
            yaml.set(path + ".last-seen", remembered.lastSeenMillis());
        });
        Files.createDirectories(directory);
        Path path = file(playerId);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path file(UUID playerId) {
        return directory.resolve(playerId + ".yml");
    }
}
