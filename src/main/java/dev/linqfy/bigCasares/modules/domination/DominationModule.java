package dev.linqfy.bigCasares.modules.domination;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dominaciones y Venganza: N muertes seguidas sobre el mismo jugador anuncian una Dominación;
 * cuando el dominado mata a su dominador se anuncia la Venganza.
 */
public final class DominationModule implements PluginModule {

    public static final String MODULE_ID = "domination-system";

    private final JavaPlugin plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private DominationTracker tracker;
    private DominationSettings settings;
    private File storageFile;

    public DominationModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        settings = DominationSettings.load(plugin.getConfig());
        tracker = new DominationTracker(settings.threshold());
        storageFile = new File(plugin.getDataFolder(), "data/domination/streaks.yml");
        load();
        scope.register("domination-state", () -> {
            save();
            tracker = null;
            settings = null;
            storageFile = null;
        });
        new BukkitRuntimeRegistrations(plugin, scope).registerListener("domination-listener", new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onDeath(PlayerDeathEvent event) {
                handleDeath(event.getEntity(), event.getEntity().getKiller());
            }
        });
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    private void handleDeath(Player victim, Player killer) {
        DominationTracker current = tracker;
        if (current == null || killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        DominationOutcome outcome = current.recordKill(killer.getUniqueId(), victim.getUniqueId());
        if (outcome.revenge()) {
            announce("§6§l¡VENGANZA! §e" + killer.getName() + " §7eliminó a su dominador §e" + victim.getName() + "§7.",
                settings.revengeSound());
            killer.sendTitle("§6§lVENGANZA", "§7contra §e" + victim.getName(), 5, 50, 15);
            victim.sendTitle("§6§lVENGANZA", "§e" + killer.getName() + " §7se vengó", 5, 50, 15);
        }
        if (outcome.domination()) {
            announce("§4§l¡DOMINACIÓN! §c" + killer.getName() + " §7está dominando a §c" + victim.getName()
                + " §8(" + outcome.streak() + " muertes seguidas)", settings.dominationSound());
            killer.sendTitle("§4§lDOMINACIÓN", "§7sobre §c" + victim.getName(), 5, 50, 15);
            victim.sendTitle("§4§lDOMINADO", "§c" + killer.getName() + " §7te domina", 5, 50, 15);
        } else if (outcome.streak() > current.threshold()) {
            killer.sendMessage("§7Seguís dominando a §c" + victim.getName() + " §8(" + outcome.streak() + ")");
        }
        save();
    }

    private void announce(String message, String sound) {
        plugin.getServer().broadcastMessage(message);
        if (sound.isEmpty()) {
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private void load() {
        if (!storageFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = yaml.getConfigurationSection("streaks");
        if (root == null) {
            return;
        }
        for (String killer : root.getKeys(false)) {
            ConfigurationSection victims = root.getConfigurationSection(killer);
            if (victims == null) {
                continue;
            }
            for (String victim : victims.getKeys(false)) {
                try {
                    tracker.restore(UUID.fromString(killer), UUID.fromString(victim), victims.getInt(victim));
                } catch (IllegalArgumentException invalid) {
                    plugin.getLogger().warning("Racha de dominación inválida: " + killer + " -> " + victim);
                }
            }
        }
    }

    private void save() {
        DominationTracker current = tracker;
        if (current == null || storageFile == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<DominationTracker.Pair, Integer> entry : current.snapshot().entrySet()) {
            yaml.set("streaks." + entry.getKey().killerId() + "." + entry.getKey().victimId(), entry.getValue());
        }
        try {
            File parent = storageFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(storageFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar las dominaciones", ex);
        }
    }
}
