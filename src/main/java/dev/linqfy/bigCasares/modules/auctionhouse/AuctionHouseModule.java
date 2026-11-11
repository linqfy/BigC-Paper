package dev.linqfy.bigCasares.modules.auctionhouse;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Clock;
import java.util.List;

/**
 * Player auction house behind {@code /ah}: fixed-price listings paid in the server's Vault currency.
 * State lives in {@code data/auction-house.yml}.
 */
public final class AuctionHouseModule implements PluginModule {

    public static final String MODULE_ID = "auction-house";
    static final String COMMAND = "ah";
    private static final long EXPIRY_CHECK_TICKS = 20L * 60;

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private AuctionHouseService<ItemStack> service;

    public AuctionHouseModule(BigCasares plugin) {
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
        if (plugin == null) {
            return;
        }
        PluginCommand command = plugin.getCommand(COMMAND);
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: " + COMMAND);
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        Economy economy = resolveEconomy();
        if (economy == null) {
            plugin.getLogger().warning("[AuctionHouse] Vault or an economy provider is missing; /ah stays closed.");
            CommandExecutor closed = (sender, ignored, label, args) -> {
                sender.sendMessage("§cLa casa de subastas no está disponible.");
                return true;
            };
            registrations.bindCommand("auction-house-command", command, closed, (sender, ignored, alias, args) -> List.of());
            return;
        }

        AuctionHouseSettings settings = AuctionHouseSettings.load(plugin.getConfig());
        AuctionBank bank = new VaultAuctionBank(plugin.getServer(), economy);
        Clock clock = Clock.systemUTC();
        AuctionHouseService<ItemStack> created = new AuctionHouseService<>(settings, bank,
            new YamlAuctionStore(plugin.getDataFolder().toPath().resolve("data/auction-house.yml"), plugin.getLogger()),
            clock);
        AuctionHouseController controller = new AuctionHouseController(plugin.getServer(), created,
            new AuctionMenus(created, bank, clock));

        registrations.registerListener("auction-house-listener", new AuctionHouseListener(controller,
            action -> registrations.scheduleImmediate("auction-house-menu", action)));
        AuctionHouseCommand handler = new AuctionHouseCommand(controller);
        registrations.bindCommand("auction-house-command", command, handler, handler);
        registrations.scheduleRepeating("auction-house-expiry", created::expire, EXPIRY_CHECK_TICKS, EXPIRY_CHECK_TICKS);
        registrations.ownCleanup("auction-house-service", () -> service = null);
        service = created;
        plugin.getLogger().info("[AuctionHouse] Module enabled with " + created.active().size() + " active listings.");
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        service = null;
    }

    public boolean isOpen() {
        return service != null;
    }

    private Economy resolveEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
