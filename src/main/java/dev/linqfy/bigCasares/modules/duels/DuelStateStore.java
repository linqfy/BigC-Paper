package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.GameMode;
import org.bukkit.Location;
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
        try {
            folder.mkdirs();
            yaml.save(file(player.getUniqueId()));
            return true;
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Duelos] No se pudo guardar el estado de " + player.getName(), failure);
            return false;
        }
    }

    /** Clears the escrowed amount once the wager has been paid out, so a crash cannot pay it twice. */
    void clearEscrow(UUID player) {
        File file = file(player);
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("escrow", 0L);
        try {
            yaml.save(file);
        } catch (IOException failure) {
            logger.log(Level.WARNING, "[Duelos] No se pudo actualizar la apuesta guardada de " + player, failure);
        }
    }

    /**
     * Gives the player back what the duel replaced and deletes the snapshot.
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
            return -1L;
        }
        player.closeInventory();
        ItemStack[] contents = new ItemStack[player.getInventory().getContents().length];
        ConfigurationSection inventory = yaml.getConfigurationSection("inventory");
        if (inventory != null) {
            for (String key : inventory.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < contents.length) {
                        contents[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(inventory.getString(key)));
                    }
                } catch (IllegalArgumentException corrupt) {
                    logger.warning("[Duelos] Item ilegible en el slot " + key + " de " + player.getName());
                }
            }
        }
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
        if (!file.delete()) {
            logger.warning("[Duelos] No se pudo borrar " + file.getName() + " tras restaurar a " + player.getName());
        }
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
