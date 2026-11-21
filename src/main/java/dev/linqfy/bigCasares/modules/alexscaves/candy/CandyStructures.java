package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyDecorations.Template;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeResolver;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModVoronoi;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureRotations;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The Candy Cavity's structures, ported from the mod: donut arches, soda bottles, licowitch towers and gingerbread
 * towns. Each is a {@code random_spread} structure with the mod's spacing, separation and salt; its layout is worked
 * out once per start from the vanilla structure random, and every chunk places only the part inside it (vanilla
 * {@code postProcess}), so pieces agree across chunk borders. The mod's exclusion zones are left out: the mod points
 * them at a tag, which 1.20.1 ignores.
 */
final class CandyStructures {
    static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;
    /** Vanilla {@code getSeaLevel()} for the overworld: template structures above sea level - 35 are dropped. */
    static final int SEA_LEVEL = 63;
    /** The surface above a start is unknown while chunks generate; the mod's overworld sits near sea level. */
    static final int ASSUMED_SURFACE = 72;
    static final StructureRotation[] ROTATIONS = StructureRotation.values();
    /** Top block of the overworld. */
    static final int MAX_Y = 319;

    private CandyStructures() {
    }

    /** Module blocks with x/z axis variants used by the templates; filled on the main thread by {@link #create}. */
    private static volatile Map<BlockData, BlockData> AXIS_SWAP = Map.of();
    private static final String[] AXIS_BLOCKS = {"cake_layer", "licoroot", "block_of_chocolate", "candy_cane_block",
        "chiseled_candy_cane_block", "stripped_candy_cane_block"};

    static List<ChunkFeature> create(CandyDecorations.Blocks b, ModBlockPalette palette, BowlFeature cakeCave) {
        Map<BlockData, BlockData> swap = new java.util.HashMap<>();
        for (String block : AXIS_BLOCKS) {
            swap.put(palette.get(block, "x"), palette.get(block, "z"));
            swap.put(palette.get(block, "z"), palette.get(block, "x"));
        }
        AXIS_SWAP = Map.copyOf(swap);
        Template[] houses = StructureTemplates.load("gingerbread_house").stream().map(Template::of).toArray(Template[]::new);
        Template center = Template.of(StructureTemplates.load("gingerbread_town_center").get(0));
        List<Template> soda = StructureTemplates.load("soda_bottle").stream().map(Template::of).toList();
        Template tower = Template.of(StructureTemplates.load("licowitch_tower").get(0));
        return List.of(new DonutArch(b, palette), new GingerbreadTown(b, palette, center, houses),
            new LicowitchTower(b, tower), new SodaBottle(soda, cakeCave));
    }

    /** Vanilla {@code WorldgenRandom.setLargeFeatureSeed}: the structure random of a start chunk. */
    static Random largeFeatureRandom(long worldSeed, int chunkX, int chunkZ) {
        return dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement.largeFeatureRandom(worldSeed, chunkX, chunkZ);
    }

    static boolean candyColumn(CaveChunk chunk, int x, int z) {
        return chunk.columnBiomeAt(x, z).filter(found -> found == BIOME).isPresent();
    }

    /** Mod {@code ACMath.getBiomesWithinAtY} all Candy Cavity: every quart column within {@code radius}. */
    static boolean candyWithin(CaveChunk chunk, int x, int z, int radius) {
        for (int qx = (x - radius) >> 2; qx <= (x + radius) >> 2; qx++) {
            for (int qz = (z - radius) >> 2; qz <= (z + radius) >> 2; qz++) {
                if (!candyColumn(chunk, qx << 2, qz << 2)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Vanilla {@code isValidBiome}: the biome at the generation point, the start chunk's middle at its height. */
    static boolean validStart(CaveChunk chunk, int startX, int startZ, int y) {
        return candyColumn(chunk, (startX << 4) + 8, (startZ << 4) + 8)
            && CaveBiomeResolver.inDepthBand(BIOME, y, ASSUMED_SURFACE);
    }

    static boolean inChunk(CaveChunk chunk, int x, int z) {
        return x >> 4 == chunk.chunkX() && z >> 4 == chunk.chunkZ();
    }

    /** A {@code random_spread} structure whose layout is computed once per start. */
    abstract static class Spread<L> implements ChunkFeature {
        private final String id;
        private final int spacing;
        private final int separation;
        private final long salt;
        private final int reach;
        private final Map<Long, Optional<L>> layouts = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<L>> eldest) {
                return size() > 256;
            }
        };

        Spread(String id, int spacing, int separation, long salt, int reach) {
            this.id = id;
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.reach = reach;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public boolean placesAnywhere() {
            return true;
        }

        @Override
        public void place(CaveChunk chunk) {
            for (int[] start : CaveBowl.origins(chunk.seed(), chunk.chunkX(), chunk.chunkZ(), reach, spacing, separation, salt)) {
                Optional<L> layout = layout(chunk, start[0], start[1]);
                layout.ifPresent(found -> placeIn(chunk, found));
            }
        }

        private Optional<L> layout(CaveChunk chunk, int startX, int startZ) {
            long key = ((long) startX << 32 ^ (startZ & 0xFFFFFFFFL)) * 31L + chunk.seed();
            synchronized (layouts) {
                Optional<L> known = layouts.get(key);
                if (known != null) {
                    return known;
                }
            }
            Optional<L> planned = plan(chunk, startX, startZ, largeFeatureRandom(chunk.seed(), startX, startZ));
            synchronized (layouts) {
                layouts.put(key, planned);
            }
            return planned;
        }

        /** The structure's layout for a start chunk, or empty when it does not generate there. */
        abstract Optional<L> plan(CaveChunk chunk, int startX, int startZ, Random random);

        /** Places the part of the layout inside the chunk. */
        abstract void placeIn(CaveChunk chunk, L layout);
    }

    /** A template placed at a template position (its zero corner) with a rotation, and its bounding box. */
    static final class Piece {
        final Template template;
        final StructureRotation rotation;
        final int x;
        final int y;
        final int z;
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        final boolean discarded;
        /** Axis variants of module blocks (x to z and back), for quarter turns. */
        private final Map<BlockData, BlockData> axisSwap;

        Piece(Template template, StructureRotation rotation, int x, int y, int z) {
            this(template, rotation, x, y, z, false, AXIS_SWAP);
        }

        private Piece(Template template, StructureRotation rotation, int x, int y, int z, boolean discarded,
                      Map<BlockData, BlockData> axisSwap) {
            this.discarded = discarded;
            this.axisSwap = axisSwap;
            this.template = template;
            this.rotation = rotation;
            this.x = x;
            this.y = y;
            this.z = z;
            int[] far = StructureRotations.rotate(template.sizeX() - 1, template.sizeZ() - 1, rotation);
            this.minX = Math.min(x, x + far[0]);
            this.maxX = Math.max(x, x + far[0]);
            this.minZ = Math.min(z, z + far[1]);
            this.maxZ = Math.max(z, z + far[1]);
        }

        Piece atY(int newY) {
            return new Piece(template, rotation, x, newY, z, false, axisSwap);
        }

        Piece discarded() {
            return new Piece(template, rotation, x, y, z, true, axisSwap);
        }

        int maxY() {
            return y + template.sizeY() - 1;
        }

        /** Vanilla {@code calculateRelativePosition} of the template's middle. */
        int[] centre() {
            int[] at = StructureRotations.rotate(template.sizeX() / 2, template.sizeZ() / 2, rotation);
            return new int[] {x + at[0], z + at[1]};
        }

        boolean touches(CaveChunk chunk) {
            return maxX >= chunk.minBlockX() && minX <= chunk.minBlockX() + 15 && maxZ >= chunk.minBlockZ()
                && minZ <= chunk.minBlockZ() + 15;
        }

        /** Vanilla template placement clipped to the chunk; containers keep their loot table. */
        void place(CaveChunk chunk) {
            if (!touches(chunk)) {
                return;
            }
            for (Template.Entry entry : template.blocks()) {
                int[] at = StructureRotations.rotate(entry.x(), entry.z(), rotation);
                int bx = x + at[0];
                int bz = z + at[1];
                if (!inChunk(chunk, bx, bz)) {
                    continue;
                }
                BlockData data = entry.data();
                if (rotation != StructureRotation.NONE) {
                    BlockData swapped = rotation == StructureRotation.CLOCKWISE_180 ? null : axisSwap.get(data);
                    if (swapped != null) {
                        // Note block states do not rotate: the module's axis blocks swap their x and z variants.
                        data = swapped;
                    } else {
                        data = data.clone();
                        data.rotate(rotation);
                    }
                }
                if (entry.lootTable() != null) {
                    chunk.blocks().setContainer(bx, y + entry.y(), bz, data, entry.lootTable());
                } else {
                    chunk.blocks().setBlock(bx, y + entry.y(), bz, data);
                }
            }
        }
    }

    static boolean fullBlock(CaveChunk chunk, int x, int y, int z) {
        Material type = chunk.blocks().getType(x, y, z);
        return type.isSolid() && type.isOccluding();
    }

    /**
     * Mod supports under licowitch towers and gingerbread houses: below the bottom two layers of the piece, where it
     * stands on liquid or on nothing solid, 3-5 blocks of chocolate then cake down to the ground.
     *
     * @param frostedTop gingerbread houses: frosted chocolate (with chocolate under it) where nothing solid is above
     */
    static void supports(CaveChunk chunk, Piece piece, CandyDecorations.Blocks b, SplittableRandom random,
                         boolean frostedTop) {
        Set<Long> needed = new java.util.LinkedHashSet<>();
        for (int px = piece.minX; px <= piece.maxX; px++) {
            for (int pz = piece.minZ; pz <= piece.maxZ; pz++) {
                for (int py = piece.y; py <= piece.y + 1; py++) {
                    Material type = chunk.blocks().getType(px, py, pz);
                    if (!type.isAir() && (chunk.blocks().getType(px, py - 1, pz) == Material.WATER
                        || !fullBlock(chunk, px, py - 1, pz))) {
                        needed.add(pack(px, py, pz));
                    }
                }
            }
        }
        BlockData chocolateY = b.chocolateY;
        BlockData cake = b.cakeY;
        for (long packed : needed) {
            int px = (int) (packed >> 38);
            int py = (int) (packed << 26 >> 52);
            int pz = (int) (packed << 38 >> 38);
            if (!inChunk(chunk, px, pz)) {
                continue;
            }
            int maxDown = 3 + random.nextInt(3);
            int gy = py - 1;
            for (int down = 0; (chunk.blocks().getType(px, gy, pz) == Material.WATER || !fullBlock(chunk, px, gy, pz))
                && gy > chunk.minY() && down < maxDown; down++) {
                if (frostedTop && down == 0 && !fullBlock(chunk, px, py, pz)) {
                    chunk.blocks().setBlock(px, gy, pz, b.frosted);
                    chunk.blocks().setBlock(px, gy - 1, pz, chocolateY);
                } else {
                    chunk.blocks().setBlock(px, gy, pz, down < maxDown * 0.4F ? chocolateY : cake);
                }
                gy--;
            }
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | (long) z & 0x3FFFFFFL;
    }

    /**
     * Donut arches (mod {@code DonutArchStructure}, spacing 11, separation 7): a ring of dough standing across the
     * cave, as wide as the biome allows, its far side frosted with sprinkles. Pieces are 16-block cubes along the
     * ring's plane.
     */
    static final class DonutArch extends Spread<DonutArch.Layout> {
        record Layout(int centerX, int centerY, int centerZ, int facing, int width, int frosting, List<int[]> pieces) {
        }

        private final BlockData dough;
        private final BlockData[] frostings;
        private final BlockData[] sprinkles;

        DonutArch(CandyDecorations.Blocks b, ModBlockPalette palette) {
            // Mod salt 22331293834: 1.20.1 reads it as an int (Gson's intValue keeps the low 32 bits).
            super("candy_donut_arch", 11, 7, 856457354L, 2);
            this.dough = palette.get("dough_block");
            this.frostings = new BlockData[] {palette.get("block_of_frosting"), palette.get("block_of_vanilla_frosting"),
                palette.get("block_of_chocolate_frosting")};
            String[] colours = {"red", "purple", "yellow", "light_blue", "orange", "lime", "green", "blue", "magenta", "pink"};
            this.sprinkles = new BlockData[colours.length];
            for (int i = 0; i < colours.length; i++) {
                sprinkles[i] = b.rockCandy.get(colours[i]);
            }
        }

        @Override
        Optional<Layout> plan(CaveChunk chunk, int startX, int startZ, Random random) {
            int x = startX << 4;
            int z = startZ << 4;
            if (!validStart(chunk, startX, startZ, -30)) {
                return Optional.empty();
            }
            int up = continuesVertically(chunk, x, z, -30, 1, 25);
            int down = continuesVertically(chunk, x, z, -30, -1, 16);
            int centerY = -30 - down + 5 + random.nextInt(Math.max(up, 10));
            int facing = random.nextInt(4);
            int left = continuesSideways(chunk, x, centerY, z, (facing + 1) % 4, 32);
            int right = continuesSideways(chunk, x, centerY, z, (facing + 3) % 4, 32);
            int widthChunks = (right + left) / 2 / 16;
            int frosting = random.nextInt(3);
            List<int[]> pieces = new ArrayList<>();
            int[] d = CandyDecorations.HORIZONTAL[facing];
            for (int along = -widthChunks; along <= widthChunks; along++) {
                for (int vertical = -widthChunks; vertical <= widthChunks; vertical++) {
                    pieces.add(new int[] {x + d[0] * along * 16, centerY + vertical * 16, z + d[2] * along * 16});
                }
            }
            return Optional.of(new Layout(x, centerY, z, facing, widthChunks * 16, frosting, pieces));
        }

        /** Mod {@code biomeContinuesInDirectionFor} up or down: 16-block steps while the biome holds. */
        private static int continuesVertically(CaveChunk chunk, int x, int z, int y, int sign, int cutoff) {
            int i;
            for (i = 0; i < cutoff; i += 16) {
                if (!candyColumn(chunk, x, z) || !CaveBiomeResolver.inDepthBand(BIOME, y + sign * i, ASSUMED_SURFACE)) {
                    break;
                }
            }
            return Math.min(i, cutoff);
        }

        private static int continuesSideways(CaveChunk chunk, int x, int y, int z, int direction, int cutoff) {
            int[] d = CandyDecorations.HORIZONTAL[direction];
            int i;
            for (i = 0; i < cutoff; i += 16) {
                if (!candyColumn(chunk, x + d[0] * i, z + d[2] * i)
                    || !CaveBiomeResolver.inDepthBand(BIOME, y, ASSUMED_SURFACE)) {
                    break;
                }
            }
            return Math.min(i, cutoff);
        }

        @Override
        void placeIn(CaveChunk chunk, Layout layout) {
            for (int[] piece : layout.pieces()) {
                if (inChunk(chunk, piece[0], piece[2])) {
                    piece(chunk, layout, piece[0], piece[1], piece[2]);
                }
            }
        }

        /** Mod {@code DonutArchStructurePiece.postProcess}. */
        private void piece(CaveChunk chunk, Layout layout, int cornerX, int cornerY, int cornerZ) {
            BlockData frosting = frostings[layout.frosting()];
            boolean axisX = layout.facing() == 1 || layout.facing() == 3;
            int ccX = layout.centerX() + 8;
            int ccY = layout.centerY();
            int ccZ = layout.centerZ() + 8;
            double outerWidth = Math.pow(layout.width(), 2.0);
            double innerWidth = Math.pow(layout.width() * 0.6, 2.0);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 15; y >= 0; y--) {
                        int px = cornerX + x;
                        int py = Math.max(chunk.minY(), Math.min(MAX_Y, cornerY + y));
                        int pz = cornerZ + z;
                        int lateral = axisX ? pz : px;
                        double forwardsNoise = (SimplexNoise3D.sample2D(lateral, py, 20.0F) + 1.0) * 0.5;
                        double length = 15.0 - 4.0 * forwardsNoise;
                        double frostingNoise = (SimplexNoise3D.sample2D(lateral, py, 10.0F) + 1.0) * 0.5;
                        // Vanilla distToCenterSqr to the ring's axis.
                        double sx = axisX ? px + 0.5 - ccX : 0.5;
                        double sy = py + 0.5 - ccY;
                        double sz = axisX ? 0.5 : pz + 0.5 - ccZ;
                        double distSides = sx * sx + sy * sy + sz * sz;
                        double distForwards = axisX ? ccZ - pz : ccX - px;
                        double distForwardsAbs = Math.abs(distForwards);
                        if (distForwardsAbs >= length) {
                            continue;
                        }
                        boolean frosted = frostingDepth(layout.facing(), ccX, ccZ, px, pz) >= 2.0 + frostingNoise * 4.0;
                        double widthShrink = frosted ? 0.0 : 25.0;
                        double clamped = distForwardsAbs / length;
                        float smin = CaveBowl.smin((float) (clamped * clamped), 1.0F, 0.3F);
                        float outerSmooth = 1.0F - smin;
                        float innerSmooth = smin + 0.5F;
                        if (distSides < (outerWidth - widthShrink) * outerSmooth
                            && distSides > (innerWidth + widthShrink) * innerSmooth
                            && chunk.blocks().getType(px, py, pz) != Material.BEDROCK
                            && py >= cornerY - 2 && py <= cornerY + 16) {
                            if (frosted) {
                                BlockData sprinkle = sprinkle(chunk.seed(), px, py, pz);
                                chunk.blocks().setBlock(px, py, pz, sprinkle != null ? sprinkle : frosting);
                            } else {
                                chunk.blocks().setBlock(px, py, pz, dough);
                            }
                        }
                    }
                }
            }
        }

        /** Mod {@code placeSprinklesAt}: rock candy sprinkles in a 3D Voronoi pattern. */
        private BlockData sprinkle(long seed, int x, int y, int z) {
            float size = 0.3F;
            ModVoronoi.Info info = ModVoronoi.get3(seed, 1.0, x * size, y * size, z * size);
            if (info.distance() < 0.5) {
                float noise = Math.abs(SimplexNoise3D.sample(x + 3334, y - 200, z + 22223, 1.0F));
                if (noise < 0.1) {
                    int index = (int) Math.max(0.0, Math.min(sprinkles.length - 1, (info.hash() + 1.0) * 0.5 * sprinkles.length));
                    return sprinkles[index];
                }
            }
            return null;
        }

        /** Mod {@code calculateFrostingDepth}: how far towards the frosted side, per facing (north, east, south, west). */
        static float frostingDepth(int facing, int ccX, int ccZ, int x, int z) {
            return switch (facing) {
                case 0 -> ccX - x;
                case 1 -> z - ccZ;
                case 2 -> x - ccX;
                default -> ccZ - z;
            };
        }
    }

    /**
     * Soda bottles (mod {@code SodaBottleStructure}, spacing 8, separation 3): a sugar glass bottle full of purple soda,
     * upright or lying, standing on a cave floor at least 35 under the sea level. The floor is found from the cake
     * cave's shape (the blocks the module carves), since neighbouring chunks may not be carved yet.
     */
    static final class SodaBottle extends Spread<Piece> {
        private final List<Template> templates;
        private final BowlFeature cakeCave;

        SodaBottle(List<Template> templates, BowlFeature cakeCave) {
            // The lying bottle is 26 long from the start's corner: up to two chunks back.
            super("candy_soda_bottle", 8, 3, 11224949L, 2);
            this.templates = templates;
            this.cakeCave = cakeCave;
        }

        @Override
        Optional<Piece> plan(CaveChunk chunk, int startX, int startZ, Random random) {
            int x = startX << 4;
            int z = startZ << 4;
            if (!candyWithin(chunk, x + 9, z + 9, 20) || !validStart(chunk, startX, startZ, 19)) {
                return Optional.empty();
            }
            StructureRotation rotation = ROTATIONS[random.nextInt(4)];
            Template template = templates.get(random.nextInt(templates.size()));
            Piece piece = new Piece(template, rotation, x, 19, z);
            int[] centre = piece.centre();
            // Mod: with no floor the bottle keeps its starting height (y=19), then sinks by the edges.
            int caveY = caveHeight(chunk, centre[0], centre[1], random);
            if (caveY == Integer.MIN_VALUE) {
                caveY = 19;
            }
            // Mod sinkByEdges: down to the lowest ground under the footprint.
            int y = edgeHeight(chunk, piece.atY(caveY));
            if (y > SEA_LEVEL - 35) {
                return Optional.empty();
            }
            return Optional.of(piece.atY(y));
        }

        /** Mod {@code getCaveHeight}: one of the cave floors of the middle column (not the top one). */
        private int caveHeight(CaveChunk chunk, int x, int z, Random random) {
            List<CaveBowl> caves = cakeCave.cavesReaching(chunk, x >> 4, z >> 4);
            List<Integer> floors = new ArrayList<>();
            for (int y = chunk.minY() + 3; y < Math.max(ASSUMED_SURFACE, SEA_LEVEL) - 35; y++) {
                if (!BowlFeature.carvesAir(caves, x, y, z) && BowlFeature.carvesAir(caves, x, y + 1, z)) {
                    floors.add(y);
                }
            }
            if (floors.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            return floors.size() <= 1 ? floors.get(0) : floors.get(random.nextInt(floors.size() - 1));
        }

        private int edgeHeight(CaveChunk chunk, Piece piece) {
            int lowest = 512;
            Map<Long, List<CaveBowl>> byChunk = new java.util.HashMap<>();
            for (int x = piece.minX; x <= piece.maxX; x++) {
                for (int z = piece.minZ; z <= piece.maxZ; z++) {
                    int cx = x >> 4;
                    int cz = z >> 4;
                    List<CaveBowl> caves = byChunk.computeIfAbsent((long) cx << 32 ^ (cz & 0xFFFFFFFFL),
                        key -> cakeCave.cavesReaching(chunk, cx, cz));
                    int y = piece.y - 1;
                    while (BowlFeature.carvesAir(caves, x, y, z) && y > chunk.minY() + 1) {
                        y--;
                    }
                    lowest = Math.min(lowest, y);
                }
            }
            return lowest;
        }

        @Override
        void placeIn(CaveChunk chunk, Piece piece) {
            piece.place(chunk);
        }
    }

    /**
     * Licowitch towers (mod {@code LicowitchTowerStructure}, spacing 21, separation 16): the licowitch's tower on the
     * cake cave's plateau, with chocolate and cake supports where it hangs over the floor.
     */
    static final class LicowitchTower extends Spread<Piece> {
        private final Template template;
        private final CandyDecorations.Blocks b;

        LicowitchTower(CandyDecorations.Blocks b, Template template) {
            super("candy_licowitch_tower", 21, 16, 23916271L, 2);
            this.b = b;
            this.template = template;
        }

        @Override
        Optional<Piece> plan(CaveChunk chunk, int startX, int startZ, Random random) {
            int x = startX << 4;
            int z = startZ << 4;
            if (!candyWithin(chunk, x + 9, z + 9, 20) || !validStart(chunk, startX, startZ, 0)) {
                return Optional.empty();
            }
            Piece piece = new Piece(template, ROTATIONS[random.nextInt(4)], x, 0, z);
            int[] centre = piece.centre();
            int y = (int) CaveBowl.plateauHeight(centre[0], centre[1], 7, true);
            return y > SEA_LEVEL - 35 ? Optional.empty() : Optional.of(piece.atY(y));
        }

        @Override
        void placeIn(CaveChunk chunk, Piece piece) {
            if (!piece.touches(chunk)) {
                return;
            }
            piece.place(chunk);
            chunk.blocks().markStructure(dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks.Kind.LICOWITCH_TOWER,
                piece.minX, piece.y, piece.minZ, piece.maxX, piece.maxY(), piece.maxZ);
            supports(chunk, piece, b, chunk.random(id()), false);
        }
    }

    /**
     * Gingerbread towns (mod {@code GingerbreadTownStructure}, spacing 13, separation 11): the town centre and a
     * gingerbread brick road of 30 blocks that branches up to 5 times into more roads and houses, all on the cake
     * cave's plateau.
     */
    static final class GingerbreadTown extends Spread<List<Object>> {
        private final CandyDecorations.Blocks b;
        private final Template center;
        private final Template[] houses;
        private final BlockData bricks;
        private final BlockData slab = Bukkit.createBlockData(Material.GRANITE_SLAB);
        private final Set<BlockData> roadReplaceable = new HashSet<>();
        private static final dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks.Kind TOWN =
            dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks.Kind.GINGERBREAD_TOWN;

        GingerbreadTown(CandyDecorations.Blocks b, ModBlockPalette palette, Template center, Template[] houses) {
            super("candy_gingerbread_town", 13, 11, 92463117L, 5);
            this.b = b;
            this.center = center;
            this.houses = houses;
            this.bricks = palette.get("gingerbread_bricks");
            roadReplaceable.add(b.frosted);
            roadReplaceable.addAll(b.rockCandy.values());
        }

        /** Mod {@code GingerbreadRoadPiece}. */
        static final class Road {
            final int x;
            final int z;
            final int length;
            final int branchesLeft;
            final int direction;
            final int minX;
            final int maxX;
            final int minZ;
            final int maxZ;
            final List<Object> pending;

            Road(int x, int z, int length, int branchesLeft, int direction, List<Object> pending) {
                this.x = x;
                this.z = z;
                this.length = length;
                this.branchesLeft = branchesLeft;
                this.direction = direction;
                int[] d = CandyDecorations.HORIZONTAL[direction];
                this.minX = Math.min(x, x + d[0] * length);
                this.maxX = Math.max(x, x + d[0] * length);
                this.minZ = Math.min(z, z + d[2] * length);
                this.maxZ = Math.max(z, z + d[2] * length);
                this.pending = pending;
            }

            /** Mod {@code inflateRoadBox}: the box moved (not grown) along the road's axis. */
            int[] shifted(int by) {
                int[] d = CandyDecorations.HORIZONTAL[direction];
                int ax = Math.abs(d[0]) * by;
                int az = Math.abs(d[2]) * by;
                return new int[] {minX + ax, maxX + ax, minZ + az, maxZ + az};
            }
        }

        @Override
        Optional<List<Object>> plan(CaveChunk chunk, int startX, int startZ, Random random) {
            int x = startX << 4;
            int z = startZ << 4;
            if (!candyWithin(chunk, x + 9, z + 9, 20) || !validStart(chunk, startX, startZ, 0)) {
                return Optional.empty();
            }
            StructureRotation rotation = ROTATIONS[random.nextInt(4)];
            boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
            int rotatedX = quarter ? center.sizeZ() : center.sizeX();
            int rotatedZ = quarter ? center.sizeX() : center.sizeZ();
            int[] zero = RuinsFeature.zeroCorner(x - rotatedX / 2, z - rotatedZ / 2, center.sizeX() - 1, center.sizeZ() - 1,
                rotation);
            List<Object> pieces = new ArrayList<>();
            List<Object> pending = new ArrayList<>();
            Road road = new Road(x, z, 30, 5, rotation.ordinal(), pending);
            pieces.add(road);
            Piece townCenter = new Piece(center, rotation, zero[0], 0, zero[1]);
            pending.add(townCenter);
            pieces.add(townCenter);
            branch(road, road, pieces, random);
            while (!pending.isEmpty()) {
                Object next = pending.remove(random.nextInt(pending.size()));
                if (next instanceof Road child) {
                    branch(road, child, pieces, random);
                }
            }
            // Mod getCaveHeight of each house: the plateau under its middle, one lower (moveDownBy).
            List<Object> placed = new ArrayList<>();
            for (Object piece : pieces) {
                if (piece instanceof Piece house) {
                    int[] centre = house.centre();
                    int y = (int) CaveBowl.plateauHeight(centre[0], centre[1], 7, true);
                    // Mod: a house above sea level - 35 is moved out of the world, but its front path is still laid.
                    placed.add(y <= SEA_LEVEL - 35 ? house.atY(y - 1) : house.discarded());
                } else {
                    placed.add(piece);
                }
            }
            return Optional.of(List.copyOf(placed));
        }

        /** Mod {@code GingerbreadRoadPiece.addChildren}. */
        private void branch(Road start, Road road, List<Object> pieces, Random random) {
            if (road.branchesLeft <= 0) {
                return;
            }
            for (int child = 0; child < road.branchesLeft; child++) {
                int direction = road.direction;
                for (int tries = 0; direction % 2 == road.direction % 2 && tries < 256; tries++) {
                    direction = random.nextInt(4);
                }
                int i = Math.max(road.length / 5, 2);
                int j = i + random.nextInt(Math.max(road.length - i, 1));
                int[] d = CandyDecorations.HORIZONTAL[road.direction];
                int ox = road.x + d[0] * (j + 1);
                int oz = road.z + d[2] * (j + 1);
                Object piece;
                boolean isRoad = false;
                if (random.nextFloat() < 0.5F) {
                    Template house = houses[random.nextInt(houses.length)];
                    StructureRotation rotation = ROTATIONS[direction];
                    boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
                    int sx = quarter ? house.sizeZ() : house.sizeX();
                    int sz = quarter ? house.sizeX() : house.sizeZ();
                    int[] zero = RuinsFeature.zeroCorner(ox - sx / 2, oz - sz / 2, house.sizeX() - 1, house.sizeZ() - 1, rotation);
                    int[] cd = CandyDecorations.HORIZONTAL[direction];
                    piece = new Piece(house, rotation, zero[0] + cd[0] * -(sx / 2 + 1), 0, zero[1] + cd[2] * -(sz / 2 + 1));
                } else {
                    piece = new Road(ox, oz, Math.max(4, road.length / 2), road.branchesLeft - 1, direction, start.pending);
                    isRoad = true;
                }
                if (!intersects(start.pending, piece, isRoad)) {
                    start.pending.add(piece);
                    pieces.add(piece);
                }
            }
        }

        /** Mod {@code intersectsWithAnyPendingChildren}; roads span y -64..10 and houses 0 up, so y always overlaps. */
        private static boolean intersects(List<Object> pending, Object piece, boolean inflateRoads) {
            int[] box = box(piece);
            for (Object other : pending) {
                int[] otherBox = box(other);
                if (inflateRoads && other instanceof Road otherRoad && piece instanceof Road road
                    && road.direction == otherRoad.direction) {
                    otherBox = otherRoad.shifted(2);
                }
                if (otherBox[1] >= box[0] && otherBox[0] <= box[1] && otherBox[3] >= box[2] && otherBox[2] <= box[3]) {
                    return true;
                }
            }
            return false;
        }

        private static int[] box(Object piece) {
            if (piece instanceof Road road) {
                return new int[] {road.minX, road.maxX, road.minZ, road.maxZ};
            }
            Piece house = (Piece) piece;
            return new int[] {house.minX, house.maxX, house.minZ, house.maxZ};
        }

        @Override
        void placeIn(CaveChunk chunk, List<Object> pieces) {
            SplittableRandom random = chunk.random(id());
            for (Object piece : pieces) {
                if (piece instanceof Road road) {
                    if (road.maxX >= chunk.minBlockX() && road.minX <= chunk.minBlockX() + 15
                        && road.maxZ >= chunk.minBlockZ() && road.minZ <= chunk.minBlockZ() + 15) {
                        road(chunk, road);
                        // Mod road piece box: from y -64 to 10.
                        chunk.blocks().markStructure(TOWN, road.minX, -64, road.minZ, road.maxX, 10, road.maxZ);
                    }
                } else if (piece instanceof Piece house && house.touches(chunk)) {
                    if (house.discarded) {
                        houseRoad(chunk, house);
                        continue;
                    }
                    house.place(chunk);
                    chunk.blocks().markStructure(TOWN, house.minX, house.y, house.minZ, house.maxX, house.maxY(), house.maxZ);
                    houseRoad(chunk, house);
                    supports(chunk, house, b, random, true);
                }
            }
        }

        /** Mod {@code GingerbreadRoadPiece.postProcess}: bricks along the plateau, slabs where it steps down. */
        private void road(CaveChunk chunk, Road road) {
            int[] d = CandyDecorations.HORIZONTAL[road.direction];
            int bx = road.x;
            int bz = road.z;
            int previousY = -1;
            for (int built = 0; built < road.length; built++) {
                int y = (int) CaveBowl.plateauHeight(bx, bz, 7, true) - 1;
                if (chunk.blocks().getType(bx, y + 1, bz).isSolid() && y < MAX_Y) {
                    y++;
                }
                if (inChunk(chunk, bx, bz)) {
                    BlockData replacing = chunk.blocks().getBlockData(bx, y, bz);
                    boolean place = false;
                    if (replacing != null && roadReplaceable.contains(replacing)) {
                        place = true;
                    } else if (CandyDecorations.Decor.replaceable(chunk, bx, y, bz)) {
                        place = true;
                        y--;
                    }
                    if (place) {
                        chunk.blocks().setBlock(bx, y, bz, bricks);
                        if (previousY > y && built > 0) {
                            chunk.blocks().setBlock(bx, y + 1, bz, slab);
                        }
                    }
                }
                bx += d[0];
                bz += d[2];
                previousY = y;
            }
        }

        /** Mod {@code GingerbreadHousePiece.postProcess}: a brick path from the house's middle out of its front. */
        private void houseRoad(CaveChunk chunk, Piece house) {
            int cx = house.minX + (house.maxX - house.minX + 1) / 2;
            int cz = house.minZ + (house.maxZ - house.minZ + 1) / 2;
            int[] d = CandyDecorations.HORIZONTAL[house.rotation.ordinal()];
            int length = Math.max(house.maxX - house.minX + 1, house.maxZ - house.minZ + 1) / 2;
            for (int i = 0; i <= length; i++) {
                int x = cx + d[0] * i;
                int z = cz + d[2] * i;
                if (!inChunk(chunk, x, z)) {
                    continue;
                }
                int y = (int) CaveBowl.plateauHeight(x, z, 7, true) - 1;
                BlockData there = chunk.blocks().getBlockData(x, y, z);
                if (there != null && roadReplaceable.contains(there)) {
                    chunk.blocks().setBlock(x, y, z, bricks);
                }
            }
        }
    }
}
