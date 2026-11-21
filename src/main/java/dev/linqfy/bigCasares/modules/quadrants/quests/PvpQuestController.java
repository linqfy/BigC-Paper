package dev.linqfy.bigCasares.modules.quadrants.quests;

import dev.linqfy.bigCasares.modules.domination.DominationSettings;
import dev.linqfy.bigCasares.modules.missions.VaultEconomyGateway;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Misiones semanales opcionales separadas de las misiones que se sortean. */
public final class PvpQuestController implements Listener {
    private final JavaPlugin plugin;
    private final Predicate<Location> fullPvp;
    private final PvpQuestService quests;

    public PvpQuestController(JavaPlugin plugin, Predicate<Location> fullPvp) {
        this.plugin = plugin;
        this.fullPvp = fullPvp;
        var config = plugin.getConfig();
        int rivals = config.getInt("pvp-quadrants.quests.rivals.goal", 5);
        int dominations = config.getInt("pvp-quadrants.quests.domination.goal", 1);
        double rivalsMoney = config.getDouble("pvp-quadrants.quests.rivals.reward", 300.0);
        double dominationMoney = config.getDouble("pvp-quadrants.quests.domination.reward", 500.0);
        int streak = DominationSettings.load(config).threshold();
        this.quests = new PvpQuestService(new PvpQuestStore(plugin.getDataFolder().toPath().resolve("data/pvp-quests")),
            rivals, dominations, streak, rivalsMoney, dominationMoney);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())
            || !fullPvp.test(killer.getLocation()) || !fullPvp.test(victim.getLocation())) return;
        try {
            quests.kill(killer.getUniqueId(), victim.getUniqueId(), week());
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar el progreso de misiones PvP", failure);
            killer.sendMessage("§cNo se pudo guardar tu progreso de misión PvP. Avisá a un administrador.");
        }
    }

    public void command(Player player, String[] args) {
        try {
            String week = week();
            if (args.length == 1) {
                PvpQuestState state = quests.status(player.getUniqueId(), week);
                player.sendMessage("§6Misiones PvP semanales §7(reinicio lunes 00:00 UTC)");
                player.sendMessage("§eRivales: §f" + state.rivals().size() + "/" + quests.rivalGoal()
                    + " §7— $" + quests.rivalReward() + " — " + label(state.rivalStarted(), state.rivalClaim()));
                player.sendMessage("§eDominaciones: §f" + state.dominated().size() + "/" + quests.dominationGoal()
                    + " §7— $" + quests.dominationReward() + " — " + label(state.dominationStarted(), state.dominationClaim()));
                player.sendMessage("§7/pvp quests start <rivals|domination>  •  /pvp quests claim <rivals|domination>");
                player.sendMessage("§7Cuentan rivales distintos y solo en PvP libre; las rachas empiezan al aceptar.");
                return;
            }
            if (args.length != 3 || !(args[2].equalsIgnoreCase("rivals") || args[2].equalsIgnoreCase("domination"))) {
                player.sendMessage("§cUsá /pvp quests [start|claim] <rivals|domination>.");
                return;
            }
            String quest = args[2].toLowerCase(Locale.ROOT);
            if (args[1].equalsIgnoreCase("start")) {
                player.sendMessage(quests.start(player.getUniqueId(), quest, week)
                    ? "§aMisión aceptada. Solo cuenta el PvP libre."
                    : "§eYa aceptaste esa misión esta semana o tiene un pago pendiente.");
            } else if (args[1].equalsIgnoreCase("claim")) {
                if (!quests.ready(player.getUniqueId(), quest, week)) {
                    player.sendMessage("§eLa misión no está lista, ya fue cobrada o tiene un pago pendiente.");
                    return;
                }
                VaultEconomyGateway economy = new VaultEconomyGateway(plugin, VaultEconomyGateway.resolveOrThrow(plugin));
                if (quests.claim(player.getUniqueId(), quest, week, economy)) player.sendMessage("§aRecompensa depositada.");
            } else {
                player.sendMessage("§cUsá /pvp quests [start|claim] <rivals|domination>.");
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Falló la misión PvP de " + player.getUniqueId(), failure);
            player.sendMessage("§cNo se pudo completar la operación. Si el pago quedó pendiente, avisá a un administrador.");
        }
    }

    static String week() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    private static String label(boolean started, PvpQuestState.Claim claim) {
        return switch (claim) {
            case PENDING -> "pago pendiente; consultá a un administrador";
            case PAID -> "cobrada";
            case NONE -> started ? "activa" : "sin aceptar";
        };
    }
}
