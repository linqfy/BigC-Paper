package dev.linqfy.bigCasares.modules.alexscaves.integration;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.IntegrationSettings;
import dev.linqfy.bigCasares.modules.missions.RewardGateway;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pays the configured money for a boss placement through the plugin's {@link RewardGateway}. The gateway is looked
 * up when the first reward is due, so a server without Vault (or without an economy provider) just logs once and
 * keeps running.
 */
public final class EconomyPlacementReward implements PlacementReward {
    private final IntegrationSettings settings;
    private final Supplier<Optional<RewardGateway>> gateway;
    private final Consumer<String> warn;
    private final PlacementNotice notice;
    private boolean unavailableLogged;
    /** Resolved once and reused; dropped when a deposit fails so a changed provider is looked up again. */
    private RewardGateway cached;

    /** Tells the player what was paid; may be a no-op. */
    @FunctionalInterface
    public interface PlacementNotice {
        void paid(UUID playerId, String bossId, int placement, double amount);
    }

    public EconomyPlacementReward(IntegrationSettings settings, Supplier<Optional<RewardGateway>> gateway,
                                  Consumer<String> warn, PlacementNotice notice) {
        this.settings = settings;
        this.gateway = gateway;
        this.warn = warn;
        this.notice = notice;
    }

    @Override
    public void award(UUID playerId, String bossId, int placement) {
        if (!settings.economyEnabled()) {
            return;
        }
        double amount = settings.reward(bossId, placement);
        if (!(amount > 0.0)) {
            return;
        }
        Optional<RewardGateway> resolved = cached != null ? Optional.of(cached) : gateway.get();
        if (resolved.isEmpty()) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                warn.accept("[AlexsCaves] integration.economy.enabled está activo pero no hay Vault con un proveedor de "
                    + "economía: los premios de dinero de los jefes no se pagan.");
            }
            return;
        }
        cached = resolved.get();
        try {
            cached.deposit(playerId, amount);
        } catch (RuntimeException failure) {
            cached = null;
            throw failure;
        }
        notice.paid(playerId, bossId, placement, amount);
    }
}
