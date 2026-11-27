package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Terrain of the Abyssal Chasm, after the mod: the ocean trench ({@code OceanTrenchStructurePiece}) dug from the real
 * sea floor down towards bedrock and flooded, with tuff sea mounts rising in it, muck on its floor and walls of stone,
 * deepslate and abyssmarine; the biome's surface rule (abyssmarine under the sea floor); and its decoration with the
 * mod's placed-feature numbers: deepslate boulders with mussels, black vents and tube worms on the sea mounts, mussels
 * on the walls and ping pong sponges on the muck.
 *
 * <p>As in the mod, the decoration that uses the {@code OCEAN_FLOOR_WG} heightmap starts at the sea floor as it was
 * before the trench was dug (the heightmap is not updated by the dig) and sinks through the water to the trench floor.
 */
public final class AbyssalFeatures {
    static final CaveBiomeId BIOME = CaveBiomeId.ABYSSAL_CHASM;
    /** Vanilla overworld sea level. */
    static final int SEA_LEVEL = 63;
    static final int BAND_TOP = (int) Math.floor(BIOME.minDepth() * 128.0);
    static final int BAND_DEPTH = (int) Math.ceil((BIOME.maxDepth() - BIOME.minDepth()) * 128.0);

    private AbyssalFeatures() {
    }

    public static List<ChunkFeature> create(ModBlockPalette palette) {
        Blocks b = new Blocks(palette);
        Trench trench = new Trench(b);
        List<ChunkFeature> structures = AbyssalStructures.create(palette, b, trench);
        // Mod steps (each places its structures before its features): the trench and boulders, the abyssal ruins, then
        // the vents and whalefalls (underground structures), the Deep One ruins (surface structures), then the sea life.
        return List.of(new Terrain(b), trench, new Boulders(b), structures.get(1), new BlackVents(b), structures.get(0),
            structures.get(2), new TubeWorms(b), new Mussels(b), new Sponges(b));
    }

    /** Blocks of the biome, made on the main thread. */
    static final class Blocks {
        final BlockData water = Bukkit.createBlockData(Material.WATER);
        final BlockData tuff = Bukkit.createBlockData(Material.TUFF);
        final BlockData stone = Bukkit.createBlockData(Material.STONE);
        final BlockData deepslate = Bukkit.createBlockData(Material.DEEPSLATE);
        final BlockData magma = Bukkit.createBlockData(Material.MAGMA_BLOCK);
        final BlockData lava = Bukkit.createBlockData(Material.LAVA);
        final BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);
        final BlockData abyssmarine;
        final BlockData muck;
        final BlockData[][] mussels = new BlockData[6][5];
        final Map<String, BlockData> tubeWorms = new java.util.HashMap<>();
        final BlockData sponge;
        final BlockData spongeTop;
        final BlockData vent;
        final BlockData ventMedium;
        final BlockData ventThin;
        /** The module's sea blocks that are waterlogged and not solid in the mod. */
        final java.util.Set<BlockData> soft = new java.util.HashSet<>();

        Blocks(ModBlockPalette palette) {
            abyssmarine = palette.get("abyssmarine");
            muck = palette.get("muck");
            String[] facings = {"down", "up", "north", "south", "west", "east"};
            for (int f = 0; f < 6; f++) {
                for (int n = 0; n < 5; n++) {
                    mussels[f][n] = palette.get("mussel", facings[f] + "_" + (n + 1));
                }
            }
            for (String type : new String[] {"straight", "turn", "elbow"}) {
                for (String facing : new String[] {"north", "south", "west", "east"}) {
                    tubeWorms.put(type + "_" + facing, palette.get("tube_worm", type + "_" + facing));
                }
            }
            sponge = palette.get("ping_pong_sponge");
            spongeTop = palette.get("ping_pong_sponge", "top");
            vent = palette.get("geothermal_vent");
            ventMedium = palette.get("geothermal_vent_medium");
            ventThin = palette.get("geothermal_vent_thin");
            for (BlockData[] facing : mussels) {
                soft.addAll(java.util.List.of(facing));
            }
            soft.addAll(tubeWorms.values());
            soft.add(sponge);
            soft.add(spongeTop);
        }

        BlockData tubeWorm(String type, int horizontal) {
            return tubeWorms.get(type + "_" + HORIZONTAL_NAMES[horizontal]);
        }
    }

    /** Vanilla {@code Direction.values()}: down, up, north, south, west, east. */
    static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** Horizontal directions by {@code Direction.from3DDataValue} 2-5: north, south, west, east. */
    static final int[][] HORIZONTAL = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final String[] HORIZONTAL_NAMES = {"north", "south", "west", "east"};

    abstract static class Feature implements ChunkFeature {
        private final String id;
        final Blocks b;

        Feature(String id, Blocks b) {
            this.id = id;
            this.b = b;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        /**
         * The trench reaches 32 and more blocks past the biome and makes what it digs Abyssal Chasm (mod
         * {@code replaceBiomes}), so the biome's features run wherever the chunk has it, not only in its band.
         */
        @Override
        public boolean placesAnywhere() {
            return true;
        }

        static Material type(CaveChunk chunk, int x, int y, int z) {
            return chunk.blocks().getType(x, y, z);
        }

        /** Water, or one of the module's waterlogged sea blocks (mussels, tube worms, sponges: not solid in the mod). */
        boolean wet(CaveChunk chunk, int x, int y, int z) {
            Material material = type(chunk, x, y, z);
            if (material == Material.WATER || material == Material.BUBBLE_COLUMN) {
                return true;
            }
            return NoteBlockService.isBase(material) && b.soft.contains(chunk.blocks().getBlockData(x, y, z));
        }

        boolean water(CaveChunk chunk, int x, int y, int z) {
            return type(chunk, x, y, z) == Material.WATER;
        }

        boolean sturdy(CaveChunk chunk, int x, int y, int z) {
            Material material = type(chunk, x, y, z);
            return material.isSolid() && material.isOccluding() && !wet(chunk, x, y, z);
        }

        /** The mod's {@code OCEAN_FLOOR_WG} start: the sea floor before the trench (it is not updated by the dig). */
        static int seaFloor(CaveChunk chunk, int x, int z) {
            return chunk.oceanFloor(x, z);
        }

        /** From the start, down through fluid to the first block that is not: the floor block's y. */
        int sink(CaveChunk chunk, int x, int y, int z) {
            while (y > chunk.minY() && (wet(chunk, x, y, z) || type(chunk, x, y, z) == Material.LAVA)) {
                y--;
            }
            return y;
        }
    }

    /** Mod surface rule: abyssmarine under the sea floor (stone and deepslate are kept near it). */
    static final class Terrain extends Feature {
        Terrain(Blocks b) {
            super("abyssal_terrain", b);
        }

        /** The biome's band only: the surface rule runs before the trench, in the biome itself. */
        @Override
        public boolean placesAnywhere() {
            return false;
        }

        @Override
        public void place(CaveChunk chunk) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    int floor = Trench.seafloor(chunk, x, z);
                    // Below the trench walls' stone and deepslate layers (12 under the sea floor).
                    for (int y = floor - 13; y >= chunk.minY() + 5; y--) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            continue;
                        }
                        Material material = blocks.getType(x, y, z);
                        if (CavernFeature.vanillaCaveDecoration(material)) {
                            blocks.setBlock(x, y, z, b.air);
                        } else if (CavernFeature.surfaceRock(material)) {
                            blocks.setBlock(x, y, z, b.abyssmarine);
                        }
                    }
                }
            }
        }
    }

    /**
     * Mod {@code OceanTrenchStructure} ({@code random_spread} spacing 2, separation 1, salt 2): each start in the biome
     * digs a trench as wide as the biome continues plus 32 (up to 150 + 32), in the chunks within its piece square. As
     * in the mod the starts dig one after another, each from the sea floor as the ones before left it
     * ({@code getSeafloorHeight} reads the live world), so overlapping starts dig deeper and wider each time. Each
     * column is dug where it is under the sea by more than 3 and {@code calcYDist} is wide enough for its distance to
     * the start; sea mounts are tuff, the floor muck, and the columns next to the trench are walled. Chunks it dug
     * become Abyssal Chasm up to sea level - 16 ({@code replaceBiomes}).
     */
    static final class Trench extends Feature {
        private static final int SPACING = 2;
        private static final int SEPARATION = 1;
        private static final long SALT = 2L;
        private static final int MAX_RADIUS = 150 + 32;
        /** The widest {@code calcYDist} (0.9 + 0.25) times the widest edge wobble (1.1): how far past r it can dig. */
        static final double MAX_REACH = Math.sqrt(1.15 * 1.1);
        private final Map<Long, Optional<int[]>> starts = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<int[]>> eldest) {
                return size() > 4096;
            }
        };

        Trench(Blocks b) {
            super("abyssal_ocean_trench", b);
        }

        /** The noise of a column, which does not depend on the height. */
        static final class Column {
            final double df1;
            final double df2;
            final double wobble;
            final float mountTarget;
            final float mountSample;
            final int minY;

            Column(int x, int z, int worldMinY) {
                df1 = SimplexNoise3D.sample(x, 0, z, 100.0F) * 0.6F;
                df2 = SimplexNoise3D.sample(x - 450, 0, z + 450, 300.0F) * 0.25F;
                wobble = radiusWobble(x, z);
                mountTarget = 20.0F + SimplexNoise3D.sample(x - 440, 0, z + 412, 30.0F) * 10.0F
                    + SimplexNoise3D.sample(x - 110, 0, z + 110, 10.0F) * 3.0F;
                mountSample = SimplexNoise3D.sample(x, 0, z, 50.0F) + SimplexNoise3D.sample(x - 440, 0, z + 412, 11.0F) * 0.2F
                    + SimplexNoise3D.sample(x - 100, 0, z - 400, 100.0F) * 0.3F - 0.1F;
                float seaFloorExtra = (1.0F + SimplexNoise3D.sample2D(x - 800, z - 212, 20.0F)) * 5.0F;
                minY = (int) (worldMinY + 2 + seaFloorExtra);
            }

            /** Mod {@code isSeaMountBlocking}. */
            boolean seaMount(int y) {
                int bottomedY = y + 64;
                float heightScale = (mountTarget - bottomedY) / (mountTarget + 15.0F);
                return mountSample >= 0.4F * Math.max(0.0F, 1.0F - heightScale);
            }

            /** Mod {@code calcYDist}. */
            double yDist(int y, int priorHeight) {
                int j = -64 - y;
                if (y < SEA_LEVEL + 1 && j <= 0 && priorHeight < SEA_LEVEL - 3) {
                    float belowSeaBy = CaveBowl.smin((SEA_LEVEL - priorHeight) / 120.0F, 1.0F, 0.2F);
                    float bedrockCloseness = CaveBowl.smin(Math.abs(j) / 50.0F - 0.1F, 1.0F, 0.2F);
                    return CaveBowl.smin((float) (belowSeaBy * (bedrockCloseness - df1) - df2), 0.9F, 0.2F) - df2;
                }
                return 0.0;
            }
        }

        /** The starts whose pieces reach a chunk, in the order they dig. */
        List<int[]> reaching(CaveChunk chunk, int chunkX, int chunkZ) {
            List<int[]> reaching = new ArrayList<>();
            for (int[] origin : CaveBowl.origins(chunk.seed(), chunkX, chunkZ, MAX_RADIUS / 16 + 4, SPACING, SEPARATION, SALT)) {
                plan(chunk, origin[0], origin[1]).ifPresent(start -> {
                    // Mod generatePieces: pieces only within widthChunks of the start chunk.
                    int widthChunks = (int) Math.ceil((start[2] + 16) / 16.0F / 2.0F) + 3;
                    if (Math.abs(chunkX - (start[0] >> 4)) > widthChunks || Math.abs(chunkZ - (start[1] >> 4)) > widthChunks) {
                        return;
                    }
                    int nx = Math.max(chunkX << 4, Math.min((chunkX << 4) + 15, start[0]));
                    int nz = Math.max(chunkZ << 4, Math.min((chunkZ << 4) + 15, start[1]));
                    double dx = nx - start[0];
                    double dz = nz - start[1];
                    double reach = start[2] * MAX_REACH;
                    if (dx * dx + dz * dz <= reach * reach) {
                        reaching.add(start);
                    }
                });
            }
            return reaching;
        }

        /** Boxes the module's structures wrote into chunks whose trench may not be dug yet, by chunk. */
        private final Map<Long, List<int[]>> guarded = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, List<int[]>> eldest) {
                return size() > 4096;
            }
        };
        /** Chunks whose trench already ran (this session). */
        private final Map<Long, Boolean> decorated = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > 4096;
            }
        };
        /** The guarded boxes of the chunk this thread is digging. */
        private final ThreadLocal<List<int[]>> digging = new ThreadLocal<>();

        private static long chunkKey(CaveChunk chunk, int chunkX, int chunkZ) {
            return ((long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL)) * 31L + chunk.seed();
        }

        /**
         * A module structure placed blocks in this box: the trench of the neighbouring chunks it covers, dug later,
         * leaves them (the chunk being generated has already dug its own).
         */
        void guard(CaveChunk chunk, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    if (cx == chunk.chunkX() && cz == chunk.chunkZ()) {
                        continue;
                    }
                    int[] box = {Math.max(minX, cx << 4), minY, Math.max(minZ, cz << 4), Math.min(maxX, (cx << 4) + 15), maxY,
                        Math.min(maxZ, (cz << 4) + 15)};
                    synchronized (guarded) {
                        guarded.computeIfAbsent(chunkKey(chunk, cx, cz), key -> new ArrayList<>()).add(box);
                    }
                }
            }
        }

        private boolean guardedAt(int x, int y, int z) {
            List<int[]> boxes = digging.get();
            if (boxes == null) {
                return false;
            }
            for (int[] box : boxes) {
                if (x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5]) {
                    return true;
                }
            }
            return false;
        }

        /** {@link #predictFloor(CaveChunk, int, int)} remembered per column for one structure placement. */
        int predictFloor(CaveChunk chunk, int x, int z, Map<Long, Integer> known) {
            return known.computeIfAbsent((long) x << 32 ^ (z & 0xFFFFFFFFL), key -> predictFloor(chunk, x, z));
        }

        /**
         * Where the trench will leave the floor of a column (the top of its ground under the water), worked out from
         * the trench's shape without touching the world: the module's structures sit on it even when the trench of a
         * neighbouring chunk is dug after them. It assumes solid ground under the original sea floor. A column whose
         * chunk was already dug (its heightmap then follows the dig) is read as it is.
         */
        int predictFloor(CaveChunk chunk, int x, int z) {
            int original = chunk.blocks().oceanFloorWG(x, z);
            boolean dug;
            synchronized (decorated) {
                dug = decorated.containsKey(chunkKey(chunk, x >> 4, z >> 4));
            }
            if (original != Integer.MIN_VALUE && (dug || dugFloor(chunk.blocks().getBlockData(x, original - 1, z)))) {
                int y = SEA_LEVEL + 7;
                while (y > chunk.minY() && (wet(chunk, x, y, z) || type(chunk, x, y, z).isAir())) {
                    y--;
                }
                return y;
            }
            int floor = (original == Integer.MIN_VALUE ? chunk.surfaceY(x, z) + 1 : original) - 1;
            Column column = new Column(x, z, chunk.minY());
            int span = SEA_LEVEL + 8 - chunk.minY();
            boolean[] water = new boolean[span];
            for (int y = floor + 1; y < SEA_LEVEL + 8; y++) {
                if (y >= chunk.minY()) {
                    water[y - chunk.minY()] = true;
                }
            }
            int prior = floor;
            for (int[] start : reaching(chunk, x >> 4, z >> 4)) {
                if (prior >= SEA_LEVEL - 3) {
                    break;
                }
                double needed = needed(start, column.wobble, x, z);
                if (needed > 1.16) {
                    continue;
                }
                for (int y = prior + 3; y >= column.minY; y--) {
                    if (y > SEA_LEVEL - 2) {
                        continue;
                    }
                    double dist = column.yDist(y, prior);
                    if (dist > 0.0 && dist >= needed) {
                        water[y - chunk.minY()] = !column.seaMount(y);
                    }
                }
                int y = SEA_LEVEL + 7;
                while (y > chunk.minY() && water[y - chunk.minY()]) {
                    y--;
                }
                prior = y;
            }
            return prior;
        }

        /** Muck and tuff on top: what the trench leaves (the sea floor before it is sand, gravel or abyssmarine). */
        private boolean dugFloor(BlockData data) {
            return b.muck.equals(data) || data.getMaterial() == Material.TUFF;
        }

        @Override
        public void place(CaveChunk chunk) {
            long key = chunkKey(chunk, chunk.chunkX(), chunk.chunkZ());
            List<int[]> boxes;
            synchronized (guarded) {
                boxes = guarded.remove(key);
            }
            digging.set(boxes);
            try {
                dig(chunk);
            } finally {
                digging.remove();
                synchronized (decorated) {
                    decorated.put(key, Boolean.TRUE);
                }
            }
        }

        private void dig(CaveChunk chunk) {
            List<int[]> reaching = reaching(chunk, chunk.chunkX(), chunk.chunkZ());
            if (reaching.isEmpty()) {
                return;
            }
            Column[] columns = new Column[256];
            int[] floors = new int[256];
            for (int i = 0; i < 256; i++) {
                int x = chunk.minBlockX() + (i & 15);
                int z = chunk.minBlockZ() + (i >> 4);
                columns[i] = new Column(x, z, chunk.minY());
                floors[i] = seafloor(chunk, x, z);
            }
            boolean dugAny = false;
            for (int[] start : reaching) {
                for (int i = 0; i < 256; i++) {
                    int x = chunk.minBlockX() + (i & 15);
                    int z = chunk.minBlockZ() + (i >> 4);
                    if (dig(chunk, start, columns, floors, i, x, z)) {
                        dugAny = true;
                    }
                }
            }
            if (dugAny) {
                chunk.replaceBiome(BIOME, SEA_LEVEL - 16);
            }
        }

        /** The start's centre and width ({@code x, z, radius}), or empty when it is not in the biome. */
        private Optional<int[]> plan(CaveChunk chunk, int startX, int startZ) {
            long key = ((long) startX << 32 ^ (startZ & 0xFFFFFFFFL)) * 31L + chunk.seed();
            synchronized (starts) {
                Optional<int[]> known = starts.get(key);
                if (known != null) {
                    return known;
                }
            }
            int x = startX << 4;
            int z = startZ << 4;
            Optional<int[]> planned = Optional.empty();
            // Vanilla isValidBiome: the biome at the start chunk's middle.
            if (inBiome(chunk, x + 8, z + 8)) {
                int sum = 0;
                int[][] directions = {{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
                for (int[] d : directions) {
                    int step;
                    for (step = 0; step < 150; step += 16) {
                        if (!inBiome(chunk, x + d[0] * step, z + d[1] * step)) {
                            break;
                        }
                    }
                    sum += Math.min(step, 150) + 32;
                }
                planned = Optional.of(new int[] {x, z, sum / 4});
            }
            synchronized (starts) {
                starts.put(key, planned);
            }
            return planned;
        }

        private static boolean inBiome(CaveChunk chunk, int x, int z) {
            return chunk.columnBiomeAt(x, z).filter(found -> found == BIOME).isPresent();
        }

        /** Mod {@code getSeafloorHeight}: from the heightmap down through fluid (waterlogged too), ice and snow. */
        static int seafloor(CaveChunk chunk, int x, int z) {
            int y = Math.max(SEA_LEVEL + 7, chunk.oceanFloor(x, z));
            while (y > chunk.minY()) {
                Material material = chunk.blocks().getType(x, y, z);
                boolean fluid = material == Material.WATER || material == Material.LAVA || material == Material.BUBBLE_COLUMN
                    || material == Material.KELP || material == Material.KELP_PLANT || material == Material.SEAGRASS
                    || material == Material.TALL_SEAGRASS
                    || chunk.blocks().getBlockData(x, y, z) instanceof org.bukkit.block.data.Waterlogged logged && logged.isWaterlogged();
                if (!(material.isAir() || fluid || Tag.ICE.isTagged(material) || Tag.SNOW.isTagged(material))) {
                    break;
                }
                y--;
            }
            return y;
        }

        static double yDist(int x, int y, int z, int priorHeight) {
            return new Column(x, z, -64).yDist(y, priorHeight);
        }

        /** Mod {@code getRadiusSq} without the start's radius: the wobble of the trench's edge. */
        static double radiusWobble(int x, int z) {
            float simplex1 = SimplexNoise3D.sample2D(x, z, 30.0F);
            float simplex2 = SimplexNoise3D.sample2D(x + 1000, z - 1000, 100.0F);
            return 0.8F + 0.2F * (1.0F + simplex1 + simplex2) * 0.5F;
        }

        static boolean seaMount(int x, int y, int z) {
            return new Column(x, z, -64).seaMount(y);
        }

        /**
         * Mod {@code shouldDig} solved for {@code yDist}: a start digs a column at the heights where {@code yDist}
         * reaches {@code distance / (radiusSq * corner)} (no corner scaling outside the radius, as in the mod).
         */
        static double needed(int[] start, double wobble, int x, int z) {
            double dx = x - start[0];
            double dz = z - start[1];
            // distToLowCornerSqr to (centre x, y - 1, centre z): one block up or down adds 1.
            double distance = dx * dx + 1.0 + dz * dz;
            double radiusSq = wobble * start[2] * start[2];
            double corner = radiusSq - distance;
            double factor = corner > 0.0 && corner <= 100.0 ? corner / 100.0 : 1.0;
            return distance / (radiusSq * factor);
        }

        /** The least {@code yDist} over several starts (for tests and the union of starts). */
        static double threshold(List<int[]> starts, int x, int z) {
            double wobble = radiusWobble(x, z);
            double best = Double.MAX_VALUE;
            for (int[] start : starts) {
                double value = needed(start, wobble, x, z);
                if (value < 1.3) {
                    best = Math.min(best, value);
                }
            }
            return best;
        }

        /** One start's piece on one column (mod {@code placeInChunk}); true when it dug something. */
        private boolean dig(CaveChunk chunk, int[] start, Column[] columns, int[] floors, int i, int x, int z) {
            Column column = columns[i];
            int priorHeight = floors[i];
            if (priorHeight >= SEA_LEVEL - 3) {
                return false;
            }
            double needed = needed(start, column.wobble, x, z);
            if (needed > 1.16) {
                return false;
            }
            CaveChunk.BlockSink blocks = chunk.blocks();
            boolean dug = false;
            int below = Integer.MIN_VALUE;
            for (int y = priorHeight + 3; y >= column.minY; y--) {
                if (y > SEA_LEVEL - 2) {
                    continue;
                }
                double dist = column.yDist(y, priorHeight);
                if (dist <= 0.0 || dist < needed) {
                    continue;
                }
                if (guardedAt(x, y, z)) {
                    // Module structures placed before this chunk's dig (they reach across chunks) are kept.
                    continue;
                }
                if (column.seaMount(y)) {
                    if (blocks.getType(x, y, z) != Material.BEDROCK) {
                        blocks.setBlock(x, y, z, b.tuff);
                    }
                } else {
                    blocks.setBlock(x, y, z, b.water);
                    dug = true;
                    below = y - 1;
                }
            }
            if (!dug) {
                floors[i] = seafloor(chunk, x, z);
                return false;
            }
            int floor = floor(chunk, column, x, below, z);
            walls(chunk, start, columns, floors, x, floor, z, priorHeight);
            floors[i] = seafloor(chunk, x, z);
            return true;
        }

        /**
         * Mod {@code decorateFloor}: muck on the floor and 1-2 under it, below sea level - 32 and off the sea mounts.
         * Returns where it stopped (the mod moves the same position down, and the walls use it).
         */
        private int floor(CaveChunk chunk, Column column, int x, int y, int z) {
            if (column.seaMount(y) || y >= SEA_LEVEL - 32) {
                return y;
            }
            CaveChunk.BlockSink blocks = chunk.blocks();
            blocks.setBlock(x, y, z, b.muck);
            SplittableRandom random = chunk.random(id() + x + ":" + z + ":" + y);
            for (int i = 0; i < 1 + random.nextInt(2); i++) {
                y--;
                Material at = blocks.getType(x, y, z);
                if (Tag.WITHER_IMMUNE.isTagged(at) || Tag.ICE.isTagged(at) || Tag.SNOW.isTagged(at)) {
                    break;
                }
                blocks.setBlock(x, y, z, at == Material.LAVA ? b.deepslate : b.muck);
            }
            return y;
        }

        /** Mod: the columns next to the trench (and under it) are walled from the bottom up to the sea floor. */
        private void walls(CaveChunk chunk, int[] start, Column[] columns, int[] floors, int x, int floor, int z, int priorHeight) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            int[][] wallDirections = {{0, -1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
            SplittableRandom random = chunk.random(id() + ":walls:" + x + ":" + z + ":" + start[0] + ":" + start[1]);
            for (int[] d : wallDirections) {
                int wx = x + d[0];
                int wy = floor + 1 + d[1];
                int wz = z + d[2];
                if (!blocks.isInRegion(wx, wy, wz)) {
                    continue;
                }
                boolean inChunk = wx >> 4 == chunk.chunkX() && wz >> 4 == chunk.chunkZ();
                Column column = inChunk ? columns[(wx & 15) | (wz & 15) << 4] : new Column(wx, wz, chunk.minY());
                Material atCentre = blocks.getType(wx, -16, wz);
                // Mod shouldDig of this piece, with this column's sea floor.
                double dist = wy > SEA_LEVEL - 2 ? 0.0 : column.yDist(wy, priorHeight);
                boolean dug = dist > 0.0 && dist >= needed(start, column.wobble, wx, wz);
                if (dug || Tag.ICE.isTagged(atCentre) || Tag.SNOW.isTagged(atCentre) || atCentre == Material.WATER) {
                    continue;
                }
                boolean seaMountBeneath = false;
                for (int y = chunk.minY() + 2; y < priorHeight - 1; y++) {
                    boolean mount = column.seaMount(y);
                    if (!seaMountBeneath || blocks.getType(wx, y, wz) != Material.WATER) {
                        wall(chunk, mount, wx, y, wz, priorHeight, random);
                    }
                    if (mount) {
                        seaMountBeneath = true;
                    }
                }
                if (inChunk) {
                    floors[(wx & 15) | (wz & 15) << 4] = seafloor(chunk, wx, wz);
                }
            }
        }

        /** Mod {@code setWallBlock}: stone (deepslate below y=0) near the sea floor, then deepslate, then abyssmarine. */
        private void wall(CaveChunk chunk, boolean mount, int x, int y, int z, int priorHeight, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            Material prior = blocks.getType(x, y, z);
            if (prior == Material.BEDROCK || Tag.ICE.isTagged(prior) || Tag.SNOW.isTagged(prior) || mount
                || guardedAt(x, y, z)) {
                return;
            }
            int dist = priorHeight - y;
            int layerOffset = random.nextInt(2);
            BlockData set;
            if (prior == Material.LAVA) {
                set = b.magma;
            } else if (dist <= 5 + layerOffset) {
                set = y < 0 ? b.deepslate : b.stone;
            } else if (dist <= 12 + layerOffset) {
                set = b.deepslate;
            } else {
                set = b.abyssmarine;
            }
            blocks.setBlock(x, y, z, set);
        }
    }

    /**
     * Mod {@code AbyssalBoulderFeature} (17-30 tries a chunk, at least 5 under the sea floor): on the muck, three
     * overlapping balls of deepslate with mussels clinging to their wet sides.
     */
    static final class Boulders extends Feature {
        Boulders(Blocks b) {
            super("abyssal_boulder", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 17, 30);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                // Mod environment scan: down to the first block that is not water, tuff or a tube worm.
                // (Boulders come before the tube worms, so there are none yet.)
                int found = ModPlacement.scanDown(chunk, x, y, z, 32, material -> material != Material.WATER
                    && material != Material.TUFF);
                if (found == Integer.MIN_VALUE || found > seaFloor(chunk, x, z) - 5 || !chunk.isBiome(x, found, z, BIOME)) {
                    continue;
                }
                boulder(chunk, x, found, z, random);
            }
        }

        private void boulder(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            while (y > chunk.minY() + 1) {
                if (!blocks.getType(x, y - 1, z).isAir() && b.muck.equals(blocks.getBlockData(x, y - 1, z))) {
                    break;
                }
                y--;
            }
            if (y <= chunk.minY() + 1) {
                return;
            }
            y += 1;
            for (int l = 0; l < 3; l++) {
                int i = random.nextInt(3);
                int j = random.nextInt(3);
                int k = random.nextInt(3);
                float f = (i + j + k) * 0.333F + 0.5F;
                double radius = f * f;
                for (int dx = -i; dx <= i; dx++) {
                    for (int dy = -j; dy <= j; dy++) {
                        for (int dz = -k; dz <= k; dz++) {
                            if (dx * dx + dy * dy + dz * dz > radius || Tag.WITHER_IMMUNE.isTagged(blocks.getType(x + dx, y + dy, z + dz))) {
                                continue;
                            }
                            blocks.setBlock(x + dx, y + dy, z + dz, b.deepslate);
                            if (random.nextInt(2) == 0) {
                                int dir = random.nextInt(6);
                                int[] d = DIRECTIONS[dir];
                                if (water(chunk, x + dx + d[0], y + dy + d[1], z + dz + d[2])) {
                                    blocks.setBlock(x + dx + d[0], y + dy + d[1], z + dz + d[2], b.mussels[dir][random.nextInt(4)]);
                                }
                            }
                        }
                    }
                }
                x += -2 + random.nextInt(4);
                y -= random.nextInt(2);
                z += -2 + random.nextInt(4);
            }
        }
    }

    /**
     * Mod {@code BlackVentFeature} (3-12 a chunk): on a tuff sea mount, a vent of 2-7 blocks over a lava core in tuff
     * (the vents smoke black: Toxic Caves' vents, smoke picked by what they stand on).
     */
    static final class BlackVents extends Feature {
        BlackVents(Blocks b) {
            super("abyssal_black_vent", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 3, 12);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = seaFloor(chunk, x, z);
                int bottom = sink(chunk, x, y, z);
                // Mod: the trench makes its whole column Abyssal Chasm, so the biome is checked where it ends up.
                if (!chunk.isBiome(x, bottom, z, BIOME) || type(chunk, x, bottom - 1, z) != Material.TUFF) {
                    continue;
                }
                vent(chunk, x, bottom, z, random);
            }
        }

        private void vent(CaveChunk chunk, int x, int bottom, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            int height = random.nextInt(6) + 2;
            blocks.setBlock(x, bottom, z - 1, b.tuff);
            blocks.setBlock(x, bottom, z + 1, b.tuff);
            blocks.setBlock(x + 1, bottom, z, b.tuff);
            blocks.setBlock(x - 1, bottom, z, b.tuff);
            blocks.setBlock(x, bottom - 1, z, b.tuff);
            blocks.setBlock(x, bottom, z, b.lava);
            int middleStart = Math.max(1, height / 3);
            int middleTop = middleStart * 2;
            for (int i = 1; i <= height; i++) {
                blocks.setBlock(x, bottom + i, z, i <= middleStart ? b.vent : i > middleTop ? b.ventThin : b.ventMedium);
            }
        }
    }

    /**
     * Mod {@code TubeWormFeature} (5-16 a chunk): on tuff sea mounts at least 30 under the sea, 4-7 tube worms of 4-15
     * segments growing up through the water, bending sideways now and then.
     */
    static final class TubeWorms extends Feature {
        TubeWorms(Blocks b) {
            super("abyssal_tube_worm", b);
        }

        private boolean isTubeWorm(BlockData data) {
            return data != null && b.tubeWorms.containsValue(data);
        }

        private boolean isVent(BlockData data) {
            return b.vent.equals(data) || b.ventMedium.equals(data) || b.ventThin.equals(data);
        }

        /** Vanilla {@code canBeReplaced} under water: the water itself. */
        private static boolean replaceable(CaveChunk chunk, int x, int y, int z) {
            Material material = type(chunk, x, y, z);
            return material == Material.WATER || material.isAir();
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 5, 16);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = seaFloor(chunk, x, z);
                int bottom = sink(chunk, x, y, z);
                if (!chunk.isBiome(x, bottom, z, BIOME) || bottom >= SEA_LEVEL - 30 || type(chunk, x, bottom - 1, z) != Material.TUFF) {
                    continue;
                }
                for (int w = 0; w < 4 + random.nextInt(4); w++) {
                    worm(chunk, x + random.nextInt(10) - 5, bottom + random.nextInt(4), z + random.nextInt(10) - 5, random);
                }
            }
        }

        private void worm(CaveChunk chunk, int wx, int wy, int wz, SplittableRandom random) {
            int attach = -1;
            // Mod Direction.from3DDataValue(2 + nextInt(3)): north, south or west.
            int dir = random.nextInt(3);
            int[] d = HORIZONTAL[dir];
            int ax;
            int ay;
            int az;
            if (sturdy(chunk, wx + d[0], wy, wz + d[2])) {
                attach = dir;
                ax = wx + d[0];
                ay = wy;
                az = wz + d[2];
            } else {
                // Mod: up out of the ground, then down through the water: wormAt ends on the floor block, and the
                // worm is attached to the block under it.
                while (sturdy(chunk, wx, wy + 1, wz) && wy < ModPlacement.TOP) {
                    wy++;
                }
                while (wet(chunk, wx, wy, wz) && wy > chunk.minY()) {
                    wy--;
                }
                ax = wx;
                ay = wy - 1;
                az = wz;
            }
            BlockData at = chunk.blocks().getBlockData(wx, wy, wz);
            if (isVent(at) || isTubeWorm(at)) {
                return;
            }
            grow(chunk, ax, ay, az, attach, random, 4 + random.nextInt(12));
        }

        /** Mod {@code growWorm}: from the block it is attached to, segments go up (or out of a wall, then up). */
        private void grow(CaveChunk chunk, int ax, int ay, int az, int attach, SplittableRandom random, int maxSegments) {
            if (ay > SEA_LEVEL - 30) {
                return;
            }
            CaveChunk.BlockSink blocks = chunk.blocks();
            int[] out = attach < 0 ? new int[] {0, 1, 0} : new int[] {-HORIZONTAL[attach][0], 0, -HORIZONTAL[attach][2]};
            int wx = ax + out[0];
            int wy = ay + out[1];
            int wz = az + out[2];
            boolean canBranch = false;
            for (int placed = 0; placed < maxSegments; placed++) {
                BlockData state = b.tubeWorm("straight", 0);
                int px = wx;
                int py = wy;
                int pz = wz;
                if (wy > SEA_LEVEL - 30) {
                    return;
                }
                if (canBranch) {
                    if (random.nextBoolean()) {
                        int turn = random.nextInt(3);
                        int[] t = HORIZONTAL[turn];
                        wx += t[0];
                        wz += t[2];
                        if (type(chunk, wx, wy, wz) == Material.WATER) {
                            if (replaceable(chunk, px, py, pz) || isTubeWorm(blocks.getBlockData(px, py, pz))) {
                                blocks.setBlock(px, py, pz, b.tubeWorm("turn", turn));
                            }
                            state = b.tubeWorm("elbow", turn ^ 1);
                        } else {
                            wx = px;
                            wy = py + 1;
                            wz = pz;
                        }
                    }
                    canBranch = false;
                } else {
                    wy++;
                    canBranch = placed > 1;
                }
                if (type(chunk, wx, wy, wz) != Material.WATER) {
                    break;
                }
                blocks.setBlock(wx, wy, wz, state);
            }
            int fx = ax + out[0];
            int fy = ay + out[1];
            int fz = az + out[2];
            if (attach >= 0 && (replaceable(chunk, fx, fy, fz) || isTubeWorm(blocks.getBlockData(fx, fy, fz)))) {
                blocks.setBlock(fx, fy, fz, b.tubeWorm("elbow", attach));
            }
        }
    }

    /** Mod {@code MusselFeature} (140-200 tries a chunk below y=64): mussels on any sturdy face under water. */
    static final class Mussels extends Feature {
        Mussels(Blocks b) {
            super("abyssal_mussel", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 140, 200);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY(), 64);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!chunk.isBiome(x, y, z, BIOME) || !mussel(chunk, x, y, z, random)) {
                    continue;
                }
                for (int extra = 0; extra < random.nextInt(3); extra++) {
                    mussel(chunk, x + random.nextInt(4) - 2, y + random.nextInt(4) - 2, z + random.nextInt(4) - 2, random);
                }
            }
        }

        private boolean mussel(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            if (!water(chunk, x, y, z)) {
                return false;
            }
            List<Integer> options = new ArrayList<>();
            for (int d = 0; d < 6; d++) {
                int nx = x + DIRECTIONS[d][0];
                int nz = z + DIRECTIONS[d][2];
                if (nx >> 4 == x >> 4 && nz >> 4 == z >> 4 && sturdy(chunk, nx, y + DIRECTIONS[d][1], nz)) {
                    options.add(d ^ 1);
                }
            }
            if (options.isEmpty()) {
                return false;
            }
            // Mod selectDirection: with several, one of all but the last.
            int facing = options.size() <= 1 ? options.get(0) : options.get(random.nextInt(options.size() - 1));
            chunk.blocks().setBlock(x, y, z, b.mussels[facing][random.nextInt(4)]);
            return true;
        }
    }

    /**
     * Mod {@code PingPongSpongeFeature} ({@code noise_based_count} 20/0/7, 1 chunk in 2): a sponge of 1-4 blocks on the
     * muck of a trench at least 15 deep.
     */
    static final class Sponges extends Feature {
        Sponges(Blocks b) {
            super("abyssal_ping_pong_sponge", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.noiseCount(chunk, 20.0, 0.0, 7.0);
            for (int i = 0; i < count; i++) {
                if (!ModPlacement.rarity(random, 2)) {
                    continue;
                }
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int start = seaFloor(chunk, x, z);
                int bottom = sink(chunk, x, start, z);
                if (!chunk.isBiome(x, bottom, z, BIOME) || !b.muck.equals(chunk.blocks().getBlockData(x, bottom - 1, z)) || start - bottom < 15) {
                    continue;
                }
                int height = (int) Math.ceil(random.nextFloat() * 3.5F);
                for (int h = 0; h <= height; h++) {
                    if (water(chunk, x, bottom + h, z)) {
                        chunk.blocks().setBlock(x, bottom + h, z, h == height ? b.spongeTop : b.sponge);
                    }
                }
            }
        }
    }
}
