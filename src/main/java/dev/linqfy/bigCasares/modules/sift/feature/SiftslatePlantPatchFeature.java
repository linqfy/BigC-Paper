package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.List;
import java.util.Random;

/**
 * Port of the mod's {@code SiftslatePlantPatchFeature}: 4-7 plants of one species
 * (Overgrown Fronds, Stalks or Chard, Siftslate Stalks, or healthy/dry sculk
 * sprouts) scattered within 4 blocks of the middle of the chunk, only on bare
 * Siftslate.
 *
 * <p>Differences: the plants are vanilla stand-ins, and the mod's {@code canSurvive}
 * check is reduced to "the block below is solid", which holds for Siftslate.</p>
 */
final class SiftslatePlantPatchFeature implements SiftFeature {

    private static final int ATTEMPTS = 32;

    private final List<BlockStateSpec> plants = List.of(
        SiftTerrain.block("overgrown_fronds"),
        SiftTerrain.block("overgrown_stalks"),
        SiftTerrain.block("overgrown_chard"),
        SiftTerrain.block("siftslate_stalks"),
        SiftTerrain.block("healthy_sculk_sprouts"),
        SiftTerrain.block("dry_healthy_sculk_sprouts"));

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int centerX = (x & ~15) + 8;
        int centerZ = (z & ~15) + 8;
        int target = 4 + random.nextInt(4);
        int placed = 0;
        BlockStateSpec plant = plants.get(random.nextInt(plants.size()));
        for (int attempt = 0; attempt < ATTEMPTS && placed < target; attempt++) {
            int plantX = centerX + random.nextInt(9) - 4;
            int plantZ = centerZ + random.nextInt(9) - 4;
            int plantY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, plantX, plantZ);
            if (!region.state(plantX, plantY - 1, plantZ).equals(SiftTerrain.SIFTSLATE)
                || !region.isAir(plantX, plantY, plantZ) || !region.isWritable(plantX, plantY, plantZ)) {
                continue;
            }
            if (SiftTerrain.placePlant(region, plantX, plantY, plantZ, plant)) {
                placed++;
            }
        }
        return placed > 0;
    }
}
