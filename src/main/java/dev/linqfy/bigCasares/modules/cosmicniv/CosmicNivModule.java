package dev.linqfy.bigCasares.modules.cosmicniv;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared equipment level library (0 to 200 by default). Other modules use it through
 * {@code BigCasares#getCosmicNivModule()}, asked again each time because {@code /bigcasares reload} replaces the
 * instance; see {@code docs/cosmicniv-lib/README.md}.
 */
public final class CosmicNivModule implements PluginModule {
    public static final String MODULE_ID = "cosmicniv-lib";
    public static final String CONFIG_FILE = CosmicNivSettingsLoader.MAIN_FILE;
    public static final String VALUES_FILE = CosmicNivSettingsLoader.VALUES_FILE;
    private static final String LOG_PREFIX = "[CosmicNiv] ";
    private static final String MEMORY_FOLDER = "data/cosmicniv-lib/players";

    private final BigCasares plugin;
    private final BukkitCosmicNivEquipmentReader reader = new BukkitCosmicNivEquipmentReader();
    private volatile CosmicNivService service;
    private volatile CosmicNivSettings lastValidSettings;
    private CosmicNivMemoryTracker tracker;
    private RuntimeRegistrationScope compatibilityScope;

    public CosmicNivModule(BigCasares plugin) {
        this(plugin, null);
    }

    /**
     * @param previousSettings settings of the module instance being replaced by {@code /bigcasares reload}, used
     *                         when the file has become invalid so the level does not jump to the bundled defaults
     */
    public CosmicNivModule(BigCasares plugin, CosmicNivSettings previousSettings) {
        this.plugin = plugin;
        this.lastValidSettings = previousSettings;
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
        for (String name : List.of(CONFIG_FILE, VALUES_FILE)) {
            if (Files.notExists(dataPath(name))) {
                plugin.saveResource(name, false);
            }
        }
        CosmicNivConfigLoadResult loaded = reload();
        if (!loaded.valid() && lastValidSettings != null) {
            install(lastValidSettings);
            plugin.getLogger().severe(LOG_PREFIX + CONFIG_FILE + " / " + VALUES_FILE
                + " have errors; keeping the configuration that was working before the reload.");
        } else if (!loaded.valid()) {
            CosmicNivConfigLoadResult defaults = loadBundled();
            if (!defaults.valid()) {
                throw new IllegalStateException("Bundled " + CONFIG_FILE + " / " + VALUES_FILE + " are invalid: "
                    + String.join("; ", defaults.errors()));
            }
            install(defaults.settings());
            plugin.getLogger().severe(LOG_PREFIX + CONFIG_FILE + " / " + VALUES_FILE
                + " have errors; using the bundled defaults until they are fixed and reloaded.");
        }

        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("cosmicniv-service", () -> service = null);
        PluginCommand pluginCommand = plugin.getCommand("cosmicniv");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: cosmicniv");
        }
        CosmicNivCommand command = new CosmicNivCommand(this);
        registrations.bindCommand("cosmicniv-command", pluginCommand, command, command);

        CosmicNivMemoryTracker memoryTracker = new CosmicNivMemoryTracker(
            () -> new CosmicNivMemoryService(requireService().settings().memory()),
            new YamlCosmicNivMemoryStorage(plugin.getDataFolder().toPath().resolve(MEMORY_FOLDER)),
            System::currentTimeMillis,
            warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        tracker = memoryTracker;
        registrations.ownCleanup("cosmicniv-memory", () -> {
            memoryTracker.saveAll();
            tracker = null;
        });
        registrations.registerListener("cosmicniv-memory-listener", new CosmicNivMemoryListener(this));
        long interval = requireService().settings().memory().checkIntervalTicks();
        registrations.scheduleRepeating("cosmicniv-memory-check", this::checkOnlinePlayers, interval, interval);
        plugin.getLogger().info(LOG_PREFIX + "Module enabled (curve " + requireService().settings().curve().type()
            + ", maximum " + requireService().maximum() + ").");
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

    /**
     * {@code true} once the module is enabled. When {@code modules.cosmicniv-lib.enabled} is false every other
     * method throws {@link IllegalStateException}.
     */
    public boolean isAvailable() {
        return service != null;
    }

    /**
     * Level of {@code entity} rounded to {@code output.decimals}, between 0 and {@link #maximum()}. Must be called
     * on the main thread because it reads the entity's inventory.
     */
    public double level(LivingEntity entity) {
        return evaluate(entity).value();
    }

    /**
     * Full breakdown for {@code entity}. Must be called on the main thread.
     */
    public CosmicNivResult evaluate(LivingEntity entity) {
        CosmicNivService current = requireService();
        return current.evaluate(reader.read(entity, current));
    }

    /**
     * Breakdown for a loadout built by hand (for example a kit, a shop preview or a boss spawn table). Thread-safe.
     */
    public CosmicNivResult evaluate(CosmicNivLoadout loadout) {
        return requireService().evaluate(loadout);
    }

    /**
     * What the model sees on {@code entity}. Must be called on the main thread.
     */
    public CosmicNivLoadout readLoadout(LivingEntity entity) {
        return reader.read(entity, requireService());
    }

    /**
     * The real level: the current equipment plus what the player is remembered to own (the pieces, weapon and tool
     * it had before taking them off or swapping them for worse ones). Never below {@link #level(LivingEntity)}.
     * Mobs, and players while {@code memory.enabled} is false, get their current level. Main thread only.
     */
    public double realLevel(LivingEntity entity) {
        return evaluateReal(entity).value();
    }

    /**
     * Full breakdown of the real level. Also updates the player's memory with what it has now. Main thread only.
     */
    public CosmicNivResult evaluateReal(LivingEntity entity) {
        CosmicNivService current = requireService();
        CosmicNivLoadout loadout = reader.read(entity, current);
        CosmicNivMemoryTracker activeTracker = tracker;
        if (!(entity instanceof Player player) || activeTracker == null) {
            return current.evaluate(loadout);
        }
        return activeTracker.check(player.getUniqueId(), current, loadout).real();
    }

    /**
     * What {@code playerId} is remembered to own right now, read from its file if the player is not being checked.
     * Forgotten items are left out; empty while {@code memory.enabled} is false. Works for offline players.
     * Main thread only.
     */
    public CosmicNivMemory rememberedEquipment(UUID playerId) {
        requireService();
        CosmicNivMemoryTracker activeTracker = tracker;
        return activeTracker == null ? CosmicNivMemory.empty() : activeTracker.peek(playerId);
    }

    public double maximum() {
        return requireService().maximum();
    }

    /**
     * The current model, for item powers and settings. It is replaced on every reload, so do not keep it.
     */
    public CosmicNivService service() {
        return requireService();
    }

    /**
     * Last settings that loaded without errors. Kept after {@link #onDisable()} so the next instance can reuse them.
     */
    public CosmicNivSettings lastValidSettings() {
        return lastValidSettings;
    }

    /**
     * Re-reads {@code cosmicniv_lib.yml} and {@code cosmicniv_valores.yml}. When they have errors the current model
     * is kept.
     */
    public synchronized CosmicNivConfigLoadResult reload() {
        CosmicNivConfigLoadResult result = loadInstalled();
        result.warnings().forEach(warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        result.errors().forEach(error -> plugin.getLogger().severe(LOG_PREFIX + error));
        if (result.valid()) {
            install(result.settings());
        } else if (service != null) {
            plugin.getLogger().severe(LOG_PREFIX + "Keeping the previous configuration.");
        }
        return result;
    }

    Server server() {
        return Objects.requireNonNull(plugin, "plugin").getServer();
    }

    void checkOnlinePlayers() {
        if (service == null || tracker == null) {
            return;
        }
        for (Player player : server().getOnlinePlayers()) {
            evaluateReal(player);
        }
    }

    void playerDied(Player player) {
        CosmicNivMemoryTracker activeTracker = tracker;
        if (service != null && activeTracker != null) {
            activeTracker.death(player.getUniqueId());
        }
    }

    void playerRespawned(Player player) {
        CosmicNivMemoryTracker activeTracker = tracker;
        if (activeTracker != null) {
            activeTracker.respawn(player.getUniqueId());
        }
    }

    void playerQuit(Player player) {
        CosmicNivMemoryTracker activeTracker = tracker;
        if (service != null && activeTracker != null) {
            evaluateReal(player);
            activeTracker.quit(player.getUniqueId());
        }
    }

    private void install(CosmicNivSettings settings) {
        try {
            BukkitCosmicNivKeyAudit.unknownKeys(settings)
                .forEach(warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        } catch (RuntimeException | LinkageError unavailableRegistry) {
            plugin.getLogger().warning(LOG_PREFIX + "Could not check keys against the server registries: "
                + unavailableRegistry);
        }
        service = new CosmicNivService(settings);
        lastValidSettings = settings;
    }

    private CosmicNivConfigLoadResult loadInstalled() {
        YamlConfiguration main = new YamlConfiguration();
        YamlConfiguration values = new YamlConfiguration();
        List<String> errors = new ArrayList<>();
        readInstalled(main, CONFIG_FILE, errors);
        readInstalled(values, VALUES_FILE, errors);
        if (!errors.isEmpty()) {
            return new CosmicNivConfigLoadResult(null, errors, List.of());
        }
        return CosmicNivSettingsLoader.load(main, values);
    }

    private void readInstalled(YamlConfiguration yaml, String name, List<String> errors) {
        try {
            yaml.load(dataPath(name).toFile());
        } catch (IOException | InvalidConfigurationException invalid) {
            errors.add(name + ": cannot read the file: " + invalid.getMessage());
        }
    }

    private CosmicNivConfigLoadResult loadBundled() {
        try {
            return CosmicNivSettingsLoader.load(bundled(CONFIG_FILE), bundled(VALUES_FILE));
        } catch (IOException invalid) {
            return new CosmicNivConfigLoadResult(null, List.of(invalid.getMessage()), List.of());
        }
    }

    private YamlConfiguration bundled(String name) throws IOException {
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            throw new IOException(name + " is missing from the jar");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private Path dataPath(String name) {
        return plugin.getDataFolder().toPath().resolve(name);
    }

    private CosmicNivService requireService() {
        CosmicNivService current = service;
        if (current == null) {
            throw new IllegalStateException("cosmicniv_lib no esta disponible (modulo apagado o sin iniciar).");
        }
        return current;
    }
}
