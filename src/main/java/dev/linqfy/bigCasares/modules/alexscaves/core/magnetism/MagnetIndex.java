package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Neodymium blocks (scarlet attract, azure repel) per loaded chunk, so the
 * magnetism tick looks up nearby magnets without scanning blocks every tick.
 */
public final class MagnetIndex {
    private final Map<ChunkKey, List<Magnet>> chunks = new ConcurrentHashMap<>();

    public void putChunk(UUID world, int chunkX, int chunkZ, List<Magnet> magnets) {
        ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
        if (magnets.isEmpty()) {
            chunks.remove(key);
        } else {
            chunks.put(key, List.copyOf(magnets));
        }
    }

    public void removeChunk(UUID world, int chunkX, int chunkZ) {
        chunks.remove(new ChunkKey(world, chunkX, chunkZ));
    }

    public void add(UUID world, Magnet magnet) {
        chunks.compute(new ChunkKey(world, magnet.x() >> 4, magnet.z() >> 4), (key, list) -> {
            List<Magnet> updated = list == null ? new ArrayList<>() : new ArrayList<>(list);
            updated.removeIf(existing -> existing.samePosition(magnet));
            updated.add(magnet);
            return List.copyOf(updated);
        });
    }

    public void remove(UUID world, int x, int y, int z) {
        chunks.computeIfPresent(new ChunkKey(world, x >> 4, z >> 4), (key, list) -> {
            List<Magnet> updated = new ArrayList<>(list);
            updated.removeIf(existing -> existing.x() == x && existing.y() == y && existing.z() == z);
            return updated.isEmpty() ? null : List.copyOf(updated);
        });
    }

    /** Magnets within {@code radius} blocks of a point. */
    public List<Magnet> near(UUID world, double x, double y, double z, double radius) {
        List<Magnet> found = new ArrayList<>();
        int minChunkX = (int) Math.floor(x - radius) >> 4;
        int maxChunkX = (int) Math.floor(x + radius) >> 4;
        int minChunkZ = (int) Math.floor(z - radius) >> 4;
        int maxChunkZ = (int) Math.floor(z + radius) >> 4;
        double radiusSquared = radius * radius;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (Magnet magnet : chunks.getOrDefault(new ChunkKey(world, cx, cz), List.of())) {
                    double dx = magnet.x() + 0.5 - x;
                    double dy = magnet.y() + 0.5 - y;
                    double dz = magnet.z() + 0.5 - z;
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        found.add(magnet);
                    }
                }
            }
        }
        return found;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public void clear() {
        chunks.clear();
    }

    public record Magnet(int x, int y, int z, Polarity polarity) {
        boolean samePosition(Magnet other) {
            return x == other.x && y == other.y && z == other.z;
        }
    }

    private record ChunkKey(UUID world, int x, int z) {
    }
}
