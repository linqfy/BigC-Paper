package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.dimension.persistence.DimensionDataStore;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * {@code dimension-framework}: hosts the {@link DimensionPlatform}. Dimension
 * modules (e.g. the Sift) register into it after it enables, so it must be
 * registered before them in {@code BigCasares.initializeRuntime()}.
 */
public final class DimensionModule implements PluginModule {

    public static final String MODULE_ID = "dimension-framework";

    private final JavaPlugin plugin;
    private final BiPredicate<ItemStack, String> itemMatcher;
    private RuntimeRegistrationScope compatibilityScope;
    private DimensionRuntime runtime;
    private PortalService portals;

    public DimensionModule(JavaPlugin plugin, BiPredicate<ItemStack, String> itemMatcher) {
        this.plugin = plugin;
        this.itemMatcher = itemMatcher == null
            ? (stack, id) -> stack.getType().getKey().asString().equals(id)
            : itemMatcher;
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
        DimensionSettings settings = DimensionSettings.load(plugin.getConfig());
        settings.warnings().forEach(warning -> plugin.getLogger().warning("[Dimensions] " + warning));
        DimensionDataStore store = new DimensionDataStore(
            plugin.getDataFolder().toPath().resolve("dimensions/state.yml"), plugin.getLogger());
        store.load();

        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        DimensionRuntime created = new DimensionRuntime(plugin, scope, settings, store);
        this.runtime = created;
        registrations.ownCleanup("dimension-runtime", created::shutdown);
        this.portals = new PortalService(plugin, created, store, itemMatcher);
        registrations.registerListener("dimension-listener",
            new DimensionListener(created, portals, () -> registrations.scheduleImmediate("phase-recheck", this::recheckCycles)));
        registrations.scheduleRepeating("dimension-autosave", () -> {
            if (created.hasDirtyStore()) {
                created.saveStore();
            }
        }, settings.autosaveTicks(), settings.autosaveTicks());
        registrations.scheduleRepeating("portal-ambience", portals::emitAmbience, 20L, 10L);

        PluginCommand command = plugin.getCommand("dimension");
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: dimension");
        }
        DimensionCommand executor = new DimensionCommand(created, plugin.getDataFolder().toPath());
        registrations.bindCommand("dimension-command", command, executor, executor);

        plugin.getServer().getServicesManager().register(DimensionPlatform.class, platformFacade(), plugin, ServicePriority.Normal);
        registrations.ownCleanup("dimension-service", () -> plugin.getServer().getServicesManager().unregister(DimensionPlatform.class));
        scope.register("module-state", () -> {
            runtime = null;
            portals = null;
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

    /** The platform for this module generation, if enabled. */
    public Optional<DimensionPlatform> platform() {
        return Optional.ofNullable(runtime == null ? null : platformFacade());
    }

    private void recheckCycles() {
        DimensionRuntime current = runtime;
        if (current == null) {
            return;
        }
        for (DimensionDefinition definition : current.definitions()) {
            current.active(definition.key()).ifPresent(ActiveDimensionHandle::checkCycles);
        }
    }

    /** Wraps the runtime so portal indexes refresh when dimensions change. */
    private DimensionPlatform platformFacade() {
        DimensionRuntime current = runtime;
        PortalService portalService = portals;
        return new DimensionPlatform() {
            @Override
            public DimensionStatus register(DimensionDefinition definition) {
                DimensionStatus status = current.register(definition);
                portalService.reload();
                return status;
            }

            @Override
            public boolean unregister(DimensionKey key) {
                boolean removed = current.unregister(key);
                portalService.reload();
                return removed;
            }

            @Override
            public Optional<DimensionDefinition> definition(DimensionKey key) {
                return current.definition(key);
            }

            @Override
            public java.util.Collection<DimensionDefinition> definitions() {
                return current.definitions();
            }

            @Override
            public DimensionStatus status(DimensionKey key) {
                return current.status(key);
            }

            @Override
            public Optional<org.bukkit.World> world(DimensionKey key) {
                return current.world(key);
            }

            @Override
            public Optional<DimensionKey> dimensionOf(org.bukkit.World world) {
                return current.dimensionOf(world);
            }

            @Override
            public Optional<dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle.Snapshot> cycleSnapshot(
                DimensionKey key, String cycleId
            ) {
                return current.cycleSnapshot(key, cycleId);
            }

            @Override
            public java.util.concurrent.CompletableFuture<dev.linqfy.bigCasares.dimension.travel.TravelResult> enter(
                org.bukkit.entity.Player player, DimensionKey key
            ) {
                return current.enter(player, key);
            }

            @Override
            public java.util.concurrent.CompletableFuture<dev.linqfy.bigCasares.dimension.travel.TravelResult> teleport(
                org.bukkit.entity.Player player, org.bukkit.Location target
            ) {
                return current.teleport(player, target);
            }

            @Override
            public java.util.concurrent.CompletableFuture<dev.linqfy.bigCasares.dimension.travel.TravelResult> leave(
                org.bukkit.entity.Player player
            ) {
                return current.leave(player);
            }
        };
    }
}
