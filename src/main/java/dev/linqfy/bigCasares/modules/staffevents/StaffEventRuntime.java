package dev.linqfy.bigCasares.modules.staffevents;

import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Staff-triggered events. Only one runs at a time and nothing here starts on its own.
 *
 * <p>Lifesteal happens in the normal worlds with keep inventory. Arena events (KOTH,
 * TNT Tag, Spleef) save the player's inventory to disk, hand out an event kit and move
 * them to a code-built arena in the event dimension; whatever happens, the saved state
 * is given back when they leave, are eliminated, the event ends or they next log in.</p>
 */
final class StaffEventRuntime implements Listener {

    static final String PREFIX = "§6[Eventos] §r";
    static final String ADMIN_PERMISSION = "bigcasares.events.admin";

    private enum Phase { LOBBY, RUNNING }

    private final JavaPlugin plugin;
    private final StaffEventSettings settings;
    private final EventSnapshotStore store;
    private final Supplier<Optional<DimensionPlatform>> dimensions;
    private final Function<RewardItem, Optional<ItemStack>> rewardItems;
    private final Supplier<Optional<Boolean>> pvpBaseline;
    private final BukkitRuntimeRegistrations registrations;
    private final LifestealHearts hearts;
    private final Random random = new Random();
    private final Set<UUID> teleportBypass = new HashSet<>();
    private final Set<UUID> restoreAfterRespawn = new HashSet<>();
    /** Deaths handled by an event, until the death event finishes (the tombstone runs after us). */
    private final Set<UUID> handledDeaths = new HashSet<>();
    private Session session;
    private int fastTicks;

    private static final class Session {
        private final StaffEventType type;
        private final String id = UUID.randomUUID().toString();
        private final Set<UUID> participants = new LinkedHashSet<>();
        private final Map<World, Boolean> previousPvp = new LinkedHashMap<>();
        private final BossBar bar;
        private Phase phase;
        private int secondsLeft;
        private int totalSeconds;
        private World world;
        private ArenaLayout layout;
        private ArenaBuilder arena;
        private KothScores koth;
        private final Set<UUID> taggers = new HashSet<>();
        private final Map<UUID, Long> taggedAt = new HashMap<>();
        private int roundLeft;
        private int graceLeft;

        private Session(StaffEventType type, BossBar bar) {
            this.type = type;
            this.bar = bar;
        }
    }

    StaffEventRuntime(
        JavaPlugin plugin,
        StaffEventSettings settings,
        EventSnapshotStore store,
        Supplier<Optional<DimensionPlatform>> dimensions,
        Function<RewardItem, Optional<ItemStack>> rewardItems,
        Supplier<Optional<Boolean>> pvpBaseline,
        BukkitRuntimeRegistrations registrations
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.dimensions = dimensions;
        this.rewardItems = rewardItems;
        this.pvpBaseline = pvpBaseline;
        this.registrations = registrations;
        this.hearts = new LifestealHearts(plugin);
    }

    // --- queries -------------------------------------------------------------------------------

    boolean protectsDeath(UUID playerId) {
        return handledDeaths.contains(playerId);
    }

    List<String> status() {
        Session current = session;
        if (current == null) {
            return List.of(PREFIX + "§7No hay ningún evento en curso.");
        }
        List<String> lines = new ArrayList<>();
        lines.add(PREFIX + "§e" + current.type.displayName() + " §7— "
            + (current.phase == Phase.LOBBY ? "esperando jugadores" : "en juego")
            + " §7(" + formatTime(current.secondsLeft) + ")");
        if (current.type.arena()) {
            lines.add("§7Participantes (" + current.participants.size() + "): §f" + names(current.participants));
        }
        return lines;
    }

    // --- staff controls ------------------------------------------------------------------------

    void start(CommandSender sender, StaffEventType type, Integer lobbyOverride) {
        if (session != null) {
            sender.sendMessage(PREFIX + "§cYa hay un evento en curso (" + session.type.displayName()
                + "). Detenelo con /evento detener.");
            return;
        }
        if (type == StaffEventType.LIFESTEAL) {
            startLifesteal(sender);
        } else {
            startArena(sender, type, lobbyOverride == null ? settings.lobbySeconds() : lobbyOverride);
        }
    }

    void stop(CommandSender sender) {
        Session current = session;
        if (current == null) {
            sender.sendMessage(PREFIX + "§7No hay ningún evento en curso.");
            return;
        }
        broadcast(PREFIX + "§c" + current.type.displayName() + " fue cancelado por " + sender.getName() + ".");
        if (current.type == StaffEventType.LIFESTEAL) {
            endLifesteal(false);
        } else {
            finishArena(List.of(), null);
        }
    }

    /** Called when the module is disabled: everybody gets their state back, nobody gets rewards. */
    void shutdown() {
        Session current = session;
        if (current == null) {
            return;
        }
        if (current.type == StaffEventType.LIFESTEAL) {
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

    void join(Player player) {
        Session current = session;
        if (current == null || !current.type.arena()) {
            player.sendMessage(PREFIX + "§cNo hay ningún evento al que puedas unirte ahora.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (current.participants.contains(playerId)) {
            player.sendMessage(PREFIX + "§7Ya estás en el evento.");
            return;
        }
        if (current.phase != Phase.LOBBY) {
            player.sendMessage(PREFIX + "§cEl evento ya empezó.");
            return;
        }
        if (player.isDead()) {
            player.sendMessage(PREFIX + "§cReaparecé antes de unirte.");
            return;
        }
        if (store.contains(playerId) && !restore(player)) {
            player.sendMessage(PREFIX + "§cTenés un inventario de evento pendiente; avisá al staff.");
            return;
        }
        try {
            store.put(capture(player));
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not save the inventory of " + player.getName(), failure);
            player.sendMessage(PREFIX + "§cNo se pudo guardar tu inventario; no entraste al evento.");
            return;
        }
        current.participants.add(playerId);
        prepare(player, GameMode.ADVENTURE);
        teleport(player, current.arena.lobbySpot());
        current.bar.addPlayer(player);
        player.sendMessage(PREFIX + "§aTu inventario quedó guardado y lo vas a recuperar al salir.");
        participantsMessage(current, PREFIX + "§f" + player.getName() + " §7se unió ("
            + current.participants.size() + ").");
    }

    void leave(Player player) {
        Session current = session;
        if (current == null || !current.participants.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§7No estás en ningún evento.");
            return;
        }
        eliminate(current, player.getUniqueId(), "§7salió del evento", true);
    }

    // --- lifesteal -----------------------------------------------------------------------------

    private void startLifesteal(CommandSender sender) {
        BossBar bar = Bukkit.createBossBar("§cLifesteal", BarColor.RED, BarStyle.SEGMENTED_10);
        Session current = new Session(StaffEventType.LIFESTEAL, bar);
        current.phase = Phase.RUNNING;
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
            hearts.clearUnlessSession(player, current.id);
            bar.addPlayer(player);
        }
        LifestealRules rules = settings.lifesteal().rules();
        broadcast(PREFIX + "§c§lLIFESTEAL §7iniciado por §f" + sender.getName()
            + " §7por §f" + settings.lifesteal().durationMinutes() + " min§7.");
        broadcast("§7PvP global con §aKeepInventory§7. Cada kill roba §c" + rules.heartsPerKill()
            + " ❤§7. Nadie baja de §e" + rules.floorHearts() + " ❤§7 (matar a alguien en el piso no da nada).");
        broadcast("§7Terminá con §c" + rules.permanentRewardThreshold()
            + " ❤ o más §7y ganás §61 corazón permanente§7.");
        updateBar(current);
    }

    private void lifestealSecond(Session current) {
        current.secondsLeft--;
        if (current.secondsLeft % 10 == 0) {
            current.previousPvp.keySet().forEach(world -> world.setPVP(true));
        }
        if (current.secondsLeft == 300 || current.secondsLeft == 60) {
            broadcast(PREFIX + "§eLifesteal termina en §f" + formatTime(current.secondsLeft) + "§e.");
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
        LifestealRules rules = settings.lifesteal().rules();
        List<String> rewarded = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (rewards) {
                int total = hearts.hearts(player);
                if (rules.earnsPermanentHeart(total)) {
                    if (hearts.permanentHearts(player) < settings.lifesteal().maxPermanentBonusHearts()) {
                        hearts.addPermanentHeart(player);
                        rewarded.add(player.getName());
                        player.sendMessage(PREFIX + "§6¡Terminaste con " + total + " ❤ y ganaste 1 corazón permanente!");
                    } else {
                        player.sendMessage(PREFIX + "§7Terminaste con " + total
                            + " ❤, pero ya tenés el máximo de corazones permanentes.");
                    }
                }
            }
            hearts.clear(player);
        }
        if (rewards) {
            broadcast(PREFIX + "§c§lLIFESTEAL §7terminó. Los corazones del evento volvieron a la normalidad.");
            broadcast(rewarded.isEmpty()
                ? "§7Nadie llegó a " + rules.permanentRewardThreshold() + " ❤."
                : "§6Corazón permanente para: §f" + String.join(", ", rewarded));
        }
    }

    private void onLifestealDeath(Session current, PlayerDeathEvent event) {
        Player victim = event.getEntity();
        keepEverything(event);
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        LifestealRules rules = settings.lifesteal().rules();
        LifestealRules.KillOutcome outcome = rules.resolveKill(hearts.hearts(victim), hearts.hearts(killer));
        if (outcome.floorProtected()) {
            killer.sendMessage(PREFIX + "§7" + victim.getName() + " está en el piso de "
                + rules.floorHearts() + " ❤: no le robaste nada.");
            return;
        }
        hearts.addEventHearts(victim, -outcome.victimLoss(), current.id);
        hearts.addEventHearts(killer, outcome.killerGain(), current.id);
        victim.sendMessage(PREFIX + "§c" + killer.getName() + " te robó " + outcome.victimLoss() + " ❤.");
        killer.sendMessage(PREFIX + (outcome.killerGain() > 0
            ? "§aLe robaste " + outcome.killerGain() + " ❤ a " + victim.getName() + " §7(" + hearts.hearts(killer) + " ❤)."
            : "§7Ya estás en el máximo de " + rules.maxHearts() + " ❤."));
    }

    // --- arena events --------------------------------------------------------------------------

    private void startArena(CommandSender sender, StaffEventType type, int lobbySeconds) {
        Optional<World> world = arenaWorld();
        if (world.isEmpty()) {
            sender.sendMessage(PREFIX + "§c" + arenaUnavailableReason());
            return;
        }
        ArenaLayout layout = ArenaLayout.of(type, settings);
        ArenaBuilder arena = new ArenaBuilder(world.get(), layout);
        try {
            arena.build();
            world.get().setTime(6000L);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not build the " + type.id() + " arena", failure);
            sender.sendMessage(PREFIX + "§cNo se pudo construir la arena; revisá la consola.");
            return;
        }
        BossBar bar = Bukkit.createBossBar(type.displayName(), BarColor.YELLOW, BarStyle.SOLID);
        Session current = new Session(type, bar);
        current.phase = Phase.LOBBY;
        current.world = world.get();
        current.layout = layout;
        current.arena = arena;
        current.totalSeconds = Math.max(5, lobbySeconds);
        current.secondsLeft = current.totalSeconds;
        session = current;
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        broadcast(PREFIX + "§e§l" + type.displayName() + " §7lo abrió §f" + sender.getName() + "§7.");
        broadcast("§7" + describe(type));
        announceJoin(current);
        updateBar(current);
    }

    private void announceJoin(Session current) {
        Component message = LegacyComponentSerializer.legacySection()
            .deserialize(PREFIX + "§7Empieza en §f" + formatTime(current.secondsLeft) + "§7. ")
            .append(Component.text("[UNIRSE]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/evento unirse"))
                .hoverEvent(HoverEvent.showText(Component.text(
                    "Tu inventario se guarda y se devuelve al salir", NamedTextColor.GRAY))));
        plugin.getServer().broadcast(message);
    }

    private void lobbySecond(Session current) {
        current.secondsLeft--;
        if (current.secondsLeft == 20 || current.secondsLeft == 10) {
            announceJoin(current);
        } else if (current.secondsLeft > 0 && current.secondsLeft <= 5) {
            participantsMessage(current, PREFIX + "§eEmpieza en §f" + current.secondsLeft + "§e...");
            participants(current).forEach(player ->
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f));
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
            broadcast(PREFIX + "§c" + current.type.displayName() + " se canceló: hacían falta "
                + minimum + " jugadores y hubo " + players.size() + ".");
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
            prepare(player, current.type == StaffEventType.SPLEEF ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            giveKit(current, player);
            teleport(player, spawns.get(index));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        }
        switch (current.type) {
            case KOTH -> {
                current.totalSeconds = settings.koth().durationMinutes() * 60;
                current.koth = new KothScores(settings.koth().captureSeconds());
                participantsMessage(current, PREFIX + "§e¡A la colina! §7Quedate solo sobre el oro "
                    + settings.koth().captureSeconds() + " s para ganar.");
            }
            case TNT_TAG -> {
                current.totalSeconds = settings.tntTag().maxMinutes() * 60;
                newTntRound(current);
            }
            case SPLEEF -> {
                current.totalSeconds = settings.spleef().maxMinutes() * 60;
                current.graceLeft = settings.spleef().graceSeconds();
                participantsMessage(current, PREFIX + (current.graceLeft > 0
                    ? "§eLa nieve se puede romper en §f" + current.graceLeft + " s§e."
                    : "§e¡A romper!"));
            }
            case LIFESTEAL -> {
            }
        }
        current.secondsLeft = current.totalSeconds;
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
                    participantsMessage(current, PREFIX + "§e§l¡A romper!");
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
        if (current.type == StaffEventType.KOTH) {
            Optional<UUID> leader = current.koth.leader();
            if (leader.isPresent()) {
                finishArena(List.of(leader.get()), "§7Se acabó el tiempo. §f" + name(leader.get())
                    + " §7tenía más tiempo en la colina.");
                return;
            }
        }
        finishArena(List.of(), "§7Se acabó el tiempo sin ganador.");
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
            case EMPTY -> "§7Colina libre";
            case CONTESTED -> "§cColina disputada";
            case HELD -> "§6Rey: §f" + name(onHill.getFirst());
        };
        for (Player player : players) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                hill + " §8| §eTus puntos: §f" + current.koth.points(player.getUniqueId())
                    + "/" + current.koth.captureSeconds()));
        }
        current.koth.capturedBy().ifPresent(winner ->
            finishArena(List.of(winner), "§f" + name(winner) + " §6es el Rey de la Colina."));
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
        participantsMessage(current, PREFIX + "§cNueva ronda: §f" + names(current.taggers)
            + " §ctiene" + (current.taggers.size() == 1 ? "" : "n") + " la TNT. §7Explota en "
            + current.roundLeft + " s.");
    }

    private void tntSecond(Session current) {
        current.roundLeft--;
        for (Player player : participants(current)) {
            boolean tagged = current.taggers.contains(player.getUniqueId());
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(tagged
                ? "§c§l¡TENÉS LA TNT! §7Pegale a alguien §8| §f" + current.roundLeft + " s"
                : "§aCorré §8| §7explota en §f" + current.roundLeft + " s"));
            if (tagged && current.roundLeft <= 5) {
                player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);
            }
        }
        if (current.roundLeft > 0) {
            return;
        }
        for (UUID taggerId : List.copyOf(current.taggers)) {
            Player tagger = Bukkit.getPlayer(taggerId);
            if (tagger != null) {
                tagger.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, tagger.getLocation(), 1);
                tagger.getWorld().playSound(tagger.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }
            current.taggers.remove(taggerId);
            eliminate(current, taggerId, "§cexplotó", true);
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
        // A freshly tagged player cannot pass the TNT straight back.
        if (now - current.taggedAt.getOrDefault(attacker.getUniqueId(), 0L) < 1000L) {
            return;
        }
        current.taggers.remove(attacker.getUniqueId());
        untag(attacker);
        tag(current, victim);
        current.taggedAt.put(victim.getUniqueId(), now);
        victim.sendMessage(PREFIX + "§c¡" + attacker.getName() + " te pasó la TNT!");
        attacker.sendMessage(PREFIX + "§aLe pasaste la TNT a " + victim.getName() + ".");
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
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
    }

    private void untag(Player player) {
        player.getInventory().setHelmet(null);
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    /** Removes a participant, gives their state back and checks whether someone has won. */
    private void eliminate(Session current, UUID playerId, String reason, boolean restoreNow) {
        if (!current.participants.remove(playerId)) {
            return;
        }
        current.taggers.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            current.bar.removePlayer(player);
            if (restoreNow) {
                restore(player);
            }
        }
        participantsMessage(current, PREFIX + "§f" + name(playerId) + " " + reason + " §7(quedan "
            + current.participants.size() + ").");
        if (player != null && restoreNow) {
            player.sendMessage(PREFIX + "§7Recuperaste tu inventario.");
        }
        if (current.phase != Phase.RUNNING) {
            return;
        }
        if (current.participants.size() <= 1) {
            List<UUID> winners = List.copyOf(current.participants);
            finishArena(winners, winners.isEmpty() ? "§7No quedó nadie en pie."
                : "§f" + name(winners.getFirst()) + " §6ganó " + current.type.displayName() + ".");
        } else if (current.type == StaffEventType.TNT_TAG && current.taggers.isEmpty() && current.roundLeft > 0) {
            newTntRound(current);
        }
    }

    private void finishArena(List<UUID> winners, String headline) {
        Session current = session;
        session = null;
        if (current == null) {
            return;
        }
        current.bar.removeAll();
        restorePvp(current);
        for (UUID playerId : List.copyOf(current.participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restore(player);
            }
        }
        if (headline != null) {
            broadcast(PREFIX + "§e§l" + current.type.displayName() + " §7terminó. " + headline);
        }
        StaffEventSettings.Rewards rewards = rewardsOf(current.type);
        for (UUID winnerId : winners) {
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                giveRewards(winner, rewards);
            }
        }
    }

    // --- ticking -------------------------------------------------------------------------------

    /** Every 5 ticks; full seconds every fourth call. */
    void tick() {
        Session current = session;
        if (current == null) {
            return;
        }
        if (current.type.arena()) {
            checkPositions(current);
        }
        if (session != current || ++fastTicks % 4 != 0) {
            return;
        }
        if (current.type == StaffEventType.LIFESTEAL) {
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
            if (current.phase == Phase.RUNNING && current.type == StaffEventType.SPLEEF) {
                eliminate(current, player.getUniqueId(), "§cse cayó", true);
            } else if (current.phase == Phase.LOBBY) {
                teleport(player, current.arena.lobbySpot());
            } else {
                player.setFallDistance(0);
                teleport(player, current.arena.spawnPoints(1, random).getFirst());
                if (current.type == StaffEventType.KOTH) {
                    player.sendMessage(PREFIX + "§7Te tiraron de la colina.");
                }
            }
        }
    }

    // --- listeners -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Session current = session;
        hearts.clearUnlessSession(player,
            current != null && current.type == StaffEventType.LIFESTEAL ? current.id : null);
        if (current != null && (current.type == StaffEventType.LIFESTEAL || current.phase == Phase.LOBBY)) {
            current.bar.addPlayer(player);
        }
        if (store.contains(player.getUniqueId())) {
            registrations.scheduleImmediate("staff-events-join-restore", () -> {
                if (player.isOnline() && !isParticipant(player.getUniqueId())) {
                    if (restore(player)) {
                        player.sendMessage(PREFIX + "§7Recuperaste el inventario que tenías antes del evento.");
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Session current = session;
        UUID playerId = event.getPlayer().getUniqueId();
        if (current != null && current.participants.contains(playerId)) {
            eliminate(current, playerId, "§7se desconectó", true);
        }
    }

    // LOW: before the keep-inventory totem (NORMAL), which skips deaths that already keep the inventory.
    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Session current = session;
        if (current == null) {
            return;
        }
        Player victim = event.getEntity();
        if (current.participants.contains(victim.getUniqueId())) {
            handledDeaths.add(victim.getUniqueId());
            keepEverything(event);
            restoreAfterRespawn.add(victim.getUniqueId());
            eliminate(current, victim.getUniqueId(), "§cmurió", false);
        } else if (current.type == StaffEventType.LIFESTEAL && !victim.getWorld().equals(arenaWorld().orElse(null))) {
            handledDeaths.add(victim.getUniqueId());
            onLifestealDeath(current, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathResolved(PlayerDeathEvent event) {
        handledDeaths.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!restoreAfterRespawn.remove(player.getUniqueId())) {
            return;
        }
        store.find(player.getUniqueId()).ifPresent(snapshot -> event.setRespawnLocation(locationOf(snapshot)));
        registrations.scheduleImmediate("staff-events-respawn-restore", () -> {
            if (player.isOnline()) {
                restore(player);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Session current = session;
        Player player = event.getPlayer();
        if (current == null || !current.type.arena()) {
            if (isArenaWorld(event.getBlock().getWorld()) && !player.hasPermission(ADMIN_PERMISSION)) {
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
        boolean spleefBreak = current.type == StaffEventType.SPLEEF
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
        if (isParticipant(event.getPlayer().getUniqueId())
            || (isArenaWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission(ADMIN_PERMISSION))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (isParticipant(event.getPlayer().getUniqueId())) {
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
        if (!isParticipant(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (!label.equals("evento") && !label.equals("eventos")) {
            event.setCancelled(true);
            player.sendMessage(PREFIX + "§cDurante el evento solo podés usar /evento. Para irte: /evento salir");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Session current = session;
        Player player = event.getPlayer();
        if (current == null || !current.participants.contains(player.getUniqueId())
            || teleportBypass.contains(player.getUniqueId())) {
            return;
        }
        if (event.getTo() != null && !current.world.equals(event.getTo().getWorld())) {
            event.setCancelled(true);
            player.sendMessage(PREFIX + "§cPara salir del evento usá /evento salir.");
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
        return settings.fallbackArenaWorld().isEmpty()
            ? Optional.empty()
            : Optional.ofNullable(Bukkit.getWorld(settings.fallbackArenaWorld()));
    }

    private boolean isArenaWorld(World world) {
        return arenaWorld().map(world::equals).orElse(false);
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
        return "No hay mundo de arenas: configurá staff-events.fallback-arena-world.";
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
            case KOTH -> inventory.setItem(0, kitItem(Material.STICK, "§6§lPalo del Rey",
                Enchantment.KNOCKBACK, settings.koth().knockbackLevel()));
            case SPLEEF -> inventory.setItem(0, kitItem(Material.WOODEN_SHOVEL, "§b§lPala de Spleef",
                Enchantment.EFFICIENCY, settings.spleef().efficiencyLevel()));
            default -> {
            }
        }
    }

    private static ItemStack kitItem(Material material, String name, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§7Objeto de evento: desaparece al salir."));
        meta.addEnchant(enchantment, level, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Gives a player their saved state back. False when nothing was saved or the file could not be updated. */
    private boolean restore(Player player) {
        Optional<EventPlayerSnapshot> found = store.find(player.getUniqueId());
        if (found.isEmpty()) {
            return false;
        }
        if (player.isDead()) {
            restoreAfterRespawn.add(player.getUniqueId());
            return false;
        }
        EventPlayerSnapshot snapshot = found.get();
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
        try {
            store.remove(player.getUniqueId());
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "[Eventos] Could not clear the saved inventory of "
                + player.getName() + "; it will be restored again on the next login", failure);
        }
        return true;
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

    private StaffEventSettings.Rewards rewardsOf(StaffEventType type) {
        return switch (type) {
            case KOTH -> settings.koth().rewards();
            case TNT_TAG -> settings.tntTag().rewards();
            case SPLEEF -> settings.spleef().rewards();
            case LIFESTEAL -> StaffEventSettings.Rewards.none();
        };
    }

    private void giveRewards(Player winner, StaffEventSettings.Rewards rewards) {
        for (String entry : rewards.items()) {
            Optional<ItemStack> item = RewardItem.parse(entry).flatMap(rewardItems);
            if (item.isEmpty()) {
                plugin.getLogger().warning("[Eventos] Unknown reward item: " + entry);
                continue;
            }
            winner.getInventory().addItem(item.get()).values()
                .forEach(leftover -> winner.getWorld().dropItemNaturally(winner.getLocation(), leftover));
            winner.sendMessage(PREFIX + "§6Premio: §f" + item.get().getAmount() + "x " + entry.split(":(?=\\d+$)")[0]);
        }
        for (String command : rewards.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", winner.getName()));
        }
    }

    private void updateBar(Session current) {
        String title = switch (current.phase) {
            case LOBBY -> "§e" + current.type.displayName() + " §7— /evento unirse §8(§f"
                + formatTime(current.secondsLeft) + "§8)";
            case RUNNING -> "§e" + current.type.displayName() + " §8(§f" + formatTime(current.secondsLeft) + "§8)"
                + (current.type.arena() ? " §7— quedan §f" + current.participants.size() : "");
        };
        current.bar.setTitle(title);
        current.bar.setProgress(Math.max(0.0, Math.min(1.0,
            current.secondsLeft / (double) Math.max(1, current.totalSeconds))));
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

    private static void participantsMessage(Session current, String message) {
        participants(current).forEach(player -> player.sendMessage(message));
    }

    private static void broadcast(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
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
        return playerIds.isEmpty() ? "-" : String.join("§7, §f", playerIds.stream().map(StaffEventRuntime::name).toList());
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

    private String describe(StaffEventType type) {
        return switch (type) {
            case KOTH -> "Zona de captura con palos irrompibles Knockback " + settings.koth().knockbackLevel()
                + ". Quedate solo en el oro para sumar.";
            case TNT_TAG -> "Persecución explosiva: pasá la TNT antes de que explote."
                + (settings.tntTag().rewards().items().stream().anyMatch(item -> item.startsWith("nuke_shot"))
                    ? " El último en pie gana el bombardeo orbital (Nuke Shot)." : "");
            case SPLEEF -> "Pista de nieve con pala de madera Efficiency " + settings.spleef().efficiencyLevel()
                + ". El que cae, queda afuera.";
            case LIFESTEAL -> "";
        };
    }
}
