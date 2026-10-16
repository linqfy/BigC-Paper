package dev.linqfy.bigCasares.modules.sift.feature;

/**
 * Port of the mod's {@code SiftWorldgenBounds}: the generating chunk plus its
 * neighbour ring, which is the area a feature may write (Paper's limited region
 * has the same one-chunk buffer).
 */
final class SiftWorldgenBounds {

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    private SiftWorldgenBounds(int chunkX, int chunkZ, int horizontalMargin) {
        this.minX = ((chunkX - 1) << 4) + horizontalMargin;
        this.maxX = ((chunkX + 2) << 4) - 1 - horizontalMargin;
        this.minZ = ((chunkZ - 1) << 4) + horizontalMargin;
        this.maxZ = ((chunkZ + 2) << 4) - 1 - horizontalMargin;
    }

    static SiftWorldgenBounds around(int originX, int originZ) {
        return new SiftWorldgenBounds(Math.floorDiv(originX, 16), Math.floorDiv(originZ, 16), 0);
    }

    static SiftWorldgenBounds aroundWithNeighbourMargin(int originX, int originZ) {
        return new SiftWorldgenBounds(Math.floorDiv(originX, 16), Math.floorDiv(originZ, 16), 1);
    }

    boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    int distanceToEdge(int x, int z) {
        return Math.max(0, Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z)));
    }
}
