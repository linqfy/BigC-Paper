package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Mod plant patches ({@code random_patch} of a {@code simple_block}): {@code rarity} 1 in N chunks, {@code count}
 * centres at a random height of the lowest 160 blocks, and around each centre {@code tries} spots spread
 * {@code xzSpread} / {@code ySpread} (vanilla {@code nextInt(s + 1) - nextInt(s + 1)}). A spot takes the plant when it
 * is air, inside the biome, on a solid block and not open to the sky (plants of cave biomes). {@code states} is the
 * plant from the bottom up (two entries for a two-block plant, which also needs air above).
 */
public final class PlantPatchFeature implements ChunkFeature {
    private final String id;
    private final CaveBiomeId biome;
    private final int rarity;
    private final int countMin;
    private final int countMax;
    private final int tries;
    private final int xzSpread;
    private final int ySpread;
    private final List<BlockData> states;
    private final Predicate<Material> floor;

    public PlantPatchFeature(String id, CaveBiomeId biome, int rarity, int countMin, int countMax, int tries,
                             int xzSpread, int ySpread, List<BlockData> states) {
        this(id, biome, rarity, countMin, countMax, tries, xzSpread, ySpread, states, Material::isSolid);
    }

    /** @param floor blocks a plant can stand on */
    PlantPatchFeature(String id, CaveBiomeId biome, int rarity, int countMin, int countMax, int tries,
                      int xzSpread, int ySpread, List<BlockData> states, Predicate<Material> floor) {
        if (states.isEmpty()) {
            throw new IllegalArgumentException("sin estados");
        }
        this.id = id;
        this.biome = biome;
        this.rarity = rarity;
        this.countMin = countMin;
        this.countMax = countMax;
        this.tries = tries;
        this.xzSpread = xzSpread;
        this.ySpread = ySpread;
        this.states = List.copyOf(states);
        this.floor = floor;
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
        if (!ModPlacement.rarity(random, rarity)) {
            return;
        }
        int count = ModPlacement.uniform(random, countMin, countMax);
        for (int i = 0; i < count; i++) {
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160) + 1;
            if (!chunk.isBiome(x, y, z, biome)) {
                continue;
            }
            for (int attempt = 0; attempt < tries; attempt++) {
                int bx = x + random.nextInt(xzSpread + 1) - random.nextInt(xzSpread + 1);
                int by = y + random.nextInt(ySpread + 1) - random.nextInt(ySpread + 1);
                int bz = z + random.nextInt(xzSpread + 1) - random.nextInt(xzSpread + 1);
                if (fits(chunk, bx, by, bz)) {
                    for (int part = 0; part < states.size(); part++) {
                        chunk.blocks().setBlock(bx, by + part, bz, states.get(part));
                    }
                }
            }
        }
    }

    boolean fits(CaveChunk chunk, int x, int y, int z) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        if (!blocks.isInRegion(x, y - 1, z) || !blocks.isInRegion(x, y + states.size() - 1, z)
            || !chunk.isBiome(x, y, z, biome) || !floor.test(blocks.getType(x, y - 1, z))
            || y + states.size() - 1 >= chunk.surfaceY(x, z)) {
            return false;
        }
        for (int part = 0; part < states.size(); part++) {
            if (!FeatureShapes.isAir(blocks.getType(x, y + part, z).name())) {
                return false;
            }
        }
        return true;
    }
}
