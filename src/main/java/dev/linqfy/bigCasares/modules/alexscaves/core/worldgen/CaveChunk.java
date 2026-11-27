package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Optional;
import java.util.SplittableRandom;

/**
 * One freshly generated chunk that contains part of a cave biome, as seen by a
 * {@link ChunkFeature}. Writes go through {@link BlockSink} so features can be
 * tested without a server.
 */
public final class CaveChunk {
    private final long seed;
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final BiomeMask biomes;
    private final BlockSink blocks;
    /** Mod {@code replaceBiomes}: biomes a structure has set for the whole chunk up to a height. */
    private final java.util.Map<CaveBiomeId, Integer> replaced = new java.util.EnumMap<>(CaveBiomeId.class);
    private final int[] oceanFloor = new int[256];
    private boolean oceanFloorRead;

    public CaveChunk(long seed, int chunkX, int chunkZ, int minY, BiomeMask biomes, BlockSink blocks) {
        this.seed = seed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.biomes = biomes;
        this.blocks = blocks;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int minBlockX() {
        return chunkX << 4;
    }

    public int minBlockZ() {
        return chunkZ << 4;
    }

    public int minY() {
        return minY;
    }

    /** The world seed itself (for noises the mod seeds with it). */
    public long seed() {
        return seed;
    }

    /** Seed per world and feature, the same in every chunk (for noise that must continue across chunks). */
    public long worldSeed(String feature) {
        return SeededHash.hash(seed ^ feature.hashCode(), 0, 0);
    }

    /** Deterministic random per world seed, chunk and feature. */
    public SplittableRandom random(String feature) {
        return new SplittableRandom(SeededHash.hash(seed ^ feature.hashCode(), chunkX, chunkZ));
    }

    public Optional<CaveBiomeId> biomeAt(int x, int y, int z) {
        return biomes.biomeAt(x, y, z);
    }

    public boolean isBiome(int x, int y, int z, CaveBiomeId biome) {
        Integer upTo = replaced.get(biome);
        if (upTo != null && y <= upTo && x >> 4 == chunkX && z >> 4 == chunkZ) {
            return true;
        }
        return biomes.biomeAt(x, y, z).filter(found -> found == biome).isPresent();
    }

    /** True when the chunk has some of the biome (in its depth band, or set by a structure). */
    public boolean mayHave(CaveBiomeId biome) {
        if (replaced.containsKey(biome)) {
            return true;
        }
        for (int x = minBlockX() + 2; x < minBlockX() + 16; x += 4) {
            for (int z = minBlockZ() + 2; z < minBlockZ() + 16; z += 4) {
                if (biomes.columnBiomeAt(x, z).filter(found -> found == biome).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Mod {@code AbstractCaveGenerationStructurePiece.replaceBiomes}: the whole chunk becomes the biome from the bottom
     * of the world up to {@code maxY}.
     */
    public void replaceBiome(CaveBiomeId biome, int maxY) {
        Integer known = replaced.get(biome);
        if (known != null && known >= maxY) {
            return;
        }
        replaced.put(biome, maxY);
        for (int x = minBlockX(); x < minBlockX() + 16; x += 4) {
            for (int z = minBlockZ(); z < minBlockZ() + 16; z += 4) {
                for (int y = minY; y <= maxY; y += 4) {
                    blocks.setCaveBiome(x, y, z, biome);
                }
            }
        }
    }

    /**
     * The block above the highest non-fluid block of a column of this chunk as the world generator left it (vanilla
     * {@code OCEAN_FLOOR_WG}, not updated by later digging), or the coarse surface where it cannot be read.
     */
    public int oceanFloor(int x, int z) {
        if (x >> 4 != chunkX || z >> 4 != chunkZ) {
            return surfaceY(x, z) + 1;
        }
        if (!oceanFloorRead) {
            oceanFloorRead = true;
            for (int i = 0; i < 256; i++) {
                int floor = blocks.oceanFloorWG(minBlockX() + (i & 15), minBlockZ() + (i >> 4));
                oceanFloor[i] = floor == Integer.MIN_VALUE ? surfaceY(minBlockX() + (i & 15), minBlockZ() + (i >> 4)) + 1 : floor;
            }
        }
        return oceanFloor[(x & 15) | (z & 15) << 4];
    }

    public int surfaceY(int x, int z) {
        return biomes.surfaceY(x, z);
    }

    /** Biome of any column of the world, loaded or not (the depth band is not checked). */
    public Optional<CaveBiomeId> columnBiomeAt(int x, int z) {
        return biomes.columnBiomeAt(x, z);
    }

    public BlockSink blocks() {
        return blocks;
    }

    public interface BiomeMask {
        Optional<CaveBiomeId> biomeAt(int x, int y, int z);

        int surfaceY(int x, int z);

        /** Biome of a column anywhere in the world, ignoring depth. */
        default Optional<CaveBiomeId> columnBiomeAt(int x, int z) {
            return Optional.empty();
        }
    }

    /** Block access limited to the populated region; writes outside it are ignored. */
    public interface BlockSink {
        boolean isInRegion(int x, int y, int z);

        /**
         * Places a structure template with its zero corner at a position (vanilla pivot), blocks only; false when
         * the region cannot take it.
         */
        default boolean placeStructure(org.bukkit.structure.Structure template, int x, int y, int z,
                                       org.bukkit.block.structure.StructureRotation rotation, java.util.Random random) {
            return placeStructure(template, x, y, z, rotation, random, null);
        }

        /**
         * As {@link #placeStructure(org.bukkit.structure.Structure, int, int, int,
         * org.bukkit.block.structure.StructureRotation, java.util.Random)}, turning each block with {@code turn}
         * (module blocks by variant, see {@link NoteBlockRotations}); null turns blocks with Bukkit's rotation only.
         */
        default boolean placeStructure(org.bukkit.structure.Structure template, int x, int y, int z,
                                       org.bukkit.block.structure.StructureRotation rotation, java.util.Random random,
                                       NoteBlockRotations turn) {
            return false;
        }

        /** Places a container block with a loot table (generated when a player opens it). */
        default void setContainer(int x, int y, int z, BlockData data, org.bukkit.loot.LootTable lootTable) {
            setBlock(x, y, z, data);
        }

        /**
         * A cave painting on side {@code facing} of block (x, y, z): the limestone there and its display (worldgen
         * only; see {@link dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings}).
         */
        default void addCavePainting(int x, int y, int z, String painting, org.bukkit.block.BlockFace facing,
                                     BlockData limestone) {
            setBlock(x, y, z, limestone);
        }

        /** Sets the biome of the quart holding the block (worldgen only). */
        default void setCaveBiome(int x, int y, int z, CaveBiomeId biome) {
        }

        /** Vanilla {@code OCEAN_FLOOR_WG} height (block above the floor), or MIN_VALUE when not available. */
        default int oceanFloorWG(int x, int z) {
            return Integer.MIN_VALUE;
        }

        /** Records that a structure piece of {@code kind} occupies this box (in the chunk being generated or its neighbours). */
        default void markStructure(StructureMarks.Kind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        }

        Material getType(int x, int y, int z);

        BlockData getBlockData(int x, int y, int z);

        void setBlock(int x, int y, int z, BlockData data);
    }
}
