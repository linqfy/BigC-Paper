package dev.linqfy.bigCasares.modules.alexscaves.integration;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.IntegrationSettings;
import dev.linqfy.bigCasares.modules.missions.RewardGateway;
import dev.linqfy.bigCasares.modules.missions.VaultEconomyGateway;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds the {@link PlacementReward} list for the enabled integrations of the server. */
public final class BossIntegrations {
    private BossIntegrations() {
    }

    public static List<PlacementReward> rewards(Plugin plugin, IntegrationSettings settings) {
        List<PlacementReward> rewards = new ArrayList<>();
        if (settings.dangerPlacements() && plugin instanceof BigCasares bigCasares) {
            rewards.add((playerId, bossId, placement) -> {
                var module = bigCasares.getDangerModule();
                if (module == null) {
                    return;
                }
                module.service().ifPresent(danger -> danger.awardBossPlacement(playerId, placement));
                try {
                    module.refreshPresentation(playerId);
                } catch (RuntimeException failure) {
                    plugin.getLogger().warning("[AlexsCaves] No se pudo refrescar el Danger de " + playerId + ": " + failure.getMessage());
                }
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§6" + display(bossId) + " §7- Puesto #" + placement + " por tu daño al jefe.");
                }
            });
        }
        if (settings.economyEnabled()) {
            rewards.add(new EconomyPlacementReward(settings, () -> gateway(plugin), plugin.getLogger()::warning,
                (playerId, bossId, placement, amount) -> {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage("§6" + display(bossId) + " §7- Puesto #" + placement + ": §e"
                            + VaultEconomyGateway.formatFallback(amount, "$") + "§7.");
                    }
                }));
        }
        return rewards;
    }

    /** Vault is optional: its classes are only touched once the plugin is known to be there. */
    private static Optional<RewardGateway> gateway(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null || !(plugin instanceof JavaPlugin java)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new VaultEconomyGateway(java, VaultEconomyGateway.resolveOrThrow(java)));
        } catch (IllegalStateException | LinkageError missing) {
            return Optional.empty();
        }
    }

    static String display(String bossId) {
        return bossId.isEmpty() ? bossId : bossId.substring(0, 1).toUpperCase(Locale.ROOT) + bossId.substring(1);
    }
}
