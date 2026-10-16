package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.Map;
import java.util.Random;

/**
 * Port of the mod's {@code SiftSnifferPlantPlacement}: the two plants of the sniffer
 * patches and caves, a torchflower or a two-block pitcher-plant bulb (age 3 or 4).
 * Each patch or cave picks one type for all its plants.
 *
 * <p>Both are vanilla blocks in the mod too, which only lets them survive on Sift soil
 * through its {@code VegetationBlockMixin} and {@code PitcherCropBlockMixin}. The port
 * keeps that exact rule instead of the "solid block below" stand-in used for mod
 * plants: the floor must be Sift terrain or the nest's willow foliage.</p>
 *
 * <p>Differences: none in outcome. The mod's mixin also skips the pitcher crop's light
 * requirement for generated bulbs, so no light check is needed here either.</p>
 */
final class SiftSnifferPlantPlacement {

    enum PlantType {
        TORCHFLOWER, PITCHER_CROP
    }

    private static final BlockStateSpec TORCHFLOWER = BlockStateSpec.of("minecraft:torchflower");

    private SiftSnifferPlantPlacement() {
    }

    /** {@code chooseBlobType}: a torchflower with the given chance, else a pitcher crop. */
    static PlantType chooseBlobType(Random random, float torchflowerChance) {
        return random.nextFloat() < torchflowerChance ? PlantType.TORCHFLOWER : PlantType.PITCHER_CROP;
    }

    /**
     * {@code place(level, plantPos, type, random)}: places the plant at the (free,
     * writable) position when the floor below carries it.
     */
    static boolean place(WorldRegion region, int x, int y, int z, PlantType type, Random random) {
        if (!region.isAir(x, y, z) || !region.isWritable(x, y, z)) {
            return false;
        }
        if (type == PlantType.TORCHFLOWER) {
            if (!canSurvive(region, x, y, z)) {
                return false;
            }
            return region.set(x, y, z, TORCHFLOWER);
        }
        if (!region.isAir(x, y + 1, z) || !region.isWritable(x, y + 1, z)) {
            return false;
        }
        // The mod rolls the age before it checks the floor, so the random stream is kept in that order.
        int age = 3 + random.nextInt(2);
        if (!canSurvive(region, x, y, z)) {
            return false;
        }
        region.set(x, y, z, pitcher(age, "lower"));
        region.set(x, y + 1, z, pitcher(age, "upper"));
        return true;
    }

    /** True for the lower or upper half of a pitcher crop or a grown pitcher plant. */
    static boolean isPitcher(String blockId) {
        return blockId.equals("minecraft:pitcher_crop") || blockId.equals("minecraft:pitcher_plant");
    }

    private static BlockStateSpec pitcher(int age, String half) {
        return BlockStateSpec.of("minecraft:pitcher_crop", Map.of("age", Integer.toString(age), "half", half));
    }

    /** The mixins' soil rule: Sift terrain or the willow foliage of a sniffer nest. */
    private static boolean canSurvive(WorldRegion region, int x, int y, int z) {
        BlockStateSpec floor = region.state(x, y - 1, z);
        return SiftTerrain.TERRAIN.contains(floor) || floor.equals(SiftTerrain.OVERGROWN_WILLOW_FOLIAGE);
    }
}
