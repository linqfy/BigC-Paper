package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * In-memory {@link WorldRegion} shaped like Paper's populator region: the
 * generating chunk plus a one-chunk buffer ring is writable, everything else
 * reads as air and rejects writes (and is counted, so tests can assert that a
 * feature never tried to write outside its region).
 */
final class InMemoryRegion implements WorldRegion {

    record Spawn(String type, double x, double y, double z, String marker) {
        Spawn(String type, double x, double y, double z) {
            this(type, x, y, z, null);
        }
    }

    private static final Set<String> NON_SOLID = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:water", "minecraft:lava", "minecraft:snow", "minecraft:vine", "minecraft:light", "minecraft:fern",
        "minecraft:bush", "minecraft:short_grass", "minecraft:crimson_roots", "minecraft:short_dry_grass",
        "minecraft:cornflower", "minecraft:torchflower", "minecraft:lily_of_the_valley", "minecraft:blue_orchid",
        "minecraft:wildflowers", "minecraft:hanging_roots", "minecraft:weeping_vines_plant", "minecraft:mangrove_propagule");
    private static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;
    private final long seed;
    private final Map<Long, BlockStateSpec> blocks = new HashMap<>();
    private final List<Spawn> spawns = new ArrayList<>();
    private final Map<String, List<ItemSpec>> containers = new TreeMap<>();
    private Function<int[], String> biomes = position -> "the_sift:overgrown_forest";
    private int rejectedWrites;
    private int staleHeight;

    InMemoryRegion(int chunkX, int chunkZ, int minY, int maxY, long seed) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.maxY = maxY;
        this.seed = seed;
    }

    /** Flat Sift ground: siftslate up to {@code surfaceY - 1} with a growth top layer. */
    static InMemoryRegion flat(int chunkX, int chunkZ, int surfaceY, long seed) {
        InMemoryRegion region = new InMemoryRegion(chunkX, chunkZ, 0, 256, seed);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int y = 0; y < surfaceY - 1; y++) {
                    region.blocks.put(key(x, y, z), SiftTerrain.SIFTSLATE);
                }
                region.blocks.put(key(x, surfaceY - 1, z), SiftTerrain.SIFTSLATE_GROWTH);
            }
        }
        return region;
    }

    InMemoryRegion biomes(Function<int[], String> biomes) {
        this.biomes = biomes;
        return this;
    }

    int minX() {
        return (chunkX - 1) << 4;
    }

    int maxX() {
        return ((chunkX + 2) << 4) - 1;
    }

    int minZ() {
        return (chunkZ - 1) << 4;
    }

    int maxZ() {
        return ((chunkZ + 2) << 4) - 1;
    }

    int rejectedWrites() {
        return rejectedWrites;
    }

    List<Spawn> spawns() {
        return spawns;
    }

    Map<String, List<ItemSpec>> containers() {
        return containers;
    }

    /** Stable snapshot for determinism checks. */
    Map<String, String> snapshot() {
        Map<String, String> out = new TreeMap<>();
        blocks.forEach((key, state) -> out.put(Long.toString(key), state.asString()));
        return out;
    }

    /** Counts blocks whose state differs between two regions of the same size. */
    int countDifferences(InMemoryRegion other) {
        int differences = 0;
        for (int x = minX(); x <= maxX(); x++) {
            for (int z = minZ(); z <= maxZ(); z++) {
                for (int y = minY; y < maxY; y++) {
                    if (!state(x, y, z).equals(other.state(x, y, z))) {
                        differences++;
                    }
                }
            }
        }
        return differences;
    }

    void put(int x, int y, int z, BlockStateSpec state) {
        blocks.put(key(x, y, z), state);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
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
    public long seed() {
        return seed;
    }

    @Override
    public boolean isWritable(int x, int y, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ() && y >= minY && y < maxY;
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
        return blocks.getOrDefault(key(x, y, z), AIR);
    }

    @Override
    public boolean set(int x, int y, int z, BlockStateSpec state) {
        if (!isWritable(x, y, z)) {
            rejectedWrites++;
            return false;
        }
        if (state.block().asString().equals("minecraft:air")) {
            blocks.remove(key(x, y, z));
        } else {
            blocks.put(key(x, y, z), state);
        }
        return true;
    }

    /**
     * Makes the motion-blocking heightmaps report {@code blocks} more than the terrain, like the heightmaps a Paper
     * populator sees: primed before decoration and not updated by the writes of the decoration step.
     */
    InMemoryRegion withStaleMotionHeight(int blocks) {
        staleHeight = blocks;
        return this;
    }

    @Override
    public int height(Heightmap heightmap, int x, int z) {
        int height = liveHeight(heightmap, x, z);
        boolean motion = heightmap == Heightmap.MOTION_BLOCKING || heightmap == Heightmap.MOTION_BLOCKING_NO_LEAVES;
        return motion && height > minY ? Math.min(maxY, height + staleHeight) : height;
    }

    private int liveHeight(Heightmap heightmap, int x, int z) {
        if (!isWritable(x, minY, z)) {
            return minY;
        }
        for (int y = maxY - 1; y >= minY; y--) {
            String id = blockId(x, y, z);
            boolean counts = switch (heightmap) {
                case WORLD_SURFACE_WG -> !isAir(x, y, z);
                case OCEAN_FLOOR_WG -> isSolid(x, y, z);
                case MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> isSolid(x, y, z) || id.equals("minecraft:water")
                    || id.equals("minecraft:lava");
            };
            if (counts) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public String biome(int x, int y, int z) {
        return biomes.apply(new int[]{x, y, z});
    }

    @Override
    public boolean spawn(String entityType, double x, double y, double z) {
        if (!isWritable((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return false;
        }
        spawns.add(new Spawn(entityType, x, y, z));
        return true;
    }

    @Override
    public boolean spawn(String entityType, double x, double y, double z, String marker) {
        if (!isWritable((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return false;
        }
        spawns.add(new Spawn(entityType, x, y, z, marker));
        return true;
    }

    @Override
    public boolean fillContainer(int x, int y, int z, List<ItemSpec> items) {
        String id = blockId(x, y, z);
        if (!id.equals("minecraft:chest") && !id.equals("minecraft:barrel") && !id.equals("minecraft:trapped_chest")) {
            return false;
        }
        containers.put(x + "," + y + "," + z, List.copyOf(items));
        return true;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return !NON_SOLID.contains(blockId(x, y, z));
    }
}
