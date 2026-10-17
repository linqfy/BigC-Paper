package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.Random;

/**
 * Port of the mod's {@code SiftLavaFloorFeature}: fills every air block of the generating chunk
 * from the bottom of the world up to Y 11 with lava, so deep caves end in a lava sea.
 *
 * <p>Faithful port: like the mod, the lava is placed without scheduling a fluid tick.</p>
 */
final class SiftLavaFloorFeature implements SiftFeature {

    private static final int LAVA_SURFACE_Y = 11;

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int chunkMinX = x & ~15;
        int chunkMinZ = z & ~15;
        // The mod's getMaxY() is the inclusive top block, the port's maxY() is exclusive.
        int maxY = Math.min(LAVA_SURFACE_Y, region.maxY() - 2);
        int placed = 0;
        for (int blockX = chunkMinX; blockX < chunkMinX + 16; blockX++) {
            for (int blockZ = chunkMinZ; blockZ < chunkMinZ + 16; blockZ++) {
                for (int blockY = region.minY(); blockY <= maxY; blockY++) {
                    if (!region.isWritable(blockX, blockY, blockZ) || !region.isAir(blockX, blockY, blockZ)) {
                        continue;
                    }
                    region.set(blockX, blockY, blockZ, SiftTerrain.LAVA);
                    placed++;
                }
            }
        }
        return placed > 0;
    }
}
