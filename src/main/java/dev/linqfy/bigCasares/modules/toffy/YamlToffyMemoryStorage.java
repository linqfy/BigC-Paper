package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.Bukkit;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One file per player: {@code <directory>/<uuid>.yml}. Enchantments are stored as a list of {@code key=level}
 * instead of a section, because Bukkit sections split keys on '.'.
 */
final class YamlToffyMemoryStorage implements ToffyMemoryStorage {
    private final Path directory;
    private final Consumer<String> severe;
    private final Set<UUID> readOnly = new HashSet<>();

    YamlToffyMemoryStorage(Path directory) {
        this(directory, message -> Bukkit.getLogger().severe(message));
    }

    /**
     * @param severe receives the SEVERE messages, once per problem
     */
    YamlToffyMemoryStorage(Path directory, Consumer<String> severe) {
        this.directory = directory;
        this.severe = severe;
    }

    /**
     * Loads the memory. A file that cannot be read is renamed to {@code <uuid>.yml.broken-<millis>} before the
     * error is thrown, so the next save does not overwrite it. If it cannot be renamed either, the player's memory
     * becomes read-only for this session: nothing is saved for that UUID, so the broken file is never overwritten.
     */
    @Override
    public ToffyMemory load(UUID playerId) throws IOException {
        Path path = file(playerId);
        if (!Files.isRegularFile(path)) {
            return ToffyMemory.empty();
        }
        try {
            return parse(path);
        } catch (IOException broken) {
            Path kept;
            try {
                kept = quarantine(path);
            } catch (IOException cannotMove) {
                if (readOnly.add(playerId)) {
                    severe.accept("Equipment memory file " + path.getFileName() + " is broken and could not be moved"
                        + " aside (" + cannotMove.getMessage() + "). Nothing will be saved for this player until"
                        + " the server restarts, so the file is not overwritten.");
                }
                throw new IOException(broken.getMessage() + " (not moved aside, not saved this session)", broken);
            }
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

    private static ToffyMemory parse(Path path) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException invalid) {
            throw new IOException("Invalid memory file " + path + ": " + invalid.getMessage(), invalid);
        }
        Map<ToffyMemorySlot, ToffyMemory.Remembered> slots = new EnumMap<>(ToffyMemorySlot.class);
        ConfigurationSection root = yaml.getConfigurationSection("slots");
        if (root != null) {
            for (String rawSlot : root.getKeys(false)) {
                ToffyMemorySlot slot = ToffyMemorySlot.fromConfigKey(rawSlot)
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
                    slots.put(slot, new ToffyMemory.Remembered(
                        ToffyItem.of(entry.getString("item"), enchantments), entry.getLong("last-seen", 0L)));
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid item in memory slot " + rawSlot + " of " + path, invalid);
                }
            }
        }
        return new ToffyMemory(slots);
    }

    @Override
    public void save(UUID playerId, ToffyMemory memory) throws IOException {
        if (readOnly.contains(playerId)) {
            return;
        }
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
