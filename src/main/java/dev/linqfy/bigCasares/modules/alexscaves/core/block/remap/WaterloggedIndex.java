package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module blocks that stand in water, by position, for the loaded chunks. The server keeps them dry (no state is spent
 * on water): each chunk saves its positions in its persistent data ({@link #pack}, an {@code int[]}) and this index
 * mirrors the loaded ones for the packet listener, which sends those positions with the wet client state.
 * <p>
 * Written on the main thread, read from Netty threads: each chunk's positions are an immutable sorted array replaced
 * on change. Chunk packets only carry x/z, so {@link #mayContain} is over every world, like {@link RemapChunkIndex}.
 */
public final class WaterloggedIndex {
    private static final int[] NONE = new int[0];

    private final Map<UUID, Map<Long, int[]>> byWorld = new ConcurrentHashMap<>();
    private final Map<Long, Integer> worldsAt = new ConcurrentHashMap<>();

    /** A block position inside its chunk: {@code y << 8 | z << 4 | x} (local x/z, world y, may be negative). */
    public static int pack(int blockX, int y, int blockZ) {
        return y << 8 | (blockZ & 15) << 4 | blockX & 15;
    }

    public static int localX(int packed) {
        return packed & 15;
    }

    public static int localZ(int packed) {
        return packed >> 4 & 15;
    }

    public static int y(int packed) {
        return packed >> 8;
    }

    /** Sorted, without repeats: the saved form of a chunk's positions. */
    public static int[] normalize(int[] packed) {
        if (packed == null || packed.length == 0) {
            return NONE;
        }
        return Arrays.stream(packed).distinct().sorted().toArray();
    }

    /** Replaces a chunk's positions (chunk load). */
    public void load(UUID world, int chunkX, int chunkZ, int[] packed) {
        put(world, RemapChunkIndex.key(chunkX, chunkZ), normalize(packed));
    }

    public void unload(UUID world, int chunkX, int chunkZ) {
        put(world, RemapChunkIndex.key(chunkX, chunkZ), NONE);
    }

    public void clearWorld(UUID world) {
        Map<Long, int[]> chunks = byWorld.remove(world);
        if (chunks != null) {
            chunks.keySet().forEach(this::release);
        }
    }

    public void clear() {
        byWorld.clear();
        worldsAt.clear();
    }

    /**
     * Marks a block (world coordinates).
     *
     * @return the chunk's new positions to save, or {@code null} when it was already marked
     */
    public int[] add(UUID world, int blockX, int y, int blockZ) {
        long key = RemapChunkIndex.key(blockX >> 4, blockZ >> 4);
        int[] old = positions(world, key);
        int packed = pack(blockX, y, blockZ);
        if (Arrays.binarySearch(old, packed) >= 0) {
            return null;
        }
        int[] updated = Arrays.copyOf(old, old.length + 1);
        updated[old.length] = packed;
        Arrays.sort(updated);
        put(world, key, updated);
        return updated;
    }

    /** @return the chunk's new positions to save, or {@code null} when the block was not marked */
    public int[] remove(UUID world, int blockX, int y, int blockZ) {
        long key = RemapChunkIndex.key(blockX >> 4, blockZ >> 4);
        int[] old = positions(world, key);
        int at = Arrays.binarySearch(old, pack(blockX, y, blockZ));
        if (at < 0) {
            return null;
        }
        int[] updated = new int[old.length - 1];
        System.arraycopy(old, 0, updated, 0, at);
        System.arraycopy(old, at + 1, updated, at, updated.length - at);
        put(world, key, updated);
        return updated;
    }

    public boolean contains(UUID world, int blockX, int y, int blockZ) {
        if (world == null) {
            return false;
        }
        return Arrays.binarySearch(positions(world, RemapChunkIndex.key(blockX >> 4, blockZ >> 4)),
            pack(blockX, y, blockZ)) >= 0;
    }

    /** A chunk's positions (packed, sorted; empty when none). Never modify the array. */
    public int[] positions(UUID world, int chunkX, int chunkZ) {
        return world == null ? NONE : positions(world, RemapChunkIndex.key(chunkX, chunkZ));
    }

    /** Whether a chunk at this position, in any world, has a marked block. */
    public boolean mayContain(int chunkX, int chunkZ) {
        return worldsAt.containsKey(RemapChunkIndex.key(chunkX, chunkZ));
    }

    /** Marked blocks over every loaded chunk. */
    public int size() {
        int total = 0;
        for (Map<Long, int[]> chunks : byWorld.values()) {
            for (int[] positions : chunks.values()) {
                total += positions.length;
            }
        }
        return total;
    }

    private int[] positions(UUID world, long key) {
        Map<Long, int[]> chunks = byWorld.get(world);
        int[] positions = chunks == null ? null : chunks.get(key);
        return positions == null ? NONE : positions;
    }

    private void put(UUID world, long key, int[] positions) {
        if (positions.length == 0) {
            Map<Long, int[]> chunks = byWorld.get(world);
            if (chunks != null && chunks.remove(key) != null) {
                release(key);
            }
            return;
        }
        if (byWorld.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>()).put(key, positions) == null) {
            worldsAt.merge(key, 1, Integer::sum);
        }
    }

    private void release(long key) {
        worldsAt.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }
}
