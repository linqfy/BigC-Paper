package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.feature.SiftSnifferPlantPlacement.PlantType;

import java.util.Random;

/**
 * Port of the mod's {@code SnifferPlantPatchFeature}: around the chunk centre, 5 to 9
 * torchflowers (58%) or pitcher-plant bulbs on healthy sculk, dry healthy sculk or
 * siftslate growth, all of one type per patch.
 *
 * <p>Differences: none; the feature has no global state in the mod.</p>
 */
final class SnifferPlantPatchFeature implements SiftFeature {

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int centerX = (x & ~15) + 8;
        int centerZ = (z & ~15) + 8;
        int target = 5 + random.nextInt(5);
        int placed = 0;
        PlantType blobType = SiftSnifferPlantPlacement.chooseBlobType(random, 0.58F);
        for (int attempt = 0; attempt < 28 && placed < target; attempt++) {
            int plantX = centerX + random.nextInt(13) - 6;
            int plantZ = centerZ + random.nextInt(13) - 6;
            int plantY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, plantX, plantZ);
            if (!isPatchFloor(region.state(plantX, plantY - 1, plantZ))
                || !region.isAir(plantX, plantY, plantZ)
                || !region.isWritable(plantX, plantY, plantZ)
                || !SiftSnifferPlantPlacement.place(region, plantX, plantY, plantZ, blobType, random)) {
                continue;
            }
            placed++;
        }
        return placed > 0;
    }

    private static boolean isPatchFloor(BlockStateSpec floor) {
        return floor.equals(SiftTerrain.HEALTHY_SCULK) || floor.equals(SiftTerrain.DRY_HEALTHY_SCULK)
            || SiftTerrain.isGrowth(floor);
    }
}
