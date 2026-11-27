package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * A mod {@code minecraft:lake} placed feature (acid lakes, Primordial Caves lakes): the vanilla lake shape (4-7
 * overlapping ellipsoids in a 16x8x16 box, the lower half fluid and the upper half air), skipped when it would
 * spill, with its sides and bottom turned into the barrier block. Placed like the mod: {@code count} tries, each
 * kept 1 in {@code rarity}, at a random height, scanned down to the floor, 5 blocks under the surface, in the biome.
 */
public final class LakeFeature implements ChunkFeature {
    private final String id;
    private final CaveBiomeId biome;
    private final int countMin;
    private final int countMax;
    private final int rarity;
    private final int minAboveBottom;
    private final Predicate<Material> floor;
    private final BlockData fluid;
    private final BlockData barrier;
    private final BlockData air;

    /** @param floor what the downward scan stops on (mod: anything but air, and for Primordial also lava) */
    public LakeFeature(String id, CaveBiomeId biome, int countMin, int countMax, int rarity, int minAboveBottom,
                       Predicate<Material> floor, BlockData fluid, BlockData barrier, BlockData air) {
        this.id = id;
        this.biome = biome;
        this.countMin = countMin;
        this.countMax = countMax;
        this.rarity = rarity;
        this.minAboveBottom = minAboveBottom;
        this.floor = floor;
        this.fluid = fluid;
        this.barrier = barrier;
        this.air = air;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CaveBiomeId biome() {
        return biome;
    }

    @Override
    public void place(CaveChunk chunk) {
        SplittableRandom random = chunk.random(id);
        int count = ModPlacement.uniform(random, countMin, countMax);
        for (int i = 0; i < count; i++) {
            if (!ModPlacement.rarity(random, rarity)) {
                continue;
            }
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = ModPlacement.uniform(random, chunk.minY() + minAboveBottom, ModPlacement.TOP);
            y = ModPlacement.scanDown(chunk, x, y, z, 32, floor);
            if (y == Integer.MIN_VALUE || !ModPlacement.belowSurface(chunk, x, y, z, -5) || !chunk.isBiome(x, y, z, biome)) {
                continue;
            }
            lake(chunk, x, y, z, random);
        }
    }

    /** Vanilla {@code LakeFeature.place}. */
    void lake(CaveChunk chunk, int originX, int originY, int originZ, SplittableRandom random) {
        if (originY <= chunk.minY() + 4) {
            return;
        }
        int oy = originY - 4;
        boolean[] grid = shape(random);
        CaveChunk.BlockSink blocks = chunk.blocks();
        Material fluidType = fluid.getMaterial();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    if (!border(grid, x, y, z)) {
                        continue;
                    }
                    Material type = blocks.getType(originX + x, oy + y, originZ + z);
                    if (y >= 4 && (type == Material.WATER || type == Material.LAVA)) {
                        return;
                    }
                    if (y < 4 && !type.isSolid() && type != fluidType) {
                        return;
                    }
                }
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    if (grid[index(x, y, z)] && replaceable(blocks.getType(originX + x, oy + y, originZ + z))) {
                        blocks.setBlock(originX + x, oy + y, originZ + z, y >= 4 ? air : fluid);
                    }
                }
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    if (border(grid, x, y, z) && (y < 4 || random.nextInt(2) != 0)) {
                        Material type = blocks.getType(originX + x, oy + y, originZ + z);
                        if (type.isSolid() && replaceable(type)) {
                            blocks.setBlock(originX + x, oy + y, originZ + z, barrier);
                        }
                    }
                }
            }
        }
    }

    /** The lake's cells: 4-7 random ellipsoids inside the 16x8x16 box. */
    static boolean[] shape(SplittableRandom random) {
        boolean[] grid = new boolean[2048];
        int blobs = random.nextInt(4) + 4;
        for (int i = 0; i < blobs; i++) {
            double sx = random.nextDouble() * 6.0 + 3.0;
            double sy = random.nextDouble() * 4.0 + 2.0;
            double sz = random.nextDouble() * 6.0 + 3.0;
            double cx = random.nextDouble() * (16.0 - sx - 2.0) + 1.0 + sx / 2.0;
            double cy = random.nextDouble() * (8.0 - sy - 4.0) + 2.0 + sy / 2.0;
            double cz = random.nextDouble() * (16.0 - sz - 2.0) + 1.0 + sz / 2.0;
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 7; y++) {
                        double dx = (x - cx) / (sx / 2.0);
                        double dy = (y - cy) / (sy / 2.0);
                        double dz = (z - cz) / (sz / 2.0);
                        if (dx * dx + dy * dy + dz * dz < 1.0) {
                            grid[index(x, y, z)] = true;
                        }
                    }
                }
            }
        }
        return grid;
    }

    /** A cell outside the lake that touches it. */
    static boolean border(boolean[] grid, int x, int y, int z) {
        return !grid[index(x, y, z)] && (x < 15 && grid[index(x + 1, y, z)] || x > 0 && grid[index(x - 1, y, z)]
            || z < 15 && grid[index(x, y, z + 1)] || z > 0 && grid[index(x, y, z - 1)]
            || y < 7 && grid[index(x, y + 1, z)] || y > 0 && grid[index(x, y - 1, z)]);
    }

    static int index(int x, int y, int z) {
        return (x * 16 + z) * 8 + y;
    }

    /** Vanilla {@code features_cannot_replace}. */
    private static boolean replaceable(Material type) {
        return type != Material.BEDROCK && type != Material.SPAWNER && type != Material.CHEST
            && type != Material.END_PORTAL_FRAME && type != Material.VOID_AIR;
    }
}
