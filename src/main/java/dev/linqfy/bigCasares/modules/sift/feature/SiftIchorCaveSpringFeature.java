package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.Random;

/**
 * Port of the mod's {@code SiftIchorCaveSpringFeature}: turns a single wall block of a buried
 * cave into an Ichor spring, like vanilla's water spring. The block must be enclosed by Sift
 * terrain on four of its five horizontal/lower sides, with exactly one open side, and none of the
 * open sides may face the surface.
 *
 * <p>The spring is an Ichor source block, i.e. water ({@link SiftTerrain#ICHOR}).</p>
 *
 * <p>Differences: the mod schedules a fluid tick so the spring starts flowing right away; there
 * is no equivalent while generating, so the source only flows once a block update reaches it.</p>
 */
final class SiftIchorCaveSpringFeature implements SiftFeature {

    /** North, south, west, east, down: {@code POSSIBLE_OPENINGS} of the mod, as {x, y, z} offsets. */
    private static final int[][] POSSIBLE_OPENINGS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}};

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        for (int[] direction : POSSIBLE_OPENINGS) {
            int openingX = x + direction[0];
            int openingY = y + direction[1];
            int openingZ = z + direction[2];
            if (!region.isAir(openingX, openingY, openingZ)) {
                continue;
            }
            int surfaceY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, openingX, openingZ);
            if (openingY < surfaceY - 1) {
                continue;
            }
            return false;
        }
        if (!SiftTerrain.isTerrain(region, x, y + 1, z) || !SiftTerrain.isTerrain(region, x, y - 1, z)) {
            return false;
        }
        if (!region.isAir(x, y, z) && !SiftTerrain.isTerrain(region, x, y, z)) {
            return false;
        }
        int rockCount = 0;
        int holeCount = 0;
        for (int[] direction : POSSIBLE_OPENINGS) {
            int adjacentX = x + direction[0];
            int adjacentY = y + direction[1];
            int adjacentZ = z + direction[2];
            if (SiftTerrain.isTerrain(region, adjacentX, adjacentY, adjacentZ)) {
                rockCount++;
            }
            if (region.isAir(adjacentX, adjacentY, adjacentZ)) {
                holeCount++;
            }
        }
        if (rockCount != 4 || holeCount != 1) {
            return false;
        }
        return region.set(x, y, z, SiftTerrain.ICHOR);
    }
}
