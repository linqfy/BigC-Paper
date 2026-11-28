package dev.linqfy.bigCasares.modules.toffy.eventos;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.InventoryCustody;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Staff-started events (Lifesteal, KOTH, TNT Tag, Spleef) of the Toffy Complementaries module. An event always runs
 * on a tournament's sign-up list. {@link dev.linqfy.bigCasares.modules.toffy.ToffyModule} creates this object and
 * enables it with its own registrations, so everything here is undone when the module is disabled.
 */
public final class ToffyEvents {
    /** Chat text cases, defined in {@code toffy_mensajes.yml}. */
    public static final String CASE_LOBBY = "evento-lobby";
    public static final String CASE_JOINED = "evento-unido";
    public static final String CASE_LEFT = "evento-salio";
    public static final String CASE_STARTED = "evento-empieza";
    public static final String CASE_WINNER = "evento-ganador";
    public static final String CASE_ELIMINATED = "evento-eliminado";
    public static final String CASE_CANCELLED = "evento-cancelado";
    public static final String CASE_FINISHED = "evento-fin";
    public static final String CASE_NO_DISPONIBLE = "evento-no-disponible";
    public static final String CASE_BOTON = "evento-boton";
    public static final String CASE_BOTON_AYUDA = "evento-boton-ayuda";
    public static final String CASE_CUENTA = "evento-cuenta";
    public static final String CASE_UNIDO_OTROS = "evento-unido-otros";
    public static final String CASE_PREPARANDO = "evento-preparando";
    public static final String CASE_REAPARECE = "evento-reaparece";
    public static final String CASE_YA_EMPEZO = "evento-ya-empezo";
    public static final String CASE_INVENTARIO_OCUPADO = "evento-inventario-ocupado";
    public static final String CASE_INVENTARIO_NO_GUARDADO = "evento-inventario-no-guardado";
    public static final String CASE_INVENTARIO_PENDIENTE = "evento-inventario-pendiente";
    public static final String CASE_COMANDO_BLOQUEADO = "evento-comando-bloqueado";
    public static final String CASE_TP_BLOQUEADO = "evento-tp-bloqueado";
    public static final String CASE_SALIDA_LIFESTEAL = "evento-salida-lifesteal";
    public static final String CASE_RECUPERADO = "evento-recuperado";
    public static final String CASE_RECUPERADO_ANTES = "evento-recuperado-antes";
    public static final String CASE_RECUPERACION_PENDIENTE = "evento-recuperacion-pendiente";
    public static final String CASE_PREMIO_PENDIENTE = "evento-premio-pendiente";
    public static final String CASE_PREMIO = "evento-premio";
    public static final String CASE_PREMIO_AL_ENTRAR = "evento-premio-al-entrar";
    public static final String CASE_CORAZON_AL_ENTRAR = "evento-corazon-al-entrar";
    public static final String CASE_ERROR_ARENA = "evento-error-arena";
    public static final String CASE_MOTIVO_MINIMO = "evento-motivo-minimo";
    public static final String CASE_MOTIVO_DETENER = "evento-motivo-detener";
    public static final String CASE_MOTIVO_TIEMPO_LIDER = "evento-motivo-tiempo-lider";
    public static final String CASE_MOTIVO_SIN_GANADOR = "evento-motivo-sin-ganador";
    public static final String CASE_MOTIVO_NADIE = "evento-motivo-nadie";
    public static final String CASE_CAUSA_MURIO = "evento-causa-murio";
    public static final String CASE_CAUSA_CAYO = "evento-causa-cayo";
    public static final String CASE_CAUSA_EXPLOTO = "evento-causa-exploto";
    public static final String CASE_CAUSA_DESCONECTO = "evento-causa-desconecto";
    public static final String CASE_CAUSA_SALIO = "evento-causa-salio";
    public static final String CASE_TNT_EXPLOTA = "evento-tnt-explota";
    public static final String CASE_LIFESTEAL_INICIADO = "evento-lifesteal-iniciado";
    public static final String CASE_LIFESTEAL_REGLAS = "evento-lifesteal-reglas";
    public static final String CASE_LIFESTEAL_META = "evento-lifesteal-meta";
    public static final String CASE_LIFESTEAL_TIEMPO = "evento-lifesteal-tiempo";
    public static final String CASE_LIFESTEAL_FIN = "evento-lifesteal-fin";
    public static final String CASE_LIFESTEAL_FIN_CORAZON = "evento-lifesteal-fin-corazon";
    public static final String CASE_LIFESTEAL_FIN_MAXIMO = "evento-lifesteal-fin-maximo";
    public static final String CASE_LIFESTEAL_FIN_NADIE = "evento-lifesteal-fin-nadie";
    public static final String CASE_LIFESTEAL_FIN_GANADORES = "evento-lifesteal-fin-ganadores";
    public static final String CASE_LIFESTEAL_ROBO_VICTIMA = "evento-lifesteal-robo-victima";
    public static final String CASE_LIFESTEAL_ROBO_ATACANTE = "evento-lifesteal-robo-atacante";
    public static final String CASE_LIFESTEAL_PISO = "evento-lifesteal-piso";
    public static final String CASE_LIFESTEAL_MAXIMO = "evento-lifesteal-maximo";
    public static final String CASE_LIFESTEAL_CANCELADO = "evento-lifesteal-cancelado";
    public static final String CASE_KOTH_AJUSTE = "evento-koth-ajuste";
    public static final String CASE_KOTH_TIRADO = "evento-koth-tirado";
    public static final String CASE_KOTH_LIBRE = "evento-koth-libre";
    public static final String CASE_KOTH_DISPUTADA = "evento-koth-disputada";
    public static final String CASE_KOTH_REY = "evento-koth-rey";
    public static final String CASE_KOTH_BARRA = "evento-koth-barra";
    public static final String CASE_TNT_TENES = "evento-tnt-tenes";
    public static final String CASE_TNT_CORRE = "evento-tnt-corre";
    public static final String CASE_TNT_RONDA = "evento-tnt-ronda";
    public static final String CASE_TNT_PASA_VICTIMA = "evento-tnt-pasa-victima";
    public static final String CASE_TNT_PASA_ATACANTE = "evento-tnt-pasa-atacante";
    public static final String CASE_SPLEEF_GRACIA = "evento-spleef-gracia";
    public static final String CASE_SPLEEF_ROMPER = "evento-spleef-romper";
    public static final String CASE_KIT_PALO = "evento-kit-palo";
    public static final String CASE_KIT_PALA = "evento-kit-pala";
    public static final String CASE_KIT_LORE = "evento-kit-lore";
    public static final String CASE_DESCRIPCION_KOTH = "evento-descripcion-koth";
    public static final String CASE_DESCRIPCION_TNT = "evento-descripcion-tnt";
    public static final String CASE_DESCRIPCION_SPLEEF = "evento-descripcion-spleef";

    /** Staff permission to start, stop and inspect events. */
    public static final String ADMIN_PERMISSION = ToffyEventRuntime.ADMIN_PERMISSION;
    public static final String PREFIX = ToffyEventRuntime.PREFIX;
    /** Shown by the Toffy commands when the events are not running (failed to start or module off). */
    public static final String UNAVAILABLE_TEXT = "§cLos eventos no estan disponibles.";
    private static final String UNAVAILABLE = UNAVAILABLE_TEXT;

    /**
     * A tournament as the events see it: its name and the players signed up to it. {@code group} is the key of the
     * group the event is limited to, or null for every sign-up.
     */
    public record Roster(String displayName, Set<UUID> players, String group) {
        public Roster {
            players = Set.copyOf(players);
        }

        public Roster(String displayName, Set<UUID> players) {
            this(displayName, players, null);
        }
    }

    /**
     * What a case shows: chat text, a title with subtitle, an action bar text, or nothing (sound only). The sound is
     * played to the same recipients; a blank key plays none. Volume and pitch are the Bukkit values.
     */
    public record Text(Kind kind, String text, String subtitle, String sound, float volume, float pitch) {
        public enum Kind { CHAT, TITLE, ACTIONBAR, NONE }

        public Text {
            kind = kind == null ? Kind.CHAT : kind;
            text = text == null ? "" : text;
            subtitle = subtitle == null ? "" : subtitle;
            sound = sound == null ? "" : sound;
            volume = clampVolume(volume);
            pitch = clampPitch(pitch);
        }

        /** Volume is kept between 0 and 10, as toffy_eventos.yml and toffy_mensajes.yml say. */
        public static float clampVolume(float volume) {
            return Math.max(0f, Math.min(10f, volume));
        }

        /** Pitch is kept between 0.5 and 2.0, as toffy_eventos.yml and toffy_mensajes.yml say. */
        public static float clampPitch(float pitch) {
            return Math.max(0.5f, Math.min(2f, pitch));
        }

        /**
         * The sound key as written, trimmed: "" for a blank key (silent), null when it is not a valid namespaced key
         * such as {@code minecraft:block.note_block.hat}.
         */
        public static String soundKey(String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }
            String key = raw.trim();
            return NamespacedKey.fromString(key) == null ? null : key;
        }

        public static Text chat(String text) {
            return new Text(Kind.CHAT, text, "", "", 1f, 1f);
        }

        public static Text actionBar(String text) {
            return new Text(Kind.ACTIONBAR, text, "", "", 1f, 1f);
        }
    }

    /**
     * Renders a case for a tournament (override first, then the file). {@code null} means send nothing. The placeholders
     * are the event values; the prefix placeholder is added by the owner of the texts.
     */
    @FunctionalInterface
    public interface EventMessages {
        Text render(String caseKey, String tournament, Map<String, String> placeholders);
    }

    /** Looks up a tournament by key; empty when it does not exist. */
    @FunctionalInterface
    public interface EventTournaments {
        Optional<Roster> roster(String tournamentKey);

        /** Sign-ups of one group of the tournament only; empty when the tournament or the group does not exist. */
        default Optional<Roster> roster(String tournamentKey, String group) {
            return group == null ? roster(tournamentKey) : Optional.empty();
        }

        /** Rewards the tournament gives its event winners instead of the file rewards; empty when it has none. */
        default Optional<ToffyEventSettings.Rewards> rewards(String tournamentKey) {
            return Optional.empty();
        }
    }

    private final BigCasares plugin;
    private final Supplier<Optional<DimensionPlatform>> dimensions;
    private final Supplier<Optional<Boolean>> pvpBaseline;
    private final Predicate<UUID> endHuntDeath;
    private final EventMessages messages;
    private final EventTournaments tournaments;
    private volatile ToffyEventRuntime runtime;

    public ToffyEvents(
        BigCasares plugin,
        Supplier<Optional<DimensionPlatform>> dimensions,
        Supplier<Optional<Boolean>> pvpBaseline,
        Predicate<UUID> endHuntDeath,
        EventMessages messages,
        EventTournaments tournaments
    ) {
        this.plugin = plugin;
        this.dimensions = dimensions;
        this.pvpBaseline = pvpBaseline;
        this.endHuntDeath = endHuntDeath;
        this.messages = messages;
        this.tournaments = tournaments;
    }

    /**
     * Starts the event services. The data folder is moved from {@code data/staff-events} first when needed. Everything
     * is registered through {@code registrations} and {@code scope}, so disabling the owner undoes it.
     */
    public void enable(BukkitRuntimeRegistrations registrations, RuntimeRegistrationScope scope,
                       ToffyEventSettings settings) {
        EventDataFolder.Resolution data = EventDataFolder.resolve(plugin.getDataFolder().toPath());
        if (data.conflict() != null) {
            // Checked before anything is registered, so a refused start leaves nothing behind in the scope.
            throw new IllegalStateException(data.conflict());
        }
        if (data.failure() != null) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not move data/staff-events to "
                + "data/toffy-complementaries/eventos; keeping the old folder so no inventory is lost.", data.failure());
        } else if (data.moved()) {
            plugin.getLogger().info("[Eventos] Moved data/staff-events to data/toffy-complementaries/eventos.");
        }
        Path folder = data.folder();
        EventSnapshotStore store = new EventSnapshotStore(folder.resolve("snapshots.yml"));
        try {
            store.load();
        } catch (IOException failure) {
            // Refuse to run rather than overwrite inventories that are still waiting to be returned.
            throw new IllegalStateException("Could not read event inventories", failure);
        }
        if (settings.arenaDimension()) {
            registerDimension(scope);
        }

        ToffyEventRuntime created = new ToffyEventRuntime(plugin, settings, store, dimensions, this::rewardItem,
            pvpBaseline, endHuntDeath, registrations, messages, tournaments, folder.resolve("rewards-pending"));
        InventoryCustody custody = created.custody();
        var services = plugin.getServer().getServicesManager();
        try {
            services.register(InventoryCustody.class, custody, plugin, ServicePriority.Normal);
            scope.register("toffy-events-custody", () -> services.unregister(InventoryCustody.class, custody));
        } catch (RuntimeException | Error failure) {
            try {
                services.unregister(InventoryCustody.class, custody);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        scope.register("toffy-events-runtime", () -> {
            created.shutdown();
            runtime = null;
        });
        runtime = created;
        registrations.registerListener("toffy-events-listener", created);
        registrations.scheduleRepeating("toffy-events-tick", created::tick, 5L, 5L);
        registrations.scheduleRepeating("toffy-events-prepare", created::prepareTick, 1L, 1L);
        registrations.scheduleRepeating("toffy-events-recover", created::retryBlockedRestores, 100L, 100L);
        // Other inventory owners register during module startup; recover only after they are visible.
        registrations.scheduleImmediate("toffy-events-initial-restore", created::restorePendingOnline);
    }

    /** Whether a death is already handled by a running event (keep inventory, no tombstone). */
    public boolean protectsDeath(UUID playerId) {
        ToffyEventRuntime current = runtime;
        return current != null && current.protectsDeath(playerId);
    }

    /** Whether the player is inside the running arena event. */
    public boolean participates(UUID playerId) {
        ToffyEventRuntime current = runtime;
        return current != null && current.participates(playerId);
    }

    /** Settings for the next event; a running event keeps its own until it ends. */
    public void applySettings(ToffyEventSettings settings) {
        ToffyEventRuntime current = runtime;
        if (current != null) {
            current.applySettings(settings);
        }
    }

    /**
     * {@code /toffy evento iniciar}: starts an event on the tournament's sign-up list. The online sign-ups join the
     * lobby; {@code lobbySeconds} overrides the configured countdown for arena events.
     */
    public boolean start(CommandSender sender, ToffyEventType type, String tournamentKey, Integer lobbySeconds) {
        return start(sender, type, tournamentKey, null, lobbySeconds);
    }

    /**
     * Same as {@link #start(CommandSender, ToffyEventType, String, Integer)}, limited to the members of {@code group}
     * when it is not null.
     */
    public boolean start(CommandSender sender, ToffyEventType type, String tournamentKey, String group,
                         Integer lobbySeconds) {
        ToffyEventRuntime current = runtime;
        if (current == null) {
            sender.sendMessage(UNAVAILABLE);
            return false;
        }
        Optional<Roster> roster = tournaments.roster(tournamentKey, group);
        if (roster.isEmpty()) {
            sender.sendMessage(group == null
                ? PREFIX + "§cNo existe el torneo " + tournamentKey + "."
                : PREFIX + "§cNo existe el grupo " + group + " en el torneo " + tournamentKey + ".");
            return false;
        }
        return current.start(sender, type, tournamentKey, roster.get(), lobbySeconds);
    }

    /** Group the running event (in any phase) is limited to, if any. */
    public Optional<String> activeGroup() {
        ToffyEventRuntime current = runtime;
        return current == null ? Optional.empty() : current.activeGroup();
    }

    /** Whether a new event may start now: events enabled and none running. */
    public boolean startable() {
        ToffyEventRuntime current = runtime;
        return current != null && current.startable();
    }

    /** Tournament of the running event, if any. */
    public Optional<String> activeTournament() {
        ToffyEventRuntime current = runtime;
        return current == null ? Optional.empty() : current.activeTournament();
    }

    /** Group the running event is limited to, if any (its lobby players must be members of it). */
    public Optional<String> lobbyGroup() {
        ToffyEventRuntime current = runtime;
        return current == null ? Optional.empty() : current.lobbyGroup();
    }

    /** Tournament whose event is waiting in its lobby, if any. */
    public Optional<String> lobbyTournament() {
        ToffyEventRuntime current = runtime;
        return current == null ? Optional.empty() : current.lobbyTournament();
    }

    /** {@code /toffy evento detener}. */
    public void stop(CommandSender sender) {
        ToffyEventRuntime current = runtime;
        if (current == null) {
            sender.sendMessage(UNAVAILABLE);
            return;
        }
        current.stop(sender);
    }

    /** Lines for {@code /toffy evento estado} and {@code /anotar estado}. */
    public List<String> status() {
        ToffyEventRuntime current = runtime;
        return current == null ? List.of(PREFIX + "§7No hay ningún evento en curso.") : current.status();
    }

    /** {@code /anotar <torneo>} while the lobby of that tournament's event is open. */
    public void joinLobby(Player player, String tournamentKey) {
        ToffyEventRuntime current = runtime;
        if (current != null) {
            current.joinLobby(player, tournamentKey);
        }
    }

    /**
     * Takes the player out of the running event of that tournament ({@code /anotar salir} or {@code /toffy torneo
     * sacar}). Works for offline players too. False when the player was not in that event.
     */
    public boolean withdraw(UUID playerId, String tournamentKey) {
        ToffyEventRuntime current = runtime;
        return current != null && current.withdraw(playerId, tournamentKey);
    }

    private void registerDimension(RuntimeRegistrationScope scope) {
        Optional<DimensionPlatform> platform = dimensions.get();
        if (platform.isEmpty()) {
            plugin.getLogger().warning("[Eventos] dimension-framework is disabled; arenas need fallback-arena-world.");
            return;
        }
        try {
            DimensionStatus status = platform.get().register(EventArenaDimension.create());
            plugin.getLogger().info("[Eventos] Registered " + EventArenaDimension.KEY + ": " + status.state()
                + (status.message().isBlank() ? "" : " (" + status.message() + ")"));
            scope.register("toffy-events-dimension", () -> platform.get().unregister(EventArenaDimension.KEY));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not register the event dimension", failure);
        }
    }

    private Optional<ItemStack> rewardItem(RewardItem reward) {
        if (reward.vanilla()) {
            Material material = Material.matchMaterial(reward.id());
            return material == null || !material.isItem()
                ? Optional.empty()
                : Optional.of(new ItemStack(material, reward.amount()));
        }
        var registry = plugin.getCustomItemRegistry();
        return registry == null
            ? Optional.empty()
            : registry.findById(reward.id()).map(item -> item.createItemStack(reward.amount()));
    }
}
