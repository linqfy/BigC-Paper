package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

/**
 * Block access for Java-side features during generation. Backed by Bukkit's
 * {@code LimitedRegion} in production (writes limited to the chunk plus a
 * buffer, like vanilla features) and by an in-memory grid in tests.
 *
 * <p>Block ids are vanilla ids; Sift blocks are expressed by their backing
 * states (see {@code SiftBlocks}), so features compare against
 * {@link #state(int, int, int)} for exact custom-block checks.</p>
 */
public interface WorldRegion {

    /** One stack for {@link #fillContainer}: a content id ({@code the_sift:charoite}) or a vanilla item id. */
    record ItemSpec(String item, int count, int slot) {
        public ItemSpec {
            if (item == null || count < 1 || count > 99 || slot < 0) {
                throw new IllegalArgumentException("Invalid item spec " + item + " x" + count + " @" + slot);
            }
        }
    }

    /** Heightmaps used by the mod's features. */
    enum Heightmap {
        WORLD_SURFACE_WG, OCEAN_FLOOR_WG, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES
    }

    int minY();

    /** World seed, for the mod's seed-derived landmark grids. */
    long seed();

    /** Exclusive top of the build range. */
    int maxY();

    /** True when the position may be read and written. */
    boolean isWritable(int x, int y, int z);

    /** Vanilla block id at the position ({@code minecraft:air} outside the region). */
    String blockId(int x, int y, int z);

    /** Full state, e.g. {@code minecraft:brown_mushroom_block[down=true,...]}. */
    BlockStateSpec state(int x, int y, int z);

    /** Writes a state; ignored outside the writable area. Returns whether it was written. */
    boolean set(int x, int y, int z, BlockStateSpec state);

    /** Y of the first free block above the surface for the heightmap ({@code minY} if none). */
    int height(Heightmap heightmap, int x, int z);

    /** Biome id at the position, e.g. {@code the_sift:overgrown_forest}. */
    String biome(int x, int y, int z);

    /** Spawns a vanilla entity during generation (e.g. sniffers in sniffer caves). */
    boolean spawn(String entityType, double x, double y, double z);

    /** Fills the container (chest, barrel) at the position. False when there is no container. */
    boolean fillContainer(int x, int y, int z, java.util.List<ItemSpec> items);

    /** True for blocks with a full solid collision shape (vine/plant supports). */
    boolean isSolid(int x, int y, int z);

    default boolean isAir(int x, int y, int z) {
        String id = blockId(x, y, z);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    default boolean is(int x, int y, int z, BlockStateSpec state) {
        return state(x, y, z).equals(state);
    }
}
