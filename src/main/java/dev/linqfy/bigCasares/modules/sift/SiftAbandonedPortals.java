package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.sift.feature.AbandonedPortalLandmarks;
import dev.linqfy.bigCasares.modules.sift.feature.AbandonedPortalSites;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

/**
 * {@code /dimension locate the_sift:the_sift abandoned_main_portal}: the port of the mod's
 * {@code SiftLocateCommand.locateAbandonedPortal} (the mod answers {@code /locate structure
 * the_sift:abandoned_main_portal}; vanilla {@code /locate} cannot name a structure that is not in a datapack
 * registry).
 *
 * <p>Ruins are recorded in {@link SiftLandmarks} when the generator raises them ({@link #sink}). The answer is
 * the nearest recorded ruin. Like the mod, the search then looks at the candidate chunks around the sender
 * ({@link AbandonedPortalSites}: the ruin of each 20-chunk cell can only stand in one chunk, a function of the
 * seed) that are nearer than that ruin, or all of them when none is recorded, and generates up to
 * {@value #BUDGET} of them, nearest first, so their ruins get recorded.</p>
 *
 * <p>Differences: the command answers at once, so what the generation finds is shown by asking again (the
 * mod blocks the server thread while it generates up to 32 chunks and answers once); the chunks are loaded one
 * after the other through Paper's asynchronous chunk loading. Each call spends a new budget on candidates not yet
 * tried since the server started, so repeating the command keeps widening the search. Ruins generated before
 * this registry existed are not recorded (the chunks already exist and are not generated again); they can only be
 * found once their chunk is regenerated, and the answer for such a world may therefore be a farther ruin or none.
 * Candidates outside the world border are skipped.</p>
 */
final class SiftAbandonedPortals {

    static final String LANDMARK = "abandoned_main_portal";
    /** The mod generates at most this many candidate chunks per search. */
    static final int BUDGET = 32;

    private final JavaPlugin plugin;
    private final SiftLandmarks landmarks;
    /** Candidate chunks (packed) already requested since startup, whatever came of them. */
    private final Set<Long> tried = new HashSet<>();

    SiftAbandonedPortals(JavaPlugin plugin, SiftLandmarks landmarks) {
        this.plugin = plugin;
        this.landmarks = landmarks;
    }

    /** What the generator calls for every ruin it raises (on a generation thread). */
    AbandonedPortalLandmarks sink() {
        return (x, y, z) -> landmarks.record(SiftLandmarks.Kind.ABANDONED_PORTAL, new SiftPos(x, y, z));
    }

    /**
     * The nearest recorded ruin to {@code from} (the Sift's spawn when the sender is in another world), and the
     * search for nearer ones started in the background. Main thread only.
     */
    Optional<Location> locate(ActiveDimension active, Location from) {
        World world = active.world();
        Location origin = from.getWorld() != null && from.getWorld().equals(world) ? from : world.getSpawnLocation();
        Optional<SiftPos> nearest = landmarks.nearestOther(SiftLandmarks.Kind.ABANDONED_PORTAL,
            new SiftPos(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()), Double.POSITIVE_INFINITY, null);
        double known = nearest.map(pos -> distanceSquared(pos, origin)).orElse(Double.POSITIVE_INFINITY);
        search(world, origin, known);
        return nearest.map(pos -> new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5));
    }

    private void search(World world, Location origin, double known) {
        WorldBorder border = world.getWorldBorder();
        List<AbandonedPortalSites.Candidate> chosen = AbandonedPortalSites.toGenerate(
            AbandonedPortalSites.around(world.getSeed(), origin.getX(), origin.getZ()), known, BUDGET,
            candidate -> tried.contains(key(candidate)) || !border.isInside(new Location(world, candidate.centerX(), 0,
                candidate.centerZ())));
        if (chosen.isEmpty()) {
            return;
        }
        chosen.forEach(candidate -> tried.add(key(candidate)));
        generate(world, new ArrayDeque<>(chosen));
    }

    /** Loads (and so generates) the queued chunks one at a time; stops when the plugin or the world is gone. */
    private void generate(World world, Queue<AbandonedPortalSites.Candidate> queue) {
        AbandonedPortalSites.Candidate next = queue.poll();
        if (next == null || !plugin.isEnabled()) {
            return;
        }
        try {
            world.getChunkAtAsync(next.chunkX(), next.chunkZ(), true, chunk -> generate(world, queue));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not generate chunk " + next.chunkX() + ", "
                + next.chunkZ() + " to look for an abandoned portal", failure);
        }
    }

    private static long key(AbandonedPortalSites.Candidate candidate) {
        return (long) candidate.chunkX() << 32 ^ candidate.chunkZ() & 0xFFFFFFFFL;
    }

    private static double distanceSquared(SiftPos pos, Location origin) {
        double dx = pos.x() + 0.5 - origin.getX();
        double dz = pos.z() + 0.5 - origin.getZ();
        return dx * dx + dz * dz;
    }
}
