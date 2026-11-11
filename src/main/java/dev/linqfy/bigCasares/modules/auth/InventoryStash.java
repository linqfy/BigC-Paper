package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds an unauthenticated player's inventory and real location on disk while they wait in limbo. The file is
 * written before the inventory is cleared, so a crash never loses items, and restoring replaces the inventory,
 * so a crash never duplicates them.
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
        try {
            Files.createDirectories(directory);
            Path target = file(playerId);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not stash inventory of " + playerId, ex);
        }
    }

    Optional<Stashed> read(UUID playerId) {
        Path path = file(playerId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Could not read inventory stash of " + playerId, ex);
        }
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(yaml.getString("items", "")));
        Location location = null;
        World world = yaml.isSet("location.world") ? Bukkit.getWorld(yaml.getString("location.world", "")) : null;
        if (world != null) {
            location = new Location(world, yaml.getDouble("location.x"), yaml.getDouble("location.y"),
                yaml.getDouble("location.z"), (float) yaml.getDouble("location.yaw"),
                (float) yaml.getDouble("location.pitch"));
        }
        return Optional.of(new Stashed(items, location));
    }

    void delete(UUID playerId) {
        try {
            Files.deleteIfExists(file(playerId));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete inventory stash of " + playerId, ex);
        }
    }

    private Path file(UUID playerId) {
        return directory.resolve(playerId + ".yml");
    }

    record Stashed(ItemStack[] items, Location location) {
    }
}
