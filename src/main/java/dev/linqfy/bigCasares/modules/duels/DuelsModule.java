package dev.linqfy.bigCasares.modules.duels;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

/**
 * 1v1 duels: {@code /duel <jugador>} with a fixed kit or a ban/pick menu, three arena sizes in a separate void
 * world, the player's own inventory swapped for the kit and given back afterwards, and optional Vault wagers.
 */
public final class DuelsModule implements PluginModule {

    public static final String MODULE_ID = "duels";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;

    public DuelsModule(BigCasares plugin) {
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
        PluginCommand command = plugin.getCommand("duel");
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: duel");
        }
        DuelSettings settings = DuelSettings.load(plugin.getConfig(), plugin.getLogger()::warning);
        Economy economy = resolveEconomy();
        if (economy == null) {
            plugin.getLogger().info("[Duelos] Sin Vault/economía: los duelos funcionan sin apuestas.");
        }
        DuelService service = new DuelService(
            plugin.getServer(),
            plugin.getLogger(),
            settings,
            new DuelArenaWorld(plugin.getServer(), settings.worldName()),
            new DuelStateStore(new File(plugin.getDataFolder(), "duels/saved"), plugin.getServer(), plugin.getLogger()),
            new DuelEscrow(economy == null ? null : new VaultDuelBank(plugin.getServer(), economy)),
            new DuelKitItems(plugin.getLogger()),
            System::currentTimeMillis
        );
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("duels-service", service::shutdown);
        registrations.registerListener("duels-listener", new DuelListener(service));
        DuelCommand handler = new DuelCommand(service, plugin.getServer());
        registrations.bindCommand("duels-command", command, handler, handler);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            service.scheduleRestoreIfSaved(online.getUniqueId());
        }
        registrations.scheduleRepeating("duels-tick", service::tick, 5L, 5L);
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    private Economy resolveEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
