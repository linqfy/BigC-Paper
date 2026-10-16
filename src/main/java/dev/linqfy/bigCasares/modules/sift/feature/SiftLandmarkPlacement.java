package dev.linqfy.bigCasares.modules.sift.feature;

/**
 * Port of the mod's {@code SiftLandmarkPlacement}: landmarks (abandoned portals,
 * sniffer caves, soul canyons) appear in seed-selected chunks of a coarse grid, so
 * placement is deterministic per world seed and independent of generation order.
 */
final class SiftLandmarkPlacement {

    static final int ABANDONED_PORTAL_CELL_CHUNKS = 20;
    static final int ABANDONED_PORTAL_CANDIDATES = 1;
    static final int SNIFFER_CAVE_OVERGROWN_CELL_CHUNKS = 16;
    static final int SNIFFER_CAVE_WASTES_CELL_CHUNKS = 28;
    static final int SNIFFER_CAVE_OVERGROWN_CANDIDATES = 4;
    static final int SNIFFER_CAVE_WASTES_CANDIDATES = 3;
    static final int SOUL_CANYON_CANDIDATES = 1;
    static final int SOUL_CANYON_CELL_CHUNKS = 12;
    static final long ABANDONED_PORTAL_SALT = 4702392765337521733L;
    static final long SNIFFER_CAVE_OVERGROWN_SALT = 6002815919507592773L;
    static final long SNIFFER_CAVE_WASTES_SALT = 6002815919508111699L;
    static final long SOUL_CANYON_SALT = 6003110614342389337L;

    record ChunkPos(int x, int z) {
    }

    private SiftLandmarkPlacement() {
    }

    static boolean isSelectedChunk(long worldSeed, int originX, int originZ, int cellChunks, long salt, int candidateCount) {
        int chunkX = Math.floorDiv(originX, 16);
        int chunkZ = Math.floorDiv(originZ, 16);
        int gridOffset = salt == SOUL_CANYON_SALT ? cellChunks / 2 : 0;
        int cellX = Math.floorDiv(chunkX - gridOffset, cellChunks);
        int cellZ = Math.floorDiv(chunkZ - gridOffset, cellChunks);
        for (int index = 0; index < candidateCount; index++) {
            ChunkPos selected = candidateChunk(worldSeed, cellX, cellZ, cellChunks, salt, index);
            if (selected.x() == chunkX && selected.z() == chunkZ) {
                return true;
            }
        }
        return false;
    }

    static ChunkPos candidateChunk(long worldSeed, int cellX, int cellZ, int cellChunks, long salt, int candidateIndex) {
        long indexedSalt = salt ^ (long) candidateIndex * -2960836687051489901L;
        long first = mix(worldSeed, cellX, cellZ, indexedSalt);
        long second = mix(worldSeed, cellZ, cellX, indexedSalt ^ 0x9E3779B97F4A7C15L);
        int inset = salt == ABANDONED_PORTAL_SALT || salt == SOUL_CANYON_SALT ? cellChunks / 3 : 0;
        int spread = cellChunks - inset * 2;
        int offsetX = inset + (int) Long.remainderUnsigned(first, spread);
        int offsetZ = inset + (int) Long.remainderUnsigned(second, spread);
        int gridOffset = salt == SOUL_CANYON_SALT ? cellChunks / 2 : 0;
        return new ChunkPos(cellX * cellChunks + gridOffset + offsetX, cellZ * cellChunks + gridOffset + offsetZ);
    }

    private static long mix(long seed, int cellX, int cellZ, long salt) {
        long value = seed ^ salt;
        value ^= (long) cellX * -7046029254386353131L;
        value ^= (long) cellZ * -4417276706812531889L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        return value ^ value >>> 31;
    }
}
