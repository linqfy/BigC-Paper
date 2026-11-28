package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventType;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventSettings;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventsConfig;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Shared equipment level library (0 to 200 by default). Other modules use it through
 * {@code BigCasares#getToffyModule()}, asked again each time because {@code /bigcasares reload} replaces the
 * instance.
 */
public final class ToffyModule implements PluginModule {
    public static final String MODULE_ID = "toffy-complementaries";
    public static final String CONFIG_FILE = ToffySettingsLoader.MAIN_FILE;
    public static final String VALUES_FILE = ToffySettingsLoader.VALUES_FILE;
    private static final String LOG_PREFIX = "[Toffy] ";
    public static final String MESSAGES_FILE = "toffy_mensajes.yml";
    public static final String EVENTS_FILE = ToffyEventsConfig.FILE;
    private static final String MEMORY_FOLDER = "data/toffy-complementaries/players";
    private static final Random RANDOM = new Random();
    private static final String TOURNAMENTS_FILE = "data/toffy-complementaries/torneos.yml";

    private final BigCasares plugin;
    private final BukkitToffyEquipmentReader reader = new BukkitToffyEquipmentReader();
    private volatile ToffyService service;
    private volatile ToffySettings lastValidSettings;
    private volatile ToffyMessages messages = ToffyMessages.builtIn();
    private volatile ToffyTournamentService tournaments;
    private volatile BukkitRuntimeRegistrations runtime;
    private ToffyMemoryTracker tracker;
    private final Supplier<Optional<DimensionPlatform>> dimensions;
    private final Supplier<Optional<Boolean>> pvpBaseline;
    private final Predicate<UUID> endHuntDeath;
    private volatile ToffyEvents events;
    private volatile ToffyEventSettings.Tournaments tournamentTimes;
    private RuntimeRegistrationScope compatibilityScope;

    public ToffyModule(BigCasares plugin) {
        this(plugin, null);
    }

    /**
     * @param previousSettings settings of the module instance being replaced by {@code /bigcasares reload}, used
     *                         when the file has become invalid so the level does not jump to the bundled defaults
     */
    public ToffyModule(BigCasares plugin, ToffySettings previousSettings) {
        this(plugin, previousSettings, Optional::empty, Optional::empty, uuid -> false);
    }

    /**
     * @param dimensions   dimension framework, used to build the event arenas (empty when it is disabled)
     * @param pvpBaseline  PvP state chosen through server control, restored when an event ends
     * @param endHuntDeath whether a death belongs to the end hunt (Lifesteal does not take hearts for it)
     */
    public ToffyModule(BigCasares plugin, ToffySettings previousSettings,
                       Supplier<Optional<DimensionPlatform>> dimensions,
                       Supplier<Optional<Boolean>> pvpBaseline,
                       Predicate<UUID> endHuntDeath) {
        this.plugin = plugin;
        this.lastValidSettings = previousSettings;
        this.dimensions = dimensions;
        this.pvpBaseline = pvpBaseline;
        this.endHuntDeath = endHuntDeath;
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
        for (String name : List.of(CONFIG_FILE, VALUES_FILE, MESSAGES_FILE)) {
            if (Files.notExists(dataPath(name))) {
                plugin.saveResource(name, false);
            }
        }
        ToffyConfigLoadResult loaded = reload();
        if (!loaded.valid() && lastValidSettings != null) {
            install(lastValidSettings);
            plugin.getLogger().severe(LOG_PREFIX + CONFIG_FILE + " / " + VALUES_FILE
                + " have errors; keeping the configuration that was working before the reload.");
        } else if (!loaded.valid()) {
            ToffyConfigLoadResult defaults = loadBundled();
            if (!defaults.valid()) {
                throw new IllegalStateException("Bundled " + CONFIG_FILE + " / " + VALUES_FILE + " are invalid: "
                    + String.join("; ", defaults.errors()));
            }
            install(defaults.settings());
            plugin.getLogger().severe(LOG_PREFIX + CONFIG_FILE + " / " + VALUES_FILE
                + " have errors; using the bundled defaults until they are fixed and reloaded.");
        }

        reloadMessages();

        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        runtime = registrations;
        registrations.ownCleanup("toffy-service", () -> {
            service = null;
            tournaments = null;
            runtime = null;
        });
        PluginCommand pluginCommand = plugin.getCommand("toffy");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: toffy");
        }
        ToffyCommand command = new ToffyCommand(this);
        registrations.bindCommand("toffy-command", pluginCommand, command, command);
        PluginCommand anotarCommand = plugin.getCommand("anotar");
        if (anotarCommand == null) {
            throw new IllegalStateException("Required command is not declared: anotar");
        }
        ToffyAnotarCommand anotar = new ToffyAnotarCommand(this);
        registrations.bindCommand("toffy-anotar-command", anotarCommand, anotar, anotar);
        tournaments = loadTournaments();
        registrations.registerListener("toffy-tournament-listener", new ToffyTournamentListener(this));
        PluginCommand eventoCommand = plugin.getCommand("evento");
        if (eventoCommand == null) {
            throw new IllegalStateException("Required command is not declared: evento");
        }
        ToffyEventoCommand evento = new ToffyEventoCommand(this);
        registrations.bindCommand("toffy-evento-command", eventoCommand, evento, evento);
        registrations.scheduleRepeating("toffy-tournament-schedule", this::checkSchedules, 20L, 20L);

        ToffyMemoryTracker memoryTracker = new ToffyMemoryTracker(
            () -> new ToffyMemoryService(requireService().settings().memory()),
            new YamlToffyMemoryStorage(plugin.getDataFolder().toPath().resolve(MEMORY_FOLDER),
                message -> plugin.getLogger().severe(LOG_PREFIX + message)),
            System::currentTimeMillis,
            warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        tracker = memoryTracker;
        registrations.ownCleanup("toffy-memory", () -> {
            memoryTracker.saveAll();
            tracker = null;
        });
        registrations.registerListener("toffy-memory-listener", new ToffyMemoryListener(this));
        long interval = requireService().settings().memory().checkIntervalTicks();
        registrations.scheduleRepeating("toffy-memory-check", this::checkOnlinePlayers, interval, interval);
        // Staff events (Lifesteal, KOTH, TNT Tag, Spleef) run on tournaments; they are enabled with this module.
        prepareEventsFile();
        ToffyEventSettings eventSettings = readEventSettings().orElseGet(this::bundledEventSettings);
        applyTournamentTimes(eventSettings.tournaments());
        ToffyEvents created = new ToffyEvents(plugin, dimensions, pvpBaseline, endHuntDeath, this::eventText,
            eventRosters());
        registrations.ownCleanup("toffy-events-state", () -> events = null);
        try {
            created.enable(registrations, scope, eventSettings);
            events = created;
        } catch (IllegalStateException failure) {
            // The events check everything before registering anything, so nothing is left to clean up here;
            // Toffy itself keeps running without the events.
            plugin.getLogger().severe(LOG_PREFIX + "Eventos desactivados: " + failure.getMessage()
                + " Toffy sigue funcionando sin eventos.");
        }
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
     * {@code true} once the module is enabled. When {@code modules.toffy-complementaries.enabled} is false every other
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
    public ToffyResult evaluate(LivingEntity entity) {
        ToffyService current = requireService();
        return current.evaluate(reader.read(entity, current));
    }

    /**
     * Breakdown for a loadout built by hand (for example a kit, a shop preview or a boss spawn table). Thread-safe.
     */
    public ToffyResult evaluate(ToffyLoadout loadout) {
        return requireService().evaluate(loadout);
    }

    /**
     * What the model sees on {@code entity}. Must be called on the main thread.
     */
    public ToffyLoadout readLoadout(LivingEntity entity) {
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
    public ToffyResult evaluateReal(LivingEntity entity) {
        ToffyService current = requireService();
        ToffyLoadout loadout = reader.read(entity, current);
        ToffyMemoryTracker activeTracker = tracker;
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
    public ToffyMemory rememberedEquipment(UUID playerId) {
        requireService();
        ToffyMemoryTracker activeTracker = tracker;
        return activeTracker == null ? ToffyMemory.empty() : activeTracker.peek(playerId);
    }

    /**
     * Chat text for {@code caseKey} (see {@code toffy_mensajes.yml}) with colors and placeholders applied: the
     * override of {@code tournament} (may be null), then the file, then the built-in default. {@code null} when the
     * text is empty, which means "send nothing". Placeholders are {jugador}, {torneo}, {cantidad}, {x}, {y}, {z},
     * {mundo}, and for staff events {evento}, {segundos}, {motivo} and {detalle}. Main thread only.
     */
    public String message(String caseKey, String tournament, Map<String, String> placeholders) {
        ToffyMessages current = messages;
        return current.render(caseKey, overrideOf(caseKey, tournament), withPrefix(placeholders, current.prefix()));
    }

    /**
     * What a staff event case shows (text, title, action bar, sound): the same lookup as {@link #message}, with the
     * event prefix. Null when nothing is shown. Main thread only.
     */
    public ToffyEvents.Text eventText(String caseKey, String tournament, Map<String, String> placeholders) {
        return messages.display(caseKey, overrideOf(caseKey, tournament), placeholders, true);
    }

    private String overrideOf(String caseKey, String tournament) {
        ToffyTournamentService current = tournaments;
        return current == null || tournament == null ? null
            : current.find(tournament).map(found -> found.message(caseKey)).orElse(null);
    }

    private static Map<String, String> withPrefix(Map<String, String> placeholders, String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders != null) {
            values.putAll(placeholders);
        }
        values.put(ToffyMessages.PLACEHOLDER_PREFIX, ToffyMessages.colors(prefix));
        return values;
    }

    /**
     * Sends {@link #message} to {@code recipient} unless it is empty.
     */
    public void send(CommandSender recipient, String caseKey, String tournament, Map<String, String> placeholders) {
        String text = message(caseKey, tournament, placeholders);
        if (text != null) {
            recipient.sendMessage(text);
        }
    }

    public double maximum() {
        return requireService().maximum();
    }

    /**
     * The current model, for item powers and settings. It is replaced on every reload, so do not keep it.
     */
    public ToffyService service() {
        return requireService();
    }

    /**
     * Last settings that loaded without errors. Kept after {@link #onDisable()} so the next instance can reuse them.
     */
    public ToffySettings lastValidSettings() {
        return lastValidSettings;
    }

    /**
     * Re-reads {@code toffy_complementaries.yml} and {@code toffy_valores.yml}. When they have errors the current model
     * is kept.
     */
    public synchronized ToffyConfigLoadResult reload() {
        ToffyConfigLoadResult result = loadInstalled();
        result.warnings().forEach(warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        result.errors().forEach(error -> plugin.getLogger().severe(LOG_PREFIX + error));
        if (result.valid()) {
            install(result.settings());
        } else if (service != null) {
            plugin.getLogger().severe(LOG_PREFIX + "Keeping the previous configuration.");
        }
        return result;
    }

    /**
     * Re-reads {@code toffy_eventos.yml}. The running event keeps its settings until it ends.
     *
     * @return false when the file cannot be read; the previous settings are kept
     */
    public synchronized boolean reloadEvents() {
        Optional<ToffyEventSettings> loaded = readEventSettings();
        loaded.ifPresent(settings -> {
            applyTournamentTimes(settings.tournaments());
            ToffyEvents current = events;
            if (current != null) {
                current.applySettings(settings);
            }
        });
        return loaded.isPresent();
    }

    /**
     * Whether a running staff event handles this player's death (keep inventory, no tombstone). False when none runs.
     */
    public boolean protectsDeath(UUID playerId) {
        ToffyEvents current = events;
        return current != null && current.protectsDeath(playerId);
    }

    /** The staff events, or null while the module is not enabled. */
    ToffyEvents events() {
        return events;
    }

    /** Timings, zone and formats of the tournaments, from the {@code torneos} section of {@code toffy_eventos.yml}. */
    ToffyEventSettings.Tournaments tournamentTimes() {
        ToffyEventSettings.Tournaments times = tournamentTimes;
        return times == null ? ToffyEventSettings.Tournaments.defaults() : times;
    }

    private void applyTournamentTimes(ToffyEventSettings.Tournaments times) {
        tournamentTimes = times;
        ToffyTournamentService current = tournaments;
        if (current != null) {
            current.useTimings(times.teleportWarningMinutes(), times.eventWarningMinutes(),
                times.lostAfterMinutes() * 60_000L);
        }
    }

    /**
     * Re-reads {@code toffy_mensajes.yml}. When the file cannot be read the previous messages are kept.
     *
     * @return false when the file is invalid
     */
    public synchronized boolean reloadMessages() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(dataPath(MESSAGES_FILE).toFile());
        } catch (IOException | InvalidConfigurationException invalid) {
            plugin.getLogger().severe(LOG_PREFIX + MESSAGES_FILE + ": cannot read the file ("
                + invalid.getMessage() + "); keeping the previous messages.");
            return false;
        }
        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(plainValues(yaml));
        loaded.warnings().forEach(warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        messages = loaded.messages();
        return true;
    }

    ToffyTournamentService tournaments() {
        return tournaments;
    }

    /** Top-level values of a YAML file, with every section turned into a map (the messages read sections). */
    private static Map<String, Object> plainValues(YamlConfiguration yaml) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.getValues(false).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection section) {
                value = section.getValues(false);
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    /**
     * Key of the group of the tournament the player is in, or empty when it is not in one (or the tournament does not
     * exist). Main thread only.
     */
    public Optional<String> groupOf(String tournament, UUID player) {
        ToffyTournamentService current = tournaments;
        return current == null ? Optional.empty() : current.groupOf(tournament, player);
    }

    /** Keys of the groups of the tournament, in creation order. Empty when there are none or it does not exist. */
    public List<String> groups(String tournament) {
        ToffyTournamentService current = tournaments;
        return current == null ? List.of() : current.groupKeys(tournament);
    }

    /**
     * Puts the player in the group, as staff does with {@code /toffy torneo grupo <torneo> asignar}: signs the player up
     * to the tournament when needed, and respects the level ranges and the capacities of both. An online player is
     * checked with its real level; an offline one has no level, so a tournament or group with a level range refuses
     * it. The player is told when it was placed. Main thread only.
     *
     * @return false when refused (unknown tournament or group, full, out of range, closed tournament)
     */
    public boolean assignGroup(String tournament, String group, UUID player) {
        ToffyTournamentService current = tournaments;
        if (current == null) {
            return false;
        }
        Player online = server().getPlayer(player);
        Double level = online == null ? null : realLevel(online);
        String name = online != null ? online.getName() : current.find(tournament)
            .map(found -> found.participants().get(player)).orElse(null);
        if (name == null) {
            return false;
        }
        ToffyTournamentService.Placement placement = current.placeInGroup(tournament, group, player,
            name, level, ToffyTournamentService.GroupMode.STAFF);
        groupChanged(tournament, List.of(player));
        ToffyTournamentService.GroupResult result = placement.result();
        boolean placed = result == ToffyTournamentService.GroupResult.ASSIGNED
            || result == ToffyTournamentService.GroupResult.MOVED;
        boolean inGroup = placed || result == ToffyTournamentService.GroupResult.ALREADY_IN_GROUP;
        if (placed && online != null) {
            current.find(tournament).ifPresent(found -> {
                String groupName = current.group(found.key(), group).map(ToffyGroup::displayName).orElse(group);
                send(online, ToffyMessages.GRUPO_ASIGNADO, found.key(), Map.of(
                    "jugador", online.getName(),
                    "torneo", found.displayName(),
                    "grupo", groupName));
            });
        }
        return inGroup;
    }

    /** The roster lookup the events use: the whole sign-up list, or one group of it. */
    private ToffyEvents.EventTournaments eventRosters() {
        return new ToffyEvents.EventTournaments() {
            @Override
            public Optional<ToffyEvents.Roster> roster(String tournamentKey) {
                return rosterOf(tournamentKey, null);
            }

            @Override
            public Optional<ToffyEvents.Roster> roster(String tournamentKey, String group) {
                return rosterOf(tournamentKey, group);
            }

            @Override
            public Optional<ToffyEventSettings.Rewards> rewards(String tournamentKey) {
                ToffyTournamentService current = tournaments;
                return current == null ? Optional.empty()
                    : current.find(tournamentKey).map(ToffyTournament::rewards).filter(rewards -> !rewards.isEmpty());
            }
        };
    }

    /**
     * Sign-ups of an event, or of one group of them when {@code group} is not null. Online players outside the level
     * range of the tournament (or of the group) are left out and told why; offline players stay (their level cannot be
     * read).
     */
    private Optional<ToffyEvents.Roster> rosterOf(String tournamentKey, String group) {
        ToffyTournamentService current = tournaments;
        if (current == null) {
            return Optional.empty();
        }
        Optional<ToffyTournament> found = current.find(tournamentKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ToffyTournament tournament = found.get();
        ToffyGroup target = null;
        if (group != null) {
            target = current.group(tournament.key(), group).orElse(null);
            if (target == null) {
                return Optional.empty();
            }
        }
        Collection<UUID> ids = target == null ? tournament.participants().keySet() : target.members();
        Set<UUID> players = new LinkedHashSet<>();
        int skipped = 0;
        for (UUID id : List.copyOf(ids)) {
            Player online = server().getPlayer(id);
            if (online != null && !fitsTournament(tournament, target, online)) {
                skipped++;
                double level = realLevel(online);
                send(online, ToffyMessages.NIVEL_FUERA_DE_RANGO, tournament.key(),
                    rangeTexts(tournament, target, online, level));
                continue;
            }
            players.add(id);
        }
        if (skipped > 0) {
            plugin.getLogger().info(LOG_PREFIX + "Evento del torneo " + tournament.displayName() + ": " + skipped
                + " anotados fuera del rango de nivel quedaron afuera.");
        }
        String name = target == null ? tournament.displayName()
            : tournament.displayName() + " (grupo " + target.displayName() + ")";
        return Optional.of(new ToffyEvents.Roster(name, players, target == null ? null : target.key()));
    }

    /** Whether the online player is inside the level range of the tournament and, when given, of the group. */
    private boolean fitsTournament(ToffyTournament tournament, ToffyGroup group, Player player) {
        double level = realLevel(player);
        return tournament.allowsLevel(level) && (group == null || group.allowsLevel(level));
    }

    /** Creates {@code toffy_eventos.yml} on first start and copies the values of an old {@code staff-events} section. */
    private void prepareEventsFile() {
        Path file = dataPath(EVENTS_FILE);
        if (Files.exists(file)) {
            return;
        }
        plugin.saveResource(EVENTS_FILE, false);
        try {
            if (ToffyEventsConfig.importLegacy(file,
                plugin.getConfig().getConfigurationSection(ToffyEventsConfig.LEGACY_SECTION))) {
                plugin.getLogger().info(LOG_PREFIX + EVENTS_FILE
                    + " created with the values of the old staff-events section of config.yml.");
            }
        } catch (IOException failure) {
            plugin.getLogger().severe(LOG_PREFIX + "Could not copy the old staff-events values into " + EVENTS_FILE
                + ": " + failure.getMessage());
        }
    }

    private Optional<ToffyEventSettings> readEventSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(dataPath(EVENTS_FILE).toFile());
        } catch (IOException | InvalidConfigurationException invalid) {
            plugin.getLogger().severe(LOG_PREFIX + EVENTS_FILE + ": cannot read the file (" + invalid.getMessage()
                + "); keeping the previous event settings.");
            return Optional.empty();
        }
        return Optional.of(logEventWarnings(ToffyEventSettings.load(yaml, ToffyEventSettings::isSolidBlock)));
    }

    private ToffyEventSettings bundledEventSettings() {
        try {
            return logEventWarnings(ToffyEventSettings.load(bundled(EVENTS_FILE), ToffyEventSettings::isSolidBlock));
        } catch (IOException invalid) {
            throw new IllegalStateException("Bundled " + EVENTS_FILE + " is invalid: " + invalid.getMessage(), invalid);
        }
    }

    private ToffyEventSettings logEventWarnings(ToffyEventSettings settings) {
        settings.warnings().forEach(warning -> plugin.getLogger().warning("[Eventos] " + warning));
        return settings;
    }

    private ToffyTournamentService loadTournaments() {
        YamlToffyTournamentStorage storage = new YamlToffyTournamentStorage(dataPath(TOURNAMENTS_FILE));
        try {
            return ToffyTournamentService.load(storage,
            message -> plugin.getLogger().warning(LOG_PREFIX + message));
        } catch (IOException broken) {
            if (Files.exists(dataPath(TOURNAMENTS_FILE))) {
                plugin.getLogger().severe(LOG_PREFIX + "Could not read or move aside the tournaments file: "
                    + broken.getMessage() + ". Tournaments stay read-only until it is fixed and /toffy reload runs.");
                return new ToffyTournamentService(new ReadOnlyTournamentStorage(), List.of());
            }
            plugin.getLogger().severe(LOG_PREFIX + "Could not read the tournaments: " + broken.getMessage()
                + ". Starting without tournaments.");
            return new ToffyTournamentService(storage, List.of());
        }
    }

    /** Used when torneos.yml is unreadable and could not be moved aside: refuses every write so it is never overwritten. */
    private static final class ReadOnlyTournamentStorage implements ToffyTournamentStorage {
        @Override
        public List<ToffyTournament> load() {
            return List.of();
        }

        @Override
        public void save(Collection<ToffyTournament> tournaments) throws IOException {
            throw new IOException("torneos.yml no se pudo leer; arreglalo y usa /toffy reload");
        }
    }

    static ToffyStoredLocation stored(Location location) {
        return new ToffyStoredLocation(location.getWorld() == null ? "" : location.getWorld().getName(),
            location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    boolean teleportTo(Player player, Location target) {
        player.leaveVehicle();
        return player.teleport(target);
    }

    /**
     * Teleports the player to the saved place and forgets it. False when the world is gone or the teleport failed
     * (the saved place is kept).
     */
    boolean returnPlayer(Player player, String tournamentKey, ToffyStoredLocation saved) {
        World world = server().getWorld(saved.world());
        ToffyTournamentService current = tournaments;
        if (world == null || current == null) {
            return false;
        }
        Location target = new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
        if (!teleportTo(player, target)) {
            return false;
        }
        current.clearReturn(tournamentKey, player.getUniqueId());
        current.find(tournamentKey).ifPresent(tournament -> send(player, ToffyMessages.DEVUELTO, tournamentKey,
            ToffyTournamentCommand.placeholders(tournament, player, target)));
        return true;
    }

    /**
     * Teleports the online participants of the tournament whose level is inside its range (and inside the range of their
     * group), spread over {@code targets} (round robin). An explicit destination ({@code explicit}: coordinates typed in
     * the command) is used for everyone. Otherwise a grouped player goes to the places of its group when the group has
     * any, and to {@code targets} when it has none. With {@code onlyGroup} (a group key) only the members of that group
     * go. Players out of range are skipped and told why. Used by {@code /toffy tp concursantes} and by the scheduled
     * teleport.
     */
    Brought bringParticipants(ToffyTournamentService service, ToffyTournament tournament, List<Location> targets,
                              String onlyGroup, boolean explicit) {
        ToffyGroup only = null;
        if (onlyGroup != null) {
            only = tournament.group(onlyGroup).orElse(null);
            if (only == null) {
                return new Brought(0, 0, 0, 0);
            }
        }
        Collection<UUID> ids = only == null ? tournament.participants().keySet() : only.members();
        int offline = 0;
        int outOfRange = 0;
        Map<String, List<Player>> pools = new LinkedHashMap<>();
        for (UUID id : List.copyOf(ids)) {
            Player player = server().getPlayer(id);
            if (player == null) {
                offline++;
                continue;
            }
            ToffyGroup group = tournament.groupOf(id).flatMap(tournament::group).orElse(null);
            double level = realLevel(player);
            if (!fitsTournament(tournament, group, player)) {
                outOfRange++;
                send(player, ToffyMessages.NIVEL_FUERA_DE_RANGO, tournament.key(),
                    rangeTexts(tournament, group, player, level));
                continue;
            }
            pools.computeIfAbsent(group == null ? "" : group.key(), key -> new ArrayList<>()).add(player);
        }
        int moved = 0;
        int failed = 0;
        for (Map.Entry<String, List<Player>> pool : pools.entrySet()) {
            ToffyGroup group = pool.getKey().isEmpty() ? null : tournament.group(pool.getKey()).orElse(null);
            List<Location> destinations = ToffyGroupRules.destinations(explicit,
                group == null ? List.of() : savedLocations(group.places()), targets);
            List<Player> players = pool.getValue();
            if (destinations.isEmpty()) {
                failed += players.size();
                continue;
            }
            List<Integer> spots = ToffyPlaces.assign(destinations.size(), players.size(), RANDOM);
            for (int index = 0; index < players.size(); index++) {
                Player player = players.get(index);
                Location target = destinations.get(spots.get(index));
                boolean recorded = service.recordDeparture(tournament.key(), player.getUniqueId(),
                    stored(player.getLocation()));
                if (!teleportTo(player, target)) {
                    if (recorded) {
                        service.clearReturn(tournament.key(), player.getUniqueId());
                    }
                    failed++;
                    continue;
                }
                moved++;
                Map<String, String> values = new HashMap<>(ToffyTournamentCommand.placeholders(tournament, player,
                    target));
                values.put("grupo", group == null ? "" : group.displayName());
                send(player, ToffyMessages.LLEVADO, tournament.key(), values);
            }
        }
        return new Brought(moved, offline, failed, outOfRange);
    }

    /** Result of {@link #bringParticipants}. */
    record Brought(int moved, int offline, int failed, int outOfRange) {
    }

    /** Online participants whose level is inside the range of the tournament. */
    List<Player> eligibleOnline(ToffyTournament tournament) {
        List<Player> eligible = new ArrayList<>();
        for (UUID id : tournament.participants().keySet()) {
            Player player = server().getPlayer(id);
            if (player != null && fitsTournament(tournament, null, player)) {
                eligible.add(player);
            }
        }
        return eligible;
    }

    /** Placeholders of the level messages: {jugador} {torneo} {nivel} {min} {max}. */
    Map<String, String> rangeTexts(ToffyTournament tournament, Player player, double level) {
        return rangeTexts(tournament, null, player, level);
    }

    /**
     * Same as {@link #rangeTexts(ToffyTournament, Player, double)}. When a group is given, the range is the one of the
     * group that refused the player (named after the tournament) instead of the one of the tournament.
     */
    Map<String, String> rangeTexts(ToffyTournament tournament, ToffyGroup group, Player player, double level) {
        int decimals = requireService().settings().decimals();
        boolean groupRefused = group != null && !group.allowsLevel(level);
        Double min = groupRefused ? group.minLevel() : tournament.minLevel();
        Double max = groupRefused ? group.maxLevel() : tournament.maxLevel();
        return Map.of(
            "jugador", player.getName(),
            "torneo", groupRefused ? tournament.displayName() + " (grupo " + group.displayName() + ")"
                : tournament.displayName(),
            "nivel", ToffyCommand.level(level, decimals),
            "min", min == null ? "*" : ToffyCommand.level(min, decimals),
            "max", max == null ? "*" : ToffyCommand.level(max, decimals));
    }

    /**
     * The saved places as Bukkit locations; a place whose world is not loaded is left out.
     */
    List<Location> savedLocations(ToffyTournament tournament) {
        return savedLocations(tournament.places());
    }

    /** Same as {@link #savedLocations(ToffyTournament)} for any list of places (the places of a group, for example). */
    List<Location> savedLocations(List<ToffyStoredLocation> places) {
        List<Location> locations = new ArrayList<>();
        for (ToffyStoredLocation place : places) {
            Location location = savedLocation(place);
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    /**
     * The saved place as a Bukkit location, or null when its world is not loaded.
     */
    Location savedLocation(ToffyStoredLocation saved) {
        World world = server().getWorld(saved.world());
        return world == null ? null
            : new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
    }

    /**
     * Called after players changed group (or left one). A running or lobby event limited to a group withdraws the
     * players that are no longer in that group, as {@code /anotar salir} does. Main thread only.
     */
    void groupChanged(String tournamentKey, Collection<UUID> players) {
        ToffyEvents events = events();
        ToffyTournamentService current = tournaments;
        if (events == null || current == null) {
            return;
        }
        Optional<String> eventTournament = events.activeTournament();
        Optional<String> eventGroup = events.activeGroup();
        if (eventTournament.isEmpty() || eventGroup.isEmpty() || !eventTournament.get().equals(tournamentKey)) {
            return;
        }
        for (UUID id : List.copyOf(players)) {
            if (!current.groupOf(tournamentKey, id).equals(eventGroup)) {
                events.withdraw(id, tournamentKey);
            }
        }
    }

    /** Announces the closed sign-ups of the tournament to the whole server (when the message is not empty). */
    void announceClosed(ToffyTournament tournament) {
        announce(ToffyMessages.CIERRE, tournament.key(), ToffyTournamentCommand.placeholders(tournament, null, null));
    }

    /** Sends {@code caseKey} of the tournament to each online sign-up, with the given placeholders. */
    private void sendToOnlineSignUps(ToffyTournament tournament, String caseKey, Map<String, String> placeholders) {
        for (UUID id : tournament.participants().keySet()) {
            Player player = server().getPlayer(id);
            if (player != null) {
                Map<String, String> values = new HashMap<>(placeholders);
                values.put("jugador", player.getName());
                send(player, caseKey, tournament.key(), values);
            }
        }
    }

    private void announce(String caseKey, String tournamentKey, Map<String, String> placeholders) {
        String text = message(caseKey, tournamentKey, placeholders);
        if (text != null) {
            server().broadcastMessage(text);
        }
    }

    /**
     * Runs every 20 ticks: the warnings, the scheduled sign-up closes, the scheduled teleports and the scheduled events
     * that are due.
     */
    void checkSchedules() {
        ToffyTournamentService current = tournaments;
        if (current == null) {
            return;
        }
        long now = current.now();
        for (ToffyTournamentService.Warning warning : current.dueWarnings(now)) {
            current.find(warning.tournament()).ifPresent(tournament -> sendToOnlineSignUps(tournament,
                ToffyMessages.TP_AVISO, Map.of("torneo", tournament.displayName(),
                    "minutos", String.valueOf(warning.minutes()))));
            current.markWarned(warning.tournament(), warning.minutes());
        }
        for (ToffyTournamentService.Warning warning : current.dueEventWarnings(now)) {
            current.find(warning.tournament()).ifPresent(tournament -> sendToOnlineSignUps(tournament,
                ToffyMessages.EVENTO_AVISO, Map.of("torneo", tournament.displayName(),
                    "evento", eventName(tournament.eventType()),
                    "minutos", String.valueOf(warning.minutes()))));
            current.markEventWarned(warning.tournament(), warning.minutes());
        }
        for (String key : current.dueCloses(now)) {
            current.find(key).ifPresent(tournament -> {
                if (current.markClosed(key)) {
                    announce(ToffyMessages.CIERRE, key, ToffyTournamentCommand.placeholders(tournament, null, null));
                }
            });
        }
        for (String key : current.dueSchedules(now)) {
            ToffyTournament tournament = current.find(key).orElse(null);
            if (tournament == null) {
                continue;
            }
            try {
                runScheduledTeleport(current, tournament);
            } catch (RuntimeException failure) {
                logSevere("Teletransporte programado del torneo " + tournament.displayName() + " fallo.", failure);
            } finally {
                markFiredSafely(() -> current.markFired(key));
            }
        }
        for (String key : current.dueEvents(now)) {
            ToffyTournament tournament = current.find(key).orElse(null);
            if (tournament == null) {
                continue;
            }
            try {
                runScheduledEvent(current, tournament);
            } catch (RuntimeException failure) {
                logSevere("Evento programado del torneo " + tournament.displayName() + " fallo.", failure);
            } finally {
                markFiredSafely(() -> current.markEventFired(key));
            }
        }
    }

    private void runScheduledTeleport(ToffyTournamentService current, ToffyTournament tournament) {
        List<Location> targets = savedLocations(tournament);
        if (targets.isEmpty()) {
            plugin.getLogger().warning(LOG_PREFIX + "Teletransporte programado del torneo " + tournament.displayName()
                + " descartado: sus lugares no estan en mundos cargados.");
            return;
        }
        if (belowMinimum(tournament)) {
            return;
        }
        Brought result = bringParticipants(current, tournament, targets, null, false);
        plugin.getLogger().info(LOG_PREFIX + "Teletransporte programado del torneo " + tournament.displayName() + ": "
            + result.moved() + " llevados, " + result.offline() + " offline, " + result.outOfRange()
            + " fuera de rango, " + result.failed() + " no se pudieron mover.");
    }

    /** Cancels a scheduled run when the eligible online sign-ups are below the minimum. */
    private boolean belowMinimum(ToffyTournament tournament) {
        Integer minimum = tournament.minPlayers();
        int eligible = eligibleOnline(tournament).size();
        if (minimum == null || eligible >= minimum) {
            return false;
        }
        plugin.getLogger().warning(LOG_PREFIX + "Programado del torneo " + tournament.displayName()
            + " cancelado: " + eligible + " anotados conectados (minimo " + minimum + ").");
        announce(ToffyMessages.MINIMO_NO_ALCANZADO, tournament.key(), Map.of(
            "torneo", tournament.displayName(),
            "cantidad", String.valueOf(eligible),
            "minimo", String.valueOf(minimum)));
        return true;
    }

    private void runScheduledEvent(ToffyTournamentService current, ToffyTournament tournament) {
        Optional<ToffyEventType> type = ToffyEventType.parse(tournament.eventType() == null ? "" : tournament.eventType());
        ToffyEvents events = events();
        String reason = null;
        if (events == null) {
            reason = "Los eventos no estan disponibles.";
        } else if (type.isEmpty()) {
            reason = "Tipo de evento desconocido.";
        } else if (!events.startable()) {
            reason = "Ya hay un evento en curso o los eventos estan apagados.";
        }
        if (reason == null && belowMinimum(tournament)) {
            return;
        }
        if (reason != null) {
            plugin.getLogger().warning(LOG_PREFIX + "Evento programado del torneo " + tournament.displayName()
                + " cancelado: " + reason);
            sendToOnlineSignUps(tournament, ToffyMessages.EVENTO_CANCELADO, Map.of(
                "torneo", tournament.displayName(),
                "evento", eventName(tournament.eventType()),
                "motivo", reason));
            return;
        }
        boolean started = events.start(plugin.getServer().getConsoleSender(), type.get(), tournament.key(),
            tournament.eventGroup(), tournament.eventLobby());
        if (!started) {
            plugin.getLogger().warning(LOG_PREFIX + "Evento programado del torneo " + tournament.displayName()
                + " no se pudo iniciar.");
            sendToOnlineSignUps(tournament, ToffyMessages.EVENTO_CANCELADO, Map.of(
                "torneo", tournament.displayName(),
                "evento", eventName(tournament.eventType()),
                "motivo", "No se pudo iniciar el evento."));
        }
    }

    private String eventName(String typeId) {
        return ToffyEventType.parse(typeId == null ? "" : typeId).map(ToffyEventType::displayName)
            .orElse(typeId == null ? "" : typeId);
    }

    /** Logs a failure with its stack trace under the Toffy prefix. */
    void logSevere(String message, Throwable failure) {
        plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + message, failure);
    }

    private void markFiredSafely(Runnable change) {
        try {
            change.run();
        } catch (UncheckedIOException failure) {
            plugin.getLogger().severe(LOG_PREFIX + "No se pudo guardar torneos.yml: " + failure.getMessage());
        }
    }

    void playerJoined(Player player) {
        ToffyTournamentService current = tournaments;
        BukkitRuntimeRegistrations registrations = runtime;
        if (current == null || registrations == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        List<ToffyTournamentService.PendingReturn> owed = current.pendingReturns(playerId);
        if (owed.isEmpty()) {
            return;
        }
        registrations.scheduleDelayed("toffy-return-" + playerId, () -> {
            Player online = server().getPlayer(playerId);
            if (online == null) {
                return;
            }
            for (ToffyTournamentService.PendingReturn pending : owed) {
                returnPlayer(online, pending.tournament(), pending.location());
            }
        }, 1L);
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
        ToffyMemoryTracker activeTracker = tracker;
        if (service != null && activeTracker != null) {
            activeTracker.death(player.getUniqueId());
        }
    }

    void playerRespawned(Player player) {
        ToffyMemoryTracker activeTracker = tracker;
        if (activeTracker != null) {
            activeTracker.respawn(player.getUniqueId());
        }
    }

    void playerQuit(Player player) {
        ToffyMemoryTracker activeTracker = tracker;
        if (service != null && activeTracker != null) {
            evaluateReal(player);
            activeTracker.quit(player.getUniqueId());
        }
    }

    private void install(ToffySettings settings) {
        try {
            // Without a registry (not yet loaded) every catalog id is accepted rather than warned about.
            var registry = plugin.getCustomItemRegistry();
            new BukkitToffyKeyAudit(catalogId -> registry == null || registry.findById(catalogId).isPresent())
                .audit(settings)
                .forEach(warning -> plugin.getLogger().warning(LOG_PREFIX + warning));
        } catch (RuntimeException | LinkageError unavailableRegistry) {
            plugin.getLogger().warning(LOG_PREFIX + "Could not check keys against the server registries: "
                + unavailableRegistry);
        }
        service = new ToffyService(settings);
        lastValidSettings = settings;
    }

    private ToffyConfigLoadResult loadInstalled() {
        YamlConfiguration main = new YamlConfiguration();
        YamlConfiguration values = new YamlConfiguration();
        List<String> errors = new ArrayList<>();
        readInstalled(main, CONFIG_FILE, errors);
        readInstalled(values, VALUES_FILE, errors);
        if (!errors.isEmpty()) {
            return new ToffyConfigLoadResult(null, errors, List.of());
        }
        return ToffySettingsLoader.load(main, values);
    }

    private void readInstalled(YamlConfiguration yaml, String name, List<String> errors) {
        try {
            yaml.load(dataPath(name).toFile());
        } catch (IOException | InvalidConfigurationException invalid) {
            errors.add(name + " > (archivo completo): cannot read the file: " + invalid.getMessage());
        }
    }

    private ToffyConfigLoadResult loadBundled() {
        try {
            return ToffySettingsLoader.load(bundled(CONFIG_FILE), bundled(VALUES_FILE));
        } catch (IOException invalid) {
            return new ToffyConfigLoadResult(null, List.of(invalid.getMessage()), List.of());
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

    private ToffyService requireService() {
        ToffyService current = service;
        if (current == null) {
            throw new IllegalStateException("Toffy Complementaries no esta disponible (modulo apagado o sin iniciar).");
        }
        return current;
    }
}
