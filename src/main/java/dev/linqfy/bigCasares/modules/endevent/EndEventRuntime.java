package dev.linqfy.bigCasares.modules.endevent;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EndEventRuntime implements Listener {
    private static final String FINAL_REASON = "Gracias por jugar <3";
    private static final FireworkEffect.Type[] FIREWORK_TYPES = {
        FireworkEffect.Type.BALL,
        FireworkEffect.Type.BALL_LARGE,
        FireworkEffect.Type.STAR,
        FireworkEffect.Type.BURST,
        FireworkEffect.Type.CREEPER
    };
    private static final Color[][] FIREWORK_PALETTES = {
        {Color.YELLOW, Color.ORANGE, Color.WHITE},
        {Color.PURPLE, Color.FUCHSIA, Color.WHITE},
        {Color.AQUA, Color.BLUE, Color.WHITE},
        {Color.RED, Color.ORANGE, Color.YELLOW},
        {Color.GREEN, Color.LIME, Color.AQUA},
        {Color.FUCHSIA, Color.PURPLE, Color.WHITE},
    };

    private final BigCasares plugin;
    private final EndEventSettings settings;
    private final YamlEndEventStorage storage;
    private final EndEventService service;
    private final BukkitRuntimeRegistrations registrations;
    private final Clock clock;
    private final NamespacedKey eggKey;
    private final NamespacedKey dropKey;
    private final NamespacedKey dropOwnerKey;
    private final NamespacedKey fireworkKey;
    private final EndEventPortalResolver portalResolver = new EndEventPortalResolver();
    private final EndEventSafeLocations safeLocations = new EndEventSafeLocations();
    private final EndEventInventoryPolicy inventoryPolicy = new EndEventInventoryPolicy();
    private final Set<UUID> optedInOperators = new LinkedHashSet<>();
    private final Map<UUID, String> originalGameModes = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> escrow = new LinkedHashMap<>();
    private final Map<UUID, BossBar> personalBars = new LinkedHashMap<>();
    private final Map<UUID, Boolean> originalGlow = new HashMap<>();
    private final Set<UUID> fakeGlowTargets = new HashSet<>();
    private final Map<UUID, FrozenPlayerState> frozenPlayers = new LinkedHashMap<>();
    private final NmsEntityGlowSender entityGlowSender;

    private EndEventSchedule schedule;
    private LocalDate eventDate;
    private EndEventLocation portal;
    private EndEventLocation eggBlock;
    private EndEventBorderSnapshot originalBorder;
    private UUID winner;
    private Instant banAt;
    private Instant debugCountdownEndsAt;
    private Instant debugCountdownStartedAt;
    private boolean delayedReveal;
    private boolean portalFailureReported;
    private int missingEggHeartbeats;
    private int victoryFireworkTick = 0;
    private BossBar globalBar;
    private UUID visualHunter;
    private Instant hunterDropFreezeReadyAt;
    private Team frozenGlowTeam;
    private int fastTickSequence;

    public EndEventRuntime(
        BigCasares plugin,
        EndEventSettings settings,
        YamlEndEventStorage storage,
        EndEventService service,
        BukkitRuntimeRegistrations registrations,
        Clock clock
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.storage = storage;
        this.service = service;
        this.registrations = registrations;
        this.clock = clock;
        this.schedule = settings.schedule();
        this.eventDate = settings.eventDate();
        this.eggKey = new NamespacedKey(plugin, "end_event_egg");
        this.dropKey = new NamespacedKey(plugin, "end_event_drop");
        this.dropOwnerKey = new NamespacedKey(plugin, "end_event_drop_owner");
        this.fireworkKey = new NamespacedKey(plugin, "end_event_firework");
        this.entityGlowSender = new NmsEntityGlowSender(plugin);
        restoreRuntime(storage.loadRuntime());
    }

    public void heartbeat() {
        Instant now = clock.instant();
        handleSchedule(now);
        if (service.phase() == EndEventPhase.EGG_AVAILABLE || service.phase() == EndEventPhase.HUNT) {
            reconcileEgg();
        }
        if (service.phase() == EndEventPhase.HUNT) {
            applyMilestones(service.claimDueMilestones());
            enforcePotionLock();
            updateCombatBars(now);
            updateHunterPresentation(now);
            reconcileTrackedDrops();
            checkWinner();
        } else if (service.phase() == EndEventPhase.VICTORY) {
            reconcileTrackedDrops();
            tickVictoryFireworks();
            if (banAt != null && !now.isBefore(banAt)) {
                finishAndBan();
            }
        } else if (service.phase() == EndEventPhase.FINISHED) {
            applyFinalLocks();
            reconcileTrackedDrops();
        }
    }

    public void fastTick() {
        if (service.phase() != EndEventPhase.HUNT) {
            clearFakeGlow(null);
            clearFrozenPlayers();
            return;
        }
        refreshHunterWallhack();
        tickFrozenPlayers(clock.instant());
    }

    public void restoreLiveState() {
        switch (service.phase()) {
            case COUNTDOWN, DELAYED_COUNTDOWN -> {
                setEndOverride(false);
                ensureGlobalBar();
            }
            case DRAGON_FIGHT -> {
                setEndOverride(true);
                ensureGlobalBar();
            }
            case EGG_AVAILABLE -> {
                setEndOverride(true);
                ensureGlobalBar();
            }
            case HUNT -> {
                setEndOverride(!endLocked());
                resumeBorder();
            }
            case VICTORY, FINISHED -> applyFinalLocks();
            default -> setEndOverride(null);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
    }

    public void shutdown() {
        clearFrozenPlayers();
        clearPresentation();
        persistRuntime();
    }

    public EndEventPhase phase() {
        return service.phase();
    }

    public boolean bypassTombstone(UUID playerId) {
        return service.phase() == EndEventPhase.HUNT && service.isParticipant(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return service.isParticipant(playerId);
    }

    public String status() {
        return "§6[EndEvent] §fFase: §e" + service.phase()
            + " §f| supervivientes: §e" + service.survivors().size()
            + " §f| cazador: §e" + service.eggCarrier()
                .map(id -> Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()))
                .orElse("ninguno");
    }

    public List<String> validate() {
        List<String> messages = new ArrayList<>();
        World overworld = overworld();
        if (overworld == null) {
            messages.add("§cNo está cargado el mundo " + settings.overworldName() + ".");
        } else if (portal != null) {
            messages.add("§aPortal resuelto: " + format(portal));
        } else {
            Optional<Location> resolved = portalResolver.resolve(overworld, settings.strongholdSearchRadiusChunks());
            if (resolved.isPresent()) {
                portal = from(resolved.orElseThrow());
                portalFailureReported = false;
                persistRuntime();
                messages.add("§aPortal encontrado: " + format(portal));
            } else {
                messages.add("§cNo se pudo encontrar el portal. Usá /endevent portal set.");
            }
        }
        World end = endWorld();
        if (end == null || end.getEnderDragonBattle() == null
            || end.getEnderDragonBattle().getEnderDragon() == null
            || end.getEnderDragonBattle().getEnderDragon().isDead()) {
            messages.add("§cNo hay una batalla de dragón viva y preparada.");
        } else {
            messages.add("§aLa batalla de dragón está preparada.");
        }
        return List.copyOf(messages);
    }

    public boolean optIn(Player player) {
        if (!player.isOp()) {
            return false;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            player.sendMessage("§cPonete en Survival o Adventure antes de inscribirte.");
            return false;
        }
        optedInOperators.add(player.getUniqueId());
        persistRuntime();
        return true;
    }

    public void setPortal(Location location) {
        portal = from(location);
        portalFailureReported = false;
        persistRuntime();
    }

    public void clearPortal() {
        portal = null;
        portalFailureReported = false;
        persistRuntime();
    }

    public void arm(LocalDate date) {
        reset();
        plugin.getConfig().set("end-event-system.event-date", date.toString());
        plugin.saveConfig();
        schedule = new EndEventSchedule(date, settings.zone());
        eventDate = date;
        service.reset();
    }

    public void abort() {
        clearPresentation();
        restoreBorder();
        setEndOverride(null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreEscrow(player);
            String original = originalGameModes.get(player.getUniqueId());
            if (original != null) {
                player.setGameMode(parseGameMode(original));
            }
        }
        dropOfflineEscrow();
        service.setPhase(EndEventPhase.ABORTED);
        persistRuntime();
    }

    public void reset() {
        if (service.phase() != EndEventPhase.ARMED && service.phase() != EndEventPhase.ABORTED) {
            abort();
        }
        removeEventEggs();
        clearPresentation();
        setEndOverride(null);
        delayedReveal = false;
        portal = null;
        eggBlock = null;
        optedInOperators.clear();
        originalGameModes.clear();
        escrow.clear();
        originalBorder = null;
        winner = null;
        banAt = null;
        debugCountdownEndsAt = null;
        debugCountdownStartedAt = null;
        service.reset();
        persistRuntime();
    }

    public void debugCountdown(int seconds) {
        reset();
        debugCountdownStartedAt = clock.instant();
        debugCountdownEndsAt = clock.instant().plusSeconds(Math.max(1, seconds));
        service.setPhase(EndEventPhase.COUNTDOWN);
        setEndOverride(false);
        expel(World.Environment.THE_END);
        ensureGlobalBar();
    }

    public boolean debugReveal() {
        if (portal == null && overworld() != null && !portalFailureReported) {
            portal = portalResolver.resolve(overworld(), settings.strongholdSearchRadiusChunks())
                .map(this::from).orElse(null);
        }
        if (portal == null) {
            return false;
        }
        service.setPhase(EndEventPhase.DRAGON_FIGHT);
        setEndOverride(true);
        updateGlobalBar("§5Portal del End: " + format(portal), 1.0);
        persistRuntime();
        return true;
    }

    public void debugDragonDead() {
        service.setPhase(EndEventPhase.EGG_AVAILABLE);
        missingEggHeartbeats = 3;
        reconcileEgg();
    }

    public void debugHunter(Player player) {
        if (!hasEventEgg(player)) {
            ItemStack egg = new ItemStack(Material.DRAGON_EGG);
            tagEgg(egg);
            player.getInventory().addItem(egg);
        }
        Set<UUID> participants = eligiblePlayers();
        participants.add(player.getUniqueId());
        beginHunt(participants, player);
    }

    public void debugElapsed(int minutes) {
        service.debugSetElapsed(Duration.ofMinutes(Math.max(0, minutes)));
        applyMilestones(service.claimDueMilestones());
    }

    public void previewWinner(Player player) {
        celebrate(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (service.phase() != EndEventPhase.HUNT || !service.isParticipant(event.getPlayer().getUniqueId())) {
            return;
        }
        UUID victim = event.getPlayer().getUniqueId();
        Optional<UUID> killer = Optional.ofNullable(event.getPlayer().getKiller()).map(Player::getUniqueId);
        EndEventDeathResult result = service.recordDeath(victim, killer);
        event.setKeepInventory(false);
        List<ItemStack> guaranteedDrops = snapshotInventory(event.getPlayer());
        event.getDrops().clear();
        event.getDrops().addAll(guaranteedDrops);
        clearInventory(event.getPlayer());
        for (ItemStack drop : event.getDrops()) {
            tagDrop(drop, victim);
        }
        if (result.customHunterMessage()) {
            event.deathMessage(Component.text("La existencia de " + event.getPlayer().getName() + " fue erradicada."));
        }
        registrations.scheduleDelayed("end-event-death-resolution", () -> {
            reconcileEgg();
            checkWinner();
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (service.phase() == EndEventPhase.DRAGON_FIGHT && event.getEntity() instanceof EnderDragon) {
            service.setPhase(EndEventPhase.EGG_AVAILABLE);
            missingEggHeartbeats = 0;
            Bukkit.broadcastMessage("§5El dragón cayó. §d¡Encuentren el huevo!");
            registrations.scheduleDelayed("end-event-egg-discovery", () -> {
                discoverEggBlock();
                reconcileEgg();
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
            && firework.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null && isFrozen(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (service.phase() != EndEventPhase.HUNT || !(event.getEntity() instanceof Player target)) {
            return;
        }
        if (attacker != null && service.isSurvivor(attacker.getUniqueId())
            && service.isSurvivor(target.getUniqueId())) {
            service.recordCombat(attacker.getUniqueId(), target.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (Bukkit.isStopping() || plugin.isReloadingPluginState()
            || service.phase() != EndEventPhase.HUNT || !service.isSurvivor(player.getUniqueId())) {
            return;
        }
        List<ItemStack> inventory = snapshotInventory(player);
        EndEventDisconnectResult result = service.disconnect(player.getUniqueId());
        clearInventory(player);
        if (result == EndEventDisconnectResult.GRACE) {
            List<ItemStack> held = new ArrayList<>();
            for (ItemStack item : inventory) {
                if (isEventEgg(item)) {
                    dropTagged(player.getLocation(), item, player.getUniqueId());
                } else {
                    held.add(item);
                }
            }
            escrow.put(player.getUniqueId(), List.copyOf(held));
        } else if (result == EndEventDisconnectResult.ELIMINATED_COMBAT_LOG
            || result == EndEventDisconnectResult.ELIMINATED_DISCONNECT_LIMIT) {
            inventory.forEach(item -> dropTagged(player.getLocation(), item, player.getUniqueId()));
            Bukkit.broadcastMessage("§c" + player.getName() + " fue eliminado por desconexión.");
        }
        persistRuntime();
        registrations.scheduleDelayed("end-event-quit-resolution", this::checkWinner, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (service.phase() != EndEventPhase.HUNT || !service.isParticipant(playerId)) {
            return;
        }
        Location requested = event.getRespawnLocation();
        if (dimensionLocked(requested.getWorld().getEnvironment())
            || requested.getWorld().getEnvironment() == World.Environment.NORMAL
            && !requested.getWorld().equals(overworld())
            || requested.getWorld().getEnvironment() == World.Environment.NORMAL
            && !safeLocations.isInsideInset(requested.getWorld(), requested, 3.0)) {
            Location safe = safeOverworld(requested);
            if (safe != null) {
                event.setRespawnLocation(safe);
            }
        }
        if (!service.isSurvivor(playerId)) {
            registrations.scheduleDelayed("end-event-spectator-respawn",
                () -> event.getPlayer().setGameMode(GameMode.SPECTATOR), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !dimensionLocked(event.getTo().getWorld().getEnvironment())
            || observerBypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cEsa dimensión está bloqueada por el evento.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        onTeleport(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (netherLocked() && event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (potionsLocked() && service.isSurvivor(player.getUniqueId())
            && EndEventPotionPolicy.isForbidden(item.getType())) {
            event.setCancelled(true);
            return;
        }
        if (item.getType() != Material.DRAGON_EGG || !eventActive()) {
            return;
        }
        if (!isEventEgg(item) && service.phase() == EndEventPhase.EGG_AVAILABLE) {
            tagEgg(item);
            tagDrop(item, null);
        }
        if (!isEventEgg(item)) {
            event.setCancelled(true);
            player.sendMessage("§cEse no es el huevo del evento.");
            return;
        }
        if (service.phase() == EndEventPhase.HUNT && !service.isSurvivor(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        registrations.scheduleDelayed("end-event-egg-pickup", this::reconcileEgg, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        boolean hunterDroppedEventEgg = service.phase() == EndEventPhase.HUNT
            && isEventEgg(event.getItemDrop().getItemStack())
            && service.eggCarrier().equals(Optional.of(event.getPlayer().getUniqueId()));
        if (hunterDroppedEventEgg) {
            activateHunterDropFreeze(event.getPlayer());
        }
        if (service.phase() == EndEventPhase.HUNT && service.isParticipant(event.getPlayer().getUniqueId())) {
            tagDrop(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId());
        }
        if (isEventEgg(event.getItemDrop().getItemStack())) {
            service.setEggCarrier(Optional.empty());
            registrations.scheduleDelayed("end-event-egg-drop", this::reconcileEgg, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() != Material.DRAGON_EGG || !eventActive()) {
            return;
        }
        if (isEventEgg(event.getEntity().getItemStack())) {
            return;
        }
        if (service.phase() == EndEventPhase.EGG_AVAILABLE) {
            tagEgg(event.getEntity().getItemStack());
            tagDrop(event.getEntity().getItemStack(), null);
            eggBlock = null;
            persistRuntime();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && isEventEgg(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (isEventEgg(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            event.getEntity().setUnlimitedLifetime(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEggBlockDrop(BlockDropItemEvent event) {
        boolean tracked = trackedEggBlock(event.getBlockState().getLocation());
        if (!tracked && service.phase() != EndEventPhase.EGG_AVAILABLE) {
            return;
        }
        for (Item item : event.getItems()) {
            if (item.getItemStack().getType() == Material.DRAGON_EGG) {
                if (tracked || !isEventEggOnGround()) {
                    tagEgg(item.getItemStack());
                    tagDrop(item.getItemStack(), null);
                }
            }
        }
        if (tracked) {
            eggBlock = null;
            persistRuntime();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (trackedEggBlock(event.getBlock().getLocation())) {
            registrations.scheduleDelayed("end-event-egg-break", this::reconcileEgg, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlockPlaced().getType() != Material.DRAGON_EGG || !eventActive()) {
            return;
        }
        if (!isEventEgg(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNo podés introducir otro huevo durante el evento.");
            return;
        }
        eggBlock = from(event.getBlockPlaced().getLocation());
        service.setEggCarrier(Optional.empty());
        persistRuntime();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEggTeleport(BlockFromToEvent event) {
        if (trackedEggBlock(event.getBlock().getLocation())) {
            eggBlock = from(event.getToBlock().getLocation());
            persistRuntime();
            registrations.scheduleDelayed("end-event-egg-teleport", this::reconcileEgg, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> trackedEggBlock(block.getLocation()))) {
            registrations.scheduleDelayed("end-event-egg-piston-extend", this::reconcileEgg, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> trackedEggBlock(block.getLocation()))) {
            registrations.scheduleDelayed("end-event-egg-piston-retract", this::reconcileEgg, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> trackedEggBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> trackedEggBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        boolean topTarget = event.getRawSlot() >= 0
            && event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean externalTop = event.getView().getTopInventory().getType()
            != org.bukkit.event.inventory.InventoryType.PLAYER;
        boolean depositingCursor = topTarget && externalTop && isEventEgg(event.getCursor());
        boolean shiftDepositing = externalTop && event.isShiftClick()
            && isEventEgg(event.getCurrentItem())
            && event.getRawSlot() >= event.getView().getTopInventory().getSize();
        if (depositingCursor || shiftDepositing) {
            event.setCancelled(true);
            player.sendMessage("§cEl huevo solo puede estar en tu inventario o en el suelo.");
        }
        if (potionsLocked() && service.isSurvivor(player.getUniqueId())) {
            registrations.scheduleDelayed("end-event-potion-click",
                () -> inventoryPolicy.removeForbidden(player), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        if (isEventEgg(event.getOldCursor()) && event.getRawSlots().stream()
            .anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
        if (event.getWhoClicked() instanceof Player player && potionsLocked()
            && service.isSurvivor(player.getUniqueId())) {
            registrations.scheduleDelayed("end-event-potion-drag",
                () -> inventoryPolicy.removeForbidden(player), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isEventEgg(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (potionsLocked() && service.isSurvivor(event.getPlayer().getUniqueId())
            && EndEventPotionPolicy.isForbidden(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (potionsLocked() && service.isSurvivor(event.getPlayer().getUniqueId())
            && event.getItem() != null && EndEventPotionPolicy.isForbidden(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§bEstás congelado y no podés actuar.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        FrozenPlayerState state = frozenPlayers.get(event.getPlayer().getUniqueId());
        if (state == null || event.getTo() == null) {
            return;
        }
        Location locked = state.location();
        Location destination = event.getTo();
        if (!destination.getWorld().equals(locked.getWorld())
            || destination.getX() != locked.getX()
            || destination.getY() != locked.getY()
            || destination.getZ() != locked.getZ()
            || destination.getYaw() != locked.getYaw()
            || destination.getPitch() != locked.getPitch()) {
            event.setTo(locked.clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (service.phase() != EndEventPhase.HUNT) {
            return;
        }
        Player player = event.getPlayer();
        if (!service.eggCarrier().equals(Optional.of(player.getUniqueId()))) {
            return;
        }
        if (!event.isSneaking()) {
            clearFakeGlow(player);
            return;
        }
        List<Player> targets = service.survivors().stream()
            .filter(id -> !id.equals(player.getUniqueId()))
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .toList();
        applyFakeGlow(player, targets);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && potionsLocked()
            && service.isSurvivor(player.getUniqueId())
            && event.getRecipe().getResult() != null
            && EndEventPotionPolicy.isForbidden(event.getRecipe().getResult().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        if (!potionsLocked() || !service.isSurvivor(player.getUniqueId())) {
            return;
        }
        if (event.getEntity() instanceof ThrownPotion
            || event.getEntity() instanceof Arrow arrow && arrow.getBasePotionType() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!eventActive() && service.phase() != EndEventPhase.FINISHED) {
            return;
        }
        registrations.scheduleDelayed("end-event-loaded-drops", this::reconcileTrackedDrops, 1L);
    }

    private void handleSchedule(Instant now) {
        if (service.phase() == EndEventPhase.ARMED) {
            Instant dayEnds = eventDate.plusDays(1).atStartOfDay(settings.zone()).toInstant();
            if (!now.isBefore(schedule.countdownStartsAt()) && now.isBefore(dayEnds)) {
                startCountdown();
            }
        }
        if (service.phase() == EndEventPhase.COUNTDOWN) {
            Instant target = debugCountdownEndsAt == null ? schedule.revealAt() : debugCountdownEndsAt;
            updateGlobalBar("§5Evento del End §d" + formatRemaining(now, target),
                progress(now, target, debugCountdownEndsAt == null ? schedule.countdownStartsAt()
                    : debugCountdownStartedAt));
            if (!now.isBefore(target)) {
                if (debugCountdownEndsAt != null) {
                    debugCountdownEndsAt = null;
                    debugCountdownStartedAt = null;
                    debugReveal();
                } else if (eligiblePlayers().size() < settings.minimumPlayers()) {
                    delayedReveal = true;
                    service.setPhase(EndEventPhase.DELAYED_COUNTDOWN);
                    Bukkit.broadcastMessage("§eHay menos de " + settings.minimumPlayers()
                        + " jugadores. El evento se retrasa una sola vez hasta las 22:15.");
                    persistRuntime();
                } else {
                    attemptReveal();
                }
            }
        } else if (service.phase() == EndEventPhase.DELAYED_COUNTDOWN) {
            updateGlobalBar("§5Evento del End §d" + schedule.formatRemaining(now, true),
                progress(now, schedule.delayedRevealAt(), schedule.revealAt()));
            if (!now.isBefore(schedule.delayedRevealAt())) {
                attemptReveal();
            }
        } else if (service.phase() == EndEventPhase.DRAGON_FIGHT && portal != null) {
            updateGlobalBar("§5Portal del End: §d" + format(portal), 1.0);
        } else if (service.phase() == EndEventPhase.EGG_AVAILABLE) {
            Location egg = eventEggLocation();
            String title = egg == null ? "§5Buscando el huevo del dragón..."
                : "§5Huevo del evento: §d" + format(from(egg));
            updateGlobalBar(title, 1.0);
        }
    }

    private void startCountdown() {
        service.setPhase(EndEventPhase.COUNTDOWN);
        setEndOverride(false);
        expel(World.Environment.THE_END);
        ensureGlobalBar();
        if (portal == null && overworld() != null && !portalFailureReported) {
            portal = portalResolver.resolve(overworld(), settings.strongholdSearchRadiusChunks())
                .map(this::from).orElse(null);
            persistRuntime();
        }
        Bukkit.broadcastMessage("§5El evento del End comienza en 30 minutos.");
    }

    private void attemptReveal() {
        if (portal == null && overworld() != null && !portalFailureReported) {
            portal = portalResolver.resolve(overworld(), settings.strongholdSearchRadiusChunks())
                .map(this::from).orElse(null);
        }
        World end = endWorld();
        boolean battleReady = end != null && end.getEnderDragonBattle() != null
            && end.getEnderDragonBattle().getEnderDragon() != null
            && !end.getEnderDragonBattle().getEnderDragon().isDead();
        if (portal == null || !battleReady) {
            updateGlobalBar("§cEvento pausado: portal o dragón no disponible", 1.0);
            if (!portalFailureReported) {
                portalFailureReported = true;
                notifyOperators("§c[EndEvent] No se pudo abrir el End: revisá /endevent validate.");
            }
            return;
        }
        portalFailureReported = false;
        service.setPhase(EndEventPhase.DRAGON_FIGHT);
        setEndOverride(true);
        updateGlobalBar("§5Portal del End: §d" + format(portal), 1.0);
        Bukkit.broadcastMessage("§5Portal del End revelado: §d" + format(portal));
        persistRuntime();
    }

    private void discoverEggBlock() {
        if (service.phase() != EndEventPhase.EGG_AVAILABLE || eggBlock != null) {
            return;
        }
        Location vanilla = findVanillaEggBlock();
        if (vanilla != null) {
            eggBlock = from(vanilla);
            persistRuntime();
        }
    }

    private boolean isEventEggOnGround() {
        return findGroundEgg().isPresent();
    }

    private void reconcileEgg() {
        Optional<Player> carrier = findEggCarrier();
        if (carrier.isPresent()) {
            missingEggHeartbeats = 0;
            Player player = carrier.orElseThrow();
            boolean changed = eggBlock != null;
            eggBlock = null;
            if (service.phase() == EndEventPhase.EGG_AVAILABLE) {
                if (!eligible(player)) {
                    removeEventEgg(player);
                    dropEgg(player.getLocation(), player.getUniqueId());
                    return;
                }
                beginHunt(eligiblePlayers(), player);
                changed = true;
            } else if (service.phase() == EndEventPhase.HUNT
                && service.isSurvivor(player.getUniqueId())
                && !service.eggCarrier().equals(Optional.of(player.getUniqueId()))) {
                service.setEggCarrier(Optional.of(player.getUniqueId()));
                changed = true;
            }
            // The heartbeat runs every second; only write the state file when it changed.
            if (changed) {
                persistRuntime();
            }
            return;
        }
        if (service.phase() == EndEventPhase.HUNT && service.eggCarrier().isPresent()) {
            service.setEggCarrier(Optional.empty());
        }
        Optional<Item> ground = findGroundEgg();
        if (ground.isPresent()) {
            missingEggHeartbeats = 0;
            if (eggBlock != null) {
                eggBlock = null;
                persistRuntime();
            }
            return;
        }
        if (eggBlock != null) {
            Location location = toLocation(eggBlock);
            if (location != null && location.getBlock().getType() == Material.DRAGON_EGG) {
                missingEggHeartbeats = 0;
                return;
            }
            eggBlock = null;
        }
        if (service.phase() == EndEventPhase.EGG_AVAILABLE) {
            Optional<Item> untagged = findUntaggedDragonEgg();
            if (untagged.isPresent()) {
                Item egg = untagged.orElseThrow();
                tagEgg(egg.getItemStack());
                tagDrop(egg.getItemStack(), null);
                missingEggHeartbeats = 0;
                persistRuntime();
                return;
            }
        }
        Location vanilla = findVanillaEggBlock();
        if (vanilla != null) {
            eggBlock = from(vanilla);
            missingEggHeartbeats = 0;
            persistRuntime();
            return;
        }
        missingEggHeartbeats++;
        if (missingEggHeartbeats >= 3) {
            createEggAtExitPortal();
            missingEggHeartbeats = 0;
        }
    }

    private void beginHunt(Set<UUID> participants, Player hunter) {
        participants.add(hunter.getUniqueId());
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                originalGameModes.putIfAbsent(id, player.getGameMode().name());
            }
        }
        service.beginHunt(participants, hunter.getUniqueId());
        removeGlobalBar();
        captureAndStartBorder();
        setEndOverride(true);
        Bukkit.broadcastMessage("§c" + hunter.getName() + " tiene el huevo y es el cazador.");
        persistRuntime();
    }

    private void applyMilestones(Set<EndEventMilestone> milestones) {
        for (EndEventMilestone milestone : milestones) {
            switch (milestone) {
                case POTIONS_LOCKED -> {
                    enforcePotionLock();
                    Bukkit.broadcastMessage("§cTodas las pociones y flechas con efecto fueron eliminadas.");
                }
                case NETHER_WARNING ->
                    Bukkit.broadcastMessage("§6El Nether será bloqueado en un minuto.");
                case NETHER_LOCKED -> {
                    Bukkit.broadcastMessage("§cEl Nether fue bloqueado.");
                    expel(World.Environment.NETHER);
                }
                case END_WARNING ->
                    Bukkit.broadcastMessage("§6El End será bloqueado en un minuto.");
                case END_LOCKED -> {
                    Bukkit.broadcastMessage("§cEl End fue bloqueado.");
                    setEndOverride(false);
                    expel(World.Environment.THE_END);
                }
                case BORDER_FINISHED -> Bukkit.broadcastMessage("§4El borde llegó a 20 bloques.");
            }
        }
    }

    private void enforcePotionLock() {
        if (!potionsLocked()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isSurvivor(player.getUniqueId())) {
                inventoryPolicy.removeForbidden(player);
            }
        }
    }

    private void updateCombatBars(Instant now) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            service.combatUntil(player.getUniqueId()).ifPresent(until -> {
                long seconds = Math.max(0L, Duration.between(now, until).toSeconds() + 1L);
                player.sendActionBar("§cEn combate: §f" + seconds + "s");
            });
        }
    }

    private void updateHunterPresentation(Instant now) {
        Optional<UUID> carrier = service.eggCarrier();
        updateVisualHunter(carrier.orElse(null));
        double progress = service.huntStartedAt().map(start ->
            Math.max(0.0, 1.0 - Duration.between(start, now).toMillis() / 1_800_000.0)
        ).orElse(0.0);
        Set<UUID> survivors = service.survivors();
        for (UUID id : new ArrayList<>(personalBars.keySet())) {
            if (!survivors.contains(id) || Bukkit.getPlayer(id) == null) {
                removePersonalBar(id);
            }
        }
        for (UUID id : survivors) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                continue;
            }
            BossBar bar = personalBars.computeIfAbsent(id, ignored -> {
                BossBar created = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
                created.addPlayer(viewer);
                return created;
            });
            bar.setProgress(progress);
            if (carrier.isEmpty()) {
                Location egg = eventEggLocation();
                bar.setTitle(egg == null ? "§dHuevo sin portador" : "§dHuevo: " + format(from(egg)));
            } else if (carrier.get().equals(id)) {
                updateHunterBar(viewer, bar, survivors, now);
            } else {
                Player hunter = Bukkit.getPlayer(carrier.get());
                bar.setTitle(hunter == null ? "§cCazador desconectado"
                    : "§cCazador " + hunter.getName() + ": " + format(from(hunter.getLocation())));
            }
        }
    }

    private void updateHunterBar(Player hunter, BossBar bar, Set<UUID> survivors, Instant now) {
        List<Player> targets = survivors.stream()
            .filter(id -> !id.equals(hunter.getUniqueId()))
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (hunter.isSneaking() && !targets.isEmpty()) {
            int index = (int) (now.getEpochSecond() % targets.size());
            Player target = targets.get(index);
            bar.setTitle("§d" + target.getName() + ": " + format(from(target.getLocation())));
            applyFakeGlow(hunter, targets);
            return;
        }
        clearFakeGlow(hunter);
        Player closest = targets.stream()
            .filter(target -> target.getWorld().equals(hunter.getWorld()))
            .min(java.util.Comparator.comparingDouble(target ->
                target.getLocation().distanceSquared(hunter.getLocation()))).orElse(null);
        bar.setTitle(closest == null ? "§7No hay objetivos en esta dimensión"
            : "§cMás cercano " + closest.getName() + ": " + format(from(closest.getLocation())));
    }

    private void updateVisualHunter(UUID nextHunter) {
        if (java.util.Objects.equals(visualHunter, nextHunter)) {
            return;
        }
        if (visualHunter != null) {
            Player previous = Bukkit.getPlayer(visualHunter);
            if (previous != null) {
                previous.setGlowing(originalGlow.getOrDefault(visualHunter, false));
                clearFakeGlow(previous);
            }
            originalGlow.remove(visualHunter);
        }
        visualHunter = nextHunter;
        if (nextHunter != null) {
            Player next = Bukkit.getPlayer(nextHunter);
            if (next != null) {
                originalGlow.put(nextHunter, next.isGlowing());
                next.setGlowing(true);
            }
        }
    }

    private void refreshHunterWallhack() {
        Player hunter = service.eggCarrier().map(Bukkit::getPlayer).orElse(null);
        if (hunter == null || !hunter.isSneaking()) {
            clearFakeGlow(hunter);
            return;
        }
        List<Player> targets = service.survivors().stream()
            .filter(id -> !id.equals(hunter.getUniqueId()))
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .toList();
        applyFakeGlow(hunter, targets);
    }

    private void applyFakeGlow(Player hunter, List<Player> targets) {
        Set<UUID> next = targets.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        for (UUID old : new HashSet<>(fakeGlowTargets)) {
            if (!next.contains(old)) {
                Player target = Bukkit.getPlayer(old);
                if (target != null) {
                    entityGlowSender.send(hunter, target, target.isGlowing());
                }
                fakeGlowTargets.remove(old);
            }
        }
        for (Player target : targets) {
            entityGlowSender.send(hunter, target, true);
            fakeGlowTargets.add(target.getUniqueId());
        }
    }

    private void clearFakeGlow(Player hunter) {
        Player viewer = hunter;
        if (viewer == null && visualHunter != null) {
            viewer = Bukkit.getPlayer(visualHunter);
        }
        for (UUID id : new HashSet<>(fakeGlowTargets)) {
            Player target = Bukkit.getPlayer(id);
            if (viewer != null && target != null) {
                entityGlowSender.send(viewer, target, target.isGlowing());
            }
        }
        fakeGlowTargets.clear();
    }

    private void activateHunterDropFreeze(Player hunter) {
        Instant now = clock.instant();
        if (!HunterDropFreezePolicy.isReady(hunterDropFreezeReadyAt, now)) {
            hunter.sendMessage("§cCongelación en cooldown: §f"
                + HunterDropFreezePolicy.remainingSeconds(hunterDropFreezeReadyAt, now) + "s");
            return;
        }
        List<Player> targets = service.survivors().stream()
            .filter(id -> !id.equals(hunter.getUniqueId()))
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .filter(target -> target.getWorld().equals(hunter.getWorld()))
            .filter(target -> HunterDropFreezePolicy.isInRangeSquared(
                target.getLocation().distanceSquared(hunter.getLocation())
            ))
            .toList();
        if (targets.isEmpty()) {
            hunter.sendMessage("§7No había supervivientes a 25 bloques para congelar.");
            return;
        }

        hunterDropFreezeReadyAt = HunterDropFreezePolicy.nextUseAt(now);
        Instant expiresAt = now.plus(HunterDropFreezePolicy.FREEZE_DURATION);
        for (Player target : targets) {
            freezePlayer(target, expiresAt);
        }
        hunter.sendMessage("§bCongelaste a §f" + targets.size()
            + " §bjugador(es) durante 5s. Cooldown: 65s.");
    }

    private void freezePlayer(Player player, Instant expiresAt) {
        FrozenPlayerState existing = frozenPlayers.get(player.getUniqueId());
        if (existing != null) {
            frozenPlayers.put(player.getUniqueId(), existing.withExpiresAt(expiresAt));
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team previousTeam = scoreboard.getEntryTeam(player.getName());
        Team freezeTeam = ensureFrozenGlowTeam(scoreboard);
        FrozenPlayerState state = new FrozenPlayerState(
            player.getName(),
            player.getLocation().clone(),
            expiresAt,
            player.isGlowing(),
            previousTeam == null ? null : previousTeam.getName(),
            player.getPotionEffect(PotionEffectType.BLINDNESS)
        );
        frozenPlayers.put(player.getUniqueId(), state);
        freezeTeam.addEntry(player.getName());
        player.setGlowing(true);
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.BLINDNESS,
            (int) HunterDropFreezePolicy.FREEZE_DURATION.toSeconds() * 20 + 5,
            0,
            false,
            false,
            false
        ));
        player.sendMessage("§bEl huevo te congeló durante 5 segundos.");
    }

    private Team ensureFrozenGlowTeam(Scoreboard scoreboard) {
        if (frozenGlowTeam == null) {
            frozenGlowTeam = scoreboard.getTeam("bc_end_frozen");
            if (frozenGlowTeam == null) {
                frozenGlowTeam = scoreboard.registerNewTeam("bc_end_frozen");
            }
            frozenGlowTeam.color(NamedTextColor.AQUA);
        }
        return frozenGlowTeam;
    }

    private void tickFrozenPlayers(Instant now) {
        fastTickSequence++;
        for (Map.Entry<UUID, FrozenPlayerState> entry
            : new ArrayList<>(frozenPlayers.entrySet())) {
            if (!now.isBefore(entry.getValue().expiresAt())) {
                thawPlayer(entry.getKey(), entry.getValue());
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && fastTickSequence % 2 == 0) {
                spawnFreezeParticles(player);
            }
        }
    }

    private void spawnFreezeParticles(Player player) {
        Location center = player.getLocation().clone().add(0.0, 1.0, 0.0);
        Particle.DustOptions blue = new Particle.DustOptions(Color.AQUA, 1.2f);
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI * 2.0 / 12.0 + fastTickSequence * 0.12;
            Location particle = center.clone().add(
                Math.cos(angle) * 0.9,
                (index % 4) * 0.45 - 0.6,
                Math.sin(angle) * 0.9
            );
            player.getWorld().spawnParticle(Particle.DUST, particle, 1, blue);
        }
    }

    private void thawPlayer(UUID playerId, FrozenPlayerState state) {
        frozenPlayers.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (frozenGlowTeam != null) {
            frozenGlowTeam.removeEntry(player == null ? state.playerName() : player.getName());
        }
        if (state.previousTeamName() != null) {
            Team previous = scoreboard.getTeam(state.previousTeamName());
            if (previous != null) {
                previous.addEntry(player == null ? state.playerName() : player.getName());
            }
        }
        if (player != null) {
            player.setGlowing(state.originalGlowing());
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            if (state.originalBlindness() != null) {
                player.addPotionEffect(state.originalBlindness());
            }
            player.sendMessage("§aYa podés moverte.");
        }
        unregisterFrozenTeamIfEmpty();
    }

    private void clearFrozenPlayers() {
        for (Map.Entry<UUID, FrozenPlayerState> entry
            : new ArrayList<>(frozenPlayers.entrySet())) {
            thawPlayer(entry.getKey(), entry.getValue());
        }
    }

    private void unregisterFrozenTeamIfEmpty() {
        if (frozenGlowTeam != null && frozenPlayers.isEmpty()) {
            frozenGlowTeam.unregister();
            frozenGlowTeam = null;
        }
    }

    private boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    private void checkWinner() {
        if (service.phase() != EndEventPhase.HUNT) {
            return;
        }
        if (service.survivors().isEmpty()) {
            notifyOperators("§c[EndEvent] No quedaron supervivientes. Se requiere resolución manual.");
            return;
        }
        service.onlineWinner().map(Bukkit::getPlayer).ifPresent(this::beginVictory);
    }

    private void beginVictory(Player player) {
        if (service.phase() != EndEventPhase.HUNT) {
            return;
        }
        winner = player.getUniqueId();
        banAt = clock.instant().plusSeconds(settings.finalBanDelaySeconds());
        victoryFireworkTick = 0;
        service.setPhase(EndEventPhase.VICTORY);
        clearPresentation();
        setBorderFinal();
        applyFinalLocks();
        celebrate(player);
        persistRuntime();
    }

    private void celebrate(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendTitle("§6¡Felicitaciones, " + player.getName() + "!", "§eÚltimo superviviente", 10, 120, 20);
        }
    }

    private void tickVictoryFireworks() {
        if (winner == null) {
            return;
        }
        Player player = Bukkit.getPlayer(winner);
        if (player == null || !player.isOnline()) {
            return;
        }
        int tick = victoryFireworkTick++;
        for (int i = 0; i < 3; i++) {
            double angle = tick * 0.4 + i * (Math.PI * 2.0 / 3.0);
            double radius = 3.0 + (tick % 5) * 0.4;
            Location loc = player.getLocation().clone()
                .add(Math.cos(angle) * radius, 1.0 + (tick % 3), Math.sin(angle) * radius);
            spawnVictoryFirework(loc, tick + i);
        }
    }

    private void spawnVictoryFirework(Location location, int seed) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        firework.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        FireworkEffect.Type type = FIREWORK_TYPES[seed % FIREWORK_TYPES.length];
        Color[] palette = FIREWORK_PALETTES[seed % FIREWORK_PALETTES.length];
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
            .withColor(palette[0], palette[1])
            .withFade(palette[2])
            .flicker(seed % 3 == 0)
            .trail(seed % 2 == 0)
            .with(type)
            .build());
        meta.setPower(1 + (seed % 2));
        firework.setFireworkMeta(meta);
    }

    private void finishAndBan() {
        service.setPhase(EndEventPhase.FINISHED);
        applyFinalLocks();
        Set<UUID> targets = new LinkedHashSet<>(service.snapshot().participants().keySet());
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!player.isOp()) {
                targets.add(player.getUniqueId());
            }
        }
        for (UUID id : targets) {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            if (!offline.isOp()) {
                offline.ban(FINAL_REASON, (Date) null, "BigCasares");
            }
        }
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!player.isOp()) {
                player.kick(Component.text(FINAL_REASON));
            }
        }
        banAt = null;
        persistRuntime();
    }

    private void applyFinalLocks() {
        setEndOverride(false);
        expel(World.Environment.NETHER);
        expel(World.Environment.THE_END);
        setBorderFinal();
    }

    private void captureAndStartBorder() {
        World world = overworld();
        if (world == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        originalBorder = new EndEventBorderSnapshot(
            world.getName(), center.getX(), center.getZ(), border.getSize()
        );
        border.changeSize(20.0, 30L * 60L);
        persistRuntime();
    }

    private void resumeBorder() {
        World world = overworld();
        if (world == null || service.huntStartedAt().isEmpty()) {
            return;
        }
        long elapsed = Math.max(0L, Duration.between(service.huntStartedAt().orElseThrow(), clock.instant()).toSeconds());
        long remaining = Math.max(0L, 30L * 60L - elapsed);
        if (remaining == 0L) {
            world.getWorldBorder().setSize(20.0);
        } else {
            world.getWorldBorder().changeSize(20.0, remaining);
        }
    }

    private void restoreBorder() {
        if (originalBorder == null) {
            return;
        }
        World world = Bukkit.getWorld(originalBorder.world());
        if (world != null) {
            world.getWorldBorder().setCenter(originalBorder.centerX(), originalBorder.centerZ());
            world.getWorldBorder().setSize(originalBorder.size());
        }
    }

    private void setBorderFinal() {
        World world = overworld();
        if (world != null) {
            world.getWorldBorder().setSize(20.0);
        }
    }

    private void reconcileTrackedDrops() {
        World overworld = overworld();
        if (overworld == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!isTrackedDrop(item.getItemStack())) {
                    continue;
                }
                Location destination = null;
                if (world.getEnvironment() != World.Environment.NORMAL
                    && dimensionLocked(world.getEnvironment())) {
                    destination = safeLocations.insideBorder(overworld, overworld.getSpawnLocation(), 2.0);
                } else if (world.equals(overworld)
                    && !safeLocations.isInsideInset(overworld, item.getLocation(), 2.0)) {
                    destination = safeLocations.insideBorder(overworld, item.getLocation(), 2.0);
                }
                if (destination != null) {
                    item.teleport(destination);
                }
            }
        }
    }

    private void expel(World.Environment environment) {
        Location safe = safeOverworld(overworld() == null ? null : overworld().getSpawnLocation());
        if (safe == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                for (Player player : List.copyOf(world.getPlayers())) {
                    if (!observerBypass(player)) {
                        player.teleport(safe);
                    }
                }
            }
        }
        reconcileTrackedDrops();
    }

    private Location safeOverworld(Location requested) {
        World world = overworld();
        if (world == null) {
            return null;
        }
        Location desired = requested == null || requested.getWorld() == null
            ? world.getSpawnLocation()
            : new Location(world, requested.getX(), requested.getY(), requested.getZ());
        return safeLocations.insideBorder(world, desired, 3.0);
    }

    private void handleJoin(Player player) {
        restoreEscrow(player);
        if (service.phase() == EndEventPhase.COUNTDOWN
            || service.phase() == EndEventPhase.DELAYED_COUNTDOWN
            || service.phase() == EndEventPhase.DRAGON_FIGHT
            || service.phase() == EndEventPhase.EGG_AVAILABLE) {
            ensureGlobalBar();
            if (!isVanished(player)) {
                globalBar.addPlayer(player);
            }
            return;
        }
        if (service.phase() == EndEventPhase.HUNT) {
            if (service.isParticipant(player.getUniqueId())) {
                if (service.isSurvivor(player.getUniqueId())) {
                    service.reconnect(player.getUniqueId());
                    Location safe = player.getWorld().getEnvironment() == World.Environment.NORMAL
                        && safeLocations.isInsideInset(player.getWorld(), player.getLocation(), 3.0)
                        ? player.getLocation() : safeOverworld(player.getLocation());
                    if (safe != null && !safe.equals(player.getLocation())) {
                        player.teleport(safe);
                    }
                } else {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            } else if (!player.isOp()) {
                originalGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode().name());
                player.setGameMode(GameMode.SPECTATOR);
                persistRuntime();
            }
        } else if ((service.phase() == EndEventPhase.VICTORY || service.phase() == EndEventPhase.FINISHED)
            && !player.isOp()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    private List<ItemStack> snapshotInventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        addItems(items, player.getInventory().getStorageContents());
        addItems(items, player.getInventory().getArmorContents());
        addItem(items, player.getInventory().getItemInOffHand());
        addItem(items, player.getItemOnCursor());
        return List.copyOf(items);
    }

    private void addItems(List<ItemStack> target, ItemStack[] source) {
        for (ItemStack item : source) {
            addItem(target, item);
        }
    }

    private void addItem(List<ItemStack> target, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            target.add(item.clone());
        }
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
    }

    private void restoreEscrow(Player player) {
        List<ItemStack> items = escrow.remove(player.getUniqueId());
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            overflow.values().forEach(left -> dropTagged(player.getLocation(), left, player.getUniqueId()));
        }
        persistRuntime();
    }

    private void dropOfflineEscrow() {
        Location safe = safeOverworld(overworld() == null ? null : overworld().getSpawnLocation());
        if (safe == null) {
            return;
        }
        escrow.forEach((owner, items) ->
            items.forEach(item -> dropTagged(safe, item, owner)));
        escrow.clear();
    }

    private void dropTagged(Location requested, ItemStack item, UUID owner) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Location safe = requested;
        if (requested.getWorld().getEnvironment() == World.Environment.NORMAL
            && !safeLocations.isInsideInset(requested.getWorld(), requested, 2.0)) {
            safe = safeLocations.insideBorder(requested.getWorld(), requested, 2.0);
        } else if (dimensionLocked(requested.getWorld().getEnvironment())) {
            safe = safeOverworld(requested);
        }
        tagDrop(item, owner);
        safe.getWorld().dropItemNaturally(safe, item.clone());
    }

    private void dropEgg(Location location, UUID owner) {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tagEgg(egg);
        dropTagged(location, egg, owner);
    }

    private void createEggAtExitPortal() {
        World end = endWorld();
        if (end == null || end.getEnderDragonBattle() == null
            || end.getEnderDragonBattle().getEndPortalLocation() == null) {
            return;
        }
        Location base = end.getEnderDragonBattle().getEndPortalLocation();
        for (int y = base.getBlockY(); y < Math.min(end.getMaxHeight() - 1, base.getBlockY() + 10); y++) {
            Block block = end.getBlockAt(base.getBlockX(), y, base.getBlockZ());
            if (block.getType() == Material.AIR) {
                block.setType(Material.DRAGON_EGG, false);
                eggBlock = from(block.getLocation());
                persistRuntime();
                return;
            }
        }
        dropEgg(base.clone().add(0.5, 5.0, 0.5), null);
    }

    private Location findVanillaEggBlock() {
        World end = endWorld();
        if (end == null || end.getEnderDragonBattle() == null
            || end.getEnderDragonBattle().getEndPortalLocation() == null) {
            return null;
        }
        Location center = end.getEnderDragonBattle().getEndPortalLocation();
        for (int x = center.getBlockX() - 8; x <= center.getBlockX() + 8; x++) {
            for (int y = Math.max(end.getMinHeight(), center.getBlockY() - 2);
                 y <= Math.min(end.getMaxHeight() - 1, center.getBlockY() + 12); y++) {
                for (int z = center.getBlockZ() - 8; z <= center.getBlockZ() + 8; z++) {
                    if (end.getBlockAt(x, y, z).getType() == Material.DRAGON_EGG) {
                        return end.getBlockAt(x, y, z).getLocation();
                    }
                }
            }
        }
        return null;
    }

    private Optional<Player> findEggCarrier() {
        return Bukkit.getOnlinePlayers().stream()
            .map(player -> (Player) player)
            .filter(this::hasEventEgg)
            .findFirst();
    }

    private Optional<Item> findGroundEgg() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isEventEgg(item.getItemStack())) {
                    return Optional.of(item);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Item> findUntaggedDragonEgg() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.getItemStack().getType() == Material.DRAGON_EGG && !isEventEgg(item.getItemStack())) {
                    return Optional.of(item);
                }
            }
        }
        return Optional.empty();
    }

    private Location eventEggLocation() {
        Optional<Player> carrier = findEggCarrier();
        if (carrier.isPresent()) {
            return carrier.orElseThrow().getLocation();
        }
        Optional<Item> ground = findGroundEgg();
        if (ground.isPresent()) {
            return ground.orElseThrow().getLocation();
        }
        return eggBlock == null ? null : toLocation(eggBlock);
    }

    private boolean hasEventEgg(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isEventEgg(item)) {
                return true;
            }
        }
        return isEventEgg(player.getInventory().getItemInOffHand());
    }

    private void removeEventEgg(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isEventEgg(contents[slot])) {
                contents[slot] = null;
            }
        }
        inventory.setStorageContents(contents);
        if (isEventEgg(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    private void removeEventEggs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeEventEgg(player);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof Item item && isEventEgg(item.getItemStack())) {
                    item.remove();
                }
            }
        }
        if (eggBlock != null) {
            Location location = toLocation(eggBlock);
            if (location != null && location.getBlock().getType() == Material.DRAGON_EGG) {
                location.getBlock().setType(Material.AIR, false);
            }
        }
    }

    private boolean trackedEggBlock(Location location) {
        if (eggBlock == null || location == null || location.getWorld() == null) {
            return false;
        }
        return eggBlock.world().equals(location.getWorld().getName())
            && (int) Math.floor(eggBlock.x()) == location.getBlockX()
            && (int) Math.floor(eggBlock.y()) == location.getBlockY()
            && (int) Math.floor(eggBlock.z()) == location.getBlockZ();
    }

    private void tagEgg(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean isEventEgg(ItemStack item) {
        return item != null && item.getType() == Material.DRAGON_EGG
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE);
    }

    private void tagDrop(ItemStack item, UUID owner) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(dropKey, PersistentDataType.BYTE, (byte) 1);
        if (owner != null) {
            meta.getPersistentDataContainer().set(dropOwnerKey, PersistentDataType.STRING, owner.toString());
        }
        item.setItemMeta(meta);
    }

    private boolean isTrackedDrop(ItemStack item) {
        return item != null && item.hasItemMeta()
            && (item.getItemMeta().getPersistentDataContainer().has(dropKey, PersistentDataType.BYTE)
            || isEventEgg(item));
    }

    private Set<UUID> eligiblePlayers() {
        Set<UUID> result = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (eligible(player)) {
                result.add(player.getUniqueId());
            }
        }
        return result;
    }

    private boolean eligible(Player player) {
        boolean playingMode = player.getGameMode() == GameMode.SURVIVAL
            || player.getGameMode() == GameMode.ADVENTURE;
        if (!playingMode) {
            return false;
        }
        if (player.isOp()) {
            return optedInOperators.contains(player.getUniqueId());
        }
        return !isVanished(player);
    }

    private boolean observerBypass(Player player) {
        return player.isOp() && !service.isParticipant(player.getUniqueId());
    }

    private boolean isVanished(Player player) {
        return plugin.getServerControlModule() != null
            && plugin.getServerControlModule().vanish() != null
            && plugin.getServerControlModule().vanish().isVanished(player);
    }

    private boolean eventActive() {
        return service.phase() == EndEventPhase.EGG_AVAILABLE
            || service.phase() == EndEventPhase.HUNT
            || service.phase() == EndEventPhase.VICTORY;
    }

    private boolean potionsLocked() {
        return service.phase() == EndEventPhase.HUNT
            && service.hasClaimed(EndEventMilestone.POTIONS_LOCKED);
    }

    private boolean netherLocked() {
        return (service.phase() == EndEventPhase.HUNT
            && service.hasClaimed(EndEventMilestone.NETHER_LOCKED))
            || service.phase() == EndEventPhase.VICTORY
            || service.phase() == EndEventPhase.FINISHED;
    }

    private boolean endLocked() {
        return service.phase() == EndEventPhase.COUNTDOWN
            || service.phase() == EndEventPhase.DELAYED_COUNTDOWN
            || service.phase() == EndEventPhase.HUNT
                && service.hasClaimed(EndEventMilestone.END_LOCKED)
            || service.phase() == EndEventPhase.VICTORY
            || service.phase() == EndEventPhase.FINISHED;
    }

    private boolean dimensionLocked(World.Environment environment) {
        return environment == World.Environment.NETHER && netherLocked()
            || environment == World.Environment.THE_END && endLocked();
    }

    private void setEndOverride(Boolean value) {
        if (plugin.getServerControlModule() != null) {
            plugin.getServerControlModule().setEventEndAccessOverride(Optional.ofNullable(value));
        }
    }

    private void ensureGlobalBar() {
        if (globalBar == null) {
            globalBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isVanished(player)) {
                    globalBar.addPlayer(player);
                }
            }
        }
    }

    private void updateGlobalBar(String title, double progress) {
        ensureGlobalBar();
        globalBar.setTitle(title);
        globalBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    private void removeGlobalBar() {
        if (globalBar != null) {
            globalBar.removeAll();
            globalBar = null;
        }
    }

    private void removePersonalBar(UUID id) {
        BossBar bar = personalBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void clearPresentation() {
        removeGlobalBar();
        personalBars.values().forEach(BossBar::removeAll);
        personalBars.clear();
        updateVisualHunter(null);
        clearFakeGlow(null);
    }

    private void persistRuntime() {
        storage.saveRuntime(new EndEventRuntimeState(
            delayedReveal,
            Optional.ofNullable(portal),
            Optional.ofNullable(eggBlock),
            optedInOperators,
            Optional.ofNullable(originalBorder),
            originalGameModes,
            escrow,
            Optional.ofNullable(winner),
            Optional.ofNullable(banAt)
        ));
    }

    private void restoreRuntime(EndEventRuntimeState state) {
        delayedReveal = state.delayedReveal();
        portal = state.portal().orElse(null);
        eggBlock = state.eggBlock().orElse(null);
        optedInOperators.addAll(state.optedInOperators());
        originalBorder = state.originalBorder().orElse(null);
        originalGameModes.putAll(state.originalGameModes());
        escrow.putAll(state.escrow());
        winner = state.winner().orElse(null);
        banAt = state.banAt().orElse(null);
    }

    private World overworld() {
        return Bukkit.getWorld(settings.overworldName());
    }

    private World endWorld() {
        return Bukkit.getWorlds().stream()
            .filter(world -> world.getEnvironment() == World.Environment.THE_END)
            .findFirst().orElse(null);
    }

    private EndEventLocation from(Location location) {
        return new EndEventLocation(
            location.getWorld().getName(), location.getX(), location.getY(), location.getZ()
        );
    }

    private Location toLocation(EndEventLocation value) {
        World world = Bukkit.getWorld(value.world());
        return world == null ? null : new Location(world, value.x(), value.y(), value.z());
    }

    private String format(EndEventLocation location) {
        return location.world() + " X:" + (int) Math.floor(location.x())
            + " Y:" + (int) Math.floor(location.y())
            + " Z:" + (int) Math.floor(location.z());
    }

    private String formatRemaining(Instant now, Instant target) {
        long seconds = Math.max(0L, Duration.between(now, target).toSeconds());
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private double progress(Instant now, Instant target, Instant start) {
        long total = Math.max(1L, Duration.between(start, target).toMillis());
        long remaining = Math.max(0L, Duration.between(now, target).toMillis());
        return Math.min(1.0, (double) remaining / total);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    private void notifyOperators(String message) {
        plugin.getLogger().warning(org.bukkit.ChatColor.stripColor(message));
        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(player -> player.sendMessage(message));
    }

    private record FrozenPlayerState(
        String playerName,
        Location location,
        Instant expiresAt,
        boolean originalGlowing,
        String previousTeamName,
        PotionEffect originalBlindness
    ) {
        private FrozenPlayerState withExpiresAt(Instant nextExpiresAt) {
            return new FrozenPlayerState(
                playerName,
                location,
                nextExpiresAt,
                originalGlowing,
                previousTeamName,
                originalBlindness
            );
        }
    }
}
