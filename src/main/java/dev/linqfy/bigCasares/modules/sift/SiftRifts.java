package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.travel.TravelResult;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The mod's rifts ({@code RiftManager}, {@code RiftEntity}, {@code RiftDirectory}):
 * temporary exits. Every 270-330 seconds a rift opens 20-64 blocks from each player
 * in the Sift and stays for five minutes. Walking into it returns the player to
 * their Overworld respawn point (or the world spawn) through an Overworld rift
 * that lives as long as the original one; walking into that rift leads back to
 * where the player left the Sift. Rifts reuse an open exit near the same anchor.
 *
 * <p>The main portal's way out ({@code RiftManager.returnFromPortal}) reuses the same exit: when the
 * portal a player entered through no longer stands, {@link #returnFromPortal} opens an Overworld rift at their
 * respawn point that lives five minutes and leads back to the main portal.</p>
 *
 * <p>Differences: rifts are BetterModel models on marker armour stands and are not
 * saved across restarts; the exit search covers 48 blocks around the anchor (the mod
 * searches up to 256), falling back to the anchor itself; rifts place no light
 * blocks and do not repel mobs.</p>
 */
final class SiftRifts implements Listener {

    static final String RIFT_MODEL = "the_sift_rift";
    static final String RIFT_OVERWORLD_MODEL = "the_sift_rift_overworld";
    private static final int LIFETIME_TICKS = 6000;
    private static final int INTERVAL_TICKS = 5400;
    private static final int INTERVAL_JITTER = 1201;
    private static final int RETRY_TICKS = 200;
    private static final int LATCH_TICKS = 100;
    private static final int EXIT_SEARCH_RADIUS = 48;
    private static final double EXIT_REUSE_DISTANCE = 64.0;

    private final class Rift {
        private final World world;
        private final RiftPlacement.Spot spot;
        private final boolean siftSide;
        private final Location linked;
        private long expiresAt;
        private ArmorStand anchor;
        private JavaModelHandle model;

        private Rift(World world, RiftPlacement.Spot spot, boolean siftSide, long expiresAt, Location linked) {
            this.world = world;
            this.spot = spot;
            this.siftSide = siftSide;
            this.expiresAt = expiresAt;
            this.linked = linked;
        }

        private Location center() {
            return new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5, spot.alongX() ? 0.0F : 90.0F, 0.0F);
        }
    }

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final JavaModelGateway models;
    private final Map<UUID, Rift> rifts = new LinkedHashMap<>();
    private final Map<UUID, Long> nextSpawn = new HashMap<>();
    private final Map<UUID, Long> latches = new HashMap<>();
    private final Random random = new Random();
    /** Players an Overworld rift sent into the Sift: they have no entrance portal to go back to (the mod clears their return point). */
    private final Set<UUID> riftArrivals = new HashSet<>();
    private Supplier<Optional<Location>> mainPortal = Optional::empty;
    private ActiveDimension dimension;
    private boolean warnedMissingModel;

    SiftRifts(JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, JavaModelGateway models) {
        this.plugin = plugin;
        this.platform = platform;
        this.models = models;
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().ownCleanup("sift-rifts-dimension", () -> {
            if (dimension == active) {
                dimension = null;
            }
        });
        active.registrations().registerListener("sift-rifts", this);
        active.registrations().scheduleRepeating("sift-rifts", this::tick, 20L, 20L);
        active.registrations().ownCleanup("sift-rifts-close", () -> List.copyOf(rifts.values()).forEach(this::close));
    }

    /** Where an Overworld rift without a link leads: the main portal (the mod's {@code ensurePortal} fallback). */
    void mainPortal(Supplier<Optional<Location>> mainPortal) {
        this.mainPortal = mainPortal;
    }

    /** Whether exits can be opened (the Sift is active and rifts are enabled). */
    boolean available() {
        return dimension != null;
    }

    /** Whether the player came into the Sift through a rift and has not left it since; consumes the mark. */
    boolean arrivedByRift(UUID player) {
        return riftArrivals.remove(player);
    }

    /** The player entered the Sift another way (a portal, a command): their entrance is their return point again. */
    void forgetRiftArrival(UUID player) {
        riftArrivals.remove(player);
    }

    /**
     * Whether a rift that is still open (either side of a pair) is within {@code radius} blocks of the point: the
     * mod's {@code ModAdvancements.isNearOpenRift}, which asks for a rift of the same level that is neither
     * removed nor closing. A rift past its time is closing.
     */
    boolean isNearOpenRift(World world, double x, double y, double z, double radius) {
        long now = now();
        double limit = radius * radius;
        for (Rift rift : rifts.values()) {
            if (rift.world.equals(world) && now < rift.expiresAt && rift.spot.distanceSquaredTo(x, y, z) <= limit) {
                return true;
            }
        }
        return false;
    }

    private static long now() {
        return Bukkit.getCurrentTick();
    }

    // --- lifetime ------------------------------------------------------------------------

    private void tick() {
        ActiveDimension active = dimension;
        if (active == null) {
            return;
        }
        long now = now();
        World sift = active.world();
        Set<UUID> present = new HashSet<>();
        for (Player player : sift.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            present.add(player.getUniqueId());
            long due = nextSpawn.computeIfAbsent(player.getUniqueId(),
                ignored -> now + INTERVAL_TICKS + random.nextInt(INTERVAL_JITTER));
            if (now < due) {
                continue;
            }
            Location at = player.getLocation();
            Optional<RiftPlacement.Spot> spot = RiftPlacement.findNatural(new WorldTerrain(sift), at.getBlockX(),
                at.getBlockZ(), random, candidate -> unoccupied(sift, candidate));
            spot.ifPresent(found -> open(sift, found, true, now + LIFETIME_TICKS, null));
            nextSpawn.put(player.getUniqueId(), now + (spot.isPresent() ? INTERVAL_TICKS + random.nextInt(INTERVAL_JITTER)
                : RETRY_TICKS));
        }
        nextSpawn.keySet().retainAll(present);
        for (Rift rift : List.copyOf(rifts.values())) {
            if (now >= rift.expiresAt) {
                close(rift);
            } else {
                show(rift);
                ambience(rift);
            }
        }
    }

    private Rift open(World world, RiftPlacement.Spot spot, boolean siftSide, long expiresAt, Location linked) {
        Rift rift = new Rift(world, spot, siftSide, expiresAt, linked);
        rifts.put(UUID.randomUUID(), rift);
        world.playSound(rift.center(), "the_sift:entity.rift.appear", SoundCategory.NEUTRAL, 2.0F, 1.0F);
        show(rift);
        return rift;
    }

    /** (Re)creates the model while the rift's chunk is loaded; marker stands are not saved. */
    private void show(Rift rift) {
        if (rift.anchor != null && rift.anchor.isValid()
            || !rift.world.isChunkLoaded(rift.spot.x() >> 4, rift.spot.z() >> 4)) {
            return;
        }
        if (rift.model != null) {
            models.close(rift.model);
            rift.model = null;
        }
        rift.anchor = rift.world.spawn(rift.center(), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        try {
            rift.model = models.attach(rift.anchor, rift.siftSide ? RIFT_MODEL : RIFT_OVERWORLD_MODEL);
            models.animate(rift.model, "appear");
        } catch (RuntimeException unavailable) {
            rift.model = null;
            if (!warnedMissingModel) {
                warnedMissingModel = true;
                plugin.getLogger().log(Level.WARNING, "[TheSift] Rift model unavailable; rifts show as particles", unavailable);
            }
        }
    }

    private void ambience(Rift rift) {
        if (rift.anchor == null || !rift.anchor.isValid() || rift.world.getPlayers().isEmpty()) {
            return;
        }
        Location center = rift.center().add(0.0, 2.0, 0.0);
        rift.world.spawnParticle(Particle.REVERSE_PORTAL, center, 24, rift.spot.alongX() ? 2.2 : 0.2, 1.2,
            rift.spot.alongX() ? 0.2 : 2.2, 0.01);
    }

    private void close(Rift rift) {
        rifts.values().remove(rift);
        if (rift.model != null) {
            models.close(rift.model);
            rift.model = null;
        }
        if (rift.anchor != null) {
            rift.anchor.remove();
            rift.anchor = null;
        }
        if (rift.world.isChunkLoaded(rift.spot.x() >> 4, rift.spot.z() >> 4)) {
            rift.world.spawnParticle(Particle.REVERSE_PORTAL, rift.center().add(0.0, 2.0, 0.0), 60, 1.5, 1.2, 1.5, 0.08);
        }
    }

    // --- travel --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (rifts.isEmpty() || event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        BoundingBox box = sweep(event.getFrom(), event.getTo());
        Rift touched = null;
        for (Rift rift : rifts.values()) {
            if (rift.world.equals(player.getWorld()) && rift.spot.touches(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                touched = rift;
                break;
            }
        }
        Long latch = latches.get(player.getUniqueId());
        if (latch != null) {
            // An arrival stays latched until the player has left every rift and the cooldown passed.
            if (touched != null || now() < latch) {
                return;
            }
            latches.remove(player.getUniqueId());
        }
        if (touched != null) {
            latches.put(player.getUniqueId(), now() + LATCH_TICKS);
            if (touched.siftSide) {
                exit(player, touched);
            } else {
                enter(player, touched);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        latches.remove(event.getPlayer().getUniqueId());
        riftArrivals.remove(event.getPlayer().getUniqueId());
    }

    /** The player's box swept from the previous position (the mod's {@code expandTowards}). */
    private static BoundingBox sweep(Location from, Location to) {
        BoundingBox start = BoundingBox.of(from.toVector(), 0.3, 0.0, 0.3).expand(0.0, 0.0, 0.0, 0.0, 1.8, 0.0);
        BoundingBox end = BoundingBox.of(to.toVector(), 0.3, 0.0, 0.3).expand(0.0, 0.0, 0.0, 0.0, 1.8, 0.0);
        return from.distanceSquared(to) > 256.0 ? end : start.union(end);
    }

    /** Out of the Sift: to the Overworld anchor through a linked exit rift. */
    private void exit(Player player, Rift origin) {
        exitTo(player, origin.expiresAt, origin.center(), true, null);
    }

    /**
     * {@code RiftManager.returnFromPortal}: out of the Sift without a Sift rift, through an exit rift at the
     * respawn point that stays open for five minutes and is not linked back to a place in the Sift (it leads
     * to the main portal). {@code closed} tells the player that the portal they entered through is gone.
     */
    void returnFromPortal(Player player, boolean closed) {
        Component notice = closed ? Component.translatable("message.the_sift.portal_closed",
            "El portal por el que entraste a la Sift se cerró durante tu exploración") : null;
        exitTo(player, now() + LIFETIME_TICKS, null, false, notice);
    }

    /**
     * @param back    where the exit rift leads back to, or null for the main portal
     * @param reward  whether using the rift counts for the Rifter advancement
     * @param notice  sent after the player arrived, or null
     */
    private void exitTo(Player player, long expiresAtTicks, Location back, boolean reward, Component notice) {
        riftArrivals.remove(player.getUniqueId());
        World overworld = Bukkit.getWorlds().getFirst();
        Location respawn = player.getRespawnLocation();
        Location anchor = respawn != null && overworld.equals(respawn.getWorld()) ? respawn : overworld.getSpawnLocation();
        Rift reused = null;
        for (Rift rift : rifts.values()) {
            if (!rift.siftSide && rift.world.equals(overworld) && horizontalDistance(rift.center(), anchor) <= EXIT_REUSE_DISTANCE
                && (reused == null || horizontalDistance(rift.center(), anchor) < horizontalDistance(reused.center(), anchor))) {
                reused = rift;
            }
        }
        if (reused != null) {
            reused.expiresAt = Math.max(reused.expiresAt, expiresAtTicks);
            go(player, reused.center(), reward).thenAccept(success -> notify(player, success, notice));
            return;
        }
        long expiresAt = Math.max(now() + 20L, expiresAtTicks);
        ActiveDimension active = dimension;
        if (active == null) {
            return;
        }
        holdAround(overworld, anchor).thenAccept(release -> active.registrations().scheduleImmediate("sift-rift-exit", () -> {
            try {
                if (!player.isOnline() || dimension != active) {
                    return;
                }
                // A mob standing on the nearest spot must not cost the player their exit rift.
                Optional<RiftPlacement.Spot> spot = RiftPlacement.findNear(new WorldTerrain(overworld), anchor.getBlockX(),
                    anchor.getBlockZ(), EXIT_SEARCH_RADIUS, candidate -> unoccupied(overworld, candidate));
                if (spot.isPresent()) {
                    go(player, open(overworld, spot.get(), false, expiresAt, back).center(), reward)
                        .thenAccept(success -> notify(player, success, notice));
                } else {
                    player.sendMessage(Component.translatable("message.the_sift.rift_no_exit",
                        "La grieta no pudo abrirse cerca de tu punto de reaparición", NamedTextColor.RED));
                    go(player, anchor, reward).thenAccept(success -> notify(player, success, notice));
                }
            } finally {
                release.run();
            }
        }));
    }

    /** Back into the Sift, where the player left it (the main portal for a rift that was opened without a link). */
    private void enter(Player player, Rift rift) {
        Location target = rift.linked != null ? rift.linked : mainPortal.get().map(portal -> {
            Location facing = player.getLocation();
            portal.setYaw(facing.getYaw());
            portal.setPitch(facing.getPitch());
            return portal;
        }).orElse(null);
        if (target != null) {
            go(player, target, true).thenAccept(success -> {
                if (success) {
                    riftArrivals.add(player.getUniqueId());
                }
            });
        }
    }

    private static void notify(Player player, boolean success, Component notice) {
        if (success && notice != null && player.isOnline()) {
            player.sendMessage(notice);
        }
    }

    /**
     * The framework teleports into its dimensions (return point, arrival search); anything else, such as
     * the Overworld, is a plain teleport.
     */
    private CompletableFuture<Boolean> go(Player player, Location target, boolean reward) {
        Optional<DimensionPlatform> dimensions = platform.get().filter(candidate -> candidate.dimensionOf(target.getWorld()).isPresent());
        CompletableFuture<Boolean> moved = dimensions.isPresent()
            ? dimensions.get().teleport(player, target).thenApply(TravelResult::succeeded)
            : player.teleportAsync(target);
        moved.thenAccept(success -> {
            if (success && reward && plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> SiftAdvancements.award(player, SiftAdvancements.RIFTER));
            }
        });
        return moved;
    }

    /**
     * Loads the chunks the exit search reads and holds them with plugin tickets until the returned release runs.
     * Without the tickets, chunks nobody stands in (the world spawn on 26.x) can unload again before the search
     * runs, which on a live server left every exit without a spot. Paper completes the futures on the main thread,
     * where the ticket is added as each chunk arrives.
     */
    private CompletableFuture<Runnable> holdAround(World world, Location anchor) {
        int reach = EXIT_SEARCH_RADIUS + 5;
        List<Chunk> held = new ArrayList<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = (anchor.getBlockX() - reach) >> 4; cx <= (anchor.getBlockX() + reach) >> 4; cx++) {
            for (int cz = (anchor.getBlockZ() - reach) >> 4; cz <= (anchor.getBlockZ() + reach) >> 4; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    if (chunk.addPluginChunkTicket(plugin)) {
                        held.add(chunk);
                    }
                }));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> () -> held.forEach(chunk -> chunk.removePluginChunkTicket(plugin)));
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** No entity in the rift volume (the mod's last {@code space} check). */
    private static boolean unoccupied(World world, RiftPlacement.Spot spot) {
        BoundingBox volume = spot.alongX()
            ? new BoundingBox(spot.x() - 4, spot.y(), spot.z(), spot.x() + 5, spot.y() + 5, spot.z() + 1)
            : new BoundingBox(spot.x(), spot.y(), spot.z() - 4, spot.x() + 1, spot.y() + 5, spot.z() + 5);
        return world.getNearbyEntities(volume).isEmpty();
    }

    /** {@link RiftPlacement.Terrain} over loaded chunks of a Bukkit world. */
    private record WorldTerrain(World world) implements RiftPlacement.Terrain {

        @Override
        public int minY() {
            return world.getMinHeight();
        }

        @Override
        public int maxY() {
            return world.getMaxHeight();
        }

        @Override
        public int surface(int x, int z) {
            return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
        }

        /** Air, or a non-solid block without fluid (plants, vines, roots). */
        @Override
        public boolean clear(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return y >= world.getMaxHeight();
            }
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) {
                return true;
            }
            if (block.isLiquid() || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                return false;
            }
            return !block.getType().isSolid();
        }

        @Override
        public boolean usable(int x, int z) {
            return world.isChunkLoaded(x >> 4, z >> 4) && world.getWorldBorder().isInside(new Location(world, x + 0.5, 0.0, z + 0.5));
        }
    }
}
