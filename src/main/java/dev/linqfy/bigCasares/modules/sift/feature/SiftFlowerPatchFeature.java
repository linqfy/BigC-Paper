package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.Random;

/**
 * Port of the mod's {@code SiftFlowerPatchFeature}: a small patch of one flower
 * species around the middle of the chunk. In overgrown biomes the species is one
 * of Wildflowers (half of the rolls), Overgrown Lotus, Sunburst Plant or
 * Whisperbloom; elsewhere Wildflowers are rare (3 in 24). Up to 3-4 flowers are
 * placed, within 3 blocks of the centre, on Siftslate, Siftslate Growth or healthy
 * sculk.
 *
 * <p>Differences: the flowers are vanilla stand-ins (cornflower, torchflower,
 * lily of the valley), and the mod's {@code canSurvive} check is reduced to "the
 * block below is solid", which holds for every soil the patch plants on.</p>
 */
final class SiftFlowerPatchFeature implements SiftFeature {

    private static final int ATTEMPTS = 24;

    private final BlockStateSpec lotus = SiftTerrain.block("overgrown_lotus");
    private final BlockStateSpec sunburst = SiftTerrain.block("sunburst_plant");
    private final BlockStateSpec whisperbloom = SiftTerrain.block("whisperbloom");

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int centerX = (x & ~15) + 8;
        int centerZ = (z & ~15) + 8;
        int target = 3 + random.nextInt(2);
        int placed = 0;
        int surfaceY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, centerX, centerZ);
        boolean overgrown = SiftTerrain.isOvergrownBiome(region.biome(centerX, surfaceY, centerZ));
        int selection = random.nextInt(overgrown ? 6 : 24);
        boolean wildflowers = selection < 3;
        BlockStateSpec flower = switch (selection % 3) {
            case 0 -> lotus;
            case 1 -> sunburst;
            default -> whisperbloom;
        };
        for (int attempt = 0; attempt < ATTEMPTS && placed < target; attempt++) {
            int plantX = centerX + random.nextInt(7) - 3;
            int plantZ = centerZ + random.nextInt(7) - 3;
            int plantY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, plantX, plantZ);
            BlockStateSpec floor = region.state(plantX, plantY - 1, plantZ);
            if (!SiftTerrain.isFlowerSoil(floor) || !region.isAir(plantX, plantY, plantZ)
                || !region.isWritable(plantX, plantY, plantZ)) {
                continue;
            }
            BlockStateSpec flowerState = wildflowers ? SiftTerrain.wildflowers(random) : flower;
            if (SiftTerrain.placePlant(region, plantX, plantY, plantZ, flowerState)) {
                placed++;
            }
        }
        return placed > 0;
    }
}
