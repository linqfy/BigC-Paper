package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.SplittableRandom;

/**
 * A mod {@code minecraft:ore} placed feature: {@code count} veins of {@code size} per chunk at a random spot of
 * the chunk, at a height drawn uniformly or as a trapezoid, kept only when that spot is in the biome; each vein
 * turns the {@code target} blocks it covers into {@code state} ({@link VanillaOre}).
 */
public final class OreFeature implements ChunkFeature {
    /** What a vein replaces: a vanilla material, or one exact block (module blocks are note block states). */
    public record Target(Material material, BlockData exact) {
        public static Target of(Material material) {
            return new Target(material, null);
        }

        public static Target of(BlockData exact) {
            return new Target(exact.getMaterial(), exact);
        }

        boolean matches(CaveChunk.BlockSink blocks, int x, int y, int z) {
            return blocks.getType(x, y, z) == material && (exact == null || exact.equals(blocks.getBlockData(x, y, z)));
        }
    }

    private final String id;
    private final CaveBiomeId biome;
    private final int count;
    private final int size;
    private final int minY;
    private final boolean minAboveBottom;
    private final int maxY;
    private final boolean trapezoid;
    private final Target target;
    private final BlockData state;

    /**
     * @param minY           lowest height ({@code above_bottom} offset when {@code minAboveBottom})
     * @param maxY           highest height (absolute)
     * @param trapezoid      {@code trapezoid} height instead of {@code uniform}
     */
    public OreFeature(String id, CaveBiomeId biome, int count, int size, int minY, boolean minAboveBottom, int maxY,
                      boolean trapezoid, Target target, BlockData state) {
        this.id = id;
        this.biome = biome;
        this.count = count;
        this.size = size;
        this.minY = minY;
        this.minAboveBottom = minAboveBottom;
        this.maxY = maxY;
        this.trapezoid = trapezoid;
        this.target = target;
        this.state = state;
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
        int low = minAboveBottom ? chunk.minY() + minY : minY;
        for (int i = 0; i < count; i++) {
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = trapezoid ? ModPlacement.trapezoid(random, low, maxY) : ModPlacement.uniform(random, low, maxY);
            if (!chunk.isBiome(x, y, z, biome)) {
                // The random state still advances like the mod's: the vein is only skipped.
                continue;
            }
            VanillaOre.place(random, x, y, z, size, (bx, by, bz) -> {
                if (blocks.isInRegion(bx, by, bz) && target.matches(blocks, bx, by, bz)) {
                    blocks.setBlock(bx, by, bz, state);
                }
            });
        }
    }
}
