package dev.linqfy.bigCasares.modules.toffy.eventos;

import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.InventoryCustody;
import dev.linqfy.bigCasares.module.runtime.PlayerDataCheckpoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Staff-triggered events on a tournament's sign-up list. Only one runs at a time and nothing here starts on its
 * own.
 *
 * <p>Lifesteal happens in the normal worlds with keep inventory. Arena events (KOTH,
 * TNT Tag, Spleef) save the player's inventory to disk, hand out an event kit and move
 * them to a code-built arena in the event dimension; whatever happens, the saved state
 * is given back when they leave, are eliminated, the event ends or they next log in.</p>
 *
 * <p>Every player-facing text is a case of {@code toffy_mensajes.yml}; the sounds and boss bars come from
 * {@code toffy_eventos.yml}. Prizes and permanent hearts owed to offline players wait in {@link EventOwedStore}.</p>
 */
final class ToffyEventRuntime implements Listener {

    static final String PREFIX = "§6[Eventos] §r";
    static final String ADMIN_PERMISSION = "bigcasares.toffy.evento.admin";
    // The "staff_event_" key names stay as they were: saved player data already uses them.
    private static final NamespacedKey RESTORED_SNAPSHOT = new NamespacedKey("bigcasares", "staff_event_restored_snapshot");
    private static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of("anotar", "toffy", "tcomp", "evento", "eventos");
    // Chat texts that take a fragment of another case (or an item name) are never prefixed with the event prefix.
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 70;
    private static final int TITLE_FADE_OUT = 20;

    private enum Phase { PREPARING, LOBBY, RUNNING }

    /** Why a participant left an arena event. The reason is shown in the elimination message. */
    private enum Cause {
        DEATH(ToffyEvents.CASE_CAUSA_MURIO), FALL(ToffyEvents.CASE_CAUSA_CAYO), EXPLOSION(ToffyEvents.CASE_CAUSA_EXPLOTO),
        DISCONNECT(ToffyEvents.CASE_CAUSA_DESCONECTO), LEFT(ToffyEvents.CASE_CAUSA_SALIO);

        private final String caseKey;

        Cause(String caseKey) {
            this.caseKey = caseKey;
        }
    }

    private final JavaPlugin plugin;
    private ToffyEventSettings settings;
    private ToffyEventSettings pendingSettings;
    private final EventSnapshotStore store;
    private final Supplier<Optional<DimensionPlatform>> dimensions;
    private final Function<RewardItem, Optional<ItemStack>> rewardItems;
    private final Supplier<Optional<Boolean>> pvpBaseline;
    private final Predicate<UUID> endHuntDeath;
    private final ToffyEvents.EventMessages messages;
    private final ToffyEvents.EventTournaments tournaments;
    private final BukkitRuntimeRegistrations registrations;
    private final LifestealHearts hearts;
    private final Random random = new Random();
    private final Set<UUID> teleportBypass = new HashSet<>();
    private final Set<UUID> restoreAfterRespawn = new HashSet<>();
    // Players remain unable to interact until their restored inventory is confirmed on disk.
    private final Set<UUID> restoreBlocked = new LinkedHashSet<>();
    private final InventoryCustody custody = this::ownsInventory;
    private final EventRewardJournal rewardJournal;
    // Prizes and permanent hearts owed to players who were offline when the event ended.
    private final EventOwedStore owed;
    /** Deaths handled by an event, until the death event finishes (the tombstone runs after us). */
    private final Set<UUID> handledDeaths = new HashSet<>();
    // Sound keys the server refused; each is logged once.
    private final Set<String> rejectedSounds = new HashSet<>();
    private boolean warnedMainWorldFallback;
    private Session session;
    private int fastTicks;

    private static final class Session {
        private final ToffyEventType type;
        private final String id = UUID.randomUUID().toString();
        private final Set<UUID> participants = new LinkedHashSet<>();
        // Lifesteal: the tournament's online sign-ups when the event started (never changes during it).
        private final Set<UUID> roster = new LinkedHashSet<>();
        // Lifesteal: the hearts of roster players when they left, to decide on the permanent heart later.
        private final Map<UUID, Integer> lastHearts = new HashMap<>();
        // Lifesteal: the permanent hearts of roster players when they left, capped with the owed permanent hearts.
        private final Map<UUID, Integer> lastPermanent = new HashMap<>();
        // Arena events: every sign-up of the tournament (or group) when the event started; the lobby takes them in.
        private final Set<UUID> signUps = new LinkedHashSet<>();
        private String tournament;
        /** Group the event is limited to, or null for the whole sign-up list. */
        private String group;
        private String tournamentName;
        private final Map<World, Boolean> previousPvp = new LinkedHashMap<>();
        private final BossBar bar;
        private Phase phase;
        private int secondsLeft;
        private int totalSeconds;
        private int lobbySeconds;
        private World world;
        private ArenaLayout layout;
        private ArenaBuilder arena;
        private KothScores koth;
        private final Set<UUID> taggers = new HashSet<>();
        private final Map<UUID, Long> taggedAt = new HashMap<>();
        private int roundLeft;
        private int graceLeft;

        private Session(ToffyEventType type, BossBar bar) {
            this.type = type;
            this.bar = bar;
        }
    }

    ToffyEventRuntime(
        JavaPlugin plugin,
        ToffyEventSettings settings,
        EventSnapshotStore store,
        Supplier<Optional<DimensionPlatform>> dimensions,
        Function<RewardItem, Optional<ItemStack>> rewardItems,
        Supplier<Optional<Boolean>> pvpBaseline,
        Predicate<UUID> endHuntDeath,
        BukkitRuntimeRegistrations registrations,
        ToffyEvents.EventMessages messages,
        ToffyEvents.EventTournaments tournaments,
        Path rewardDirectory
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.dimensions = dimensions;
        this.rewardItems = rewardItems;
        this.pvpBaseline = pvpBaseline;
        this.endHuntDeath = endHuntDeath;
        this.registrations = registrations;
        this.messages = messages;
        this.tournaments = tournaments;
        this.hearts = new LifestealHearts(plugin);
        this.rewardJournal = new EventRewardJournal(rewardDirectory);
        this.owed = new EventOwedStore(rewardDirectory.resolveSibling("owed.yml"));
        // Never throws: a broken owed.yml is moved aside, and an unmovable one blocks saving and paying until restart.
        owed.load().forEach(warning -> plugin.getLogger().warning("[Eventos] " + warning));
        if (!owed.writable()) {
            plugin.getLogger().severe("[Eventos] Premios y corazones pendientes NO se entregan ni se guardan hasta reiniciar.");
        }
        try {
            if (rewardJournal.hasPending()) {
                plugin.getLogger().warning("[Eventos] Hay premios de una versión anterior marcados para revisión manual en "
                    + rewardJournal.directory() + " (los nuevos se pagan solos al volver a entrar).");
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] No se pudo revisar la evidencia de premios pendientes.", failure);
        }
    }

    // --- queries -------------------------------------------------------------------------------

    boolean protectsDeath(UUID playerId) {
        return handledDeaths.contains(playerId) || restoreBlocked.contains(playerId);
    }

    InventoryCustody custody() {
        return custody;
    }

    private boolean ownsInventory(UUID playerId) {
        if (isParticipant(playerId) || restoreBlocked.contains(playerId) || restoreAfterRespawn.contains(playerId)) {
            return true;
        }
        EventPlayerSnapshot snapshot = store.find(playerId).orElse(null);
        if (snapshot == null) return false;
        Player player = plugin.getServer().getPlayer(playerId);
        return player == null || !snapshot.revision().toString().equals(
            player.getPersistentDataContainer().get(RESTORED_SNAPSHOT, PersistentDataType.STRING));
    }

    List<String> status() {
        Session current = session;
        if (current == null) {
            return List.of(PREFIX + "§7No hay ningún evento en curso.");
        }
        List<String> lines = new ArrayList<>();
        lines.add(PREFIX + "§e" + current.type.displayName() + " §7— "
            + (current.phase == Phase.PREPARING ? "preparando arena"
                : current.phase == Phase.LOBBY ? "esperando jugadores" : "en juego")
            + " §7(" + formatTime(current.secondsLeft) + ")");
        if (current.tournamentName != null) {
            lines.add("§7Torneo: §f" + current.tournamentName);
        }
        if (current.type.arena()) {
            lines.add("§7Participantes (" + current.participants.size() + "): §f" + names(current.participants));
        }
        return lines;
    }

    /** Whether the player is inside the running arena event. */
    boolean participates(UUID playerId) {
        return isParticipant(playerId);
    }

    /** New settings apply right away when no event runs, otherwise when the running event ends. */
    void applySettings(ToffyEventSettings next) {
        if (session == null) {
            settings = next;
            pendingSettings = null;
        } else {
            pendingSettings = next;
        }
    }

    private void applyPending() {
        if (pendingSettings != null && session == null) {
            settings = pendingSettings;
            pendingSettings = null;
        }
    }

    // --- staff controls ------------------------------------------------------------------------

    /** @return true when the event started (its session exists now) */
    boolean start(CommandSender sender, ToffyEventType type, String tournamentKey, ToffyEvents.Roster roster,
                  Integer lobbyOverride) {
        if (!settings.enabled()) {
            sender.sendMessage(PREFIX + "§cLos eventos estan apagados (enabled: false en toffy_eventos.yml).");
            return false;
        }
        if (session != null) {
            sender.sendMessage(PREFIX + "§cYa hay un evento en curso (" + session.type.displayName()
                + "). Detenelo con /toffy evento detener.");
            return false;
        }
        if (type == ToffyEventType.LIFESTEAL) {
            startLifesteal(sender, tournamentKey, roster);
        } else {
            startArena(sender, type, tournamentKey, roster,
                lobbyOverride == null ? settings.lobbySeconds() : lobbyOverride);
        }
        if (session != null) {
            session.group = roster.group();
        }
        return session != null;
    }

    /** Group the running event (in any phase) is limited to, if any. */
    Optional<String> activeGroup() {
        Session current = session;
        return current == null ? Optional.empty() : Optional.ofNullable(current.group);
    }

    /** Group the event is limited to, if its lobby is open. */
    Optional<String> lobbyGroup() {
        Session current = session;
        return current == null || current.phase != Phase.LOBBY ? Optional.empty() : Optional.ofNullable(current.group);
    }

    /** Whether a new event may start now (events enabled and none running). */
    boolean startable() {
        return settings.enabled() && session == null;
    }

    /** Tournament of the running event, if any. */
    Optional<String> activeTournament() {
        Session current = session;
        return current == null ? Optional.empty() : Optional.of(current.tournament);
    }

    /** Tournament whose event is in its lobby (players may still join), if any. */
    Optional<String> lobbyTournament() {
        Session current = session;
        return current == null || current.phase != Phase.LOBBY ? Optional.empty() : Optional.of(current.tournament);
    }

    void stop(CommandSender sender) {
        Session current = session;
        if (current == null) {
            sender.sendMessage(PREFIX + "§7No hay ningún evento en curso.");
            return;
        }
        if (current.type == ToffyEventType.LIFESTEAL) {
            broadcastCase(ToffyEvents.CASE_LIFESTEAL_CANCELADO, current,
                values(current, "staff", sender.getName()));
            endLifesteal(false);
        } else {
            broadcastCase(ToffyEvents.CASE_CANCELLED, current,
                values(current, "motivo", fragment(ToffyEvents.CASE_MOTIVO_DETENER, current, "staff", sender.getName())));
            finishArena(List.of(), null);
        }
    }

    /** Called when the module is disabled: everybody gets their state back, nobody gets rewards. */
    void shutdown() {
        Session current = session;
        if (current == null) {
            return;
        }
        if (current.type == ToffyEventType.LIFESTEAL) {
            endLifesteal(false);
        } else {
            finishArena(List.of(), null);
        }
    }

    /** Restores players that logged in while the module was reloading. */
    void restorePendingOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hearts.clearUnlessSession(player, null);
            if (store.contains(player.getUniqueId())) {
                restore(player);
            }
        }
    }

    // --- player controls -----------------------------------------------------------------------

    /**
     * {@code /anotar <torneo>} for a tournament whose event is running: joins the lobby while it is open, otherwise
     * says why the player cannot join. Silent for players already in the event.
     */
    void joinLobby(Player player, String tournamentKey) {
        Session current = session;
        if (current == null || !tournamentKey.equals(current.tournament)
            || current.participants.contains(player.getUniqueId()) || current.roster.contains(player.getUniqueId())) {
            return;
        }
        if (current.type.arena() && current.phase == Phase.LOBBY) {
            join(current, player);
        } else if (current.phase == Phase.PREPARING) {
            sendCase(player, current, ToffyEvents.CASE_PREPARANDO, values(current));
        } else {
            sendCase(player, current, ToffyEvents.CASE_YA_EMPEZO, values(current));
        }
    }

    /**
     * Takes a player out of the running event of that tournament. An arena participant leaves the way {@code /anotar
     * salir} always did (inventory restored when online). A Lifesteal player leaves the roster, kills stop counting and
     * the event hearts are cleared, without the permanent reward. False when the player was not in that event.
     */
    boolean withdraw(UUID playerId, String tournamentKey) {
        Session current = session;
        if (current == null || !tournamentKey.equals(current.tournament)) {
            return false;
        }
        if (current.type == ToffyEventType.LIFESTEAL) {
            if (!current.roster.remove(playerId)) {
                return false;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                current.bar.removePlayer(player);
                hearts.clear(player);
                sendCase(player, current, ToffyEvents.CASE_SALIDA_LIFESTEAL, values(current));
            }
            return true;
        }
        if (!current.type.arena() || !current.participants.contains(playerId)) {
            return false;
        }
        if (current.koth != null) {
            current.koth.forget(playerId);
        }
        eliminate(current, playerId, Cause.LEFT, true);
        return true;
    }

    /** Commands a participant may still use during an event: the default list of {@code allowed-commands}. */
    static boolean allowedDuringEvent(String label) {
        return allowedDuringEvent(label, DEFAULT_ALLOWED_COMMANDS);
    }

    static boolean allowedDuringEvent(String label, Collection<String> allowed) {
        return allowed.contains(label);
    }

    private void join(Session current, Player player) {
        UUID playerId = player.getUniqueId();
        if (current.phase == Phase.PREPARING) {
            sendCase(player, current, ToffyEvents.CASE_PREPARANDO, values(current));
            return;
        }
        if (player.isDead()) {
            sendCase(player, current, ToffyEvents.CASE_REAPARECE, values(current));
            return;
        }
        if (InventoryCustody.ownedByOther(plugin.getServer(), playerId, custody)) {
            sendCase(player, current, ToffyEvents.CASE_INVENTARIO_OCUPADO, values(current));
            return;
        }
        if (store.contains(playerId) && !restore(player)) {
            sendCase(player, current, ToffyEvents.CASE_INVENTARIO_PENDIENTE, values(current));
            return;
        }
        try {
            store.put(capture(player));
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not save the inventory of " + player.getName(), failure);
            sendCase(player, current, ToffyEvents.CASE_INVENTARIO_NO_GUARDADO, values(current));
            return;
        }
        current.participants.add(playerId);
        prepare(player, GameMode.ADVENTURE);
        teleport(player, current.arena.lobbySpot());
        current.bar.addPlayer(player);
        sendCase(player, current, ToffyEvents.CASE_JOINED, values(current, "jugador", player.getName()));
        participantsCase(current, ToffyEvents.CASE_UNIDO_OTROS, values(current, "jugador", player.getName()));
    }

    /** Every sign-up of the tournament (or of its group) that is online joins the lobby when it opens. */
    private void joinSignedUp(Session current) {
        for (UUID playerId : List.copyOf(current.signUps)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !current.participants.contains(playerId)) {
                join(current, player);
            }
        }
    }

    /** Placeholders of a case; {@code current} may be null when no event runs (rewards of offline players). */
    private static Map<String, String> values(Session current, String... pairs) {
        Map<String, String> map = new HashMap<>();
        map.put("evento", current == null ? "" : current.type.displayName());
        map.put("torneo", current == null || current.tournamentName == null ? "" : current.tournamentName);
        map.put("clave", current == null || current.tournament == null ? "" : current.tournament);
        map.put("cantidad", current == null ? "0" : String.valueOf(current.participants.size()));
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }

    /** Text of a case rendered for the running event, or null when the case is empty (send nothing). */
    private ToffyEvents.Text text(String caseKey, Session current, Map<String, String> placeholders) {
        return messages.render(caseKey, current == null ? null : current.tournament, placeholders);
    }

    /** The plain text of a fragment case (a part of another message, or an item name); empty when empty. */
    private String fragment(String caseKey, Session current, String... pairs) {
        ToffyEvents.Text text = text(caseKey, current, values(current, pairs));
        return text == null ? "" : text.text();
    }

    private void sendCase(Player player, Session current, String caseKey, Map<String, String> placeholders) {
        ToffyEvents.Text text = text(caseKey, current, placeholders);
        if (text != null) {
            show(player, text);
        }
    }

    private void participantsCase(Session current, String caseKey, Map<String, String> placeholders) {
        ToffyEvents.Text text = text(caseKey, current, placeholders);
        if (text != null) {
            participants(current).forEach(player -> show(player, text));
        }
    }

    private void broadcastCase(String caseKey, Session current, Map<String, String> placeholders) {
        ToffyEvents.Text text = text(caseKey, current, placeholders);
        if (text != null) {
            broadcast(text);
        }
    }

    /** Shows a text to one player as its kind says (chat, title, action bar or nothing) and plays its sound. */
    private void show(Player player, ToffyEvents.Text text) {
        switch (text.kind()) {
            case CHAT -> {
                if (!text.text().isBlank()) player.sendMessage(text.text());
            }
            case TITLE -> player.sendTitle(text.text(), text.subtitle(), TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
            case ACTIONBAR -> player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text.text()));
            case NONE -> {
            }
        }
        play(player, text.sound(), text.volume(), text.pitch());
    }

    /** Plays a sound to one player. A sound the server rejects is logged once and skipped; nothing else breaks. */
    private void play(Player player, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), key, volume, pitch);
        } catch (RuntimeException rejected) {
            if (rejectedSounds.add(key)) {
                plugin.getLogger().log(Level.WARNING, "[Eventos] No se pudo reproducir el sonido " + key, rejected);
            }
        }
    }

    /** Shows a text to every online player; the console gets the chat and title texts. */
    private void broadcast(ToffyEvents.Text text) {
        Bukkit.getOnlinePlayers().forEach(player -> show(player, text));
        if (text.kind() != ToffyEvents.Text.Kind.NONE && !text.text().isBlank()) {
            Bukkit.getConsoleSender().sendMessage(text.text());
        }
    }

    /** Plays one of the event sounds ({@code sounds.*} in toffy_eventos.yml) to a player, unless it is silent. */
    private void playSound(Player player, String name) {
        ToffyEventSettings.SoundSpec sound = settings.sound(name);
        if (!sound.silent()) {
            play(player, sound.key(), sound.volume(), sound.pitch());
        }
    }

    // --- lifesteal -----------------------------------------------------------------------------

    private void startLifesteal(CommandSender sender, String tournamentKey, ToffyEvents.Roster roster) {
        List<UUID> online = roster.players().stream().filter(id -> Bukkit.getPlayer(id) != null).toList();
        if (online.isEmpty()) {
            sender.sendMessage(PREFIX + "§cNo hay concursantes online en " + roster.displayName() + ".");
            return;
        }
        if (online.size() < settings.lifesteal().minPlayers()) {
            sender.sendMessage(PREFIX + "§cHacen falta " + settings.lifesteal().minPlayers()
                + " concursantes online en " + roster.displayName() + "; hay " + online.size() + ".");
            return;
        }
        ToffyEventSettings.BossBarSpec look = settings.bossBar(ToffyEventType.LIFESTEAL);
        BossBar bar = Bukkit.createBossBar(barName(ToffyEventType.LIFESTEAL), look.color(), look.style());
        Session current = new Session(ToffyEventType.LIFESTEAL, bar);
        current.phase = Phase.RUNNING;
        current.tournament = tournamentKey;
        current.tournamentName = roster.displayName();
        current.roster.addAll(online);
        current.totalSeconds = settings.lifesteal().durationMinutes() * 60;
        current.secondsLeft = current.totalSeconds;
        session = current;
        World arenaWorld = arenaWorld().orElse(null);
        for (World world : Bukkit.getWorlds()) {
            if (world != arenaWorld) {
                forcePvp(current, world);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (current.roster.contains(player.getUniqueId())) {
                hearts.clearUnlessSession(player, current.id);
                bar.addPlayer(player);
            } else {
                hearts.clearUnlessSession(player, null);
            }
        }
        LifestealRules rules = settings.lifesteal().rules();
        Map<String, String> start = values(current, "staff", sender.getName(),
            "minutos", String.valueOf(settings.lifesteal().durationMinutes()),
            "cantidad", String.valueOf(current.roster.size()));
        broadcastCase(ToffyEvents.CASE_LIFESTEAL_INICIADO, current, start);
        broadcastCase(ToffyEvents.CASE_LIFESTEAL_REGLAS, current, values(current,
            "robo", String.valueOf(rules.heartsPerKill()), "piso", String.valueOf(rules.floorHearts())));
        broadcastCase(ToffyEvents.CASE_LIFESTEAL_META, current, values(current,
            "umbral", String.valueOf(rules.permanentRewardThreshold()),
            "permanente", String.valueOf(settings.lifesteal().permanentHeartReward())));
        updateBar(current);
    }

    private void lifestealSecond(Session current) {
        current.secondsLeft--;
        if (current.secondsLeft % 10 == 0) {
            current.previousPvp.keySet().forEach(world -> world.setPVP(true));
        }
        if (settings.lifesteal().warningSeconds().contains(current.secondsLeft)) {
            broadcastCase(ToffyEvents.CASE_LIFESTEAL_TIEMPO, current,
                values(current, "tiempo", formatTime(current.secondsLeft)));
        }
        updateBar(current);
        if (current.secondsLeft <= 0) {
            endLifesteal(true);
        }
    }

    private void endLifesteal(boolean rewards) {
        Session current = session;
        session = null;
        if (current == null) {
            return;
        }
        current.bar.removeAll();
        restorePvp(current);
        ToffyEventSettings.Lifesteal config = settings.lifesteal();
        LifestealRules rules = config.rules();
        List<String> rewarded = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (rewards && current.roster.contains(player.getUniqueId())) {
                int total = hearts.hearts(player);
                if (rules.earnsPermanentHeart(total)) {
                    int granted = grantPermanent(player, config.permanentHeartReward());
                    if (granted > 0) {
                        rewarded.add(player.getName());
                        sendCase(player, current, ToffyEvents.CASE_LIFESTEAL_FIN_CORAZON, values(current,
                            "total", String.valueOf(total), "permanente", String.valueOf(granted)));
                        giveRewards(player, config.rewards());
                    } else {
                        sendCase(player, current, ToffyEvents.CASE_LIFESTEAL_FIN_MAXIMO, values(current,
                            "total", String.valueOf(total)));
                    }
                }
            }
            hearts.clear(player);
        }
        if (rewards) {
            rewarded.addAll(owePermanentHearts(current, rules));
            broadcastCase(ToffyEvents.CASE_LIFESTEAL_FIN, current, values(current));
            broadcastCase(rewarded.isEmpty() ? ToffyEvents.CASE_LIFESTEAL_FIN_NADIE : ToffyEvents.CASE_LIFESTEAL_FIN_GANADORES,
                current, values(current, "umbral", String.valueOf(rules.permanentRewardThreshold()),
                    "jugadores", String.join(", ", rewarded)));
        }
        applyPending();
    }

    /**
     * Roster players who are offline at the end keep their permanent heart as an owed reward, given when they join.
     * Their hearts are the ones they had when they left. Returns the names of the ones who earned it.
     */
    private List<String> owePermanentHearts(Session current, LifestealRules rules) {
        List<String> names = new ArrayList<>();
        boolean changed = false;
        for (UUID playerId : current.roster) {
            if (Bukkit.getPlayer(playerId) != null) {
                continue;
            }
            Integer last = current.lastHearts.get(playerId);
            if (last == null || !rules.earnsPermanentHeart(last)) {
                continue;
            }
            ToffyEventSettings.Lifesteal config = settings.lifesteal();
            // The same cap as the online path: the permanent hearts they already had count against max-permanent-bonus-hearts.
            int room = Math.max(0, config.maxPermanentBonusHearts() - current.lastPermanent.getOrDefault(playerId, 0));
            int amount = Math.min(config.permanentHeartReward(), room);
            if (amount <= 0) {
                continue;
            }
            owed.addHearts(playerId, amount);
            if (!config.rewards().isEmpty()) {
                owed.addReward(playerId, new EventOwedStore.Reward(current.id, ToffyEventType.LIFESTEAL, config.rewards()));
            }
            names.add(name(playerId));
            changed = true;
        }
        if (changed) {
            saveOwed();
        }
        return names;
    }

    /** Gives up to {@code amount} permanent hearts, never past {@code maxPermanentBonusHearts}. Returns how many. */
    private int grantPermanent(Player player, int amount) {
        int max = settings.lifesteal().maxPermanentBonusHearts();
        int granted = 0;
        for (int index = 0; index < amount && hearts.permanentHearts(player) < max; index++) {
            hearts.addPermanentHeart(player);
            granted++;
        }
        return granted;
    }

    private void onLifestealDeath(Session current, PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        // Only the sign-ups of the event take part; anybody else killing them gets nothing.
        if (!current.roster.contains(victim.getUniqueId()) || !current.roster.contains(killer.getUniqueId())) {
            return;
        }
        LifestealRules rules = settings.lifesteal().rules();
        LifestealRules.KillOutcome outcome = rules.resolveKill(hearts.hearts(victim), hearts.hearts(killer));
        if (outcome.floorProtected()) {
            sendCase(killer, current, ToffyEvents.CASE_LIFESTEAL_PISO, values(current,
                "victima", victim.getName(), "piso", String.valueOf(rules.floorHearts())));
            return;
        }
        hearts.addEventHearts(victim, -outcome.victimLoss(), current.id);
        hearts.addEventHearts(killer, outcome.killerGain(), current.id);
        sendCase(victim, current, ToffyEvents.CASE_LIFESTEAL_ROBO_VICTIMA, values(current,
            "atacante", killer.getName(), "robado", String.valueOf(outcome.victimLoss())));
        if (outcome.killerGain() > 0) {
            sendCase(killer, current, ToffyEvents.CASE_LIFESTEAL_ROBO_ATACANTE, values(current,
                "robado", String.valueOf(outcome.killerGain()), "victima", victim.getName(),
                "total", String.valueOf(hearts.hearts(killer))));
        } else {
            sendCase(killer, current, ToffyEvents.CASE_LIFESTEAL_MAXIMO, values(current,
                "maximo", String.valueOf(rules.maxHearts())));
        }
    }

    // --- arena events --------------------------------------------------------------------------

    private void startArena(CommandSender sender, ToffyEventType type, String tournamentKey,
                            ToffyEvents.Roster roster, int lobbySeconds) {
        Optional<World> world = arenaWorld();
        if (world.isEmpty()) {
            sender.sendMessage(PREFIX + "§c" + arenaUnavailableReason());
            return;
        }
        ArenaLayout layout = ArenaLayout.of(type, settings);
        boolean dedicated = dimensions.get().flatMap(platform -> platform.world(EventArenaDimension.KEY))
            .filter(world.get()::equals).isPresent();
        ArenaBuilder arena;
        try {
            arena = new ArenaBuilder(world.get(), layout, settings.arena().materials(), plugin, !dedicated);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Eventos] Could not prepare the " + type.id() + " arena", failure);
            sender.sendMessage(PREFIX + "§cNo se pudo preparar la arena; revisá la consola.");
            return;
        }
        ToffyEventSettings.BossBarSpec look = settings.bossBar(type);
        BossBar bar = Bukkit.createBossBar(barName(type), look.color(), look.style());
        Session current = new Session(type, bar);
        current.phase = Phase.PREPARING;
        current.world = world.get();
        current.layout = layout;
        current.arena = arena;
        current.lobbySeconds = Math.max(5, lobbySeconds);
        current.tournament = tournamentKey;
        current.tournamentName = roster.displayName();
        current.signUps.addAll(roster.players());
        current.group = roster.group();
        session = current;
        sender.sendMessage(PREFIX + "§7Preparando la arena de " + type.displayName() + ".");
    }

    /** Build without blocking the command tick; the lobby only opens after every chunk is ready. */
    void prepareTick() {
        Session current = session;
        if (current == null || current.phase != Phase.PREPARING) return;
        try {
            if (!current.arena.step(settings.arena().buildBlocksPerTick())) return;
            current.arena.close();
            current.world.setTime(settings.arena().timeOfDay());
            if (session != current) return;
            current.phase = Phase.LOBBY;
            current.totalSeconds = current.lobbySeconds;
            current.secondsLeft = current.totalSeconds;
            Bukkit.getOnlinePlayers().forEach(current.bar::addPlayer);
            describe(current);
            joinSignedUp(current);
            announceJoin(current);
            updateBar(current);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not build the " + current.type.id() + " arena", failure);
            broadcastCase(ToffyEvents.CASE_ERROR_ARENA, current, values(current));
            if (session == current) finishArena(List.of(), null);
        }
    }

    /** Announces what the event is about: the description case of its type. */
    private void describe(Session current) {
        Map<String, String> values = switch (current.type) {
            case KOTH -> values(current, "knockback", String.valueOf(settings.koth().knockbackLevel()));
            case TNT_TAG -> values(current, "bombardeo", settings.tntTag().rewards().items().stream()
                .anyMatch(item -> item.startsWith("nuke_shot")) ? " El último en pie gana el bombardeo orbital (Nuke Shot)." : "");
            case SPLEEF -> values(current, "eficiencia", String.valueOf(settings.spleef().efficiencyLevel()));
            case LIFESTEAL -> values(current);
        };
        String caseKey = switch (current.type) {
            case KOTH -> ToffyEvents.CASE_DESCRIPCION_KOTH;
            case TNT_TAG -> ToffyEvents.CASE_DESCRIPCION_TNT;
            case SPLEEF -> ToffyEvents.CASE_DESCRIPCION_SPLEEF;
            case LIFESTEAL -> null;
        };
        if (caseKey != null) {
            broadcastCase(caseKey, current, values);
        }
    }

    /**
     * The lobby announcement, with the [UNIRSE] button when the text is chat. The announcement shows the countdown in
     * seconds; the button and its hover text come from their own cases.
     */
    private void announceJoin(Session current) {
        ToffyEvents.Text text = text(ToffyEvents.CASE_LOBBY, current, values(current,
            "segundos", String.valueOf(current.secondsLeft)));
        if (text == null) {
            return;
        }
        if (text.kind() != ToffyEvents.Text.Kind.CHAT) {
            broadcast(text);
            return;
        }
        Component message = LegacyComponentSerializer.legacySection().deserialize(text.text());
        String button = fragment(ToffyEvents.CASE_BOTON, current);
        if (!button.isBlank()) {
            String help = fragment(ToffyEvents.CASE_BOTON_AYUDA, current);
            message = message.append(LegacyComponentSerializer.legacySection().deserialize(" " + button)
                .clickEvent(ClickEvent.runCommand("/anotar " + current.tournament))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(help))));
        }
        plugin.getServer().broadcast(message);
        if (!text.sound().isBlank()) {
            Bukkit.getOnlinePlayers().forEach(player ->
                play(player, text.sound(), text.volume(), text.pitch()));
        }
    }

    private void lobbySecond(Session current) {
        current.secondsLeft--;
        if (settings.lobbyCountdownSeconds().contains(current.secondsLeft)) {
            announceJoin(current);
        } else if (current.secondsLeft > 0 && current.secondsLeft <= 5) {
            participantsCase(current, ToffyEvents.CASE_CUENTA, values(current,
                "segundos", String.valueOf(current.secondsLeft)));
            participants(current).forEach(player -> playSound(player, ToffyEventSettings.SOUND_LOBBY_COUNTDOWN));
        }
        updateBar(current);
        if (current.secondsLeft <= 0) {
            begin(current);
        }
    }

    private void begin(Session current) {
        List<Player> players = participants(current);
        int minimum = settings.minPlayers(current.type);
        if (players.size() < minimum) {
            broadcastCase(ToffyEvents.CASE_CANCELLED, current, values(current,
                "motivo", fragment(ToffyEvents.CASE_MOTIVO_MINIMO, current,
                    "minimo", String.valueOf(minimum), "cantidad", String.valueOf(players.size()))));
            finishArena(List.of(), null);
            return;
        }
        current.phase = Phase.RUNNING;
        current.bar.removeAll();
        players.forEach(current.bar::addPlayer);
        forcePvp(current, current.world);
        List<Location> spawns = current.arena.spawnPoints(players.size(), random);
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            prepare(player, current.type == ToffyEventType.SPLEEF ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            giveKit(current, player);
            teleport(player, spawns.get(index));
            playSound(player, ToffyEventSettings.SOUND_BEGIN);
        }
        switch (current.type) {
            case KOTH -> {
                current.totalSeconds = settings.koth().durationMinutes() * 60;
                current.koth = new KothScores(settings.koth().captureSeconds());
                participantsCase(current, ToffyEvents.CASE_KOTH_AJUSTE, values(current,
                    "segundos", String.valueOf(settings.koth().captureSeconds())));
            }
            case TNT_TAG -> {
                current.totalSeconds = settings.tntTag().maxMinutes() * 60;
                newTntRound(current);
            }
            case SPLEEF -> {
                current.totalSeconds = settings.spleef().maxMinutes() * 60;
                current.graceLeft = settings.spleef().graceSeconds();
                if (current.graceLeft > 0) {
                    participantsCase(current, ToffyEvents.CASE_SPLEEF_GRACIA, values(current,
                        "segundos", String.valueOf(current.graceLeft)));
                } else {
                    participantsCase(current, ToffyEvents.CASE_SPLEEF_ROMPER, values(current));
                }
            }
            case LIFESTEAL -> {
            }
        }
        current.secondsLeft = current.totalSeconds;
        participantsCase(current, ToffyEvents.CASE_STARTED, values(current));
        updateBar(current);
    }

    private void runningSecond(Session current) {
        current.secondsLeft--;
        if (current.secondsLeft % 10 == 0) {
            current.world.setPVP(true);
        }
        switch (current.type) {
            case KOTH -> kothSecond(current);
            case TNT_TAG -> tntSecond(current);
            case SPLEEF -> {
                if (current.graceLeft > 0 && --current.graceLeft == 0) {
                    participantsCase(current, ToffyEvents.CASE_SPLEEF_ROMPER, values(current));
                }
            }
            case LIFESTEAL -> {
            }
        }
        if (session != current) {
            return;
        }
        updateBar(current);
        if (current.secondsLeft <= 0) {
            timeOut(current);
        }
    }

    private void timeOut(Session current) {
        if (current.type == ToffyEventType.KOTH) {
            // A leader who already left the event does not win the time-out.
            Optional<UUID> leader = current.koth.leader().filter(current.participants::contains);
            if (leader.isPresent()) {
                finishArena(List.of(leader.get()), fragment(ToffyEvents.CASE_MOTIVO_TIEMPO_LIDER, current,
                    "jugador", name(leader.get())));
                return;
            }
        }
        finishArena(List.of(), fragment(ToffyEvents.CASE_MOTIVO_SIN_GANADOR, current));
    }

    private void kothSecond(Session current) {
        List<Player> players = participants(current);
        List<UUID> onHill = players.stream()
            .filter(player -> player.getWorld().equals(current.world))
            .filter(player -> {
                Location location = player.getLocation();
                return current.layout.onHill(location.getX(), location.getY(), location.getZ());
            })
            .map(Player::getUniqueId)
            .toList();
        KothScores.HillState state = current.koth.tick(onHill);
        String hill = switch (state) {
            case EMPTY -> fragment(ToffyEvents.CASE_KOTH_LIBRE, current);
            case CONTESTED -> fragment(ToffyEvents.CASE_KOTH_DISPUTADA, current);
            case HELD -> fragment(ToffyEvents.CASE_KOTH_REY, current, "rey", name(onHill.getFirst()));
        };
        for (Player player : players) {
            ToffyEvents.Text bar = text(ToffyEvents.CASE_KOTH_BARRA, current, values(current,
                "colina", hill,
                "puntos", String.valueOf(current.koth.points(player.getUniqueId())),
                "captura", String.valueOf(current.koth.captureSeconds())));
            if (bar != null) {
                show(player, bar);
            }
        }
        current.koth.capturedBy().ifPresent(winner -> finishArena(List.of(winner), ""));
    }

    private void newTntRound(Session current) {
        for (UUID previous : current.taggers) {
            Player player = Bukkit.getPlayer(previous);
            if (player != null) {
                untag(player);
            }
        }
        current.taggers.clear();
        current.taggedAt.clear();
        current.taggers.addAll(TntTagRules.chooseTaggers(
            current.participants, settings.tntTag().playersPerTagger(), random));
        current.roundLeft = settings.tntTag().roundSeconds();
        for (Player player : participants(current)) {
            if (current.taggers.contains(player.getUniqueId())) {
                tag(current, player);
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
            }
        }
        participantsCase(current, ToffyEvents.CASE_TNT_RONDA, values(current,
            "portadores", names(current.taggers),
            "tiene", current.taggers.size() == 1 ? "tiene" : "tienen",
            "segundos", String.valueOf(current.roundLeft)));
    }

    private void tntSecond(Session current) {
        current.roundLeft--;
        for (Player player : participants(current)) {
            boolean tagged = current.taggers.contains(player.getUniqueId());
            ToffyEvents.Text line = text(tagged ? ToffyEvents.CASE_TNT_TENES : ToffyEvents.CASE_TNT_CORRE, current,
                values(current, "segundos", String.valueOf(current.roundLeft)));
            if (line != null) {
                show(player, line);
            }
            if (tagged && current.roundLeft <= settings.tntTag().warningSeconds()) {
                playSound(player, ToffyEventSettings.SOUND_TNT_WARNING);
            }
        }
        if (current.roundLeft > 0) {
            return;
        }
        for (UUID taggerId : List.copyOf(current.taggers)) {
            Player tagger = Bukkit.getPlayer(taggerId);
            if (tagger != null) {
                tagger.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, tagger.getLocation(), 1);
                ToffyEventSettings.SoundSpec boom = settings.sound(ToffyEventSettings.SOUND_EXPLOSION);
                if (!boom.silent()) {
                    play(tagger, boom.key(), boom.volume(), boom.pitch());
                }
            }
            current.taggers.remove(taggerId);
            participantsCase(current, ToffyEvents.CASE_TNT_EXPLOTA, values(current,
                "jugador", name(taggerId)));
            eliminate(current, taggerId, Cause.EXPLOSION, true);
            if (session != current) {
                return;
            }
        }
        if (current.participants.size() >= 2) {
            newTntRound(current);
        }
    }

    private void onTntHit(Session current, Player attacker, Player victim) {
        if (!current.taggers.contains(attacker.getUniqueId()) || current.taggers.contains(victim.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        // A freshly tagged player cannot pass the TNT straight back (tnt-tag.tag-cooldown-ms).
        if (now - current.taggedAt.getOrDefault(attacker.getUniqueId(), 0L) < settings.tntTag().tagCooldownMillis()) {
            return;
        }
        current.taggers.remove(attacker.getUniqueId());
        untag(attacker);
        tag(current, victim);
        current.taggedAt.put(victim.getUniqueId(), now);
        sendCase(victim, current, ToffyEvents.CASE_TNT_PASA_VICTIMA, values(current,
            "atacante", attacker.getName()));
        sendCase(attacker, current, ToffyEvents.CASE_TNT_PASA_ATACANTE, values(current,
            "victima", victim.getName()));
    }

    private void tag(Session current, Player player) {
        current.taggers.add(player.getUniqueId());
        ItemStack helmet = new ItemStack(Material.TNT);
        ItemMeta meta = helmet.getItemMeta();
        meta.setDisplayName("§c§l¡TNT!");
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        helmet.setItemMeta(meta);
        player.getInventory().setHelmet(helmet);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        playSound(player, ToffyEventSettings.SOUND_TAG);
    }

    private void untag(Player player) {
        player.getInventory().setHelmet(null);
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    /** Removes a participant, gives their state back and checks whether someone has won. */
    private void eliminate(Session current, UUID playerId, Cause cause, boolean restoreNow) {
        if (!current.participants.remove(playerId)) {
            return;
        }
        current.taggers.remove(playerId);
        if (current.koth != null) {
            current.koth.forget(playerId);
        }
        Player player = Bukkit.getPlayer(playerId);
        boolean restored = false;
        if (player != null) {
            current.bar.removePlayer(player);
            if (restoreNow) {
                restored = restore(player);
            }
        }
        if (cause == Cause.LEFT) {
            participantsCase(current, ToffyEvents.CASE_LEFT, values(current, "jugador", name(playerId)));
            if (player != null) {
                sendCase(player, current, ToffyEvents.CASE_LEFT, values(current, "jugador", name(playerId)));
            }
        } else {
            participantsCase(current, ToffyEvents.CASE_ELIMINATED,
                values(current, "jugador", name(playerId), "motivo", fragment(cause.caseKey, current)));
        }
        if (player != null && restored) {
            sendCase(player, current, ToffyEvents.CASE_RECUPERADO, values(current));
        }
        if (current.phase != Phase.RUNNING) {
            return;
        }
        if (current.participants.size() <= 1) {
            List<UUID> winners = List.copyOf(current.participants);
            finishArena(winners, winners.isEmpty() ? fragment(ToffyEvents.CASE_MOTIVO_NADIE, current) : "");
        } else if (current.type == ToffyEventType.TNT_TAG && current.taggers.isEmpty() && current.roundLeft > 0) {
            newTntRound(current);
        }
    }

    /**
     * Ends the arena event and gives everybody their state back. A null detail means the event was cancelled or
     * shut down, so nobody is announced; otherwise the winners and the end of the event are broadcast.
     */
    private void finishArena(List<UUID> winners, String detail) {
        Session current = session;
        session = null;
        if (current == null) {
            applyPending();
            return;
        }
        current.bar.removeAll();
        if (current.arena != null) current.arena.close();
        restorePvp(current);
        Set<UUID> failedRestores = new HashSet<>();
        for (UUID playerId : List.copyOf(current.participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                try {
                    if (!restore(player)) failedRestores.add(playerId);
                } catch (RuntimeException failure) {
                    failedRestores.add(playerId);
                    restoreBlocked.add(playerId);
                    quarantineRestore(player, failure);
                }
            }
        }
        if (detail != null) {
            for (UUID winnerId : winners) {
                broadcastCase(ToffyEvents.CASE_WINNER, current, values(current, "jugador", name(winnerId)));
            }
            broadcastCase(ToffyEvents.CASE_FINISHED, current, values(current, "detalle", detail));
        }
        ToffyEventSettings.Rewards rewards = rewardsOf(current);
        for (UUID winnerId : winners) {
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner == null) {
                // Offline: the prize waits in the owed store and is given on the next join.
                deferReward(current, winnerId, rewards, false);
            } else if (failedRestores.contains(winnerId) || restoreBlocked.contains(winnerId)) {
                deferReward(current, winnerId, rewards, true);
            } else {
                giveWinnerRewards(winner, rewards);
            }
        }
        applyPending();
    }

    // --- ticking -------------------------------------------------------------------------------

    /** Every 5 ticks; full seconds every fourth call. */
    void tick() {
        Session current = session;
        if (current == null || current.phase == Phase.PREPARING) {
            return;
        }
        if (current.type.arena()) {
            checkPositions(current);
        }
        if (session != current || ++fastTicks % 4 != 0) {
            return;
        }
        if (current.type == ToffyEventType.LIFESTEAL) {
            lifestealSecond(current);
        } else if (current.phase == Phase.LOBBY) {
            lobbySecond(current);
        } else {
            runningSecond(current);
        }
    }

    private void checkPositions(Session current) {
        for (Player player : participants(current)) {
            if (session != current) {
                return;
            }
            Location location = player.getLocation();
            boolean outside = !location.getWorld().equals(current.world) || location.getY() < current.layout.fallOutY();
            if (!outside || player.isDead()) {
                continue;
            }
            if (current.phase == Phase.RUNNING && current.type == ToffyEventType.SPLEEF) {
                eliminate(current, player.getUniqueId(), Cause.FALL, true);
            } else if (current.phase == Phase.LOBBY) {
                teleport(player, current.arena.lobbySpot());
            } else {
                player.setFallDistance(0);
                teleport(player, current.arena.spawnPoints(1, random).getFirst());
                if (current.type == ToffyEventType.KOTH) {
                    sendCase(player, current, ToffyEvents.CASE_KOTH_TIRADO, values(current));
                }
            }
        }
    }

    // --- listeners -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        retireRestoredSnapshot(player);
        if (!store.contains(player.getUniqueId())) {
            restoreBlocked.remove(player.getUniqueId());
        }
        Session current = session;
        boolean lifestealPlayer = current != null && current.type == ToffyEventType.LIFESTEAL
            && current.roster.contains(player.getUniqueId());
        hearts.clearUnlessSession(player, lifestealPlayer ? current.id : null);
        if (current != null && (lifestealPlayer || current.phase == Phase.LOBBY)) {
            current.bar.addPlayer(player);
        }
        if (owed.has(player.getUniqueId())) {
            registrations.scheduleImmediate("toffy-events-owed", () -> {
                if (player.isOnline()) {
                    deliverOwed(player);
                }
            });
        }
        if (store.contains(player.getUniqueId()) && !isParticipant(player.getUniqueId())) {
            restoreBlocked.add(player.getUniqueId());
            registrations.scheduleImmediate("toffy-events-join-restore", () -> {
                if (player.isOnline() && !isParticipant(player.getUniqueId())) {
                    if (restore(player)) {
                        sendCase(player, session, ToffyEvents.CASE_RECUPERADO_ANTES, values(session));
                        deliverOwed(player);
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Session current = session;
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (current != null && current.type == ToffyEventType.LIFESTEAL && current.roster.contains(playerId)) {
            current.lastHearts.put(playerId, hearts.hearts(player));
            current.lastPermanent.put(playerId, hearts.permanentHearts(player));
        }
        if (current != null && current.participants.contains(playerId)) {
            eliminate(current, playerId, Cause.DISCONNECT, true);
        }
    }

    // LOW: before the keep-inventory totem (NORMAL), which skips deaths that already keep the inventory.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (restoreBlocked.contains(victim.getUniqueId())) {
            handledDeaths.add(victim.getUniqueId());
            keepEverything(event);
            return;
        }
        Session current = session;
        if (current == null) return;
        if (current.participants.contains(victim.getUniqueId())) {
            handledDeaths.add(victim.getUniqueId());
            keepEverything(event);
        } else if (current.type == ToffyEventType.LIFESTEAL && LifestealRules.keepsLifestealDeath(
            settings.lifesteal().keepInventoryEveryone(), current.roster.contains(victim.getUniqueId()),
            isEventDimension(victim.getWorld()), endHuntDeath.test(victim.getUniqueId()))) {
            handledDeaths.add(victim.getUniqueId());
            keepEverything(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathResolved(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID playerId = victim.getUniqueId();
        boolean handled = handledDeaths.remove(playerId);
        if (!handled || event.isCancelled()) return;
        if (restoreBlocked.contains(playerId)) {
            restoreAfterRespawn.add(playerId);
            return;
        }
        Session current = session;
        if (current == null) return;
        if (current.participants.contains(playerId)) {
            restoreAfterRespawn.add(playerId);
            eliminate(current, playerId, Cause.DEATH, false);
        } else if (current.type == ToffyEventType.LIFESTEAL && !isEventDimension(victim.getWorld())) {
            onLifestealDeath(current, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!restoreAfterRespawn.remove(player.getUniqueId())) {
            return;
        }
        store.find(player.getUniqueId()).ifPresent(snapshot -> event.setRespawnLocation(locationOf(snapshot)));
        registrations.scheduleImmediate("toffy-events-respawn-restore", () -> {
            if (player.isOnline() && restore(player)) {
                deliverOwed(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        Session current = session;
        if (current == null || !current.type.arena() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? attackingPlayer(byEntity.getDamager()) : null;
        boolean victimIn = current.participants.contains(victim.getUniqueId());
        boolean attackerIn = attacker != null && current.participants.contains(attacker.getUniqueId());
        if (!victimIn) {
            if (attackerIn) {
                event.setCancelled(true);
            }
            return;
        }
        if (current.phase != Phase.RUNNING || !attackerIn) {
            event.setCancelled(true);
            return;
        }
        switch (current.type) {
            // Knockback without damage: falling off the hill is the only way to lose it.
            case KOTH -> event.setDamage(0.0);
            case TNT_TAG -> {
                event.setCancelled(true);
                onTntHit(current, attacker, victim);
            }
            default -> event.setCancelled(true);
        }
    }

    // Break and place protection of the arena world only applies while an arena event runs in that world.
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Session current = session;
        Player player = event.getPlayer();
        if (restoreBlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (current == null || !current.type.arena()) {
            // The real event dimension is always protected; the fallback world only during an arena session.
            if (isEventDimension(event.getBlock().getWorld()) && !player.hasPermission(ADMIN_PERMISSION)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!current.participants.contains(player.getUniqueId())) {
            if (event.getBlock().getWorld().equals(current.world) && !player.hasPermission(ADMIN_PERMISSION)) {
                event.setCancelled(true);
            }
            return;
        }
        boolean spleefBreak = current.type == ToffyEventType.SPLEEF
            && current.phase == Phase.RUNNING
            && current.graceLeft <= 0
            && event.getBlock().getType() == Material.SNOW_BLOCK;
        if (spleefBreak) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Session current = session;
        if (restoreBlocked.contains(event.getPlayer().getUniqueId())
            || isParticipant(event.getPlayer().getUniqueId())
            || ((isEventDimension(event.getBlock().getWorld())
                || (current != null && current.type.arena() && event.getBlock().getWorld().equals(current.world)))
                && !event.getPlayer().hasPermission(ADMIN_PERMISSION))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId()) || isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (restoreBlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Session current = session;
        if (current == null || !current.participants.contains(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (!allowedDuringEvent(label, settings.allowedCommands())) {
            event.setCancelled(true);
            sendCase(player, current, ToffyEvents.CASE_COMANDO_BLOQUEADO, values(current));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Session current = session;
        Player player = event.getPlayer();
        if (teleportBypass.contains(player.getUniqueId())) return;
        if (restoreBlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (current == null || !current.participants.contains(player.getUniqueId())) return;
        if (event.getTo() != null && !current.world.equals(event.getTo().getWorld())) {
            event.setCancelled(true);
            sendCase(player, current, ToffyEvents.CASE_TP_BLOQUEADO, values(current));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedMove(PlayerMoveEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId()) && !teleportBypass.contains(event.getPlayer().getUniqueId())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedInteraction(PlayerInteractEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedEntityInteraction(PlayerInteractEntityEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedInventoryClick(InventoryClickEvent event) {
        if (restoreBlocked.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedInventoryDrag(InventoryDragEvent event) {
        if (restoreBlocked.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedSwap(PlayerSwapHandItemsEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedConsume(PlayerItemConsumeEvent event) {
        if (restoreBlocked.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedPickup(EntityPickupItemEvent event) {
        if (restoreBlocked.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockedDamage(EntityDamageEvent event) {
        if (restoreBlocked.isEmpty()) return;
        if (restoreBlocked.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackingPlayer(byEntity.getDamager());
            if (attacker != null && restoreBlocked.contains(attacker.getUniqueId())) event.setCancelled(true);
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private boolean isParticipant(UUID playerId) {
        Session current = session;
        return current != null && current.participants.contains(playerId);
    }

    private Optional<World> arenaWorld() {
        if (settings.arenaDimension()) {
            Optional<World> world = dimensions.get().flatMap(platform -> platform.world(EventArenaDimension.KEY));
            if (world.isPresent()) {
                return world;
            }
        }
        if (settings.fallbackArenaWorld().isEmpty()) {
            return Optional.empty();
        }
        World fallback = Bukkit.getWorld(settings.fallbackArenaWorld());
        if (fallback != null && isMainWorld(fallback)) {
            // The main world is never an arena: the arenas wipe what is in their world.
            if (!warnedMainWorldFallback) {
                warnedMainWorldFallback = true;
                plugin.getLogger().warning("[Eventos] fallback-arena-world '" + fallback.getName()
                    + "' es el mundo principal del servidor; se ignora. Usá un mundo vacío dedicado.");
            }
            return Optional.empty();
        }
        return Optional.ofNullable(fallback);
    }

    private static boolean isMainWorld(World world) {
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.getFirst().equals(world);
    }

    /** Whether the world is the event dimension itself (not the fallback world). */
    private boolean isEventDimension(World world) {
        return dimensions.get().flatMap(platform -> platform.world(EventArenaDimension.KEY))
            .filter(world::equals).isPresent();
    }

    private String arenaUnavailableReason() {
        if (settings.arenaDimension()) {
            Optional<DimensionPlatform> platform = dimensions.get();
            if (platform.isEmpty()) {
                return "El dimension-framework está desactivado y no hay fallback-arena-world.";
            }
            DimensionStatus status = platform.get().status(EventArenaDimension.KEY);
            if (status.state() == DimensionStatus.State.PENDING_RESTART) {
                return "La dimensión de eventos se instaló y aparece después del próximo reinicio.";
            }
            return "La dimensión de eventos no está activa (" + status.state() + ").";
        }
        if (!settings.fallbackArenaWorld().isEmpty() && isMainWorld(Bukkit.getWorld(settings.fallbackArenaWorld()))) {
            return "fallback-arena-world no puede ser el mundo principal; elegí un mundo vacío dedicado.";
        }
        return "No hay mundo de arenas: configurá fallback-arena-world en toffy_eventos.yml.";
    }

    private EventPlayerSnapshot capture(Player player) {
        Location location = player.getLocation();
        return new EventPlayerSnapshot(
            player.getUniqueId(),
            player.getInventory().getContents(),
            new EventPlayerSnapshot.StoredLocation(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch()),
            player.getGameMode().name(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getLevel(),
            player.getExp(),
            List.copyOf(player.getActivePotionEffects()));
    }

    private void prepare(Player player, GameMode mode) {
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(mode);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
    }

    private void giveKit(Session current, Player player) {
        PlayerInventory inventory = player.getInventory();
        switch (current.type) {
            case KOTH -> inventory.setItem(0, kitItem(Material.STICK, fragment(ToffyEvents.CASE_KIT_PALO, current),
                fragment(ToffyEvents.CASE_KIT_LORE, current), Enchantment.KNOCKBACK, settings.koth().knockbackLevel()));
            case SPLEEF -> inventory.setItem(0, kitItem(Material.WOODEN_SHOVEL, fragment(ToffyEvents.CASE_KIT_PALA, current),
                fragment(ToffyEvents.CASE_KIT_LORE, current), Enchantment.EFFICIENCY, settings.spleef().efficiencyLevel()));
            default -> {
            }
        }
    }

    private static ItemStack kitItem(Material material, String name, String lore, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (!name.isBlank()) {
            meta.setDisplayName(name);
        }
        if (!lore.isBlank()) {
            meta.setLore(List.of(lore));
        }
        meta.addEnchant(enchantment, level, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void retireRestoredSnapshot(Player player) {
        var playerData = player.getPersistentDataContainer();
        String restored = playerData.get(RESTORED_SNAPSHOT, PersistentDataType.STRING);
        if (restored == null) {
            return;
        }
        if (store.find(player.getUniqueId()).stream().noneMatch(snapshot -> snapshot.revision().toString().equals(restored))) {
            playerData.remove(RESTORED_SNAPSHOT);
            return;
        }
        try {
            store.remove(player.getUniqueId());
            playerData.remove(RESTORED_SNAPSHOT);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "[Eventos] Could not retire the restored snapshot of "
                + player.getName(), failure);
        }
    }

    /** Restores a snapshot once per revision; a fresh join confirms it reached durable player data. */
    private boolean restore(Player player) {
        Optional<EventPlayerSnapshot> found = store.find(player.getUniqueId());
        if (found.isEmpty()) {
            return false;
        }
        if (player.isDead()) {
            restoreAfterRespawn.add(player.getUniqueId());
            restoreBlocked.add(player.getUniqueId());
            return false;
        }
        EventPlayerSnapshot snapshot = found.get();
        var playerData = player.getPersistentDataContainer();
        String revision = snapshot.revision().toString();
        if (revision.equals(playerData.get(RESTORED_SNAPSHOT, PersistentDataType.STRING))) {
            if (restoreBlocked.contains(player.getUniqueId())) {
                try {
                    PlayerDataCheckpoint.confirm(player, RESTORED_SNAPSHOT, revision);
                } catch (IOException | RuntimeException failure) {
                    quarantineRestore(player, failure);
                    return false;
                }
            }
            restoreBlocked.remove(player.getUniqueId());
            return true;
        }
        if (InventoryCustody.ownedByOther(plugin.getServer(), player.getUniqueId(), custody)) {
            restoreBlocked.add(player.getUniqueId());
            return false;
        }
        restoreBlocked.add(player.getUniqueId());
        EventPlayerSnapshot before;
        ItemStack cursor;
        String previousReceipt;
        try {
            player.closeInventory();
            before = capture(player);
            ItemStack currentCursor = player.getItemOnCursor();
            cursor = currentCursor == null ? null : currentCursor.clone();
            previousReceipt = playerData.get(RESTORED_SNAPSHOT, PersistentDataType.STRING);
        } catch (RuntimeException failure) {
            quarantineRestore(player, failure);
            return false;
        }
        boolean restored = ToffyEventRestore.attempt(() -> {
            applySnapshot(player, snapshot);
            PlayerDataCheckpoint.save(player, RESTORED_SNAPSHOT, revision);
        }, () -> {
            if (previousReceipt == null) playerData.remove(RESTORED_SNAPSHOT);
            else playerData.set(RESTORED_SNAPSHOT, PersistentDataType.STRING, previousReceipt);
            applySnapshot(player, before);
            player.setItemOnCursor(cursor);
        }, failure -> quarantineRestore(player, failure));
        if (restored) {
            restoreBlocked.remove(player.getUniqueId());
        }
        return restored;
    }

    private void quarantineRestore(Player player, Exception failure) {
        try {
            player.setGameMode(GameMode.SPECTATOR);
        } catch (RuntimeException spectatorFailure) {
            failure.addSuppressed(spectatorFailure);
        }
        plugin.getLogger().log(Level.SEVERE, "[Eventos] Inventory restore is pending for " + player.getName(), failure);
        sendCase(player, session, ToffyEvents.CASE_RECUPERACION_PENDIENTE, values(session));
    }

    private void applySnapshot(Player player, EventPlayerSnapshot snapshot) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(Arrays.copyOf(snapshot.contents(), inventory.getSize()));
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        snapshot.effects().forEach(player::addPotionEffect);
        try {
            player.setGameMode(GameMode.valueOf(snapshot.gameMode()));
        } catch (IllegalArgumentException unknown) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.max(1.0, Math.min(max, snapshot.health())));
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setLevel(snapshot.level());
        player.setExp(Math.max(0f, Math.min(1f, snapshot.exp())));
        player.setFireTicks(0);
        player.setFallDistance(0);
        teleport(player, locationOf(snapshot));
    }

    /** Retry one player at a time to keep recovery I/O bounded. */
    void retryBlockedRestores() {
        for (UUID playerId : List.copyOf(restoreBlocked)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                restoreBlocked.remove(playerId);
                continue;
            }
            if (isParticipant(playerId)) continue;
            if (restore(player)) {
                sendCase(player, session, ToffyEvents.CASE_RECUPERADO_ANTES, values(session));
                deliverOwed(player);
            } else {
                restoreBlocked.remove(playerId);
                restoreBlocked.add(playerId);
            }
            break;
        }
    }

    private static Location locationOf(EventPlayerSnapshot snapshot) {
        EventPlayerSnapshot.StoredLocation stored = snapshot.location();
        World world = Bukkit.getWorld(stored.world());
        if (world == null) {
            return Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        return new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), stored.pitch());
    }

    private void teleport(Player player, Location target) {
        player.leaveVehicle();
        teleportBypass.add(player.getUniqueId());
        try {
            player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            teleportBypass.remove(player.getUniqueId());
        }
    }

    private static void keepEverything(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    private void forcePvp(Session current, World world) {
        current.previousPvp.putIfAbsent(world, world.getPVP());
        world.setPVP(true);
    }

    private void restorePvp(Session current) {
        Optional<Boolean> baseline = pvpBaseline.get();
        current.previousPvp.forEach((world, previous) -> world.setPVP(baseline.orElse(previous)));
        current.previousPvp.clear();
    }

    /**
     * The prizes of the event: the tournament's own rewards when it has any, otherwise the ones of toffy_eventos.yml.
     */
    private ToffyEventSettings.Rewards rewardsOf(Session current) {
        if (current.type != ToffyEventType.LIFESTEAL) {
            Optional<ToffyEventSettings.Rewards> own = tournaments.rewards(current.tournament);
            if (own.isPresent()) {
                return own.get();
            }
        }
        return switch (current.type) {
            case KOTH -> settings.koth().rewards();
            case TNT_TAG -> settings.tntTag().rewards();
            case SPLEEF -> settings.spleef().rewards();
            case LIFESTEAL -> ToffyEventSettings.Rewards.none();
        };
    }

    /**
     * Keeps the prize of a winner who cannot get it now: the journal records it as evidence and the owed store gives
     * it when the player next joins. An online winner is told it is pending.
     */
    /**
     * Owes a winner who cannot get the prize now. The owed store is the only channel that pays it, on the next join.
     * The journal only keeps an audit entry marked auto-owed, so nobody is asked to pay it by hand.
     */
    private void deferReward(Session current, UUID playerId, ToffyEventSettings.Rewards rewards, boolean online) {
        if (rewards.isEmpty()) return;
        owed.addReward(playerId, new EventOwedStore.Reward(current.id, current.type, rewards));
        saveOwed();
        audit(new EventRewardJournal.Pending(UUID.fromString(current.id), playerId, name(playerId),
            current.type, Instant.now(), rewards));
        if (online) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                sendCase(player, current, ToffyEvents.CASE_PREMIO_PENDIENTE, values(current));
            }
        }
    }

    /**
     * Gives a player what the owed store holds: permanent hearts and prizes. Called only once the inventory restore is
     * confirmed. The removal is saved before anything is given; when the save fails nothing is given and the entry
     * stays owed.
     */
    private void deliverOwed(Player player) {
        UUID playerId = player.getUniqueId();
        if (!owed.has(playerId) || !owed.writable() || isParticipant(playerId) || restoreBlocked.contains(playerId)) {
            return;
        }
        Optional<EventOwedStore.Owed> entry = owed.take(playerId);
        if (entry.isEmpty()) {
            return;
        }
        try {
            owed.save();
        } catch (IOException failure) {
            owed.keep(playerId, entry.get());
            plugin.getLogger().log(Level.SEVERE, "[Eventos] No se pudo guardar la entrega de premios de "
                + player.getName() + "; sigue pendiente.", failure);
            return;
        }
        int granted = grantPermanent(player, entry.get().hearts());
        if (granted > 0) {
            sendCase(player, null, ToffyEvents.CASE_CORAZON_AL_ENTRAR, values(null, "corazones", String.valueOf(granted)));
        }
        for (EventOwedStore.Reward reward : entry.get().rewards()) {
            sendCase(player, null, ToffyEvents.CASE_PREMIO_AL_ENTRAR, values(null,
                "evento", reward.type().displayName(), "premio", rewardLabel(reward.rewards())));
            giveRewards(player, reward.rewards());
        }
    }

    private String rewardLabel(ToffyEventSettings.Rewards rewards) {
        List<String> labels = new ArrayList<>();
        for (String item : rewards.items()) {
            labels.add(item.split(":(?=\\d+$)")[0]);
        }
        for (String command : rewards.commands()) {
            labels.add("comando");
        }
        return String.join(", ", labels);
    }

    private void saveOwed() {
        try {
            owed.save();
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] No se pudo guardar " + owed.file()
                + "; se reintenta en el próximo guardado.", failure);
        }
    }

    /** Audit entry of a prize the owed store pays. Best effort: the owed store is the source of truth. */
    private void audit(EventRewardJournal.Pending evidence) {
        try {
            rewardJournal.record(evidence, EventRewardJournal.AUTO_OWED, "queued-for-join");
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Eventos] No se pudo escribir la auditoría del premio (igual se entrega al volver a entrar): "
                + evidence, failure);
        }
    }

    private void giveWinnerRewards(Player winner, ToffyEventSettings.Rewards rewards) {
        playSound(winner, ToffyEventSettings.SOUND_WINNER);
        giveRewards(winner, rewards);
    }

    private void giveRewards(Player winner, ToffyEventSettings.Rewards rewards) {
        for (String entry : rewards.items()) {
            Optional<ItemStack> item = RewardItem.parse(entry).flatMap(rewardItems);
            if (item.isEmpty()) {
                plugin.getLogger().warning("[Eventos] Unknown reward item: " + entry);
                continue;
            }
            winner.getInventory().addItem(item.get()).values()
                .forEach(leftover -> winner.getWorld().dropItemNaturally(winner.getLocation(), leftover));
            sendCase(winner, null, ToffyEvents.CASE_PREMIO, values(null,
                "premio", item.get().getAmount() + "x " + entry.split(":(?=\\d+$)")[0]));
        }
        for (String command : rewards.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", winner.getName()));
        }
    }

    private void updateBar(Session current) {
        String base = barName(current.type);
        String title = switch (current.phase) {
            case PREPARING -> "§e" + base + " §7— preparando arena";
            case LOBBY -> "§e" + base + " §7— /anotar " + current.tournament + " §8(§f"
                + formatTime(current.secondsLeft) + "§8)";
            case RUNNING -> "§e" + base + " §8(§f" + formatTime(current.secondsLeft) + "§8)"
                + (current.type.arena() ? " §7— quedan §f" + current.participants.size() : "");
        };
        current.bar.setTitle(title);
        current.bar.setProgress(Math.max(0.0, Math.min(1.0,
            current.secondsLeft / (double) Math.max(1, current.totalSeconds))));
    }

    /** Name shown in the boss bar: {@code bossbar.<event>.title} or the event name. */
    private String barName(ToffyEventType type) {
        String title = settings.bossBar(type).title();
        return title == null || title.isBlank() ? type.displayName() : title;
    }

    private static List<Player> participants(Session current) {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : List.copyOf(current.participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private static String name(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offline = Bukkit.getOfflinePlayer(playerId).getName();
        return offline == null ? playerId.toString().substring(0, 8) : offline;
    }

    private static String names(Set<UUID> playerIds) {
        return playerIds.isEmpty() ? "-" : String.join("§7, §f", playerIds.stream().map(ToffyEventRuntime::name).toList());
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return safe >= 60 ? (safe / 60) + ":" + String.format(Locale.ROOT, "%02d", safe % 60) : safe + " s";
    }
}
