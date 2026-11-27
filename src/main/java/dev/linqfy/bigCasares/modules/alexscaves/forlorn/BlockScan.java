package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds the blocks the mobs look for (light sources, moth balls) without walking the whole search range block by block:
 * each chunk is scanned once and the positions it holds are kept for a while; callers check a block again before they
 * act on it. The mod's point of interest and {@code MoveToBlockGoal} searches do the same job in vanilla.
 */
final class BlockScan {
    /** The kinds of block that are looked for. */
    enum Kind { UNDERZEALOT_LIGHT, GLOOMOTH_LIGHT, MOTH_BALL }

    /** @param value the matcher's value for the block (the number of balls of a moth ball) */
    record Found(int x, int y, int z, int value) {
        double distanceSquared(Location from) {
            double dx = x + 0.5 - from.getX();
            double dy = y + 0.5 - from.getY();
            double dz = z + 0.5 - from.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record Matcher(Set<Material> materials, Map<BlockData, Integer> modBlocks) {
    }

    private record Key(java.util.UUID world, int x, int z, Kind kind) {
    }

    private record Scanned(long scannedAt, List<Found> found) {
    }

    /** Ticks a scanned chunk is trusted for. */
    static final long TTL = 200;
    /** Chunks scanned in one server tick at most; the rest answer from older results. */
    private static final int SCANS_PER_TICK = 2;

    private static final Map<Kind, Matcher> MATCHERS = new EnumMap<>(Kind.class);
    /** Chunks scanned for the first time in one server tick at most. */
    private static final int FIRST_SCANS_PER_TICK = 6;
    private static final int MAX_ENTRIES = 1024;
    /** Least recently used out first, one at a time (a full clear would make every mob scan at once). */
    private static final Map<Key, Scanned> CACHE = new java.util.LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Scanned> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private static boolean incomplete;
    private static int scanTick = -1;
    private static int scans;
    private static int firstScans;

    private BlockScan() {
    }

    static void configure(Kind kind, Set<Material> materials, Map<BlockData, Integer> modBlocks) {
        MATCHERS.put(kind, new Matcher(Set.copyOf(materials), Map.copyOf(modBlocks)));
        CACHE.clear();
    }

    static void clear() {
        CACHE.clear();
    }

    /** Whether the last search skipped chunks it had no time to scan (so an empty answer may be wrong: ask again soon). */
    static boolean lastSearchIncomplete() {
        return incomplete;
    }

    /** A block changed here: the chunk is scanned again the next time it is asked about. */
    static void touched(World world, int x, int z) {
        for (Kind kind : Kind.values()) {
            CACHE.remove(new Key(world.getUID(), x >> 4, z >> 4, kind));
        }
    }

    /** Whether a block of this material may be one the scans look for (what is worth invalidating a chunk for). */
    static boolean relevant(Material type) {
        if (type == Material.NOTE_BLOCK) {
            return true;
        }
        for (Matcher matcher : MATCHERS.values()) {
            if (matcher.materials().contains(type)) {
                return true;
            }
        }
        return false;
    }

    /** The value of the block at the position for the kind (0 when it is not one): what a caller checks before using a cached result. */
    static int valueAt(World world, int x, int y, int z, Kind kind) {
        Matcher matcher = MATCHERS.get(kind);
        if (matcher == null || y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return 0;
        }
        var block = world.getBlockAt(x, y, z);
        return valueOf(matcher, block.getType(), block.getBlockData());
    }

    /**
     * The closest block of the kind within {@code range} blocks sideways and {@code vertical} up or down, among those
     * {@code valid} accepts, or null.
     */
    static Found nearest(Location origin, int range, int vertical, Kind kind, Predicate<Found> valid) {
        World world = origin.getWorld();
        if (world == null || !MATCHERS.containsKey(kind)) {
            return null;
        }
        long now = Bukkit.getCurrentTick();
        incomplete = false;
        Found best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int cx = (origin.getBlockX() - range) >> 4; cx <= (origin.getBlockX() + range) >> 4; cx++) {
            for (int cz = (origin.getBlockZ() - range) >> 4; cz <= (origin.getBlockZ() + range) >> 4; cz++) {
                List<Found> inChunk = chunk(world, cx, cz, kind, now);
                if (inChunk == null) {
                    incomplete = true;
                    continue;
                }
                for (Found found : inChunk) {
                    if (Math.abs(found.x() + 0.5 - origin.getX()) > range || Math.abs(found.z() + 0.5 - origin.getZ()) > range
                        || Math.abs(found.y() + 0.5 - origin.getY()) > vertical) {
                        continue;
                    }
                    double distance = found.distanceSquared(origin);
                    if (distance < bestDistance && valueAt(world, found.x(), found.y(), found.z(), kind) > 0 && valid.test(found)) {
                        best = found;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Every block of the kind within {@code range} (a sphere) of the origin that still is one, with its current value. */
    static List<Found> within(Location origin, int range, Kind kind) {
        List<Found> result = new ArrayList<>();
        World world = origin.getWorld();
        if (world == null || !MATCHERS.containsKey(kind)) {
            return result;
        }
        long now = Bukkit.getCurrentTick();
        for (int cx = (origin.getBlockX() - range) >> 4; cx <= (origin.getBlockX() + range) >> 4; cx++) {
            for (int cz = (origin.getBlockZ() - range) >> 4; cz <= (origin.getBlockZ() + range) >> 4; cz++) {
                List<Found> inChunk = chunk(world, cx, cz, kind, now);
                if (inChunk == null) {
                    continue;
                }
                for (Found found : inChunk) {
                    if (found.distanceSquared(origin) <= (double) range * range) {
                        int value = valueAt(world, found.x(), found.y(), found.z(), kind);
                        if (value > 0) {
                            result.add(new Found(found.x(), found.y(), found.z(), value));
                        }
                    }
                }
            }
        }
        return result;
    }

    /** The chunk's blocks of the kind: from the cache, scanned now, or null when it has not been scanned and there is no time. */
    private static List<Found> chunk(World world, int cx, int cz, Kind kind, long now) {
        if (!world.isChunkLoaded(cx, cz)) {
            return List.of();
        }
        Key key = new Key(world.getUID(), cx, cz, kind);
        Scanned cached = CACHE.get(key);
        if (cached != null && now - cached.scannedAt() < TTL) {
            return cached.found();
        }
        int tick = Bukkit.getCurrentTick();
        if (tick != scanTick) {
            scanTick = tick;
            scans = 0;
            firstScans = 0;
        }
        // Old results are only refreshed within the budget; a chunk never scanned has a bigger one.
        if (cached != null ? scans >= SCANS_PER_TICK : firstScans >= FIRST_SCANS_PER_TICK) {
            return cached == null ? null : cached.found();
        }
        if (cached != null) {
            scans++;
        } else {
            firstScans++;
        }
        Scanned scanned = new Scanned(now, scan(world.getChunkAt(cx, cz, false), kind));
        CACHE.put(key, scanned);
        return scanned.found();
    }

    private static List<Found> scan(Chunk chunk, Kind kind) {
        Matcher matcher = MATCHERS.get(kind);
        World world = chunk.getWorld();
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        boolean modded = false;
        for (BlockData data : matcher.modBlocks().keySet()) {
            if (snapshot.contains(data)) {
                modded = true;
                break;
            }
        }
        List<Found> found = new ArrayList<>();
        int minSection = world.getMinHeight() >> 4;
        int maxSection = (world.getMaxHeight() - 1) >> 4;
        for (int section = minSection; section <= maxSection; section++) {
            if (snapshot.isSectionEmpty(section - minSection)) {
                continue;
            }
            for (int y = section << 4; y < (section << 4) + 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Material type = snapshot.getBlockType(x, y, z);
                        int value = 0;
                        if (type != Material.NOTE_BLOCK && matcher.materials().contains(type)) {
                            value = 1;
                        } else if (modded && type == Material.NOTE_BLOCK) {
                            value = matcher.modBlocks().getOrDefault(snapshot.getBlockData(x, y, z), 0);
                        }
                        if (value > 0) {
                            found.add(new Found((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z, value));
                        }
                    }
                }
            }
        }
        return found;
    }

    private static int valueOf(Matcher matcher, Material type, BlockData data) {
        if (type == Material.NOTE_BLOCK) {
            return matcher.modBlocks().getOrDefault(data, 0);
        }
        return matcher.materials().contains(type) ? 1 : 0;
    }
}
