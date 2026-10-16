package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionRules;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.dimension.DimensionStatus.State;
import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.event.DimensionEnterEvent;
import dev.linqfy.bigCasares.dimension.event.DimensionExitEvent;
import dev.linqfy.bigCasares.dimension.event.DimensionLifecycleEvent;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.dimension.persistence.DimensionDataStore;
import dev.linqfy.bigCasares.dimension.travel.CoordinateMapping;
import dev.linqfy.bigCasares.dimension.travel.SafeLocationFinder;
import dev.linqfy.bigCasares.dimension.travel.TravelCooldowns;
import dev.linqfy.bigCasares.dimension.travel.TravelResult;
import dev.linqfy.bigCasares.dimension.travel.TravelRules;
import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupOutcome;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Bukkit implementation of {@link DimensionPlatform}. Lives for one module
 * generation; all work runs on the server thread except chunk loading.
 */
public final class DimensionRuntime implements DimensionPlatform {

    private final JavaPlugin plugin;
    private final RuntimeRegistrationScope scope;
    private final DimensionSettings settings;
    private final DimensionDataStore store;
    private final DatapackCompiler compiler = new DatapackCompiler();
    private final DatapackInstaller installer;
    private final TravelCooldowns cooldowns;
    private final Map<DimensionKey, Entry> entries = new LinkedHashMap<>();
    private GeneratedDatapack lastPack;
    private boolean shutDown;

    private static final class Entry {
        private final DimensionDefinition definition;
        private DimensionStatus status = DimensionStatus.of(State.REGISTERED, "");
        private ActiveDimensionHandle active;

        private Entry(DimensionDefinition definition) {
            this.definition = definition;
        }
    }

    DimensionRuntime(JavaPlugin plugin, RuntimeRegistrationScope scope, DimensionSettings settings, DimensionDataStore store) {
        this.plugin = plugin;
        this.scope = scope;
        this.settings = settings;
        this.store = store;
        this.installer = new DatapackInstaller(Clock.systemDefaultZone());
        this.cooldowns = new TravelCooldowns(Clock.systemUTC());
    }

    // --- registration & lifecycle -------------------------------------------------------

    @Override
    public synchronized DimensionStatus register(DimensionDefinition definition) {
        requireRunning();
        DimensionKey key = definition.key();
        if (entries.containsKey(key)) {
            throw new IllegalArgumentException("Dimension already registered: " + key);
        }
        List<DimensionDefinition> candidate = new ArrayList<>(definitions());
        candidate.add(definition);
        // Compile before mutating state: a broken definition is rejected and nothing is installed.
        DatapackCompiler.Result compiled = compiler.compile(candidate);
        Entry entry = new Entry(definition);
        entries.put(key, entry);
        publishPack(compiled.datapack());
        activateIfLoaded(entry);
        return entry.status;
    }

    @Override
    public synchronized boolean unregister(DimensionKey key) {
        Entry entry = entries.remove(key);
        if (entry == null) {
            return false;
        }
        deactivate(entry);
        setStatus(entry, DimensionStatus.of(State.UNREGISTERED, "Unregistered; world files are kept"));
        return true;
    }

    private void publishPack(GeneratedDatapack pack) {
        this.lastPack = pack;
        Path levelDirectory = plugin.getServer().getLevelDirectory();
        Path datapacks = levelDirectory.resolve("datapacks");
        Path pluginCopy = plugin.getDataFolder().toPath().resolve("dimensions/generated-datapack");
        Path backups = plugin.getDataFolder().toPath().resolve("dimensions/datapack-backups");
        DatapackStartupLedger.observe(DatapackInstaller.installedHash(datapacks.resolve(settings.datapackFolder())).orElse(null));
        try {
            installer.install(pack, plugin.getDataFolder().toPath().resolve("dimensions"), "generated-datapack", backups);
            if (settings.installMode() == DimensionSettings.InstallMode.AUTO) {
                DatapackInstaller.InstallResult result = installer.install(pack, datapacks, settings.datapackFolder(), backups);
                if (result.changed()) {
                    store.recordInstalledPack(result.installedHash(), Instant.now().toString());
                    plugin.getLogger().warning("[Dimensions] Datapack " + result.outcome().name().toLowerCase()
                        + " at " + result.directory() + " (" + pack.size() + " files). Restart the server to load"
                        + " new or changed dimensions; biomes and dimension types are only read at startup.");
                }
            } else {
                plugin.getLogger().info("[Dimensions] install-mode=manual: copy " + pluginCopy
                    + " into " + datapacks + " and restart to load dimensions.");
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Dimensions] Could not write the dimension datapack", exception);
        }
    }

    private boolean restartRecommended() {
        if (lastPack == null) {
            return false;
        }
        return !DatapackStartupLedger.startupHash().map(lastPack.contentHash()::equals).orElse(false);
    }

    private void activateIfLoaded(Entry entry) {
        DimensionKey key = entry.definition.key();
        World world = plugin.getServer().getWorld(new NamespacedKey(key.namespace(), key.path()));
        if (world == null) {
            setStatus(entry, DimensionStatus.of(State.PENDING_RESTART,
                "Datapack installed; the world loads after a server restart"));
            return;
        }
        try {
            applyRules(world, entry.definition.rules());
            ActiveDimensionHandle handle = new ActiveDimensionHandle(plugin, entry.definition, world, scope, store);
            entry.active = handle;
            handle.start();
            setStatus(entry, new DimensionStatus(State.ACTIVE, "World " + world.getName(), restartRecommended()));
            for (Player player : world.getPlayers()) {
                announceEnter(entry, player);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Dimensions] Activation of " + key + " failed", failure);
            deactivate(entry);
            setStatus(entry, DimensionStatus.of(State.FAILED, failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        }
    }

    private void deactivate(Entry entry) {
        ActiveDimensionHandle handle = entry.active;
        entry.active = null;
        if (handle == null) {
            return;
        }
        for (RuntimeCleanupOutcome failure : handle.stop().failures()) {
            plugin.getLogger().log(Level.SEVERE, "[Dimensions] Cleanup failed for " + entry.definition.key()
                + ": " + failure.resourceId(), failure.failure());
        }
    }

    private void setStatus(Entry entry, DimensionStatus status) {
        entry.status = status;
        plugin.getServer().getPluginManager().callEvent(new DimensionLifecycleEvent(entry.definition.key(), status));
    }

    private void applyRules(World world, DimensionRules rules) {
        for (Map.Entry<String, String> rule : rules.gameRules().entrySet()) {
            applyGameRule(world, rule.getKey(), rule.getValue());
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(rules.worldBorderCenterX(), rules.worldBorderCenterZ());
        border.setSize(rules.worldBorderDiameter());
    }

    @SuppressWarnings("unchecked")
    private void applyGameRule(World world, String id, String value) {
        GameRule<?> rule = Registry.GAME_RULE.get(NamespacedKey.minecraft(id));
        if (rule == null) {
            plugin.getLogger().warning("[Dimensions] Unknown game rule '" + id + "' for " + world.getName() + "; skipped");
            return;
        }
        Class<?> type = rule.getType();
        if (type == Boolean.class) {
            world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
        } else if (type == Integer.class) {
            try {
                world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value));
            } catch (NumberFormatException invalid) {
                plugin.getLogger().warning("[Dimensions] Game rule '" + id + "' needs an integer, got '" + value + "'");
            }
        } else {
            plugin.getLogger().warning("[Dimensions] Game rule '" + id + "' has unsupported type " + type.getSimpleName());
        }
    }

    /** Stops every active dimension and flushes state. Idempotent. */
    synchronized void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        for (Entry entry : entries.values()) {
            deactivate(entry);
        }
        saveStore();
    }

    void saveStore() {
        try {
            store.save();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Dimensions] Could not save dimension state", exception);
        }
    }

    private void requireRunning() {
        if (shutDown) {
            throw new IllegalStateException("Dimension runtime is shut down");
        }
    }

    // --- queries ------------------------------------------------------------------------

    @Override
    public synchronized Optional<DimensionDefinition> definition(DimensionKey key) {
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.definition);
    }

    @Override
    public synchronized Collection<DimensionDefinition> definitions() {
        return entries.values().stream().map(entry -> entry.definition).toList();
    }

    @Override
    public synchronized DimensionStatus status(DimensionKey key) {
        Entry entry = entries.get(key);
        return entry == null ? DimensionStatus.of(State.UNREGISTERED, "Not registered") : entry.status;
    }

    @Override
    public synchronized Optional<World> world(DimensionKey key) {
        Entry entry = entries.get(key);
        return entry == null || entry.active == null ? Optional.empty() : Optional.of(entry.active.world());
    }

    @Override
    public synchronized Optional<DimensionKey> dimensionOf(World world) {
        if (world == null) {
            return Optional.empty();
        }
        for (Entry entry : entries.values()) {
            if (entry.active != null && entry.active.world().getUID().equals(world.getUID())) {
                return Optional.of(entry.definition.key());
            }
        }
        return Optional.empty();
    }

    synchronized Optional<ActiveDimensionHandle> active(DimensionKey key) {
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.active);
    }

    @Override
    public synchronized Optional<EnvironmentCycle.Snapshot> cycleSnapshot(DimensionKey key, String cycleId) {
        return active(key).flatMap(handle -> handle.snapshot(cycleId));
    }

    Optional<GeneratedDatapack> lastPack() {
        return Optional.ofNullable(lastPack);
    }

    DimensionDataStore store() {
        return store;
    }

    // --- player presence ----------------------------------------------------------------

    void onWorldChange(Player player, World from, World to) {
        dimensionOf(from).flatMap(this::entryOf).ifPresent(entry -> announceExit(entry, player, false));
        dimensionOf(to).flatMap(this::entryOf).ifPresent(entry -> announceEnter(entry, player));
    }

    void onJoin(Player player) {
        dimensionOf(player.getWorld()).flatMap(this::entryOf).ifPresent(entry -> announceEnter(entry, player));
    }

    void onQuit(Player player) {
        dimensionOf(player.getWorld()).flatMap(this::entryOf).ifPresent(entry -> announceExit(entry, player, true));
    }

    private synchronized Optional<Entry> entryOf(DimensionKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    private void announceEnter(Entry entry, Player player) {
        boolean firstVisit = store.markVisited(player.getUniqueId(), entry.definition.key().asString());
        plugin.getServer().getPluginManager().callEvent(new DimensionEnterEvent(player, entry.definition.key(), firstVisit));
        if (entry.active != null) {
            entry.active.playerEntered(player);
        }
    }

    private void announceExit(Entry entry, Player player, boolean disconnect) {
        plugin.getServer().getPluginManager().callEvent(new DimensionExitEvent(player, entry.definition.key(), disconnect));
        if (entry.active != null) {
            entry.active.playerExited(player);
        }
    }

    // --- travel -------------------------------------------------------------------------

    @Override
    public CompletableFuture<TravelResult> enter(Player player, DimensionKey key) {
        return enter(player, key, DimensionTravelEvent.Cause.API);
    }

    CompletableFuture<TravelResult> enter(Player player, DimensionKey key, DimensionTravelEvent.Cause cause) {
        Optional<ActiveDimensionHandle> handle = active(key);
        if (handle.isEmpty()) {
            return done(TravelResult.of(TravelResult.Status.DIMENSION_UNAVAILABLE, status(key).message()));
        }
        World world = handle.get().world();
        if (player.getWorld().getUID().equals(world.getUID())) {
            return done(TravelResult.of(TravelResult.Status.FAILED, "Already inside " + key));
        }
        TravelRules rules = handle.get().definition().travel();
        Location source = player.getLocation();
        double[] mapped = rules.mapping().toDimension(source.getX(), source.getZ());
        WorldBorder border = world.getWorldBorder();
        double[] clamped = CoordinateMapping.clampToBorder(mapped[0], mapped[1],
            border.getCenter().getX(), border.getCenter().getZ(), border.getSize(), 16.0);
        Location target = new Location(world, clamped[0], source.getY(), clamped[1], source.getYaw(), source.getPitch());
        return transfer(player, key, true, cause, target, rules);
    }

    @Override
    public CompletableFuture<TravelResult> teleport(Player player, Location target) {
        Optional<DimensionKey> key = dimensionOf(target.getWorld());
        if (key.isEmpty()) {
            return done(TravelResult.of(TravelResult.Status.FAILED, "Target is not in a custom dimension"));
        }
        TravelRules rules = definition(key.get()).map(DimensionDefinition::travel).orElse(TravelRules.DEFAULT);
        return transfer(player, key.get(), true, DimensionTravelEvent.Cause.API, target, rules);
    }

    @Override
    public CompletableFuture<TravelResult> leave(Player player) {
        return leave(player, DimensionTravelEvent.Cause.RETURN);
    }

    CompletableFuture<TravelResult> leave(Player player, DimensionTravelEvent.Cause cause) {
        Optional<DimensionKey> key = dimensionOf(player.getWorld());
        if (key.isEmpty()) {
            return done(TravelResult.of(TravelResult.Status.FAILED, "Not inside a custom dimension"));
        }
        TravelRules rules = definition(key.get()).map(DimensionDefinition::travel).orElse(TravelRules.DEFAULT);
        Location target = store.returnPoint(player.getUniqueId(), key.get().asString())
            .flatMap(point -> Optional.ofNullable(plugin.getServer().getWorld(point.world()))
                .map(world -> new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch())))
            .orElseGet(() -> mappedReturn(player, rules));
        return transfer(player, key.get(), false, cause, target, rules);
    }

    private Location mappedReturn(Player player, TravelRules rules) {
        World main = plugin.getServer().getWorlds().getFirst();
        Location here = player.getLocation();
        double[] back = rules.mapping().fromDimension(here.getX(), here.getZ());
        return new Location(main, back[0], here.getY(), back[1], here.getYaw(), here.getPitch());
    }

    /**
     * Shared transfer pipeline: permission and cooldown checks, async chunk loading,
     * safe-spot search, cancellable event, return-point bookkeeping, async teleport.
     */
    CompletableFuture<TravelResult> transfer(
        Player player, DimensionKey key, boolean entering, DimensionTravelEvent.Cause cause,
        Location requested, TravelRules rules
    ) {
        if (entering && rules.entryPermission() != null && !player.hasPermission(rules.entryPermission())) {
            return done(TravelResult.of(TravelResult.Status.NO_PERMISSION, "Missing permission " + rules.entryPermission()));
        }
        Duration remaining = cooldowns.remaining(player.getUniqueId(), rules.cooldown());
        if (!remaining.isZero()) {
            return done(new TravelResult(TravelResult.Status.ON_COOLDOWN, "Travel cooldown", remaining));
        }
        World world = requested.getWorld();
        if (world == null) {
            return done(TravelResult.of(TravelResult.Status.FAILED, "Target world is not loaded"));
        }
        int radius = rules.arrivalSearchRadius();
        int chunkRadius = Math.min(2, (radius + 15) >> 4);
        int cx = requested.getBlockX() >> 4;
        int cz = requested.getBlockZ() >> 4;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                loads.add(world.getChunkAtAsync(cx + dx, cz + dz, true));
            }
        }
        Location source = player.getLocation();
        CompletableFuture<TravelResult> result = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) ->
            plugin.getServer().getScheduler().runTask(plugin, scope.guard(() -> {
                if (failure != null) {
                    result.complete(TravelResult.of(TravelResult.Status.FAILED, "Chunk loading failed: " + failure.getMessage()));
                    return;
                }
                if (!player.isOnline()) {
                    result.complete(TravelResult.of(TravelResult.Status.CANCELLED, "Player went offline"));
                    return;
                }
                Location arrival = findArrival(world, requested, Math.min(radius, chunkRadius * 16 + 8));
                DimensionTravelEvent event = new DimensionTravelEvent(player, key, entering, cause, source, arrival);
                plugin.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    result.complete(TravelResult.of(TravelResult.Status.CANCELLED, "Cancelled by a plugin"));
                    return;
                }
                Location destination = event.to();
                if (entering && rules.rememberReturnPoint() && dimensionOf(source.getWorld()).isEmpty()) {
                    store.setReturnPoint(player.getUniqueId(), key.asString(), new DimensionDataStore.StoredLocation(
                        source.getWorld().getName(), source.getX(), source.getY(), source.getZ(),
                        source.getYaw(), source.getPitch()));
                }
                cooldowns.record(player.getUniqueId());
                player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, error) -> {
                    if (error != null || !Boolean.TRUE.equals(ok)) {
                        cooldowns.forget(player.getUniqueId());
                        result.complete(TravelResult.of(TravelResult.Status.FAILED,
                            error == null ? "Teleport was refused" : error.getMessage()));
                    } else {
                        result.complete(TravelResult.of(TravelResult.Status.TELEPORTED, destination.getWorld().getName()));
                    }
                });
            })));
        return result;
    }

    /** Safe spot near the request, else the surface, else a small platform. */
    private Location findArrival(World world, Location requested, int radius) {
        BukkitBlockGrid grid = new BukkitBlockGrid(world);
        int x = requested.getBlockX();
        int z = requested.getBlockZ();
        int preferredY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 3, requested.getBlockY()));
        Optional<SafeLocationFinder.Spot> spot = new SafeLocationFinder(radius).find(grid, x, z, preferredY);
        if (spot.isPresent()) {
            return centered(world, spot.get().x(), spot.get().y(), spot.get().z(), requested);
        }
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 3, surface + 1));
        // Last resort: a 3x3 platform with headroom, never replacing non-air except liquids/hazards.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var floor = world.getBlockAt(x + dx, y - 1, z + dz);
                if (floor.isEmpty() || floor.isLiquid() || grid.isHazard(x + dx, y - 1, z + dz)) {
                    floor.setType(Material.COBBLESTONE, false);
                }
                for (int up = 0; up < 2; up++) {
                    var body = world.getBlockAt(x + dx, y + up, z + dz);
                    if (!body.isPassable() || body.isLiquid()) {
                        body.setType(Material.AIR, false);
                    }
                }
            }
        }
        return centered(world, x, y, z, requested);
    }

    private static Location centered(World world, int x, int y, int z, Location facing) {
        return new Location(world, x + 0.5, y, z + 0.5, facing.getYaw(), facing.getPitch());
    }

    private static CompletableFuture<TravelResult> done(TravelResult result) {
        return CompletableFuture.completedFuture(result);
    }

    boolean hasDirtyStore() {
        return store.isDirty();
    }

    Path levelDatapacks() {
        return plugin.getServer().getLevelDirectory().resolve("datapacks").resolve(settings.datapackFolder());
    }

    boolean datapackPresent() {
        return Files.isRegularFile(levelDatapacks().resolve("pack.mcmeta"));
    }
}
