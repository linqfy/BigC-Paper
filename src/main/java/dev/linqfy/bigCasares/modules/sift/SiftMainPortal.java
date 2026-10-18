package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.feature.OverlayRegion;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalBuilder;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Anchor;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Candidate;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Origin;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Window;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Template;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The Sift's main portal ({@code SiftPortalStructure} and {@code SiftTeleportManager} of the mod): one
 * structure that every Ancient City portal leads to, and that leads back out.
 *
 * <p><b>Building.</b> The mod builds it on the first visit, synchronously. Here it is prepared when the
 * dimension activates (after ten seconds), in the background and once: the chunks of the search area
 * (about 320) are generated asynchronously and snapshotted a few per tick, the site is chosen and the
 * portal planned on an asynchronous thread over the snapshots ({@link SiftMainPortalSite},
 * {@link SiftMainPortalBuilder}), and the resulting block changes (tens of thousands) are applied on the server thread
 * two thousand per tick. Then the portal is lit as a framework portal ({@link SiftDimension#MAIN_PORTAL}),
 * which makes walking into it leave through the framework's return point, and the anchor is stored in the
 * dimension's persistent values. A server that stops before that point builds again on the next start.</p>
 *
 * <p><b>Travel.</b> A player entering through the Ancient City portal arrives at the anchor facing the way
 * they faced. If the portal is not ready yet the transfer proceeds like any other portal's (1:1
 * coordinates and a small return frame) and the build is started if it is not running. Leaving through the
 * main portal returns the player to where they entered; if that portal no longer stands, or they came in
 * through a rift, an exit rift opens at their respawn point instead ({@link SiftRifts#returnFromPortal}).</p>
 *
 * <p>The site is never moved once stored, and the structure is not rebuilt if players break it.</p>
 */
final class SiftMainPortal implements Listener {

    static final String LANDMARK = "main_portal";
    static final String ANCHOR_VALUE = "main_portal.anchor";
    private static final String PREFIX = "[TheSift] ";
    private static final long START_DELAY_TICKS = 200L;
    private static final int SNAPSHOTS_PER_TICK = 24;
    private static final int CHANGES_PER_TICK = 2_000;
    private static final long RETRY_DELAY_TICKS = 6_000L;
    /** The frame block lit through: the floor row of the portal, away from the arrival point. */
    private static final int FRAME_X = SiftMainPortalSite.MARKER_X;
    private static final int FRAME_Y = SiftMainPortalSite.MARKER_Y;
    private static final int FRAME_Z = 12;

    private enum Phase {
        IDLE, BUILDING, BUILT, FAILED
    }

    private record Plan(Origin origin, List<OverlayRegion.Change> changes) {
    }

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final SiftTemplates templates;
    private final SiftRifts rifts;
    private final Map<String, BlockData> blockData = new HashMap<>();
    private final Set<Long> tickets = new HashSet<>();
    private volatile ActiveDimension dimension;
    private Phase phase = Phase.IDLE;
    private long retryAtTick;
    /** The stored anchor; null until the structure exists. */
    private volatile Anchor anchor;
    /** Whether the structure's portal is lit, so that players can be sent to the anchor. */
    private volatile boolean lit;
    private boolean verifying;

    SiftMainPortal(JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, SiftTemplates templates, SiftRifts rifts) {
        this.plugin = plugin;
        this.platform = platform;
        this.templates = templates;
        this.rifts = rifts;
    }

    static String encode(Anchor anchor) {
        return anchor.x() + "," + anchor.y() + "," + anchor.z();
    }

    static Optional<Anchor> decode(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Anchor(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())));
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().ownCleanup("sift-main-portal-dimension", () -> {
            if (dimension == active) {
                dimension = null;
            }
            releaseTickets(active.world());
        });
        active.registrations().registerListener("sift-main-portal", this);
        anchor = active.values().get(ANCHOR_VALUE).flatMap(SiftMainPortal::decode).orElse(null);
        phase = anchor == null ? Phase.IDLE : Phase.BUILT;
        // The framework indexes its lit portals right after registering the dimension, so look at them later.
        active.registrations().scheduleDelayed("sift-main-portal-start", this::startup, START_DELAY_TICKS);
    }

    private void startup() {
        if (dimension == null) {
            return;
        }
        if (anchor == null) {
            ensureBuilt();
        } else {
            verifyLit();
        }
    }

    /** The arrival point of a ready portal. */
    Optional<Location> arrival() {
        ActiveDimension active = dimension;
        Anchor ready = lit ? anchor : null;
        if (active == null || ready == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(active.world(), ready.x() + 0.5, ready.y(), ready.z() + 0.5));
    }

    /** {@code /dimension locate}: the arrival point; starts the build when there is none (the mod builds on demand). */
    Optional<Location> locate() {
        Optional<Location> found = arrival();
        if (found.isEmpty()) {
            ensureBuilt();
        }
        return found;
    }

    // --- travel ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTravel(DimensionTravelEvent event) {
        if (dimension == null || !event.dimension().equals(SiftDimension.KEY)) {
            return;
        }
        if (event.isEntering()) {
            if (event.cause() != DimensionTravelEvent.Cause.API) {
                rifts.forgetRiftArrival(event.getPlayer().getUniqueId());
            }
            if (!event.portal().equals(Optional.of(SiftDimension.ANCIENT_CITY_PORTAL))) {
                return;
            }
            Anchor ready = lit ? anchor : null;
            if (ready == null) {
                // Not ready: this transfer keeps the framework's destination.
                ensureBuilt();
                verifyLit();
                return;
            }
            MainPortalTravel.redirect(event, ready).ifPresent(event::setTo);
        } else if (MainPortalTravel.leavesThroughMainPortal(event) && rifts.available()) {
            Player player = event.getPlayer();
            Location returnPoint = event.to();
            MainPortalTravel.Exit exit = MainPortalTravel.exit(rifts.arrivedByRift(player.getUniqueId()), returnPoint,
                (x, y, z) -> returnPoint.getWorld().getBlockAt(x, y, z).getType() == Material.LIGHT);
            if (exit != MainPortalTravel.Exit.FRAMEWORK) {
                // The framework would put the player where an entrance used to be.
                event.setCancelled(true);
                rifts.returnFromPortal(player, exit == MainPortalTravel.Exit.ENTRANCE_CLOSED);
            }
        }
    }

    // --- building --------------------------------------------------------------------------

    /** Starts the build unless there is a portal, a build in progress, or a recent failure. */
    void ensureBuilt() {
        ActiveDimension active = dimension;
        if (active == null || phase == Phase.BUILDING || phase == Phase.BUILT) {
            return;
        }
        if (phase == Phase.FAILED && Bukkit.getCurrentTick() < retryAtTick) {
            return;
        }
        Optional<Template> template = templates.template(SiftMainPortalSite.TEMPLATE);
        if (template.isEmpty()) {
            plugin.getLogger().severe(PREFIX + "The main portal template is missing; players keep arriving at the same coordinates.");
            phase = Phase.FAILED;
            retryAtTick = Long.MAX_VALUE;
            return;
        }
        phase = Phase.BUILDING;
        new Build(active, template.get()).start();
    }

    private final class Build {

        private final ActiveDimension active;
        private final World world;
        private final Template template;

        private Build(ActiveDimension active, Template template) {
            this.active = active;
            this.world = active.world();
            this.template = template;
        }

        void start() {
            Window search = SiftMainPortalSite.searchWindow();
            plugin.getLogger().info(PREFIX + "Preparing the main portal: generating " + search.chunkCount()
                + " chunks to choose its site. This happens once.");
            loadChunks(world, search)
                .thenCompose(ignored -> snapshots(world, search))
                .thenCompose(chunks -> async(() -> choose(chunks)))
                .thenCompose(this::planAt)
                .thenCompose(this::apply)
                .whenComplete((plan, failure) -> onMain(() -> {
                    finish(plan, failure);
                    return null;
                }));
        }

        private Candidate choose(Map<Long, ChunkSnapshot> chunks) {
            checkActive();
            OverlayRegion region = new OverlayRegion(new ChunkSnapshotSource(chunks, world.getMinHeight(), world.getMaxHeight()),
                world.getSeed());
            return SiftMainPortalSite.find(SiftMainPortalSite.heights(region));
        }

        private CompletableFuture<Plan> planAt(Candidate candidate) {
            Origin origin = SiftMainPortalSite.origin(candidate, world.getMinHeight(), world.getMaxHeight());
            Window window = SiftMainPortalSite.buildWindow(origin);
            plugin.getLogger().info(PREFIX + "Main portal site chosen at " + origin.anchor().x() + ", " + origin.anchor().z()
                + " (ground " + candidate.groundY() + ", roughness " + Math.round(candidate.roughness() * 100) / 100.0
                + "); shaping the terrain.");
            return loadChunks(world, window)
                .thenCompose(ignored -> snapshots(world, window))
                .thenCompose(chunks -> async(() -> {
                    checkActive();
                    OverlayRegion region = new OverlayRegion(
                        new ChunkSnapshotSource(chunks, world.getMinHeight(), world.getMaxHeight()), world.getSeed());
                    SiftMainPortalBuilder.Result result = SiftMainPortalBuilder.build(region, template, origin);
                    if (result.rejectedWrites() > 0) {
                        throw new IllegalStateException(result.rejectedWrites() + " portal blocks fall outside the loaded area");
                    }
                    return new Plan(origin, region.changes());
                }));
        }

        private CompletableFuture<Plan> apply(Plan plan) {
            List<List<OverlayRegion.Change>> slices = new ArrayList<>();
            for (int from = 0; from < plan.changes().size(); from += CHANGES_PER_TICK) {
                slices.add(plan.changes().subList(from, Math.min(plan.changes().size(), from + CHANGES_PER_TICK)));
            }
            return eachTick(slices, 1, slice -> {
                checkActive();
                for (OverlayRegion.Change change : slice) {
                    Block block = world.getBlockAt(change.x(), change.y(), change.z());
                    block.setBlockData(dataOf(change.state()), false);
                }
            }).thenApply(ignored -> plan);
        }

        private void checkActive() {
            if (dimension != active) {
                throw new CancellationException("The Sift was deactivated");
            }
        }

        private void finish(Plan plan, Throwable failure) {
            if (dimension != active) {
                return;
            }
            if (failure != null || plan == null) {
                releaseTickets(world);
                phase = Phase.FAILED;
                retryAtTick = Bukkit.getCurrentTick() + RETRY_DELAY_TICKS;
                plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not build the main portal; players keep arriving at the same"
                    + " coordinates, and the build is tried again in five minutes", failure);
                return;
            }
            anchor = plan.origin().anchor();
            active.values().put(ANCHOR_VALUE, encode(anchor));
            phase = Phase.BUILT;
            lit = light();
            releaseTickets(world);
            plugin.getLogger().info(PREFIX + "Main portal ready at " + anchor.x() + ", " + anchor.y() + ", " + anchor.z() + " ("
                + plan.changes().size() + " blocks changed" + (lit ? "" : "; the portal could not be lit, see above") + ").");
        }
    }

    /** Lights the portal if it is not lit yet (the framework answers with the lit shape either way). */
    private boolean light() {
        ActiveDimension active = dimension;
        Anchor stored = anchor;
        Optional<DimensionPlatform> dimensions = platform.get();
        if (active == null || stored == null || dimensions.isEmpty()) {
            return false;
        }
        Origin origin = stored.origin();
        Block frame = active.world().getBlockAt(origin.x() + FRAME_X, origin.y() + FRAME_Y, origin.z() + FRAME_Z);
        Optional<PortalShape> shape = dimensions.get().lightPortal(SiftDimension.KEY, SiftDimension.MAIN_PORTAL, frame, null);
        if (shape.isEmpty()) {
            plugin.getLogger().warning(PREFIX + "The main portal at " + stored.x() + ", " + stored.y() + ", " + stored.z()
                + " could not be lit: its frame is incomplete (broken?) or lighting was cancelled. Players keep arriving"
                + " at the same coordinates.");
        }
        return shape.isPresent();
    }

    /** After a restart: loads the portal's chunks and lights it again if the framework does not know it. */
    private void verifyLit() {
        ActiveDimension active = dimension;
        Anchor stored = anchor;
        if (active == null || stored == null || lit || verifying) {
            return;
        }
        verifying = true;
        Origin origin = stored.origin();
        Window window = new Window(origin.x(), origin.x() + SiftMainPortalSite.SIZE_X - 1, origin.z(),
            origin.z() + SiftMainPortalSite.SIZE_Z - 1);
        loadChunks(active.world(), window).whenComplete((ignored, failure) -> onMain(() -> {
            verifying = false;
            if (dimension == active && failure == null) {
                lit = light();
            }
            releaseTickets(active.world());
            return null;
        }));
    }

    // --- pipeline helpers ------------------------------------------------------------------

    /** Keeps the window's chunks loaded (until {@link #releaseTickets}) and generates the missing ones asynchronously. */
    private CompletableFuture<Void> loadChunks(World world, Window window) {
        return onMain(() -> {
            List<CompletableFuture<?>> loads = new ArrayList<>();
            for (int cx = window.minChunkX(); cx <= window.maxChunkX(); cx++) {
                for (int cz = window.minChunkZ(); cz <= window.maxChunkZ(); cz++) {
                    if (tickets.add(ChunkSnapshotSource.chunkKey(cx, cz))) {
                        world.addPluginChunkTicket(cx, cz, plugin);
                    }
                    loads.add(world.getChunkAtAsync(cx, cz, true));
                }
            }
            return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
        }).thenCompose(Function.identity());
    }

    private void releaseTickets(World world) {
        for (long key : tickets) {
            world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
        }
        tickets.clear();
    }

    /** Snapshots of the window's loaded chunks, a few per tick to spread the copying. */
    private CompletableFuture<Map<Long, ChunkSnapshot>> snapshots(World world, Window window) {
        List<int[]> coordinates = new ArrayList<>();
        for (int cx = window.minChunkX(); cx <= window.maxChunkX(); cx++) {
            for (int cz = window.minChunkZ(); cz <= window.maxChunkZ(); cz++) {
                coordinates.add(new int[]{cx, cz});
            }
        }
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        return eachTick(coordinates, SNAPSHOTS_PER_TICK, chunk -> snapshots.put(ChunkSnapshotSource.chunkKey(chunk[0], chunk[1]),
            world.getChunkAt(chunk[0], chunk[1]).getChunkSnapshot(false, false, false))).thenApply(ignored -> snapshots);
    }

    /** Runs {@code action} for every item, {@code perTick} items per server tick. */
    private <T> CompletableFuture<Void> eachTick(List<T> items, int perTick, Consumer<T> action) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Iterator<T> remaining = items.iterator();
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            try {
                for (int count = 0; count < perTick && remaining.hasNext(); count++) {
                    action.accept(remaining.next());
                }
                if (remaining.hasNext()) {
                    ActiveDimension active = dimension;
                    if (active == null) {
                        done.cancel(false);
                        return;
                    }
                    active.registrations().scheduleDelayed("sift-main-portal-step", step[0], 1L);
                } else {
                    done.complete(null);
                }
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        };
        onMain(() -> {
            step[0].run();
            return null;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    /** Runs the work on the server thread (the next tick at the latest). */
    private <T> CompletableFuture<T> onMain(Supplier<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        ActiveDimension active = dimension;
        if (active == null) {
            result.cancel(false);
            return result;
        }
        try {
            active.registrations().scheduleImmediate("sift-main-portal-step", () -> {
                try {
                    result.complete(work.get());
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private <T> CompletableFuture<T> async(Supplier<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    result.complete(work.get());
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private BlockData dataOf(BlockStateSpec state) {
        return blockData.computeIfAbsent(state.asString(), plugin.getServer()::createBlockData);
    }
}
