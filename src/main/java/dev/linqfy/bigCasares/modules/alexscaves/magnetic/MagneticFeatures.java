package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.OreFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Terrain of the Magnetic Caves, after the mod's surface rules and features:
 * galena rock with energized veins, iron ore, metal swarf blobs, neodymium node
 * clusters, floating galena orbs and tesla bulbs. Counts are scaled down from
 * the mod's attempt counts to the placements that actually succeed there.
 */
public final class MagneticFeatures {
    private static final CaveBiomeId BIOME = CaveBiomeId.MAGNETIC_CAVES;

    private MagneticFeatures() {
    }

    public static List<ChunkFeature> create(ModBlockPalette palette) {
        // Mod ferrocave first: the terrain then turns the walls of the new cave into galena.
        BlockData galena = palette.get("galena");
        return List.of(new BowlFeature("magnetic_ferrocave", BIOME, CaveBowl.Kind.FERROCAVE,
                Bukkit.createBlockData(Material.CAVE_AIR), galena, galena, null),
            new Terrain(palette),
            // Mod magnetic_ruins: 1 chunk in 9.
            new RuinsFeature("magnetic_ruins", BIOME, 9, 1, StructureTemplates.load("magnetic_ruins"), false, null),
            // Mod ore features on galena (tag galena_gen_replaceables).
            new OreFeature("magnetic_galena_iron_ore", BIOME, 15, 14, -24, false, 56, true,
                OreFeature.Target.of(galena), palette.get("galena_iron_ore")),
            new OreFeature("magnetic_metal_swarf", BIOME, 3, 50, -100, false, 100, true,
                OreFeature.Target.of(galena), palette.get("metal_swarf")),
            new Nodes(palette, "scarlet"), new Nodes(palette, "azure"), new TeslaBulbs(palette));
    }

    /**
     * Mod surface rule: galena everywhere stone was, with scarlet, azure and (where both cross) neutral energized
     * veins. The mod's vanilla ores look for stone after the surface rule, so none survive in the biome: they turn
     * to galena too, and iron comes from galena iron ore veins.
     */
    static final class Terrain implements ChunkFeature {
        private final ModBlockPalette palette;

        private final BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);

        Terrain(ModBlockPalette palette) {
            this.palette = palette;
        }

        @Override
        public String id() {
            return "magnetic_terrain";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            BlockData galena = palette.get("galena");
            BlockData scarlet = palette.get("energized_galena_scarlet");
            BlockData azure = palette.get("energized_galena_azure");
            BlockData neutral = palette.get("energized_galena_neutral");
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    int top = chunk.surfaceY(x, z);
                    for (int y = chunk.minY() + 5; y < top; y++) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            continue;
                        }
                        Material type = blocks.getType(x, y, z);
                        if (CavernFeature.vanillaCaveDecoration(type)) {
                            blocks.setBlock(x, y, z, air);
                        } else if (CavernFeature.surfaceRock(type)) {
                            boolean inScarlet = vein(SCARLET, x, y, z);
                            boolean inAzure = vein(AZURE, x, y, z);
                            blocks.setBlock(x, y, z, inScarlet && inAzure ? neutral : inScarlet ? scarlet : inAzure ? azure : galena);
                        }
                    }
                }
            }
        }

        static final int AZURE = 0;
        static final int SCARLET = 1;

        /** Mod {@code simplexCondition(-0.025, 0.025, 90, 1, offset)}: thin sheets where the 3D noise crosses zero. */
        static boolean vein(int offset, int x, int y, int z) {
            float value = SimplexNoise3D.sample(x + offset * 1000, y + offset * 2000, z - offset * 3000, 90.0F);
            return value > -0.025F && value <= 0.025F;
        }
    }

    /** Neodymium crystal clusters growing from walls (port of {@code MagneticNodeFeature}). */
    static final class Nodes implements ChunkFeature {
        private final ModBlockPalette palette;
        private final String color;

        Nodes(ModBlockPalette palette, String color) {
            this.palette = palette;
            this.color = color;
        }

        @Override
        public String id() {
            return color + "_magnetic_node";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int attempts = ModPlacement.uniform(random, 80, 150);
            for (int attempt = 0; attempt < attempts; attempt++) {
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!ModPlacement.belowSurface(chunk, x, y, z, -13)) {
                    continue;
                }
                if (!chunk.isBiome(x, y, z, BIOME) || !FeatureShapes.isAir(chunk.blocks().getType(x, y, z).name())) {
                    continue;
                }
                List<Integer> solidSides = new ArrayList<>();
                for (int d = 0; d < 6; d++) {
                    int[] o = FeatureShapes.DIRECTIONS.get(d);
                    if (chunk.blocks().getType(x + o[0], y + o[1], z + o[2]).isSolid()) {
                        solidSides.add(FeatureShapes.opposite(d));
                    }
                }
                if (solidSides.isEmpty()) {
                    continue;
                }
                int facing = solidSides.get(random.nextInt(solidSides.size()));
                int centerHeight = 3 + random.nextInt(3);
                pillar(chunk, x, y, z, facing, centerHeight, random);
                int[] f = FeatureShapes.DIRECTIONS.get(facing);
                for (int extra = 0; extra < 2 + random.nextInt(3); extra++) {
                    int ox = f[0] != 0 ? 0 : random.nextInt(4) - 2;
                    int oy = f[1] != 0 ? 0 : random.nextInt(4) - 2;
                    int oz = f[2] != 0 ? 0 : random.nextInt(4) - 2;
                    int distance = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
                    if (distance > 0) {
                        pillar(chunk, x + ox, y + oy, z + oz, facing, Math.max(centerHeight - distance - random.nextInt(2), 1) + 2, random);
                    }
                }
            }
        }

        private void pillar(CaveChunk chunk, int x, int y, int z, int facing, int height, SplittableRandom random) {
            int[] f = FeatureShapes.DIRECTIONS.get(facing);
            String direction = FeatureShapes.DIRECTION_NAMES.get(facing);
            BlockData pillar = palette.get(color + "_neodymium_pillar", direction);
            for (int step = 0; step < height; step++) {
                int px = x + f[0] * step;
                int py = y + f[1] * step;
                int pz = z + f[2] * step;
                if (!placeInAir(chunk, px, py, pz, step == height - 1 ? palette.get(color + "_neodymium_node", direction) : pillar)) {
                    return;
                }
                if (random.nextInt(3) == 0) {
                    int side = random.nextInt(6);
                    if (side / 2 != facing / 2) {
                        int[] s = FeatureShapes.DIRECTIONS.get(side);
                        placeInAir(chunk, px + s[0], py + s[1], pz + s[2],
                            palette.get(color + "_neodymium_node", FeatureShapes.DIRECTION_NAMES.get(side)));
                    }
                }
            }
        }
    }

    /**
     * Tesla bulbs (mod {@code tesla_bulb}: 1-12 attempts per chunk below y=60 and 1 block under the surface; each
     * from an open spot, up to the ceiling or down to the floor at random). The mod's tesla pillars around them
     * could not be recovered from the decompiled code, so only the bulb is placed.
     */
    static final class TeslaBulbs implements ChunkFeature {
        private final ModBlockPalette palette;

        TeslaBulbs(ModBlockPalette palette) {
            this.palette = palette;
        }

        @Override
        public String id() {
            return "tesla_bulb";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int attempts = ModPlacement.uniform(random, 1, 12);
            for (int attempt = 0; attempt < attempts; attempt++) {
                int y = ModPlacement.uniform(random, chunk.minY(), 60);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                boolean ceiling = random.nextBoolean();
                if (!ModPlacement.belowSurface(chunk, x, y, z, -1) || !chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                Material at = blocks.getType(x, y, z);
                if (!at.isAir() && at != Material.WATER) {
                    continue;
                }
                int step = ceiling ? 1 : -1;
                while (y > chunk.minY() && y < chunk.minY() + 384 && blocks.isInRegion(x, y, z)
                    && !blocks.getType(x, y, z).isSolid()) {
                    y += step;
                }
                if (!blocks.isInRegion(x, y, z) || !chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                placeInAir(chunk, x, y - step, z, palette.get("tesla_bulb", ceiling ? "down" : "up"));
            }
        }
    }

    private static boolean placeInAir(CaveChunk chunk, int x, int y, int z, BlockData data) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        if (!blocks.isInRegion(x, y, z) || !FeatureShapes.isAir(blocks.getType(x, y, z).name())) {
            return false;
        }
        blocks.setBlock(x, y, z, data);
        return true;
    }
}
