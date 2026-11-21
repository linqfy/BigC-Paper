package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds an unauthenticated player's inventory and real location on disk while they wait in limbo. The file is
 * written before the inventory is cleared. A fresh login observes the restoration revision stored in the
 * same player data as the inventory before retiring the stash.
 */
final class InventoryStash {

    private final Path directory;

    InventoryStash(Path directory) {
        this.directory = directory;
    }

    boolean exists(UUID playerId) {
        return Files.isRegularFile(file(playerId));
    }

    void write(UUID playerId, ItemStack[] contents, Location returnLocation) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("items", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        yaml.set("slots", contents.length);
        if (returnLocation != null && returnLocation.getWorld() != null) {
            yaml.set("location.world", returnLocation.getWorld().getName());
            yaml.set("location.x", returnLocation.getX());
            yaml.set("location.y", returnLocation.getY());
            yaml.set("location.z", returnLocation.getZ());
            yaml.set("location.yaw", (double) returnLocation.getYaw());
            yaml.set("location.pitch", (double) returnLocation.getPitch());
        }
        yaml.set("saved-at", System.currentTimeMillis());
        yaml.set("revision", UUID.randomUUID().toString());
        try {
            Files.createDirectories(directory);
            Path target = file(playerId);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not stash inventory of " + playerId, ex);
        }
    }

    Optional<Stashed> read(UUID playerId) {
        Optional<SavedStash> saved = load(playerId);
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        SavedStash snapshot = saved.get();
        YamlConfiguration yaml = snapshot.yaml();
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(yaml.getString("items", "")));
        Location location = null;
        World world = yaml.isSet("location.world") ? Bukkit.getWorld(yaml.getString("location.world", "")) : null;
        if (world != null) {
            location = new Location(world, yaml.getDouble("location.x"), yaml.getDouble("location.y"),
                yaml.getDouble("location.z"), (float) yaml.getDouble("location.yaw"),
                (float) yaml.getDouble("location.pitch"));
        }
        return Optional.of(new Stashed(items, location, snapshot.revision()));
    }

    Optional<String> revision(UUID playerId) {
        return load(playerId).map(SavedStash::revision);
    }

    boolean retireRestored(UUID playerId, String restoredRevision) {
        if (restoredRevision == null || revision(playerId).filter(restoredRevision::equals).isEmpty()) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(playerId));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete inventory stash of " + playerId, ex);
        }
    }

    private Optional<SavedStash> load(UUID playerId) {
        Path path = file(playerId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            yaml.loadFromString(source);
            String revision = yaml.getString("revision");
            if (revision == null) {
                revision = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
            }
            return Optional.of(new SavedStash(yaml, revision));
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Could not read inventory stash of " + playerId, ex);
        }
    }

    private Path file(UUID playerId) {
        return directory.resolve(playerId + ".yml");
    }

    private record SavedStash(YamlConfiguration yaml, String revision) {
    }

    record Stashed(ItemStack[] items, Location location, String revision) {
    }
}
