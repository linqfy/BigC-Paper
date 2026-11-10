package dev.linqfy.bigCasares.modules.forex;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.UUID;

/**
 * FOREX & Cryptos: virtual emeralds as the safe haven and player-launched shitcoins with creator fees, staking
 * dividends, rug pulls, random pumps and the 50/50 on big bubbles. Everything settles in Vault money.
 */
public final class ForexModule implements PluginModule {

    public static final String MODULE_ID = "forex";

    private final BigCasares plugin;
    private final ForexTeams teams;
    private RuntimeRegistrationScope compatibilityScope;
    private ForexRuntime runtime;

    public ForexModule(BigCasares plugin, ForexTeams teams) {
        this.plugin = plugin;
        this.teams = Objects.requireNonNull(teams, "teams");
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
        if (plugin == null) {
            return;
        }
        PluginCommand command = plugin.getCommand("forex");
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: forex");
        }
        Economy economy = resolveEconomy();
        if (economy == null) {
            plugin.getLogger().warning("[Forex] Vault or an economy provider is missing; the market stays closed.");
            return;
        }
        ForexSettings settings = ForexSettings.load(plugin.getConfig());
        ForexRuntime created = new ForexRuntime(plugin, settings, new VaultForexBank(plugin.getServer(), economy), teams);
        created.start();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("forex-runtime", created::stop);
        registrations.registerListener("forex-listener", new ForexListener());
        ForexCommand handler = new ForexCommand(created);
        registrations.bindCommand("forex-command", command, handler, handler);
        registrations.scheduleRepeating("forex-market", created::tickMarket, 20L, 20L);
        registrations.scheduleRepeating("forex-effects", created::tickEffects, 1L, 1L);
        registrations.scheduleRepeating("forex-aura", created::tickAura, 40L, 40L);
        runtime = created;
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        runtime = null;
    }

    public boolean isOpen() {
        return runtime != null;
    }

    /** Holds enough of the team's official coin to walk through its protected chests. */
    public boolean hasClanPass(UUID player, String teamId) {
        ForexRuntime current = runtime;
        return current != null && current.market().hasClanPass(player, teamId);
    }

    /** Multiplier Don Casares applies to this player's loan interest: below 1 is prestige, above is a record. */
    public double creditMultiplier(UUID player) {
        ForexRuntime current = runtime;
        return current == null ? 1.0 : current.market().creditMultiplier(player);
    }

    private Economy resolveEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
