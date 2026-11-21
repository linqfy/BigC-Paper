package dev.linqfy.bigCasares.modules.quadrants;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.quadrants.quests.PvpQuestController;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Cuatro cuadrantes: protegido, consensuado y dos de PvP libre.
 * Un golpe PvP deja a ambos jugadores en combate y mientras dure no pueden entrar a la zona Chill.
 */
public final class QuadrantModule implements PluginModule {

    public static final String MODULE_ID = "pvp-quadrants";

    private final JavaPlugin plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private QuadrantRuntime runtime;

    public QuadrantModule(JavaPlugin plugin) {
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
        QuadrantSettings settings;
        try {
            settings = QuadrantSettings.load(plugin.getConfig());
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("[Cuadrantes] Configuración inválida, uso valores por defecto: "
                + invalid.getMessage());
            settings = QuadrantSettings.defaults();
        }
        QuadrantRuntime created = new QuadrantRuntime(settings.map(),
            new CombatTagTracker(settings.combatTagSeconds(), System::currentTimeMillis));
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        scope.register("quadrant-runtime", created::clear);
        registrations.registerListener("quadrant-listener", new QuadrantListener(created));
        registrations.scheduleRepeating("quadrant-combat-tick", () -> tick(created), 20L, 20L);
        plugin.getServer().getOnlinePlayers().forEach(player -> created.observe(player, player.getLocation()));
        runtime = created;
        PvpQuestController configuredQuests = null;
        if (plugin.getConfig().getBoolean("pvp-quadrants.quests.enabled", true)) {
            try {
                configuredQuests = new PvpQuestController(plugin, this::isFullPvp);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("[Cuadrantes] Misiones PvP desactivadas por configuración inválida: "
                    + invalid.getMessage());
            }
        }
        final PvpQuestController quests = configuredQuests;
        if (quests != null) registrations.registerListener("quadrant-pvp-quests", quests);
        var command = plugin.getCommand("pvp");
        if (command == null) throw new IllegalStateException("Missing pvp command");
        registrations.bindCommand("quadrant-pvp-command", command, (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Solo jugadores pueden usar este comando.");
                return true;
            }
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("quests") && quests != null) quests.command(player, args);
                else player.sendMessage("§cUsá /pvp o /pvp quests.");
                return true;
            }
            if (created.isOptedIn(player) && created.isTagged(player)) {
                player.sendMessage("§cNo podés salir del PvP consensuado mientras estás en combate.");
                return true;
            }
            boolean enabled = created.toggleOptIn(player);
            player.sendMessage(enabled ? "§ePvP consensuado activado. Ambos jugadores deben aceptarlo."
                : "§aPvP consensuado desactivado.");
            return true;
        }, null);
        var services = plugin.getServer().getServicesManager();
        FullPvpPolicy fullPvp = this::isFullPvp;
        services.register(FullPvpPolicy.class, fullPvp, plugin, ServicePriority.Normal);
        scope.register("quadrant-full-pvp-policy", () -> services.unregister(FullPvpPolicy.class, fullPvp));
        plugin.getLogger().info("[Cuadrantes] NW protegido, NE consensuado, SW/SE PvP libre; búfer "
            + settings.combatTagSeconds() + "s.");
    }

    @Override
    public void onDisable() {
        QuadrantRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.clear();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    /** Zona del jugador en su posición actual, si el mundo está dividido en cuadrantes. */
    public Optional<QuadrantZone> zoneAt(Location location) {
        QuadrantRuntime current = runtime;
        return current == null ? Optional.empty() : current.zoneAt(location);
    }

    public boolean isFullPvp(Location location) {
        return zoneAt(location).filter(QuadrantZone.ANARCHY::equals).isPresent();
    }

    public boolean isInCombat(UUID playerId) {
        QuadrantRuntime current = runtime;
        return current != null && current.combat().isTagged(playerId);
    }

    /**
     * Cada segundo: muestra la cuenta regresiva del combate y devuelve a la Anarquía a quien esté
     * en combate dentro de la zona Chill (monturas, elytra u otros movimientos sin evento de jugador).
     */
    private void tick(QuadrantRuntime current) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (current.combat().expire(playerId)) {
                player.sendActionBar("§aYa no estás en combate.");
                continue;
            }
            if (!current.isTagged(player)) {
                continue;
            }
            if (!current.exempt(player)
                && current.zoneAt(player.getLocation()).filter(QuadrantZone.CHILL::equals).isPresent()) {
                Optional<Location> back = current.lastOutsideChill(playerId)
                    .filter(location -> location.getWorld() == player.getWorld());
                if (back.isPresent()) {
                    if (player.isInsideVehicle()) {
                        player.leaveVehicle();
                    }
                    player.teleport(back.get());
                }
            }
            player.sendActionBar("§c⚔ En combate: " + current.combat().remainingSeconds(playerId) + "s");
        }
    }
}
