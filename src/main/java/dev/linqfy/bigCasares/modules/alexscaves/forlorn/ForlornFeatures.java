package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.NoteBlockRotations;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.OreFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.PlantPatchFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Terrain of the Forlorn Hollows, after the mod, in its generation order: the surface rule (packed mud floors,
 * coprolith blobs in guanostone), the forlorn canyon ({@code ForlornCanyonStructure}), forlorn posts, bridges across
 * the canyon ({@code ForlornBridgeStructure}), thornwood trees, the biome's ores and peering coprolith, the caveman
 * huts and idol ({@code forlorn_ruins}), guano piles, thornwood roots and brown mushrooms
 * ({@link ForlornDecorations}) and underweed patches.
 */
public final class ForlornFeatures {
    static final CaveBiomeId BIOME = CaveBiomeId.FORLORN_HOLLOWS;
    static final int BAND_TOP = (int) Math.floor(BIOME.minDepth() * 128.0);
    static final int BAND_DEPTH = (int) Math.ceil((BIOME.maxDepth() - BIOME.minDepth()) * 128.0);

    private ForlornFeatures() {
    }

    /** Made on the main thread, in the mod's step order. */
    public static List<ChunkFeature> create(ModBlockPalette palette) {
        Blocks b = new Blocks(palette);
        NoteBlockRotations rotations = NoteBlockRotations.of(palette);
        List<ChunkFeature> features = new ArrayList<>();
        // Raw generation: the surface rule runs first in the mod, then the canyon carves through it.
        features.add(new Terrain(b));
        features.add(new Canyon(b));
        // Local modifications: forlorn_posts (1 chunk in 4, on packed mud, raised 1 block).
        features.add(new RuinsFeature("forlorn_posts", BIOME, 4, -1, StructureTemplates.load("forlorn_post"), true,
            b.packedMud, null, false, rotations));
        // Underground structures: the bridges.
        features.add(new ForlornBridges(b));
        features.add(new ForlornDecorations.Trees(b));
        // Ores (mod numbers: count, size, height).
        features.add(new OreFeature("forlorn_dirt_replace_packed_mud", BIOME, 10, 40, -100, false, 100, true,
            OreFeature.Target.of(Material.PACKED_MUD), Bukkit.createBlockData(Material.DIRT)));
        features.add(new OreFeature("forlorn_coprolith_coal_ore", BIOME, 25, 14, -15, true, 192, false,
            OreFeature.Target.of(b.coprolith), palette.get("coprolith_coal_ore")));
        features.add(new OreFeature("forlorn_guanostone_redstone_ore", BIOME, 6, 10, -32, true, 15, false,
            OreFeature.Target.of(b.guanostone), palette.get("guanostone_redstone_ore")));
        features.add(new ForlornDecorations.PeeringCoprolith(b));
        // Underground decoration: the huts and the idol (1 chunk in 8), then guano piles.
        List<Structure> ruins = new ArrayList<>(StructureTemplates.load("forlorn_hut"));
        ruins.addAll(StructureTemplates.load("forlorn_idol"));
        features.add(new RuinsFeature("forlorn_ruins", BIOME, 8, -1, ruins, true, b.packedMud, null, false, rotations));
        features.add(new ForlornDecorations.GuanoPiles(b));
        // Vegetal decoration.
        features.add(new ForlornDecorations.Roots(b));
        features.add(new ForlornDecorations.BrownMushrooms(b));
        // Mod underweed: random_patch of 20 tries (spread 5 / 3), 50-100 per chunk.
        if (palette.registry().shapedBasesActive()) {
            // Only without collision: in legacy mode the plants would be solid note blocks.
            features.add(new PlantPatchFeature("forlorn_underweed", BIOME, 1, 50, 100, 20, 5, 3,
                List.of(palette.get("underweed"))));
        }
        return List.copyOf(features);
    }

    /** Vanilla {@code Direction.values()}: down, up, north, south, west, east. */
    static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final String[] DIRECTION_NAMES = {"down", "up", "north", "south", "west", "east"};
    /** Mod {@code ACMath.HORIZONTAL_DIRECTIONS} as indexes of {@link #DIRECTIONS}: north, east, south, west. */
    static final int[] HORIZONTAL = {2, 5, 3, 4};
    static final int DOWN = 0;
    static final int UP = 1;

    static int opposite(int direction) {
        return direction ^ 1;
    }

    /** Vanilla {@code Direction.getClockWise} of a horizontal direction. */
    static int clockWise(int direction) {
        return switch (direction) {
            case 2 -> 5;
            case 5 -> 3;
            case 3 -> 4;
            default -> 2;
        };
    }

    static int counterClockWise(int direction) {
        return opposite(clockWise(direction));
    }

    /**
     * The surface of a column for the mod's {@code canSeeSky}: the generation heightmap of any column this chunk may
     * read (the same from every chunk), or {@link Integer#MAX_VALUE} where it is unknown, so an unknown column never
     * counts as open sky.
     */
    static int knownSurface(CaveChunk chunk, int x, int z) {
        int floor = chunk.blocks().oceanFloorWG(x, z);
        return floor == Integer.MIN_VALUE ? Integer.MAX_VALUE : floor - 1;
    }

    /** Axis name of a direction. */
    static String axis(int direction) {
        return direction < 2 ? "y" : direction < 4 ? "z" : "x";
    }

    /** Blocks of the Forlorn Hollows, made on the main thread. */
    static final class Blocks {
        final BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);
        final BlockData packedMud = Bukkit.createBlockData(Material.PACKED_MUD);
        final BlockData guanostone;
        final BlockData coprolith;
        final BlockData peering;
        final BlockData porous;
        final BlockData[] log = new BlockData[3];
        final BlockData[] wood = new BlockData[3];
        final BlockData[] branch = new BlockData[6];
        final BlockData[] idol = new BlockData[6];
        /** Guano layers by layer count (index 1 to 8). */
        final BlockData[] guano = new BlockData[9];
        final BlockData strippedDarkOakLog = Bukkit.createBlockData(Material.STRIPPED_DARK_OAK_LOG);
        final BlockData bridgeSlab;
        final BlockData[] bridgeStairs = new BlockData[6];
        final BlockData fence = Bukkit.createBlockData(Material.DARK_OAK_FENCE);
        final BlockData chain = Bukkit.createBlockData(Material.IRON_CHAIN);
        final BlockData brownMushroom = Bukkit.createBlockData(Material.BROWN_MUSHROOM);
        /** Module blocks that are not full blocks in the mod (no solid render, not sturdy). */
        final Set<BlockData> notFull = new HashSet<>();
        /** Module blocks of the biome's rock, which the canyon carves like stone. */
        final Set<BlockData> rock = new HashSet<>();

        Blocks(ModBlockPalette palette) {
            guanostone = palette.get("guanostone");
            coprolith = palette.get("coprolith");
            peering = palette.get("peering_coprolith");
            porous = palette.get("porous_coprolith");
            String[] axes = {"x", "y", "z"};
            for (int i = 0; i < 3; i++) {
                log[i] = palette.get("thornwood_log", axes[i]);
                wood[i] = palette.get("thornwood_wood", axes[i]);
            }
            for (int d = 0; d < 6; d++) {
                branch[d] = palette.get("thornwood_branch", DIRECTION_NAMES[d]);
                notFull.add(branch[d]);
                if (d >= 2) {
                    idol[d] = palette.get("forsaken_idol", DIRECTION_NAMES[d]);
                    notFull.add(idol[d]);
                    Stairs stairs = (Stairs) Bukkit.createBlockData(Material.DARK_OAK_STAIRS);
                    stairs.setHalf(Bisected.Half.TOP);
                    stairs.setFacing(BlockFace.valueOf(DIRECTION_NAMES[d].toUpperCase(java.util.Locale.ROOT)));
                    bridgeStairs[d] = stairs;
                }
            }
            for (int layers = 1; layers <= 8; layers++) {
                guano[layers] = palette.get("guano_layer", Integer.toString(layers));
                if (layers < 8) {
                    notFull.add(guano[layers]);
                }
            }
            Slab slab = (Slab) Bukkit.createBlockData(Material.DARK_OAK_SLAB);
            slab.setType(Slab.Type.TOP);
            bridgeSlab = slab;
            rock.add(guanostone);
            rock.add(coprolith);
            rock.add(palette.get("coprolith_coal_ore"));
            rock.add(palette.get("guanostone_redstone_ore"));
            rock.add(peering);
            rock.add(porous);
        }

        BlockData log(int direction) {
            return log[axisIndex(direction)];
        }

        BlockData wood(int direction) {
            return wood[axisIndex(direction)];
        }

        private static int axisIndex(int direction) {
            return direction < 2 ? 1 : direction < 4 ? 2 : 0;
        }

        boolean isWood(BlockData data) {
            return data.equals(wood[0]) || data.equals(wood[1]) || data.equals(wood[2]);
        }

        boolean isBranch(BlockData data) {
            for (BlockData state : branch) {
                if (state.equals(data)) {
                    return true;
                }
            }
            return false;
        }

        /** Vanilla {@code isSolidRender}: an opaque full block (the module's non-full blocks are not). */
        boolean solidRender(CaveChunk.BlockSink blocks, int x, int y, int z) {
            Material type = blocks.getType(x, y, z);
            if (!type.isOccluding()) {
                return false;
            }
            return type != Material.NOTE_BLOCK || !notFull.contains(blocks.getBlockData(x, y, z));
        }

        /** Vanilla {@code BlockState.canBeReplaced}: air, liquids and replaceable plants. */
        static boolean canBeReplaced(Material type) {
            return type.isAir() || type == Material.WATER || type == Material.LAVA || Tag.REPLACEABLE.isTagged(type);
        }

        /** Materials whose block data can be waterlogged (the only ones whose data is read for fluids). */
        private static final boolean[] WATERLOGGABLE = new boolean[Material.values().length];

        static {
            for (Material material : Material.values()) {
                WATERLOGGABLE[material.ordinal()] = material.isBlock() && !material.isLegacy()
                    && Waterlogged.class.isAssignableFrom(material.data);
            }
        }

        /** A block holding a fluid (vanilla {@code !getFluidState().isEmpty()}). */
        static boolean hasFluid(CaveChunk.BlockSink blocks, int x, int y, int z) {
            Material type = blocks.getType(x, y, z);
            if (type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN) {
                return true;
            }
            return waterlogged(blocks, type, x, y, z);
        }

        static boolean water(CaveChunk.BlockSink blocks, int x, int y, int z) {
            Material type = blocks.getType(x, y, z);
            return type == Material.WATER || type == Material.BUBBLE_COLUMN || waterlogged(blocks, type, x, y, z);
        }

        private static boolean waterlogged(CaveChunk.BlockSink blocks, Material type, int x, int y, int z) {
            return WATERLOGGABLE[type.ordinal()] && blocks.getBlockData(x, y, z) instanceof Waterlogged waterlogged
                && waterlogged.isWaterlogged();
        }

        /** Mod tag {@code unmoveable}: blocks features never write over. */
        static boolean unmoveable(Material type) {
            return switch (type) {
                case BEDROCK, BARRIER, END_PORTAL_FRAME, END_PORTAL, REINFORCED_DEEPSLATE, COMMAND_BLOCK -> true;
                default -> false;
            };
        }
    }

    /**
     * Mod surface rule ({@code createForlornHollowsRules}): packed mud on every floor (stone with air or a liquid
     * above), coprolith where {@code simplexCondition(-0.2, 0.4, 40, 6, 3)} holds, guanostone everywhere else. As in
     * the mod, vanilla ores looked for stone after the rule, so they become the biome's rock too.
     */
    static final class Terrain implements ChunkFeature {
        private final Blocks b;

        Terrain(Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_terrain";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    int top = chunk.surfaceY(x, z) - BAND_TOP;
                    for (int y = top; y >= Math.max(chunk.minY() + 5, top - BAND_DEPTH); y--) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            continue;
                        }
                        Material type = blocks.getType(x, y, z);
                        if (CavernFeature.vanillaCaveDecoration(type)) {
                            blocks.setBlock(x, y, z, b.air);
                            continue;
                        }
                        if (!CavernFeature.surfaceRock(type)) {
                            continue;
                        }
                        Material above = blocks.getType(x, y + 1, z);
                        if (above.isAir() || above == Material.WATER || above == Material.LAVA) {
                            blocks.setBlock(x, y, z, b.packedMud);
                        } else {
                            blocks.setBlock(x, y, z, coprolith(x, y, z) ? b.coprolith : b.guanostone);
                        }
                    }
                }
            }
        }

        /** Mod {@code simplexCondition(-0.2, 0.4, 40, 6, 3)}. */
        static boolean coprolith(int x, int y, int z) {
            float value = SimplexNoise3D.sample(x + 3000, (int) (y * 6.0F + 6000), z - 9000, 40.0F);
            return value > -0.2F && value <= 0.4F;
        }
    }

    /**
     * The forlorn canyon ({@code ForlornCanyonStructure}, {@code random_spread} 6/4/6): each start in the biome opens a
     * canyon as wide as the biome continues around it (up to 100) from y=-10, in 16-block pieces. Each piece carves its
     * columns from the top down until the canyon stops, walls liquids next to it with guanostone and raises the floor
     * under the opening with 1 to 4 blocks of packed mud ({@code decorateFloor}). As in the mod the pieces carve wherever
     * they reach, whatever the biome there; only rock, dirt, ores, liquids and the biome's blocks are carved (the mod
     * carves anything but bedrock, before the vanilla structures that are already in place here).
     */
    static final class Canyon implements ChunkFeature {
        private final Blocks b;
        private final BowlFeature planner;

        Canyon(Blocks b) {
            this.b = b;
            // Only plans the canyons (cavesReaching); its own carving is not used.
            this.planner = new BowlFeature("forlorn_canyon", BIOME, CaveBowl.Kind.FORLORN_CANYON, b.air, b.guanostone,
                b.guanostone, null);
        }

        @Override
        public String id() {
            return "forlorn_canyon";
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
            for (CaveBowl canyon : planner.cavesReaching(chunk)) {
                carve(chunk, canyon);
            }
        }

        private void carve(CaveChunk chunk, CaveBowl canyon) {
            int heightChunks = canyon.heightChunks();
            int fromPiece = Math.max(-heightChunks, (int) Math.ceil(chunk.minY() / 16.0F));
            int toPiece = Math.min(heightChunks, 20);
            for (int piece = fromPiece; piece <= toPiece; piece++) {
                int cornerY = canyon.centerY() + piece * 16;
                if (cornerY + 15 < canyon.carveMinY() || cornerY > canyon.carveMaxY()) {
                    continue;
                }
                for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                    for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                        if (canyon.columnMayCarve(x, z)) {
                            column(chunk, canyon, cornerY, x, z);
                        }
                    }
                }
            }
        }

        /** Mod {@code ForlornCanyonStructurePiece.postProcess} for one column of one piece. */
        private void column(CaveChunk chunk, CaveBowl canyon, int cornerY, int x, int z) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            boolean doFloor = false;
            int carveBelow = 0;
            for (int dy = 15; dy >= 0; dy--) {
                int y = Math.max(chunk.minY(), cornerY + dy);
                if (canyon.carve(x, y, z) == CaveBowl.Carve.AIR && blocks.getType(x, y, z) != Material.BEDROCK) {
                    if (carvable(blocks, x, y, z)) {
                        blocks.setBlock(x, y, z, b.air);
                        wallLiquids(chunk, cornerY, x, y, z);
                    }
                    carveBelow = y - 1;
                    doFloor = true;
                } else if (doFloor) {
                    break;
                }
            }
            if (doFloor && inPiece(cornerY, carveBelow) && !blocks.getType(x, carveBelow, z).isAir()) {
                decorateFloor(blocks, cornerY, x, carveBelow, z);
            }
        }

        private boolean carvable(CaveChunk.BlockSink blocks, int x, int y, int z) {
            Material type = blocks.getType(x, y, z);
            if (CavernFeature.carvable(type) || CavernFeature.surfaceRock(type) || type == Material.PACKED_MUD
                || Blocks.hasFluid(blocks, x, y, z) && !type.isSolid()) {
                return true;
            }
            return type == Material.NOTE_BLOCK && b.rock.contains(blocks.getBlockData(x, y, z));
        }

        /** Mod {@code surroundCornerOfLiquid}: liquids next to the carved block, inside the piece, become guanostone. */
        private void wallLiquids(CaveChunk chunk, int cornerY, int x, int y, int z) {
            for (int[] d : DIRECTIONS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (nx >> 4 == chunk.chunkX() && nz >> 4 == chunk.chunkZ() && inPiece(cornerY, ny)
                    && Blocks.hasFluid(chunk.blocks(), nx, ny, nz)) {
                    chunk.blocks().setBlock(nx, ny, nz, b.guanostone);
                }
            }
        }

        /** Mod {@code decorateFloor}: packed mud on the floor and 0 to 3 blocks over it, higher where a noise is. */
        private void decorateFloor(CaveChunk.BlockSink blocks, int cornerY, int x, int y, int z) {
            float floorNoise = (SimplexNoise3D.sample2D(x, z, 50.0F) + 1.0F) * 0.5F;
            blocks.setBlock(x, y, z, b.packedMud);
            for (int i = 0; i < Math.ceil(floorNoise * 3.0F); i++) {
                y++;
                if (inPiece(cornerY, y)) {
                    blocks.setBlock(x, y, z, b.packedMud);
                }
            }
        }

        /** The piece's box: from 2 below its corner to 16 above. */
        private static boolean inPiece(int cornerY, int y) {
            return y >= cornerY - 2 && y <= cornerY + 16;
        }
    }
}
