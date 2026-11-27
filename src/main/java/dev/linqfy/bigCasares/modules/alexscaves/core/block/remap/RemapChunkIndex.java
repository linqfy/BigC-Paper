package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loaded chunks that may hold a remapped state. Written on the main thread (chunk load scan, unload) and from Netty
 * threads (a block change of a remapped state marks its chunk), read from Netty threads for every chunk packet: a
 * chunk packet whose position is not here is sent without being decoded.
 * <p>
 * Chunk packets only carry x/z, so lookups are by position over every world ({@link #mayContain}); a chunk of another
 * world at the same position only costs one useless decode. A chunk stays marked until it unloads, even if its last
 * remapped block is broken (the next load scans it again).
 */
public final class RemapChunkIndex {
    private final Map<UUID, Set<Long>> byWorld = new ConcurrentHashMap<>();
    private final Map<Long, Integer> worldsAt = new ConcurrentHashMap<>();

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** @return whether the chunk was not marked yet */
    public boolean mark(UUID world, int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        if (!byWorld.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(key)) {
            return false;
        }
        worldsAt.merge(key, 1, Integer::sum);
        return true;
    }

    public void unmark(UUID world, int chunkX, int chunkZ) {
        Set<Long> chunks = byWorld.get(world);
        long key = key(chunkX, chunkZ);
        if (chunks != null && chunks.remove(key)) {
            release(key);
        }
    }

    public void clearWorld(UUID world) {
        Set<Long> chunks = byWorld.remove(world);
        if (chunks != null) {
            chunks.forEach(this::release);
        }
    }

    public void clear() {
        byWorld.clear();
        worldsAt.clear();
    }

    /** Whether a chunk at this position, in any world, may hold a remapped state. */
    public boolean mayContain(int chunkX, int chunkZ) {
        return worldsAt.containsKey(key(chunkX, chunkZ));
    }

    public boolean contains(UUID world, int chunkX, int chunkZ) {
        Set<Long> chunks = byWorld.get(world);
        return chunks != null && chunks.contains(key(chunkX, chunkZ));
    }

    public int size() {
        int total = 0;
        for (Set<Long> chunks : byWorld.values()) {
            total += chunks.size();
        }
        return total;
    }

    private void release(long key) {
        worldsAt.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }
}
