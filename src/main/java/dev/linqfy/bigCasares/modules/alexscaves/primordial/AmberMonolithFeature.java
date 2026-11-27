package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.SplittableRandom;

/**
 * Mod {@code amber_monolith} (placed feature: 1 chunk in 10, one try, a random height scanned down 32 blocks for the
 * first block that is not air) and {@code AmberMonolithFeature}: a limestone pillar 4 or 5 high topped by the monolith
 * (and, half the time, a slab), with limestone and amber rubble around the foot.
 */
final class AmberMonolithFeature implements ChunkFeature {
    private static final CaveBiomeId BIOME = CaveBiomeId.PRIMORDIAL_CAVES;
    private final BlockData pillarY;
    private final BlockData pillarX;
    private final BlockData pillarZ;
    private final BlockData monolith;
    private final BlockData amber;
    private final BlockData slab = Bukkit.createBlockData(Material.SANDSTONE_SLAB);

    AmberMonolithFeature(ModBlockPalette palette) {
        this.pillarY = palette.get("limestone_pillar", "y");
        this.pillarX = palette.get("limestone_pillar", "x");
        this.pillarZ = palette.get("limestone_pillar", "z");
        this.monolith = palette.get("amber_monolith");
        this.amber = palette.get("amber");
    }

    @Override
    public String id() {
        return "primordial_amber_monolith";
    }

    @Override
    public CaveBiomeId biome() {
        return BIOME;
    }

    @Override
    public void place(CaveChunk chunk) {
        SplittableRandom random = chunk.random(id());
        if (!ModPlacement.rarity(random, 10)) {
            return;
        }
        CaveChunk.BlockSink blocks = chunk.blocks();
        int x = chunk.minBlockX() + random.nextInt(16);
        int z = chunk.minBlockZ() + random.nextInt(16);
        int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
        y = ModPlacement.scanDown(chunk, x, y, z, 32, ModPlacement::notAir);
        if (y == Integer.MIN_VALUE || !chunk.isBiome(x, y, z, BIOME) || !blocks.isInRegion(x, y - 1, z)
            || !blocks.getType(x, y - 1, z).isSolid()) {
            return;
        }
        int top = y;
        for (int i = 0; i < 4 + random.nextInt(2); i++) {
            if (blocks.isInRegion(x, top, z)) {
                blocks.setBlock(x, top, z, pillarY);
            }
            top++;
        }
        if (blocks.isInRegion(x, top, z)) {
            blocks.setBlock(x, top, z, monolith);
            blocks.markStructure(StructureMarks.Kind.AMBER_MONOLITH, x, top, z, x, top, z);
        }
        if (random.nextBoolean() && blocks.isInRegion(x, top + 1, z)) {
            top++;
            blocks.setBlock(x, top, z, slab);
        }
        for (int i = 0; i < 4 + random.nextInt(6); i++) {
            int ox = x + random.nextInt(6) - 3;
            int oz = z + random.nextInt(6) - 3;
            int oy = top + 1;
            while (blocks.isInRegion(ox, oy, oz) && blocks.getType(ox, oy, oz).isAir() && oy > chunk.minY()) {
                oy--;
            }
            if (blocks.isInRegion(ox, oy + 1, oz) && blocks.getType(ox, oy, oz).isSolid()
                && blocks.getType(ox, oy + 1, oz).isAir()) {
                float f = random.nextFloat();
                BlockData rubble = f < 0.3F ? slab : f < 0.6F ? pillarX : f < 0.9F ? pillarZ : amber;
                blocks.setBlock(ox, oy + 1, oz, rubble);
            }
        }
    }
}
