package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;

import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SiftIchorSnowFeature}: covers the generating chunk's columns in the
 * Ichor Snowy Peaks biome with snow layers. Ground gets one or two layers from two smooth value
 * noises (replacing low vegetation), willow canopies get a layer on top, and the siftslate growth
 * under a snow layer turns {@code snowy}.
 *
 * <p>The mod's Ichor snow layer is vanilla snow ({@code SiftBlocks.remap("ichor_snow", layers)}) and
 * the "can the layer survive here" check is "the support block is solid".</p>
 *
 * <p>Differences: the mod's {@code MOTION_BLOCKING_NO_LEAVES} heightmap ignores willow foliage
 * (it is a leaves block there), but the server's heightmap sees the port's foliage as a full
 * block, so the ground is found by skipping foliage below the heightmap (and the air and plants a populator's
 * stale heightmap reports, see {@link SiftTerrain#motionBlockingTop}). Plants are matched by
 * their vanilla stand-ins. The random generator argument is unused: the noise depends only on the
 * world seed and the column, like the mod's.</p>
 */
final class SiftIchorSnowFeature implements SiftFeature {

    private static final String ICHOR_SNOWY_PEAKS = "the_sift:ichor_snowy_peaks";
    private static final long SNOW_SALT = 5279142693729160783L;
    private static final long CANOPY_SALT = 2164546982323162371L;
    private static final long DETAIL_SALT = 0x534E4F5744455441L;

    private static final BlockStateSpec SNOW_ONE_LAYER = snowLayers(1);
    private static final BlockStateSpec SNOW_TWO_LAYERS = snowLayers(2);
    private static final BlockStateSpec WILLOW_FOLIAGE = SiftTerrain.OVERGROWN_WILLOW_FOLIAGE;
    private static final Set<BlockStateSpec> WILLOW_SUPPORT = Set.of(WILLOW_FOLIAGE,
        SiftBlocks.remap("overgrown_willow_log", Map.of("axis", "y")).orElseThrow(),
        SiftBlocks.remap("overgrown_willow_log", Map.of("axis", "x")).orElseThrow(),
        SiftBlocks.remap("overgrown_willow_log", Map.of("axis", "z")).orElseThrow(),
        SiftTerrain.OVERGROWN_WILLOW_WOOD);
    /** Vanilla types of the mod's low plants (stand-ins of its sprouts, stalks, chard, fronds, lotus, sunburst, whisperbloom). */
    private static final Set<String> PEAK_VEGETATION = Set.of("minecraft:fern", "minecraft:bush", "minecraft:short_grass",
        "minecraft:crimson_roots", "minecraft:short_dry_grass", "minecraft:cornflower", "minecraft:torchflower",
        "minecraft:lily_of_the_valley", "minecraft:sculk_vein", "minecraft:wildflowers", "minecraft:torchflower_crop",
        "minecraft:pitcher_crop", "minecraft:pitcher_plant");
    private static final Set<String> PITCHER = Set.of("minecraft:pitcher_crop", "minecraft:pitcher_plant");

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int minX = x & ~15;
        int minZ = z & ~15;
        long seed = region.seed();
        // The mod's getMaxY() is the inclusive top block, the port's maxY() is exclusive.
        int topY = region.maxY() - 1;
        boolean changed = false;
        for (int blockX = minX; blockX < minX + 16; blockX++) {
            for (int blockZ = minZ; blockZ < minZ + 16; blockZ++) {
                int groundY = groundY(region, blockX, blockZ);
                if (groundY < region.minY() || groundY >= topY - 2
                    || !ICHOR_SNOWY_PEAKS.equals(region.biome(blockX, groundY, blockZ))) {
                    continue;
                }
                if (isNaturalMountainSurface(region, blockX, groundY, blockZ) && !SiftTerrain.isFluid(region, blockX, groundY, blockZ)) {
                    changed |= placeSnowLayer(region, new Pos(blockX, groundY, blockZ), seed, SNOW_SALT, true);
                }
                int canopyY = SiftTerrain.motionBlockingTop(region, WorldRegion.Heightmap.MOTION_BLOCKING, blockX, blockZ);
                if (canopyY < region.minY() || canopyY >= topY - 1
                    || !ICHOR_SNOWY_PEAKS.equals(region.biome(blockX, canopyY, blockZ))
                    || !WILLOW_SUPPORT.contains(region.state(blockX, canopyY, blockZ))) {
                    continue;
                }
                changed |= placeSnowLayer(region, new Pos(blockX, canopyY, blockZ), seed, CANOPY_SALT, false);
            }
        }
        return changed;
    }

    private static BlockStateSpec snowLayers(int layers) {
        return SiftBlocks.remap("ichor_snow", Map.of("layers", Integer.toString(layers))).orElseThrow();
    }

    /** The mod's {@code getHeight(MOTION_BLOCKING_NO_LEAVES) - 1}: foliage counts as leaves and is skipped. */
    private static int groundY(WorldRegion region, int x, int z) {
        int groundY = SiftTerrain.motionBlockingTop(region, WorldRegion.Heightmap.MOTION_BLOCKING_NO_LEAVES, x, z);
        while (groundY >= region.minY() && (region.is(x, groundY, z, WILLOW_FOLIAGE)
            || !region.isSolid(x, groundY, z) && !SiftTerrain.isFluid(region, x, groundY, z))) {
            groundY--;
        }
        return groundY;
    }

    private static boolean placeSnowLayer(WorldRegion region, Pos support, long seed, long salt, boolean replaceVegetation) {
        int x = support.x();
        int z = support.z();
        Pos snowPos = support.above();
        boolean free = region.isAir(snowPos.x(), snowPos.y(), snowPos.z())
            || replaceVegetation && isReplaceablePeakVegetation(region.blockId(snowPos.x(), snowPos.y(), snowPos.z()));
        if (!free || !region.isWritable(snowPos.x(), snowPos.y(), snowPos.z())) {
            return false;
        }
        double field = valueNoise(seed ^ salt, x, z, 26);
        double detail = valueNoise(seed ^ DETAIL_SALT ^ salt, x, z, 9);
        int layers = field + detail * 0.35 > 0.88 ? 2 : 1;
        if (!region.isSolid(support.x(), support.y(), support.z())) {
            return false;
        }
        clearUpperPitcherHalf(region, snowPos);
        region.set(snowPos.x(), snowPos.y(), snowPos.z(), layers == 2 ? SNOW_TWO_LAYERS : SNOW_ONE_LAYER);
        if (region.is(support.x(), support.y(), support.z(), SiftTerrain.SIFTSLATE_GROWTH)
            && region.isWritable(support.x(), support.y(), support.z())) {
            region.set(support.x(), support.y(), support.z(), SiftTerrain.SIFTSLATE_GROWTH_SNOWY);
        }
        return true;
    }

    private static boolean isNaturalMountainSurface(WorldRegion region, int x, int y, int z) {
        return SiftTerrain.isTerrain(region, x, y, z) || region.blockId(x, y, z).equals("minecraft:sculk");
    }

    private static boolean isReplaceablePeakVegetation(String blockId) {
        return PEAK_VEGETATION.contains(blockId);
    }

    /** Snow replacing the lower half of a pitcher plant must not leave its upper half floating. */
    private static void clearUpperPitcherHalf(WorldRegion region, Pos lowerPos) {
        if (!PITCHER.contains(region.blockId(lowerPos.x(), lowerPos.y(), lowerPos.z()))) {
            return;
        }
        Pos upperPos = lowerPos.above();
        if (PITCHER.contains(region.blockId(upperPos.x(), upperPos.y(), upperPos.z()))
            && region.isWritable(upperPos.x(), upperPos.y(), upperPos.z())) {
            region.set(upperPos.x(), upperPos.y(), upperPos.z(), SiftTerrain.AIR);
        }
    }

    private static double valueNoise(long seed, int x, int z, int scale) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double localX = (double) Math.floorMod(x, scale) / (double) scale;
        double localZ = (double) Math.floorMod(z, scale) / (double) scale;
        double smoothX = localX * localX * (3.0 - 2.0 * localX);
        double smoothZ = localZ * localZ * (3.0 - 2.0 * localZ);
        double a = unit(seed, cellX, cellZ);
        double b = unit(seed, cellX + 1, cellZ);
        double c = unit(seed, cellX, cellZ + 1);
        double d = unit(seed, cellX + 1, cellZ + 1);
        return lerp(smoothZ, lerp(smoothX, a, b), lerp(smoothX, c, d));
    }

    private static double unit(long seed, int x, int z) {
        long value = seed ^ (long) x * -7046029254386353131L ^ (long) z * -4417276706812531889L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (double) (value >>> 11) * (double) 1.110223E-16F;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
