package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;

import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Shared block sets and helpers of the mod's features (the static helpers of
 * {@code SiftMonolithFeature}, {@code SiftFlowerPatchFeature} and
 * {@code TheSiftDimension}), expressed with the port's backing states.
 *
 * <p>Mod plants become single-block vanilla stand-ins (see {@code SiftBlocks}); the
 * mod's {@code canSurvive} checks become "the block below is solid", which holds
 * for every Sift soil the features plant on.</p>
 */
final class SiftTerrain {

    static final BlockStateSpec SIFTSLATE = block("siftslate");
    static final BlockStateSpec COBBLED_SIFTSLATE = block("cobbled_siftslate");
    static final BlockStateSpec REINFORCED_SIFTSLATE = block("reinforced_siftslate");
    static final BlockStateSpec SIFT_RUBBLE = block("sift_rubble");
    static final BlockStateSpec SIFTSLATE_GROWTH = block("siftslate_growth");
    static final BlockStateSpec SIFTSLATE_GROWTH_SNOWY = SiftBlocks.remap("siftslate_growth", Map.of("snowy", "true")).orElseThrow();
    static final BlockStateSpec HEALTHY_SCULK = block("healthy_sculk");
    static final BlockStateSpec DRY_HEALTHY_SCULK = block("dry_healthy_sculk");
    static final BlockStateSpec DRY_HEALTHY_SCULK_GROWTH = block("dry_healthy_sculk_growth");
    static final BlockStateSpec SOUL_BLOCK = block("soul_block");
    static final BlockStateSpec ICHOR_SNOW_BLOCK = block("ichor_snow_block");
    static final BlockStateSpec SONOROUS_DEEPSLATE = SiftBlocks.remap("sonorous_deepslate", Map.of("mode", "horn")).orElseThrow();
    static final BlockStateSpec OVERGROWN_WILLOW_LOG = block("overgrown_willow_log");
    static final BlockStateSpec OVERGROWN_WILLOW_WOOD = block("overgrown_willow_wood");
    static final BlockStateSpec OVERGROWN_WILLOW_FOLIAGE = block("overgrown_willow_foliage");

    /** Ichor is rendered and simulated as water (vanilla clients have no new fluids). */
    static final BlockStateSpec ICHOR = block("ichor");
    static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");
    static final BlockStateSpec LAVA = BlockStateSpec.of("minecraft:lava", Map.of("level", "0"));

    static final Set<BlockStateSpec> TERRAIN = Set.of(SIFTSLATE, SIFTSLATE_GROWTH, SIFTSLATE_GROWTH_SNOWY, HEALTHY_SCULK,
        DRY_HEALTHY_SCULK, DRY_HEALTHY_SCULK_GROWTH);
    /** {@code SiftFlowerPatchFeature.isFlowerSoil}. */
    static final Set<BlockStateSpec> FLOWER_SOIL = Set.of(SIFTSLATE, SIFTSLATE_GROWTH, SIFTSLATE_GROWTH_SNOWY,
        DRY_HEALTHY_SCULK, HEALTHY_SCULK);
    /** Vanilla types of the plant stand-ins the mod's {@code canReplace} accepts. */
    static final Set<String> PLANT_TYPES = Set.of("minecraft:fern", "minecraft:bush", "minecraft:short_grass",
        "minecraft:crimson_roots", "minecraft:short_dry_grass");
    private static final Set<String> OVERGROWN_BIOMES = Set.of("the_sift:overgrown_clearing", "the_sift:overgrown_forest",
        "the_sift:overgrown_forest_slopes", "the_sift:overgrown_slopes", "the_sift:overgrown_peaks");
    private static final String[] WILDFLOWER_FACING = {"south", "west", "north", "east"};

    private SiftTerrain() {
    }

    /** Backing state (custom block or stand-in) of a mod block path. */
    static BlockStateSpec block(String path) {
        return SiftBlocks.remap(path, Map.of()).orElseThrow(() -> new IllegalArgumentException("Unknown Sift block " + path));
    }

    /** {@code SiftMonolithFeature.isSiftTerrain}. */
    static boolean isTerrain(WorldRegion region, int x, int y, int z) {
        return TERRAIN.contains(region.state(x, y, z));
    }

    static boolean isGrowth(BlockStateSpec state) {
        return state.equals(SIFTSLATE_GROWTH) || state.equals(SIFTSLATE_GROWTH_SNOWY);
    }

    /** {@code SiftMonolithFeature.canReplace}: air, Sift terrain or a Sift plant. */
    static boolean canReplace(WorldRegion region, int x, int y, int z) {
        return region.isAir(x, y, z) || isTerrain(region, x, y, z) || PLANT_TYPES.contains(region.blockId(x, y, z));
    }

    /** How far below a stale heightmap the ground is looked for. */
    static final int STALE_HEIGHT_SEARCH = 16;

    /**
     * The top motion-blocking block of a column. A Paper populator reads heightmaps primed before decoration
     * that the decoration's own writes do not update, so the reported top can be air or a plant that earlier Sift
     * features left there; the top is the first solid block or fluid at or below it.
     */
    static int motionBlockingTop(WorldRegion region, WorldRegion.Heightmap heightmap, int x, int z) {
        int y = region.height(heightmap, x, z) - 1;
        int lowest = Math.max(region.minY(), y - STALE_HEIGHT_SEARCH);
        while (y > lowest && !region.isSolid(x, y, z) && !isFluid(region, x, y, z)) {
            y--;
        }
        return y;
    }

    static boolean isFluid(WorldRegion region, int x, int y, int z) {
        String id = region.blockId(x, y, z);
        return id.equals("minecraft:water") || id.equals("minecraft:lava") || id.equals("minecraft:bubble_column")
            || "true".equals(region.state(x, y, z).properties().get("waterlogged"));
    }

    /** {@code SiftMonolithFeature.isDrySupportedSurface}. */
    static boolean isDrySupportedSurface(WorldRegion region, int x, int surfaceY, int z, int depth) {
        if (isFluid(region, x, surfaceY, z)) {
            return false;
        }
        for (int offset = 1; offset <= depth; offset++) {
            if (!isTerrain(region, x, surfaceY - offset, z)) {
                return false;
            }
        }
        return true;
    }

    /** {@code SiftMonolithFeature.findTerrainSurface}: first free Y above Sift terrain, or -1. */
    static int findTerrainSurface(WorldRegion region, int x, int z) {
        int y = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z);
        for (int step = 0; step < 8 && y > region.minY(); step++, y--) {
            if (isTerrain(region, x, y - 1, z)) {
                return y;
            }
        }
        return -1;
    }

    /** {@code TheSiftDimension.isOvergrownBiome}. */
    static boolean isOvergrownBiome(String biome) {
        return OVERGROWN_BIOMES.contains(biome);
    }

    static boolean isFlowerSoil(BlockStateSpec floor) {
        return FLOWER_SOIL.contains(floor);
    }

    /** {@code SiftFlowerPatchFeature.isLakeFlowerSoil}. */
    static boolean isLakeFlowerSoil(BlockStateSpec floor) {
        return isFlowerSoil(floor) || floor.equals(DRY_HEALTHY_SCULK_GROWTH);
    }

    /** {@code SiftFlowerPatchFeature.wildflowersState}: random facing and 1-4 flowers. */
    static BlockStateSpec wildflowers(Random random) {
        String facing = WILDFLOWER_FACING[random.nextInt(4)];
        return BlockStateSpec.of("minecraft:wildflowers", Map.of("facing", facing,
            "flower_amount", Integer.toString(1 + random.nextInt(4))));
    }

    /**
     * Places a mod plant (by mod path) on solid ground if the spot is free and
     * writable; the stand-in for the mod's {@code canSurvive} + {@code setBlock}.
     */
    static boolean placePlant(WorldRegion region, int x, int y, int z, String plantPath) {
        return placePlant(region, x, y, z, block(plantPath));
    }

    static boolean placePlant(WorldRegion region, int x, int y, int z, BlockStateSpec plant) {
        if (!region.isWritable(x, y, z) || !region.isAir(x, y, z) || !region.isSolid(x, y - 1, z)) {
            return false;
        }
        return region.set(x, y, z, plant);
    }

    /** The mod's position hash ({@code SiftMonolithFeature.hash01}), uniform in [0, 1). */
    static double hash01(int x, int y, int z, long salt) {
        long value = salt;
        value ^= (long) x * -7046029254386353131L;
        value ^= (long) y * -4417276706812531889L;
        value ^= (long) z * 1609587929392839161L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (double) (value >>> 11) * (double) 1.110223E-16F;
    }
}
