package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.feature.BlockSource;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BlockSource} over chunk snapshots taken on the server thread, so that the main portal can be
 * planned on an asynchronous thread. Columns of chunks that were not snapshotted are outside the area.
 */
final class ChunkSnapshotSource implements BlockSource {

    private final Map<Long, ChunkSnapshot> chunks;
    private final int minY;
    private final int maxY;
    private final Map<BlockData, BlockStateSpec> specs = new ConcurrentHashMap<>();
    private final Map<Long, int[]> tops = new ConcurrentHashMap<>();

    ChunkSnapshotSource(Map<Long, ChunkSnapshot> chunks, int minY, int maxY) {
        this.chunks = chunks;
        this.minY = minY;
        this.maxY = maxY;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private ChunkSnapshot chunk(int x, int z) {
        return chunks.get(chunkKey(x >> 4, z >> 4));
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public boolean contains(int x, int z) {
        return chunk(x, z) != null;
    }

    @Override
    public BlockStateSpec state(int x, int y, int z) {
        ChunkSnapshot chunk = chunk(x, z);
        if (chunk == null || y < minY || y >= maxY) {
            return BlockStateSpec.of("minecraft:air");
        }
        BlockData data = chunk.getBlockData(x & 15, y, z & 15);
        return specs.computeIfAbsent(data, ignored -> BlockStateSpec.parse(data.getAsString()));
    }

    @Override
    public boolean solid(int x, int y, int z) {
        ChunkSnapshot chunk = chunk(x, z);
        if (chunk == null || y < minY || y >= maxY) {
            return false;
        }
        Material type = chunk.getBlockType(x & 15, y, z & 15);
        return type.isSolid() && type.isOccluding();
    }

    @Override
    public int topY(int x, int z) {
        ChunkSnapshot chunk = chunk(x, z);
        if (chunk == null) {
            return minY - 1;
        }
        int[] column = tops.computeIfAbsent(chunkKey(x >> 4, z >> 4), ignored -> {
            int[] heights = new int[256];
            java.util.Arrays.fill(heights, Integer.MIN_VALUE);
            return heights;
        });
        int index = (z & 15) << 4 | (x & 15);
        if (column[index] == Integer.MIN_VALUE) {
            int y = maxY - 1;
            while (y >= minY && chunk.getBlockType(x & 15, y, z & 15).isAir()) {
                y--;
            }
            column[index] = y;
        }
        return column[index];
    }
}
