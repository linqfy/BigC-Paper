package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.LakeFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.PlantPatchFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.OreFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SeededValueNoise;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Terrain of the Primordial Caves, after the mod's surface rules and features: an
 * open cavern (the mod carves it with its own noise), limestone rock with
 * sandstone bands, grass floors over dirt or packed mud, ferns and grass, mossy
 * limestone boulders, and ambersol orbs on the ceiling that light the cavern
 * below them. Trees, plants and structures come in later parts.
 */
public final class PrimordialFeatures {
    static final CaveBiomeId BIOME = CaveBiomeId.PRIMORDIAL_CAVES;
    /** The biome's depth band in blocks below the surface (CaveBiomeId: 0.15 to 1.5 depth units of 128). */
    static final int BAND_TOP = (int) Math.floor(CaveBiomeId.PRIMORDIAL_CAVES.minDepth() * 128.0);
    static final int BAND_DEPTH = (int) Math.ceil((CaveBiomeId.PRIMORDIAL_CAVES.maxDepth()
        - CaveBiomeId.PRIMORDIAL_CAVES.minDepth()) * 128.0);
    /** Cavern floors are searched from this depth below the surface down. */
    private static final int FLOOR_SEARCH_BELOW_SURFACE = 20;

    private PrimordialFeatures() {
    }

    public static List<ChunkFeature> create(ModBlockPalette palette) {
        Vanilla vanilla = new Vanilla();
        // Without Paper's tripwire options the plants would be solid note blocks: none are generated then.
        boolean plants = palette.registry().shapedBasesActive();
        // Mod dino bowl walls liquids with sandstone (DinoBowlStructurePiece.surroundCornerOfLiquid).
        List<ChunkFeature> features = new java.util.ArrayList<>(List.of(new BowlFeature("primordial_cavern", BIOME, CaveBowl.Kind.DINO_BOWL, vanilla.air, vanilla.sandstone,
                vanilla.sandstone, null), new Terrain(palette, vanilla),
            new Volcano.Feature(palette),
            // Mod primordial_caves_lake: 10-20 tries, each 1 in 7; the scan also skips lava.
            new LakeFeature("primordial_caves_lake", BIOME, 10, 20, 7, 0,
                material -> ModPlacement.notAir(material) && material != Material.LAVA, vanilla.water, vanilla.packedMud,
                vanilla.air),
            // Mod caveman_house: 1 chunk in 6, on packed mud, raised 4 with packed mud supports.
            // Their cave paintings: smooth limestone with the painting's display (CavePaintings).
            new RuinsFeature("caveman_house", BIOME, 6, -4, StructureTemplates.load("caveman_house"), true, vanilla.packedMud)
                .withPaintings(StructureTemplates.loadPaintings("caveman_house"), palette.get("smooth_limestone")),
            // Mod ore features over the surface rule: packed mud patches in the dirt, moss patches in the grass.
            new OreFeature("primordial_packed_mud_replace_dirt", BIOME, 30, 64, -100, false, 100, true,
                OreFeature.Target.of(Material.DIRT), vanilla.packedMud),
            new OreFeature("primordial_moss_replace_grass", BIOME, 22, 64, -100, false, 100, true,
                OreFeature.Target.of(Material.GRASS_BLOCK), vanilla.mossBlock),
            new Roosts(palette, vanilla), new Rocks(palette, vanilla), new Volcano.Boulders(palette),
            new Ambersol(palette, vanilla), new PewenTrees(palette, vanilla), new AncientTrees(vanilla), new GiantAncientTrees(vanilla),
            new ArchaicVines(vanilla), new AmberMonolithFeature(palette),
            plants ? new Grass(vanilla, palette.get("curly_fern"), palette.get("curly_fern", "upper"))
                : new Grass(vanilla, vanilla.largeFernLower, vanilla.largeFernUpper)));
        if (plants) {
            // Mod flytrap: random_patch of 40 tries (spread 7 / 3), 5-9 per chunk, 1 chunk in 2.
            features.add(new PlantPatchFeature("primordial_flytrap", BIOME, 2, 5, 9, 40, 7, 3,
                List.of(palette.get("flytrap"))));
            // Mod cycad: the stack's tip is the default variant, the rest of the stack is the trunk.
            features.add(new Cycads(palette.get("cycad"), palette.get("cycad", "trunk")));
        }
        return List.copyOf(features);
    }

    /** Vanilla states, created once on the main thread when the module enables. */
    static final class Vanilla {
        final BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);
        final BlockData grassBlock = Bukkit.createBlockData(Material.GRASS_BLOCK);
        final BlockData stone = Bukkit.createBlockData(Material.STONE);
        final BlockData dirt = Bukkit.createBlockData(Material.DIRT);
        final BlockData packedMud = Bukkit.createBlockData(Material.PACKED_MUD);
        final BlockData sandstone = Bukkit.createBlockData(Material.SANDSTONE);
        final BlockData shortGrass = Bukkit.createBlockData(Material.SHORT_GRASS);
        final BlockData fern = Bukkit.createBlockData(Material.FERN);
        final BlockData largeFernLower = Bukkit.createBlockData("minecraft:large_fern[half=lower]");
        final BlockData largeFernUpper = Bukkit.createBlockData("minecraft:large_fern[half=upper]");
        final BlockData mossCarpet = Bukkit.createBlockData(Material.MOSS_CARPET);
        final BlockData mossBlock = Bukkit.createBlockData(Material.MOSS_BLOCK);
        final BlockData water = Bukkit.createBlockData(Material.WATER);
        final BlockData light = Bukkit.createBlockData("minecraft:light[level=15]");
        final BlockData jungleLog = Bukkit.createBlockData("minecraft:jungle_log[axis=y]");
        final BlockData jungleLogX = Bukkit.createBlockData("minecraft:jungle_log[axis=x]");
        final BlockData jungleLogZ = Bukkit.createBlockData("minecraft:jungle_log[axis=z]");
        // Persistent: the module's pewen logs are note blocks, which vanilla leaves do not count as logs.
        final BlockData ancientLeaves = Bukkit.createBlockData("minecraft:jungle_leaves[persistent=true]");
        final BlockData pewenPines = Bukkit.createBlockData("minecraft:spruce_leaves[persistent=true]");
        // Archaic vines (mod: block_column of 1-11 vine blocks and a tip). Age 25 stops the tip growing.
        final BlockData vine = Bukkit.createBlockData("minecraft:cave_vines_plant[berries=false]");
        final BlockData vineTip = Bukkit.createBlockData("minecraft:cave_vines[age=25,berries=false]");
    }

    /** The open cavern itself. Carving stops at anything that is not plain rock, dirt or ore. */
    /** Primordial cavern: floor around y=4 (±10), 24 blocks high (±16), pillars a few blocks wide. */
    static final class Cavern {
        private Cavern() {
        }

        static boolean carvable(Material material) {
            return CavernFeature.carvable(material);
        }
    }

    /** Mod surface rules: grass floor (dirt under water), dirt or packed mud below, sandstone bands, limestone. */
    static final class Terrain implements ChunkFeature {
        private final ModBlockPalette palette;
        private final Vanilla vanilla;

        Terrain(ModBlockPalette palette, Vanilla vanilla) {
            this.palette = palette;
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_terrain";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SeededValueNoise mud = new SeededValueNoise(chunk.worldSeed("primordial_mud"));
            SeededValueNoise bands = new SeededValueNoise(chunk.worldSeed("primordial_bands"));
            // Vanilla ores look for stone after the mod's surface rule, so none survive in the biome.
            BlockData limestone = palette.get("limestone");
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    BlockData soil = mud.sample(x / 24.0, z / 24.0) > 0.2 ? vanilla.packedMud : vanilla.dirt;
                    // Mod band condition: a noise between -0.7 and 0.8 leaves a few gaps in each band.
                    double bandNoise = bands.sample(x / 32.0, z / 32.0);
                    boolean bandHere = bandNoise > -0.7 && bandNoise <= 0.8;
                    int top = chunk.surfaceY(x, z) - BAND_TOP;
                    int soilLeft = 0;
                    for (int y = top; y >= Math.max(chunk.minY() + 5, top - BAND_DEPTH); y--) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            soilLeft = 0;
                            continue;
                        }
                        Material type = blocks.getType(x, y, z);
                        if (CavernFeature.vanillaCaveDecoration(type)) {
                            blocks.setBlock(x, y, z, vanilla.air);
                            soilLeft = 0;
                            continue;
                        }
                        if (!CavernFeature.surfaceRock(type)) {
                            soilLeft = 0;
                            continue;
                        }
                        Material above = blocks.getType(x, y + 1, z);
                        if (FeatureShapes.isAir(above.name()) || above == Material.WATER) {
                            blocks.setBlock(x, y, z, above == Material.WATER ? soil : vanilla.grassBlock);
                            soilLeft = 3;
                        } else if (soilLeft > 0) {
                            blocks.setBlock(x, y, z, soil);
                            soilLeft--;
                        } else {
                            blocks.setBlock(x, y, z, bandHere && isBand(y) ? vanilla.sandstone : limestone);
                        }
                    }
                }
            }
        }

        /**
         * Mod {@code createBands(15, 1, 20, sandstone)}: 15 flat bands, one block thick, at y = 62 - 20i (two blocks
         * thick when i is a multiple of 3), from y=42 down.
         */
        static boolean isBand(int y) {
            for (int i = 1; i <= 15; i++) {
                int bottom = 62 - 20 * i;
                int extra = i % 3 == 0 ? 1 : 0;
                if (y >= bottom && y <= bottom + extra) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Limestone boulders covered with moss carpet (mod: {@code primordial_forest_rock}, 1 in 4 chunks). */
    static final class Rocks implements ChunkFeature {
        private final ModBlockPalette palette;
        private final Vanilla vanilla;

        Rocks(ModBlockPalette palette, Vanilla vanilla) {
            this.palette = palette;
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_forest_rock";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            BlockData limestone = palette.get("limestone");
            // Mod placement: 10-20 tries, each kept 1 in 4, scanned down to a floor.
            int tries = ModPlacement.uniform(random, 10, 20);
            int kept = 0;
            for (int i = 0; i < tries; i++) {
                if (ModPlacement.rarity(random, 4)) {
                    kept++;
                }
            }
            for (int[] origin : floorOrigins(chunk, random, kept)) {
                int x = origin[0];
                int z = origin[2];
                int floor = origin[1] - 1;
                double radius = 1.5 + random.nextDouble() * 1.5;
                int r = (int) Math.ceil(radius);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -1; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (FeatureShapes.inSphere(dx, dy, dz, radius)) {
                                placeIfReplaceable(chunk, x + dx, floor + 1 + dy, z + dz, limestone);
                            }
                        }
                    }
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int y = floor + 1 + r; y >= floor; y--) {
                            if (limestone.equals(chunk.blocks().getBlockData(x + dx, y, z + dz))) {
                                placeIfReplaceable(chunk, x + dx, y + 1, z + dz, vanilla.mossCarpet);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Ambersol orbs hanging from the ceiling (mod {@code AmbersolFeature}): amber balls around one
     * ambersol block, and a column of light blocks down to the floor (mod: {@code ambersol_light}).
     */
    static final class Ambersol implements ChunkFeature {
        private static final int LIGHT_SPACING = 3;
        private final ModBlockPalette palette;
        private final Vanilla vanilla;

        Ambersol(ModBlockPalette palette, Vanilla vanilla) {
            this.palette = palette;
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_ambersol";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            BlockData amber = palette.get("amber");
            BlockData ambersol = palette.get("ambersol");
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int attempt = 0; attempt < 1 + random.nextInt(2); attempt++) {
                int x = chunk.minBlockX() + 3 + random.nextInt(10);
                int z = chunk.minBlockZ() + 3 + random.nextInt(10);
                int floor = findFloor(chunk, x, z);
                if (floor == Integer.MIN_VALUE) {
                    continue;
                }
                int ceiling = floor + 1;
                while (ceiling < chunk.surfaceY(x, z) && FeatureShapes.isAir(blocks.getType(x, ceiling, z).name())) {
                    ceiling++;
                }
                if (ceiling >= chunk.surfaceY(x, z) || ceiling - floor < 8 || !chunk.isBiome(x, ceiling, z, BIOME)) {
                    continue;
                }
                int center = ceiling - 1;
                for (int orb = 0; orb < 3; orb++) {
                    orb(chunk, x + random.nextInt(4) - 2, center + random.nextInt(4) - 2, z + random.nextInt(4) - 2,
                        2 + random.nextInt(2), random, amber);
                }
                orb(chunk, x, center, z, 2, random, amber);
                blocks.setBlock(x, center, z, ambersol);
                // Light column below the orb, down to the floor.
                boolean belowOrb = false;
                int step = 0;
                for (int y = center - 1; y > floor; y--) {
                    if (!FeatureShapes.isAir(blocks.getType(x, y, z).name())) {
                        if (belowOrb) {
                            break;
                        }
                        continue;
                    }
                    belowOrb = true;
                    if (step++ % LIGHT_SPACING == 0) {
                        blocks.setBlock(x, y, z, vanilla.light);
                    }
                }
            }
        }

        private static void orb(CaveChunk chunk, int cx, int cy, int cz, int radius, SplittableRandom random, BlockData amber) {
            double limit = radius * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= limit - random.nextDouble() * 4.0) {
                            int x = cx + dx;
                            int y = cy + dy;
                            int z = cz + dz;
                            if (chunk.blocks().isInRegion(x, y, z) && chunk.isBiome(x, y, z, BIOME)
                                && FeatureShapes.isAir(chunk.blocks().getType(x, y, z).name())) {
                                chunk.blocks().setBlock(x, y, z, amber);
                            }
                        }
                    }
                }
            }
        }
    }

    /** The 8 branch directions of the mod's {@code PewenBranchBlock} rotations (N, NE, E, SE, S, SW, W, NW). */
    static final int[][] BRANCH_DIRECTIONS = {
        {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /**
     * Pewen (monkey puzzle) trees, after {@code PewenTreeFeature}: a tall pewen trunk with whorls of
     * branches at the top. The mod's thin branch and pine blocks are drawn as pewen logs (first
     * block of the straight branches) and spruce leaves, since only full cubes can be note blocks.
     */
    static final class PewenTrees implements ChunkFeature {
        private final ModBlockPalette palette;
        private final Vanilla vanilla;

        PewenTrees(ModBlockPalette palette, Vanilla vanilla) {
            this.palette = palette;
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_pewen_tree";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            for (int[] origin : floorOrigins(chunk, random, noiseCount(chunk, 16.0, 0.7, 120.0))) {
                int x = origin[0];
                int floor = origin[1] - 1;
                int z = origin[2];
                int height = 11 + random.nextInt(10);
                int levels = 3 + random.nextInt(2);
                if (!isSoil(chunk.blocks().getType(x, floor, z))
                    || !hasRoom(chunk, x, floor + 1, z, height + 2, levels > 3 ? 3 : 2)) {
                    continue;
                }
                grow(chunk, x, floor + 1, z, height, levels, random);
            }
        }

        private void grow(CaveChunk chunk, int x, int baseY, int z, int height, int levels, SplittableRandom random) {
            int crownStart = height - levels;
            for (int i = 0; i <= height; i++) {
                int y = baseY + i;
                chunk.blocks().setBlock(x, y, z, palette.get(i == height ? "pewen_wood" : "pewen_log", "y"));
                if (i == height) {
                    placeIfReplaceable(chunk, x, y + 1, z, vanilla.pewenPines);
                }
                if (i > crownStart) {
                    whorl(chunk, x, y, z, levels - (i - crownStart) + 1);
                } else if (i == crownStart) {
                    whorl(chunk, x, y, z, 1);
                } else if (random.nextInt(5) == 0) {
                    int[] d = BRANCH_DIRECTIONS[random.nextInt(7)];
                    placeIfReplaceable(chunk, x + d[0], y, z + d[1], vanilla.pewenPines);
                }
            }
        }

        private void whorl(CaveChunk chunk, int x, int y, int z, int width) {
            for (int rotation = 0; rotation < 8; rotation++) {
                int[] d = BRANCH_DIRECTIONS[rotation];
                boolean diagonal = rotation % 2 != 0;
                int length = diagonal && width > 1 ? width - 1 : width;
                for (int step = 1; step <= length; step++) {
                    BlockData block = !diagonal && step == 1 && length > 1
                        ? palette.get("pewen_log", d[0] != 0 ? "x" : "z") : vanilla.pewenPines;
                    placeIfReplaceable(chunk, x + d[0] * step, y, z + d[1] * step, block);
                }
            }
        }
    }

    /**
     * Ancient trees, after {@code AncientTreeFeature}: a short jungle log trunk with roots and four
     * branches, each ending in a leaf ball (mod: ancient leaves, drawn as jungle leaves). One in four
     * chunks also tries a giant one (taller trunk, leaf balls along it).
     */
    static final class AncientTrees implements ChunkFeature {
        private final Vanilla vanilla;

        AncientTrees(Vanilla vanilla) {
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_ancient_tree";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            for (int[] origin : floorOrigins(chunk, random, noiseCount(chunk, 35.0, 0.1, 40.0))) {
                int x = origin[0];
                int floor = origin[1] - 1;
                int z = origin[2];
                if (isSoil(chunk.blocks().getType(x, floor, z)) && hasRoom(chunk, x, floor + 1, z, 12, 3)) {
                    normal(chunk, x, floor + 1, z, 3 + random.nextInt(4), random);
                }
            }
        }

        private void normal(CaveChunk chunk, int x, int baseY, int z, int height, SplittableRandom random) {
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : sides) {
                for (int i = 0; i < random.nextInt(2); i++) {
                    placeIfReplaceable(chunk, x + d[0], baseY + i, z + d[1], vanilla.jungleLog);
                }
                int canopy = baseY + height;
                int out = 1 + random.nextInt(2);
                BlockData horizontal = d[0] != 0 ? vanilla.jungleLogX : vanilla.jungleLogZ;
                for (int i = 1; i <= out; i++) {
                    placeIfReplaceable(chunk, x + d[0] * i, canopy, z + d[1] * i, horizontal);
                }
                for (int i = 1; i <= 2; i++) {
                    placeIfReplaceable(chunk, x + d[0] * (out + 1), canopy + i, z + d[1] * (out + 1), vanilla.jungleLog);
                }
                leafBall(chunk, x + d[0] * out, canopy + 3, z + d[1] * out, 2 + random.nextInt(2),
                    4 + random.nextInt(2), random);
            }
            for (int i = 0; i <= height; i++) {
                chunk.blocks().setBlock(x, baseY + i, z, vanilla.jungleLog);
            }
        }

        /** Mod {@code drawLeafOrb}: a flattened ball, a bit ragged at the edge. */
        void leafBall(CaveChunk chunk, int cx, int cy, int cz, int radius, int radiusY, SplittableRandom random) {
            double equal = (radius * 2 + radiusY) / 3.0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radiusY / 3; dy <= radiusY; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= equal * equal - random.nextDouble() * 7.0) {
                            placeIfReplaceable(chunk, cx + dx, cy + dy, cz + dz, vanilla.ancientLeaves);
                        }
                    }
                }
            }
        }
    }

    /**
     * Giant ancient trees (mod {@code GiantAncientTreeFeature}, 4-17 tries per chunk): a 3-wide trunk 20-29 tall that
     * narrows towards the top, with leaf orbs every 4-5 blocks that shrink with height. The mod's tree stars on
     * the leaves are left out (the module has no tree star block).
     */
    static final class GiantAncientTrees implements ChunkFeature {
        private final Vanilla vanilla;
        private final AncientTrees leaves;

        GiantAncientTrees(Vanilla vanilla) {
            this.vanilla = vanilla;
            this.leaves = new AncientTrees(vanilla);
        }

        @Override
        public String id() {
            return "primordial_giant_ancient_tree";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            for (int[] origin : floorOrigins(chunk, random, ModPlacement.uniform(random, 4, 17))) {
                int maxHeight = 20 + random.nextInt(10);
                if (canPlace(chunk, origin[0], origin[1], origin[2], maxHeight)) {
                    grow(chunk, origin[0], origin[1], origin[2], maxHeight, random);
                }
            }
        }

        /** Mod {@code GiantAncientTreeFeature.drawLeafOrb}. */
        private void leafOrb(CaveChunk chunk, int cx, int cy, int cz, int radiusX, int radiusY, int radiusZ,
                             SplittableRandom random) {
            double equal = (radiusX + radiusY + radiusZ) / 3.0;
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dy = -radiusY / 3; dy <= radiusY; dy++) {
                    for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= equal * equal - random.nextFloat() * 2.0F) {
                            placeIfReplaceable(chunk, cx + dx, cy + dy, cz + dz, vanilla.ancientLeaves);
                        }
                    }
                }
            }
        }

        private boolean canPlace(CaveChunk chunk, int x, int y, int z, int height) {
            if (!isSoil(chunk.blocks().getType(x, y - 1, z))) {
                return false;
            }
            for (int i = 0; i < height; i++) {
                if (!replaceable(chunk.blocks().getType(x, y + i, z).name())) {
                    return false;
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (!replaceable(chunk.blocks().getType(x + dx, y + height + dy, z + dz).name())) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void grow(CaveChunk chunk, int x, int bottom, int z, int maxHeight, SplittableRandom random) {
            int trunk = 3;
            int lastLeaves = 0;
            int y = bottom - 2;
            for (int height = 0; height <= maxHeight; height++) {
                y++;
                double radShrink = 1.0 - 0.8 * Math.pow((float) height / maxHeight, 2.0);
                double leavesShrink = 1.0F - 0.45F * ((float) height / maxHeight);
                for (int dx = -1; dx < 2; dx++) {
                    for (int dz = -1; dz < 2; dz++) {
                        // Mod distToCenterSqr from the trunk root to the log position.
                        double distance = (0.5 - dx) * (0.5 - dx) + 0.25 + (0.5 - dz) * (0.5 - dz);
                        if (distance <= trunk * trunk / 4.0 * radShrink) {
                            placeIfReplaceable(chunk, x + dx, y, z + dz, vanilla.jungleLog);
                        }
                    }
                }
                if (height == maxHeight || height > 3 && (height - lastLeaves > 4 + random.nextInt(2) || random.nextInt(5) == 0)) {
                    lastLeaves = height;
                    int ox = (int) ((random.nextInt(2) - 1) * radShrink);
                    int oz = (int) ((random.nextInt(2) - 1) * radShrink);
                    int radiusX = (int) Math.ceil(4.0 * leavesShrink) + random.nextInt(2);
                    int radiusY = 1 + random.nextInt(2);
                    int radiusZ = (int) Math.ceil(4.0 * leavesShrink) + random.nextInt(2);
                    leafOrb(chunk, x + ox, y, z + oz, radiusX, radiusY, radiusZ, random);
                }
            }
        }
    }

    /**
     * Mod placement shared by the trees and boulders: random spots of the chunk at a random height, scanned down at
     * most 32 blocks to the first solid block, in the biome. Returns the open block above that floor.
     */
    static java.util.List<int[]> floorOrigins(CaveChunk chunk, SplittableRandom random, int count) {
        java.util.List<int[]> origins = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
            if (!FeatureShapes.isAir(chunk.blocks().getType(x, y, z).name())) {
                continue;
            }
            int floor = ModPlacement.scanDown(chunk, x, y, z, 32, ModPlacement::notAir);
            if (floor != Integer.MIN_VALUE && chunk.isBiome(x, floor + 1, z, BIOME)) {
                origins.add(new int[] {x, floor + 1, z});
            }
        }
        return origins;
    }

    /**
     * Vanilla {@code noise_based_count}: {@code ceil((noise(x / factor, z / factor) + offset) * ratio)} tries, more
     * where a world-wide 2D noise is high, none where it is low.
     */
    static int noiseCount(CaveChunk chunk, double factor, double offset, double ratio) {
        return ModPlacement.noiseCount(chunk, factor, offset, ratio);
    }

    /**
     * Subterranodon roosts (mod {@code SubterranodonRoostFeature}, 22-45 tries per chunk between 45 above the bottom
     * and y=64): a crescent ledge sticking out of a cliff, of the rock it grows from (sandstone or limestone), with
     * fern thatch nests and moss. The mod also puts subterranodon eggs and a subterranodon on the nests; here eggs
     * are entities that cannot be made during generation, and subterranodons come from natural spawning.
     */
    static final class Roosts implements ChunkFeature {
        /** Horizontal directions in the mod's order (north, east, south, west). */
        private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        private final ModBlockPalette palette;
        private final Vanilla vanilla;

        /** Mod tag {@code volcano_blocks}: roosts never grow out of a volcano. */
        private final java.util.Set<BlockData> volcanic;

        Roosts(ModBlockPalette palette, Vanilla vanilla) {
            this.palette = palette;
            this.vanilla = vanilla;
            this.volcanic = java.util.Set.of(palette.get("flood_basalt", "x"), palette.get("flood_basalt", "y"),
                palette.get("flood_basalt", "z"), palette.get("primal_magma"), palette.get("primal_magma", "active"),
                palette.get("volcanic_core"));
        }

        @Override
        public String id() {
            return "primordial_subterranodon_roost";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 22, 45);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY() + 45, 64);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (chunk.isBiome(x, y, z, BIOME)) {
                    roost(chunk, x, y, z, random);
                }
            }
        }

        private void roost(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            if (!isAir(blocks, x, y - 1, z) || !isAir(blocks, x, y + 1, z)) {
                return;
            }
            int direction = cliffDirection(chunk, x, y, z, random, volcanic);
            if (direction < 0) {
                return;
            }
            int[] dir = HORIZONTAL[direction];
            int[] clockwise = HORIZONTAL[(direction + 1) % 4];
            int centerLength = 2 + random.nextInt(1);
            int maxLeft = 2 + random.nextInt(5);
            int maxRight = 2 + random.nextInt(5);
            // Start maxRight blocks counter-clockwise, then walk clockwise along the cliff.
            int cx = x - clockwise[0] * maxRight;
            int cz = z - clockwise[1] * maxRight;
            float radius = (maxLeft + maxRight) / 2.0F;
            for (int i = -maxRight; i < maxLeft; i++) {
                float dist = Math.abs(i) / radius;
                cx += clockwise[0];
                cz += clockwise[1];
                int length = (int) Math.round(centerLength * Math.sqrt(1.0F - dist));
                for (int j = 0; j < length + 1; j++) {
                    // Mod: the offset is applied after each fill, so the first two rows share the start.
                    int back = Math.max(0, j - 1);
                    fillCliff(chunk, cx - dir[0] * back, y - back, cz - dir[1] * back, dir, length, j == 0, random);
                }
            }
        }

        private void fillCliff(CaveChunk chunk, int x, int y, int z, int[] dir, float length, boolean decorate,
                               SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            int back = 0;
            int maxBack = (int) (Math.ceil(length) + 1.0);
            while (isAir(blocks, x, y, z) && back < maxBack) {
                back++;
                x -= dir[0];
                z -= dir[1];
            }
            BlockData set = vanilla.sandstone.equals(blocks.getBlockData(x, y, z)) ? vanilla.sandstone : palette.get("limestone");
            BlockData thatch = palette.get("fern_thatch");
            x += dir[0];
            z += dir[1];
            for (int i = 0; i < Math.ceil(length); i++) {
                if (isAir(blocks, x, y, z)) {
                    if (decorate && random.nextInt(3) == 0 && i == 0) {
                        blocks.setBlock(x, y, z, set);
                        blocks.setBlock(x, y + 1, z, thatch);
                    } else if (decorate && random.nextInt(7) == 0) {
                        blocks.setBlock(x, y - 1, z, set);
                        for (int[] side : HORIZONTAL) {
                            blocks.setBlock(x + side[0], y, z + side[1], thatch);
                            blocks.setBlock(x + side[0], y - 1, z + side[1], set);
                        }
                        blocks.setBlock(x, y, z, thatch);
                    } else {
                        blocks.setBlock(x, y, z, set);
                        if (decorate && random.nextInt(5) == 0) {
                            blocks.setBlock(x, y + 1, z, vanilla.mossCarpet);
                        }
                    }
                }
                x += dir[0];
                z += dir[1];
            }
        }

        /** Mod {@code getCliffDirection}: away from a solid side in the same chunk (not volcano rock). */
        private static int cliffDirection(CaveChunk chunk, int x, int y, int z, SplittableRandom random,
                                          java.util.Set<BlockData> volcanic) {
            java.util.List<Integer> options = new java.util.ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nx = x + HORIZONTAL[d][0];
                int nz = z + HORIZONTAL[d][1];
                if (nx >> 4 == x >> 4 && nz >> 4 == z >> 4 && chunk.blocks().getType(nx, y, nz).isSolid()
                    && !volcanic.contains(chunk.blocks().getBlockData(nx, y, nz))) {
                    options.add((d + 2) % 4);
                }
            }
            if (options.isEmpty()) {
                return -1;
            }
            // Mod selectDirection: nextInt(size - 1), so with several options the last one is never picked.
            return options.size() == 1 ? options.get(0) : options.get(random.nextInt(options.size() - 1));
        }

        private static boolean isAir(CaveChunk.BlockSink blocks, int x, int y, int z) {
            return blocks.isInRegion(x, y, z) && FeatureShapes.isAir(blocks.getType(x, y, z).name());
        }
    }

    /** Vines hanging from the cavern roof (mod {@code archaic_vine}: 2 to 12 blocks long), as cave vines. */
    static final class ArchaicVines implements ChunkFeature {
        private final Vanilla vanilla;

        ArchaicVines(Vanilla vanilla) {
            this.vanilla = vanilla;
        }

        @Override
        public String id() {
            return "primordial_archaic_vine";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int floor = findFloor(chunk, x, z);
                if (floor == Integer.MIN_VALUE) {
                    continue;
                }
                int roof = floor + 1;
                while (roof < chunk.surfaceY(x, z) && FeatureShapes.isAir(blocks.getType(x, roof, z).name())) {
                    roof++;
                }
                if (roof >= chunk.surfaceY(x, z) || !blocks.getType(x, roof, z).isSolid() || !chunk.isBiome(x, roof, z, BIOME)) {
                    continue;
                }
                int length = Math.min(1 + random.nextInt(11), roof - floor - 3);
                for (int i = 1; i <= length; i++) {
                    blocks.setBlock(x, roof - i, z, i == length ? vanilla.vineTip : vanilla.vine);
                }
            }
        }
    }

    static boolean isSoil(Material material) {
        return material == Material.GRASS_BLOCK || material == Material.DIRT || material == Material.PACKED_MUD
            || material == Material.MOSS_BLOCK || material == Material.PODZOL;
    }

    /** Air (or plants) for a trunk of {@code height} and a crown of {@code radius} around its top. */
    private static boolean hasRoom(CaveChunk chunk, int x, int baseY, int z, int height, int radius) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        for (int i = 0; i < height; i++) {
            if (!replaceable(blocks.getType(x, baseY + i, z).name())) {
                return false;
            }
        }
        int top = baseY + height - 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (blocks.isInRegion(x + dx, top, z + dz) && !replaceable(blocks.getType(x + dx, top, z + dz).name())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean replaceable(String type) {
        return FeatureShapes.isAir(type) || type.equals("SHORT_GRASS") || type.equals("FERN")
            || type.equals("LARGE_FERN") || type.equals("MOSS_CARPET");
    }

    /** Grass, ferns and large ferns on grass floors (mod: {@code primordial_caves_grass}, 200-230 tries). */
    static final class Grass implements ChunkFeature {
        private final Vanilla vanilla;
        private final BlockData curlyFern;
        private final BlockData curlyFernTop;

        Grass(Vanilla vanilla, BlockData curlyFern, BlockData curlyFernTop) {
            this.vanilla = vanilla;
            this.curlyFern = curlyFern;
            this.curlyFernTop = curlyFernTop;
        }

        @Override
        public String id() {
            return "primordial_grass";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    int top = chunk.surfaceY(x, z) - BAND_TOP;
                    for (int y = top; y > Math.max(chunk.minY() + 5, top - BAND_DEPTH); y--) {
                        if (blocks.getType(x, y, z) != Material.GRASS_BLOCK || !chunk.isBiome(x, y, z, BIOME)
                            || !FeatureShapes.isAir(blocks.getType(x, y + 1, z).name()) || random.nextInt(10) >= 4) {
                            continue;
                        }
                        // Mod weights: grass 8, fern 20, large fern 5, curly fern 4 (one per facing; here one
                        // facing) out of 37.
                        int roll = random.nextInt(37);
                        if (roll < 8) {
                            blocks.setBlock(x, y + 1, z, vanilla.shortGrass);
                        } else if (roll < 28) {
                            blocks.setBlock(x, y + 1, z, vanilla.fern);
                        } else if (FeatureShapes.isAir(blocks.getType(x, y + 2, z).name())) {
                            boolean curly = roll >= 33;
                            blocks.setBlock(x, y + 1, z, curly ? curlyFern : vanilla.largeFernLower);
                            blocks.setBlock(x, y + 2, z, curly ? curlyFernTop : vanilla.largeFernUpper);
                        }
                    }
                }
            }
        }
    }

    /**
     * Mod {@code cycad} (placed feature 15-30 tries per chunk, {@code CycadFeature}): a spot at a random height scanned
     * down (32 steps) to the first block that is not air. When the block under that one is dirt, a stack of cycads is
     * built from that block up, {@code ceil(random * 2.5)} blocks above it, replacing only air and soft plants; the
     * last position is the tip. The scanned block itself is rock or grass, so in practice the stack starts on it.
     */
    static final class Cycads implements ChunkFeature {
        private final BlockData tip;
        private final BlockData trunk;
        private final Predicate<Material> dirt;

        Cycads(BlockData tip, BlockData trunk) {
            this(tip, trunk, Tag.DIRT::isTagged);
        }

        Cycads(BlockData tip, BlockData trunk, Predicate<Material> dirt) {
            this.tip = tip;
            this.trunk = trunk;
            this.dirt = dirt;
        }

        @Override
        public String id() {
            return "primordial_cycad";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int tries = ModPlacement.uniform(random, 15, 30);
            for (int i = 0; i < tries; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                int ground = ModPlacement.scanDown(chunk, x, y, z, 32,
                    material -> !FeatureShapes.isAir(material.name()) && material != Material.LIGHT);
                if (ground != Integer.MIN_VALUE && chunk.isBiome(x, ground, z, BIOME)) {
                    plant(chunk, x, ground, z, (float) random.nextDouble());
                }
            }
        }

        void plant(CaveChunk chunk, int x, int ground, int z, float roll) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            if (!blocks.isInRegion(x, ground - 1, z) || !dirt.test(blocks.getType(x, ground - 1, z))) {
                return;
            }
            int height = CycadRules.generatedHeight(roll);
            for (int i = 0; i <= height; i++) {
                if (blocks.isInRegion(x, ground + i, z) && replaceable(blocks.getType(x, ground + i, z).name())) {
                    blocks.setBlock(x, ground + i, z, i == height ? tip : trunk);
                }
            }
        }
    }

    /** Highest floor (solid with air above) of the cavern in a column, inside the biome. */
    static int findFloor(CaveChunk chunk, int x, int z) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        for (int y = chunk.surfaceY(x, z) - FLOOR_SEARCH_BELOW_SURFACE; y > chunk.minY() + 5; y--) {
            if (chunk.isBiome(x, y, z, BIOME) && blocks.getType(x, y, z).isSolid()
                && FeatureShapes.isAir(blocks.getType(x, y + 1, z).name())
                && FeatureShapes.isAir(blocks.getType(x, y + 2, z).name())) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void placeIfReplaceable(CaveChunk chunk, int x, int y, int z, BlockData data) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        if (!blocks.isInRegion(x, y, z) || !chunk.isBiome(x, y, z, BIOME)) {
            return;
        }
        if (replaceable(blocks.getType(x, y, z).name())) {
            blocks.setBlock(x, y, z, data);
        }
    }
}
