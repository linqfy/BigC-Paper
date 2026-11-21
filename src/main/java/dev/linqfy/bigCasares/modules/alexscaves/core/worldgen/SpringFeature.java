package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.SplittableRandom;

/**
 * A mod {@code minecraft:spring_feature} placed feature (acid springs): a fluid source in a wall where the block
 * above and below are rock and, of its four sides and bottom, exactly four are rock and one is open. Placed like
 * the mod: {@code count} tries per chunk at a random height, in the biome.
 */
public final class SpringFeature implements ChunkFeature {
    private static final int[][] SIDES = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};

    private final String id;
    private final CaveBiomeId biome;
    private final int countMin;
    private final int countMax;
    private final BlockData rock;
    private final BlockData fluid;

    public SpringFeature(String id, CaveBiomeId biome, int countMin, int countMax, BlockData rock, BlockData fluid) {
        this.id = id;
        this.biome = biome;
        this.countMin = countMin;
        this.countMax = countMax;
        this.rock = rock;
        this.fluid = fluid;
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
        CaveChunk.BlockSink blocks = chunk.blocks();
        int count = ModPlacement.uniform(random, countMin, countMax);
        for (int i = 0; i < count; i++) {
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
            if (!chunk.isBiome(x, y, z, biome) || !isRock(blocks, x, y + 1, z) || !isRock(blocks, x, y - 1, z)) {
                continue;
            }
            Material here = blocks.getType(x, y, z);
            if (!here.isAir() && !isRock(blocks, x, y, z)) {
                continue;
            }
            int rocks = 0;
            int holes = 0;
            for (int[] side : SIDES) {
                if (isRock(blocks, x + side[0], y + side[1], z + side[2])) {
                    rocks++;
                } else if (blocks.getType(x + side[0], y + side[1], z + side[2]).isAir()) {
                    holes++;
                }
            }
            if (rocks == 4 && holes == 1) {
                blocks.setBlock(x, y, z, fluid);
            }
        }
    }

    private boolean isRock(CaveChunk.BlockSink blocks, int x, int y, int z) {
        return blocks.isInRegion(x, y, z) && blocks.getType(x, y, z) == rock.getMaterial() && rock.equals(blocks.getBlockData(x, y, z));
    }
}
