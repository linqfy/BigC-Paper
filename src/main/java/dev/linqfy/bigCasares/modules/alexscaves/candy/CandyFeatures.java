package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModVoronoi;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SeededValueNoise;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Terrain of the Candy Cavity, after the mod: the cake cave ({@link CaveBowl.Kind#CAKE_CAVE}), the mod's surface
 * rule (frosted chocolate floors over chocolate, chocolate bands in cake layer) and the cake cave's floor: purple
 * soda rivers lined with chocolate, and rock candy roads in Voronoi rings with gingerbread bridges over the rivers
 * ({@code CakeCaveStructurePiece.decorateFloor}). Purple soda is the biome's water, coloured like it.
 */
public final class CandyFeatures {
    static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;
    static final int BAND_TOP = (int) Math.floor(BIOME.minDepth() * 128.0);
    static final int BAND_DEPTH = (int) Math.ceil((BIOME.maxDepth() - BIOME.minDepth()) * 128.0);
    /** Mod {@code ROAD_BLOCKS}. */
    static final String[] ROAD = {"rock_candy_red", "rock_candy_purple", "rock_candy_yellow", "rock_candy_light_blue",
        "rock_candy_orange", "rock_candy_lime"};

    private CandyFeatures() {
    }

    public static List<ChunkFeature> create(ModBlockPalette palette) {
        BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);
        BlockData cake = palette.get("cake_layer", "y");
        List<ChunkFeature> features = new java.util.ArrayList<>();
        // Mod surroundCornerOfLiquid: liquids next to the cave become cake inside the piece, away from the rivers.
        BowlFeature bowl = new BowlFeature("candy_cake_cave", BIOME, CaveBowl.Kind.CAKE_CAVE, air, cake, cake, null,
            (x, z) -> CakeFloor.riveriness(x, z) > 500.0);
        features.add(bowl);
        features.add(new Terrain(palette, air, bowl));
        features.add(new CakeFloor(palette, air, bowl));
        features.addAll(CandyDecorations.create(palette, bowl));
        if (palette.registry().shapedBasesActive()) {
            // Only without collision: in legacy mode the plants would be solid note blocks.
            features.add(new SweetPuffs(palette.get("block_of_frosted_chocolate"), palette.get("sweet_puff"),
                palette.get("lollipop_bunch")));
            features.add(new SundropPatches(palette.get("block_of_frosted_chocolate"), palette.get("sundrop")));
        }
        return List.copyOf(features);
    }

    /**
     * Mod surface rule: frosted chocolate on floors not under water, chocolate under them, 20 chocolate bands two
     * blocks thick every 10 blocks from y=52 down, cake layer everywhere else.
     */
    static final class Terrain implements ChunkFeature {
        private final BlockData cake;
        private final BlockData chocolate;
        private final BlockData frosted;
        private final BlockData air;
        private final BowlFeature bowl;

        Terrain(ModBlockPalette palette, BlockData air, BowlFeature bowl) {
            this.bowl = bowl;
            this.cake = palette.get("cake_layer", "y");
            this.chocolate = palette.get("block_of_chocolate", "y");
            this.frosted = palette.get("block_of_frosted_chocolate");
            this.air = air;
        }

        @Override
        public String id() {
            return "candy_terrain";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SeededValueNoise bands = new SeededValueNoise(chunk.worldSeed("candy_bands"));
            CaveChunk.BlockSink blocks = chunk.blocks();
            // The mod's surface rule runs before the cake cave is carved: blocks the cave opened count as rock.
            List<CaveBowl> caves = bowl.cavesReaching(chunk);
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    double bandNoise = bands.sample(x / 32.0, z / 32.0);
                    boolean bandHere = bandNoise > -0.7 && bandNoise <= 0.8;
                    int top = chunk.surfaceY(x, z) - BAND_TOP;
                    int under = 0;
                    for (int y = top; y >= Math.max(chunk.minY() + 5, top - BAND_DEPTH); y--) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            under = 0;
                            continue;
                        }
                        Material type = blocks.getType(x, y, z);
                        if (type.isAir() && BowlFeature.carvesAir(caves, x, y, z)) {
                            under = Math.max(0, under - 1);
                            continue;
                        }
                        if (CavernFeature.vanillaCaveDecoration(type)) {
                            blocks.setBlock(x, y, z, air);
                            under = 0;
                            continue;
                        }
                        if (!CavernFeature.surfaceRock(type)) {
                            under = 0;
                            continue;
                        }
                        Material above = blocks.getType(x, y + 1, z);
                        if (FeatureShapes.isAir(above.name()) && !BowlFeature.carvesAir(caves, x, y + 1, z)) {
                            blocks.setBlock(x, y, z, frosted);
                            under = 3;
                        } else if (above == Material.WATER) {
                            blocks.setBlock(x, y, z, chocolate);
                            under = 3;
                        } else if (under > 0) {
                            blocks.setBlock(x, y, z, chocolate);
                            under--;
                        } else {
                            blocks.setBlock(x, y, z, bandHere && isBand(y) ? chocolate : cake);
                        }
                    }
                }
            }
        }

        /** Mod {@code createBands(20, 2, 10, chocolate)}. */
        static boolean isBand(int y) {
            for (int i = 1; i <= 20; i++) {
                int bottom = 62 - 10 * i;
                int extra = i % 3 == 0 ? 1 : 0;
                if (y >= bottom && y <= bottom + 1 + extra) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The cake cave's floor (mod {@code CakeCaveStructurePiece.decorateFloor} and {@code generateRiverAt}). */
    static final class CakeFloor implements ChunkFeature {
        private final BlockData air;
        private final BlockData soda = Bukkit.createBlockData(Material.WATER);
        private final BlockData chocolate;
        private final BlockData frosted;
        private final BlockData gingerbread;
        private final BlockData gingerbreadBricks;
        private final BlockData whiteRockCandy;
        private final BlockData[] road;
        private final BowlFeature bowl;

        CakeFloor(ModBlockPalette palette, BlockData air, BowlFeature bowl) {
            this.bowl = bowl;
            this.air = air;
            this.chocolate = palette.get("block_of_chocolate", "y");
            this.frosted = palette.get("block_of_frosted_chocolate");
            this.gingerbread = palette.get("gingerbread_block");
            this.gingerbreadBricks = palette.get("gingerbread_bricks");
            this.whiteRockCandy = palette.get("rock_candy_white");
            this.road = new BlockData[ROAD.length];
            for (int i = 0; i < ROAD.length; i++) {
                road[i] = palette.get(ROAD[i]);
            }
        }

        @Override
        public String id() {
            return "candy_cake_floor";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            // Mod: new VoronoiGenerator(level.getSeed()).
            long seed = chunk.seed();
            List<CaveBowl> caves = bowl.cavesReaching(chunk);
            if (caves.isEmpty()) {
                return;
            }
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    // The cake cave carves down to the plateau: its floor is the block just under it.
                    int floor = (int) CaveBowl.plateauHeight(x, z, 7, true) - 1;
                    // Mod doFloor: only the columns the cake cave itself opened.
                    if (!chunk.isBiome(x, floor, z, BIOME) || !blocks.getType(x, floor, z).isSolid()
                        || !FeatureShapes.isAir(blocks.getType(x, floor + 1, z).name())
                        || !BowlFeature.carvesAir(caves, x, floor + 1, z)) {
                        continue;
                    }
                    decorate(new Clipped(blocks, floor), x, floor, z, random, seed);
                }
            }
        }

        /**
         * Mod {@code checkedSetBlock}: writes stay inside the 16-block piece that holds the floor, from 2 below it
         * (pieces start at y = -48 + 16k).
         */
        record Clipped(CaveChunk.BlockSink blocks, int bottom, int top) {
            Clipped(CaveChunk.BlockSink blocks, int floor) {
                this(blocks, corner(floor) - 2, corner(floor) + 16);
            }

            static int corner(int floor) {
                return -48 + 16 * Math.floorDiv(floor + 1 + 48, 16);
            }

            void setBlock(int x, int y, int z, BlockData data) {
                if (y >= bottom && y <= top) {
                    blocks.setBlock(x, y, z, data);
                }
            }
        }

        private void decorate(Clipped blocks, int x, int y, int z, SplittableRandom random, long seed) {
            int underDownFor = 1 + random.nextInt(2);
            float roadNoise = Math.abs(SimplexNoise3D.sample2D(x + 1200, z + 10222, 100.0F));
            float roadNoiseSq = (float) Math.pow(roadNoise, 3.0);
            double riveriness = riveriness(x, z);
            boolean river = river(blocks, riveriness, x, y, z, random);
            boolean roadFlag = false;
            BlockData top = frosted;
            BlockData under = chocolate;
            double roadRadius = 0.001;
            if (roadNoiseSq < roadRadius) {
                ModVoronoi.Info info = ModVoronoi.get2(seed, 1.0, x * 0.025F, z * 0.025F);
                double normalized = info.distance() * ROAD.length;
                double border = normalized % 1.0;
                boolean edge = roadNoiseSq >= roadRadius - 7.5E-4;
                if (river) {
                    top = edge ? gingerbread : gingerbreadBricks;
                    underDownFor = 0;
                } else if ((!(border > 0.45F) || !(border < 0.65F)) && !edge) {
                    int index = Math.max(0, Math.min(ROAD.length - 1, (int) Math.round(normalized)));
                    top = road[index];
                    underDownFor = 1;
                } else {
                    top = whiteRockCandy;
                    underDownFor = 0;
                }
                under = whiteRockCandy;
                roadFlag = true;
            }
            if (!river || roadFlag) {
                blocks.setBlock(x, y, z, top);
            }
            if (!river) {
                for (int i = 0; i < underDownFor; i++) {
                    blocks.setBlock(x, y - 1 - i, z, under);
                }
            }
        }

        /** Mod {@code generateRiverAt}. */
        private boolean river(Clipped blocks, double riveriness, int x, int y, int z, SplittableRandom random) {
            double plateau = CaveBowl.plateauHeight(x, z, 0, false);
            if (riveriness < 1000.0) {
                int depth = 3 + (int) ((1.0 - Math.sqrt(riveriness / 1000.0)) * 8.0)
                    + (int) (1.75F * SimplexNoise3D.sample2D(x + 123, z + 120, 10.0F));
                int ry = (int) plateau + 15;
                while (ry > y - depth) {
                    ry--;
                    blocks.setBlock(x, ry, z, ry < (int) plateau ? soda : air);
                }
                blocks.setBlock(x, ry - 1, z, chocolate);
                return true;
            }
            if (riveriness < 3000.0) {
                for (int i = 0; i < 2 + random.nextInt(2); i++) {
                    blocks.setBlock(x, y - i, z, chocolate);
                }
                return true;
            }
            return false;
        }

        /** Mod {@code getRiveriness}: below 1000 a river, below 3000 its chocolate bank. */
        static double riveriness(int x, int z) {
            int rx = x + (int) (350.0F * SimplexNoise3D.sample2D(x + 1200, z - 1200, 150.0F));
            int rz = z + (int) (350.0F * SimplexNoise3D.sample2D(x - 1200, z + 1200, 150.0F));
            float noise = Math.abs(SimplexNoise3D.sample2D(rx + 68200, rz - 2248, 1000.0F));
            return (float) Math.pow(noise * 1000.0F, 2.0);
        }
    }

    /**
     * Mod {@code SweetPuffFeature} (10-40 per chunk, height in the lowest 160 blocks, scanned down to the floor): on
     * a solid floor, 3-5 tries in an 8x8 square, each dropping to the floor and, on frosted chocolate with air
     * above, placing a lollipop bunch (15%) or a sweet puff.
     */
    static final class SweetPuffs implements ChunkFeature {
        private final BlockData frostedChocolate;
        private final BlockData sweetPuff;
        private final BlockData lollipopBunch;

        SweetPuffs(BlockData frostedChocolate, BlockData sweetPuff, BlockData lollipopBunch) {
            this.frostedChocolate = frostedChocolate;
            this.sweetPuff = sweetPuff;
            this.lollipopBunch = lollipopBunch;
        }

        @Override
        public String id() {
            return "candy_sweet_puff";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int count = ModPlacement.uniform(random, 10, 40);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int start = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160);
                int floor = ModPlacement.scanDown(chunk, x, start, z, 32, ModPlacement::notAir);
                if (floor == Integer.MIN_VALUE || !chunk.isBiome(x, floor + 1, z, BIOME)
                    || !blocks.getType(x, floor, z).isSolid()) {
                    continue;
                }
                int tries = 3 + random.nextInt(3);
                for (int attempt = 0; attempt < tries; attempt++) {
                    int bx = x + random.nextInt(8) - 4;
                    int bz = z + random.nextInt(8) - 4;
                    int by = floor + 2;
                    while (by > chunk.minY() && blocks.isInRegion(bx, by, bz) && blocks.getType(bx, by, bz).isAir()) {
                        by--;
                    }
                    if (blocks.isInRegion(bx, by + 1, bz) && blocks.getBlockData(bx, by, bz).equals(frostedChocolate)
                        && blocks.getType(bx, by + 1, bz).isAir() && chunk.isBiome(bx, by + 1, bz, BIOME)) {
                        blocks.setBlock(bx, by + 1, bz, random.nextFloat() < 0.15F ? lollipopBunch : sweetPuff);
                    }
                }
            }
        }
    }

    /**
     * Mod {@code sundrop_patch} (once per chunk): a spot at a height in the lowest 160 blocks, scanned down to the
     * first block that is not air, moved at most one block up or down; the feature needs a solid block there and
     * makes 3-6 tries (the bound is drawn again on every pass) one block above and up to 6 blocks around, dropped
     * to the floor; a sundrop goes on frosted chocolate with air above. Only upward sundrops: the module block has
     * no other facing.
     */
    static final class SundropPatches implements ChunkFeature {
        private final BlockData frostedChocolate;
        private final BlockData sundrop;
        private final java.util.function.Predicate<Material> solid;

        SundropPatches(BlockData frostedChocolate, BlockData sundrop) {
            this(frostedChocolate, sundrop, Material::isSolid);
        }

        SundropPatches(BlockData frostedChocolate, BlockData sundrop, java.util.function.Predicate<Material> solid) {
            this.frostedChocolate = frostedChocolate;
            this.sundrop = sundrop;
            this.solid = solid;
        }

        private static boolean air(CaveChunk.BlockSink blocks, int x, int y, int z) {
            return FeatureShapes.isAir(blocks.getType(x, y, z).name());
        }

        @Override
        public String id() {
            return "candy_sundrop_patch";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int start = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160);
            int found = ModPlacement.scanDown(chunk, x, start, z, 32,
                material -> !FeatureShapes.isAir(material.name()) && material != Material.LIGHT);
            if (found == Integer.MIN_VALUE) {
                return;
            }
            // random_offset: y_spread 1 is uniform in [-1, 1].
            int y = found + random.nextInt(3) - 1;
            if (!blocks.isInRegion(x, y - 1, z) || !chunk.isBiome(x, y, z, BIOME)
                || !solid.test(blocks.getType(x, y - 1, z))) {
                return;
            }
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                int bx = x + random.nextInt(12) - 6;
                int by = y + 1;
                int bz = z + random.nextInt(12) - 6;
                while (blocks.isInRegion(bx, by, bz) && air(blocks, bx, by, bz) && by > chunk.minY()) {
                    by--;
                }
                if (blocks.isInRegion(bx, by + 1, bz) && blocks.getBlockData(bx, by, bz).equals(frostedChocolate)
                    && air(blocks, bx, by + 1, bz)) {
                    blocks.setBlock(bx, by + 1, bz, sundrop);
                }
            }
        }
    }
}
