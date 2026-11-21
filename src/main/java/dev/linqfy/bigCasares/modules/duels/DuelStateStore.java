package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saves everything a duel replaces (position, inventory, health, effects, game mode and the escrowed wager) to
 * one file per player before the fight, so players get it back even after a disconnect or a crash.
 */
final class DuelStateStore {

    private static final NamespacedKey RESTORED_REVISION = new NamespacedKey("bigcasares", "duel_restored_revision");
    private final File folder;
    private final Server server;
    private final Logger logger;

    DuelStateStore(File folder, Server server, Logger logger) {
        this.folder = folder;
        this.server = server;
        this.logger = logger;
    }

    boolean has(UUID player) {
        return file(player).isFile();
    }

    /** Writes the snapshot; returns false (and changes nothing in game) when it could not be stored. */
    boolean save(Player player, long escrow) {
        if (has(player.getUniqueId()) && !restoredInMemory(player)) {
            logger.warning("[Duelos] Hay un estado pendiente de " + player.getName() + "; no se reemplazará.");
            return false;
        }
        // Return any item held on the cursor before taking the inventory snapshot.
        player.closeInventory();
        YamlConfiguration yaml = new YamlConfiguration();
        Location home = player.getLocation();
        yaml.set("location.world", home.getWorld().getUID().toString());
        yaml.set("location.x", home.getX());
        yaml.set("location.y", home.getY());
        yaml.set("location.z", home.getZ());
        yaml.set("location.yaw", home.getYaw());
        yaml.set("location.pitch", home.getPitch());
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                yaml.set("inventory." + slot, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
        }
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exhaustion", player.getExhaustion());
        yaml.set("fire-ticks", player.getFireTicks());
        yaml.set("game-mode", player.getGameMode().name());
        yaml.set("allow-flight", player.getAllowFlight());
        yaml.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        yaml.set("escrow", escrow);
        yaml.set("revision", UUID.randomUUID().toString());
        try {
            saveSnapshot(file(player.getUniqueId()), yaml);
            return true;
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Duelos] No se pudo guardar el estado de " + player.getName(), failure);
            return false;
        }
    }

    boolean restoredInMemory(Player player) {
        File snapshot = file(player.getUniqueId());
        if (!snapshot.isFile()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(snapshot);
        String revision = yaml.getString("revision");
        String marker = player.getPersistentDataContainer().get(RESTORED_REVISION, PersistentDataType.STRING);
        return DuelSnapshotCodec.mayReplace(revision, marker);
    }

    void retireOnJoin(Player player) {
        File snapshot = file(player.getUniqueId());
        if (!snapshot.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(snapshot);
        String marker = player.getPersistentDataContainer().get(RESTORED_REVISION, PersistentDataType.STRING);
        if (DuelSnapshotCodec.mayRetire(yaml.getString("revision"), marker, true) && !snapshot.delete()) {
            logger.warning("[Duelos] No se pudo retirar el estado restaurado de " + player.getName());
        }
    }

    private void saveSnapshot(File target, YamlConfiguration yaml) throws IOException {
        Path parent = target.toPath().toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "duel-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(yaml.saveToString());
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
                try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Gives the player back what the duel replaced; a later fresh join retires the snapshot.
     *
     * @return the escrowed wager still owed to the player (only non-zero after a crash), or -1 when there
     *     was nothing to restore
     */
    long restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.isFile()) {
            return -1L;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            logger.log(Level.SEVERE, "[Duelos] Estado guardado ilegible para " + player.getName()
                + "; se conserva el archivo " + file.getName(), failure);
            player.sendMessage("§cNo se pudo restaurar tu estado de duelo. Contactá al staff antes de continuar.");
            return -1L;
        }
        ItemStack[] contents = new ItemStack[player.getInventory().getContents().length];
        try {
            var decoded = DuelSnapshotCodec.decode(yaml.getConfigurationSection("inventory"), contents.length,
                ItemStack::deserializeBytes);
            decoded.forEach((slot, item) -> contents[slot] = item);
        } catch (RuntimeException corrupt) {
            logger.log(Level.SEVERE, "[Duelos] Inventario guardado ilegible para " + player.getName()
                + "; se conserva el archivo " + file.getName(), corrupt);
            player.sendMessage("§cNo se pudo restaurar tu inventario. Contactá al staff antes de continuar.");
            return -1L;
        }
        String revision = yaml.getString("revision");
        if (revision == null) {
            revision = UUID.randomUUID().toString();
            yaml.set("revision", revision);
            try {
                saveSnapshot(file, yaml);
            } catch (IOException failure) {
                logger.log(Level.SEVERE, "[Duelos] No se pudo actualizar el estado guardado de " + player.getName(), failure);
                return -1L;
            }
        }
        player.closeInventory();
        player.getInventory().setContents(contents);
        player.setItemOnCursor(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        List<?> effects = yaml.getList("effects", List.of());
        for (Object effect : effects) {
            if (effect instanceof PotionEffect potion) {
                player.addPotionEffect(potion);
            }
        }
        try {
            player.setGameMode(GameMode.valueOf(yaml.getString("game-mode", "SURVIVAL")));
        } catch (IllegalArgumentException unknown) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(yaml.getBoolean("allow-flight", false));
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.max(1.0, Math.min(cap, yaml.getDouble("health", cap))));
        player.setFoodLevel(yaml.getInt("food", 20));
        player.setSaturation((float) yaml.getDouble("saturation", 5.0));
        player.setExhaustion((float) yaml.getDouble("exhaustion", 0.0));
        player.setFireTicks(yaml.getInt("fire-ticks", 0));
        player.setFallDistance(0f);
        player.teleport(home(yaml), PlayerTeleportEvent.TeleportCause.PLUGIN);
        long escrow = yaml.getLong("escrow", 0L);
        player.getPersistentDataContainer().set(RESTORED_REVISION, PersistentDataType.STRING, revision);
        player.saveData();
        return escrow;
    }

    private Location home(YamlConfiguration yaml) {
        World world = null;
        try {
            world = server.getWorld(UUID.fromString(yaml.getString("location.world", "")));
        } catch (IllegalArgumentException malformed) {
            // Falls back to the main world below.
        }
        if (world == null) {
            return server.getWorlds().getFirst().getSpawnLocation();
        }
        return new Location(world, yaml.getDouble("location.x"), yaml.getDouble("location.y"),
            yaml.getDouble("location.z"), (float) yaml.getDouble("location.yaw"), (float) yaml.getDouble("location.pitch"));
    }

    private File file(UUID player) {
        return new File(folder, player + ".yml");
    }
}
