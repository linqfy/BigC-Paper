package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * "El Gran Casares": card tables, the big roulette, slot machines, the fight pit, the loan shark and his
 * collector, the pawn shop and the bar. Bets are paid in the server's Vault currency ("fichas").
 */
public final class CasinoModule implements PluginModule {

    public static final String MODULE_ID = "casino";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private CasinoRuntime runtime;
    private ToDoubleFunction<UUID> creditRating = ignored -> 1.0;

    public CasinoModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    /** Lets another module adjust Don Casares' interest per borrower (the forex credit history). */
    public void setCreditRating(ToDoubleFunction<UUID> rating) {
        this.creditRating = rating == null ? ignored -> 1.0 : rating;
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
        PluginCommand command = plugin.getCommand("casino");
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: casino");
        }
        Economy economy = resolveEconomy();
        if (economy == null) {
            plugin.getLogger().warning("[Casino] Vault or an economy provider is missing; the casino stays closed.");
            return;
        }
        CasinoSettings settings = CasinoSettings.load(plugin.getConfig());
        CasinoRuntime created = new CasinoRuntime(plugin, settings, new VaultCasinoBank(plugin.getServer(), economy));
        created.loans().creditRating(creditRating);
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("casino-runtime", created::stop);
        registrations.registerListener("casino-listener", new CasinoListener(created));
        CasinoCommand handler = new CasinoCommand(created);
        registrations.bindCommand("casino-command", command, handler, handler);
        created.start();
        registrations.scheduleRepeating("casino-tick", created::tick, 1L, 1L);
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

    private Economy resolveEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
