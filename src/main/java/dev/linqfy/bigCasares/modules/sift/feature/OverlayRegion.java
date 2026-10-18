package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link WorldRegion} that reads a {@link BlockSource} and records its writes instead of
 * performing them. A feature written against {@code WorldRegion} (the main portal builder)
 * can therefore run on a snapshot away from the server thread, and the resulting
 * {@link #changes()} are applied to the live world afterwards, spread over several ticks.
 *
 * <p>Entities and containers are not supported ({@link #spawn} and {@link #fillContainer} refuse).
 * Not thread-safe: one thread builds one overlay.</p>
 */
public final class OverlayRegion implements WorldRegion {

    /** A net difference to the source. */
    public record Change(int x, int y, int z, BlockStateSpec state) {
    }

    private static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");

    private final BlockSource source;
    private final long seed;
    private final Map<Long, Change> writes = new HashMap<>();
    private final Map<Long, Integer> highestWrite = new HashMap<>();

    public OverlayRegion(BlockSource source, long seed) {
        this.source = source;
        this.seed = seed;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The writes that differ from the source, grouped by chunk and ordered from the bottom up. */
    public List<Change> changes() {
        List<Change> changes = new ArrayList<>();
        for (Change change : writes.values()) {
            if (!source.state(change.x(), change.y(), change.z()).equals(change.state())) {
                changes.add(change);
            }
        }
        changes.sort(Comparator.<Change>comparingInt(change -> change.x() >> 4).thenComparingInt(change -> change.z() >> 4)
            .thenComparingInt(Change::y).thenComparingInt(Change::x).thenComparingInt(Change::z));
        return changes;
    }

    @Override
    public int minY() {
        return source.minY();
    }

    @Override
    public int maxY() {
        return source.maxY();
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public boolean isWritable(int x, int y, int z) {
        return y >= source.minY() && y < source.maxY() && source.contains(x, z);
    }

    @Override
    public String blockId(int x, int y, int z) {
        return state(x, y, z).block().asString();
    }

    @Override
    public BlockStateSpec state(int x, int y, int z) {
        if (!isWritable(x, y, z)) {
            return AIR;
        }
        Change written = writes.isEmpty() ? null : writes.get(key(x, y, z));
        return written != null ? written.state() : source.state(x, y, z);
    }

    @Override
    public boolean set(int x, int y, int z, BlockStateSpec state) {
        if (!isWritable(x, y, z)) {
            return false;
        }
        writes.put(key(x, y, z), new Change(x, y, z, state));
        highestWrite.merge(column(x, z), y, Math::max);
        return true;
    }

    @Override
    public int height(Heightmap heightmap, int x, int z) {
        if (!source.contains(x, z)) {
            return source.minY();
        }
        Integer written = highestWrite.get(column(x, z));
        int top = Math.max(source.topY(x, z), written == null ? Integer.MIN_VALUE : written);
        for (int y = Math.min(top, source.maxY() - 1); y >= source.minY(); y--) {
            if (counts(heightmap, x, y, z)) {
                return y + 1;
            }
        }
        return source.minY();
    }

    private boolean counts(Heightmap heightmap, int x, int y, int z) {
        if (isAir(x, y, z)) {
            return false;
        }
        return switch (heightmap) {
            case WORLD_SURFACE_WG -> true;
            case OCEAN_FLOOR_WG -> isSolid(x, y, z);
            case MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> isSolid(x, y, z) || blockId(x, y, z).equals("minecraft:water")
                || blockId(x, y, z).equals("minecraft:lava");
        };
    }

    @Override
    public String biome(int x, int y, int z) {
        return "minecraft:plains";
    }

    @Override
    public boolean spawn(String entityType, double x, double y, double z) {
        return false;
    }

    @Override
    public boolean fillContainer(int x, int y, int z, List<ItemSpec> items) {
        return false;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        if (!isWritable(x, y, z)) {
            return false;
        }
        Change written = writes.isEmpty() ? null : writes.get(key(x, y, z));
        return written != null ? solidState(written.state()) : source.solid(x, y, z);
    }

    /** Solidity of a state written by a feature: air, plants, light blocks and fluids are not. */
    private static boolean solidState(BlockStateSpec state) {
        String id = state.block().asString();
        return !(id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")
            || id.equals("minecraft:light") || id.equals("minecraft:water") || id.equals("minecraft:lava")
            || SiftTerrain.PLANT_TYPES.contains(id) || id.equals("minecraft:vine"));
    }
}
