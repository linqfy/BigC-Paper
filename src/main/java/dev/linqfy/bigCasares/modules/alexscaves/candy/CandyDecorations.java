package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureRotations;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Decoration of the Candy Cavity, ported from the mod's features with their placed-feature numbers: ice cream
 * scoops and ceiling cones, cookie shelves, lollipops, licoroot trees, floating gummy rings, candy canes, encrusted
 * peppermints, spilled ice cream cones, peppermint patches and gobstopper geodes. Blocks the module cannot draw yet
 * (plants without collision: small candy canes) are left out; licoroot sprouts need the Paper options; licoroot vines are weeping vines.
 *
 * <p>Mod placement notes: {@code random_offset} with {@code xz_spread: 8} is a constant offset, so features without
 * {@code in_square} stand on the chunk's middle column; {@code environment_scan} returns the block it found.
 */
final class CandyDecorations {
    static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;
    /** Mod {@code ACMath.HORIZONTAL_DIRECTIONS}: north, east, south, west. */
    static final int[][] HORIZONTAL = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    static final String[] HORIZONTAL_NAMES = {"north", "east", "south", "west"};
    static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** Vanilla {@code Direction.values()}: down, up, north, south, west, east. */
    static final int[][] ALL_DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final String[] ALL_NAMES = {"down", "up", "north", "south", "west", "east"};

    private CandyDecorations() {
    }

    /** Blocks shared by the candy features, made on the main thread. */
    static final class Blocks {
        final BlockData frosted;
        final Set<BlockData> chocolate;
        final Set<BlockData> cake;
        final BlockData chocolateY;
        final BlockData cakeY;
        final BlockData cookie;
        final BlockData wafer;
        final BlockData waferWall = Bukkit.createBlockData(Material.SANDSTONE_WALL);
        /** Wafer cookie stairs (birch stairs) by facing (horizontal index), half (0 bottom, 1 top), outer left. */
        final Map<String, BlockData> waferStairs = new HashMap<>();
        final BlockData[][] iceCream;
        final Set<BlockData> anyIceCream = new HashSet<>();
        final BlockData sweetberry;
        final BlockData licorootY;
        final BlockData licorootX;
        final BlockData licorootZ;
        /** Licoroot sprout: a plant without collision, null in legacy mode (it would be a solid note block). */
        final BlockData licorootSprout;
        final BlockData vine;
        final BlockData vineEnd;
        final BlockData[] gummyRings;
        final BlockData[] sunkGummyRings;
        final BlockData candyCanePole;
        final BlockData[] candyCaneHook;
        final BlockData strippedCaneBlock;
        final BlockData[] smallPeppermint;
        final BlockData[] largePeppermint;
        final Map<String, BlockData> rockCandy = new HashMap<>();
        final BlockData frostmintTop;
        final BlockData frostmintDouble;

        Blocks(ModBlockPalette palette) {
            frosted = palette.get("block_of_frosted_chocolate");
            chocolate = Set.of(palette.get("block_of_chocolate", "x"), palette.get("block_of_chocolate", "y"),
                palette.get("block_of_chocolate", "z"));
            cake = Set.of(palette.get("cake_layer", "x"), palette.get("cake_layer", "y"), palette.get("cake_layer", "z"));
            chocolateY = palette.get("block_of_chocolate", "y");
            cakeY = palette.get("cake_layer", "y");
            cookie = palette.get("cookie_block");
            wafer = palette.get("wafer_cookie_block");
            for (int facing = 0; facing < 4; facing++) {
                for (int half = 0; half < 2; half++) {
                    for (int outer = 0; outer < 2; outer++) {
                        Stairs stairs = (Stairs) Bukkit.createBlockData(Material.BIRCH_STAIRS);
                        stairs.setFacing(HORIZONTAL_FACES[facing]);
                        stairs.setHalf(half == 1 ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
                        stairs.setShape(outer == 1 ? Stairs.Shape.OUTER_LEFT : Stairs.Shape.STRAIGHT);
                        waferStairs.put(facing + ":" + half + ":" + outer, stairs);
                    }
                }
            }
            String[] flavours = {"vanilla_ice_cream", "chocolate_ice_cream", "sweetberry_ice_cream"};
            iceCream = new BlockData[3][3];
            for (int f = 0; f < 3; f++) {
                for (int type = 0; type < 3; type++) {
                    iceCream[f][type] = palette.get(flavours[f], Integer.toString(type));
                    anyIceCream.add(iceCream[f][type]);
                }
            }
            sweetberry = palette.get("giant_sweetberry");
            licorootX = palette.get("licoroot", "x");
            licorootY = palette.get("licoroot", "y");
            licorootZ = palette.get("licoroot", "z");
            licorootSprout = palette.registry().shapedBasesActive() ? palette.get("licoroot_sprout") : null;
            org.bukkit.block.data.Ageable end = (org.bukkit.block.data.Ageable) Bukkit.createBlockData(Material.WEEPING_VINES);
            end.setAge(end.getMaximumAge());
            vineEnd = end;
            vine = Bukkit.createBlockData(Material.WEEPING_VINES_PLANT);
            gummyRings = new BlockData[] {palette.get("gummy_ring_red", "up"), palette.get("gummy_ring_green", "up"),
                palette.get("gummy_ring_yellow", "up"), palette.get("gummy_ring_blue", "up")};
            sunkGummyRings = new BlockData[] {palette.get("gummy_ring_red", "sunk"), palette.get("gummy_ring_green", "sunk"),
                palette.get("gummy_ring_yellow", "sunk"), palette.get("gummy_ring_blue", "sunk")};
            candyCanePole = palette.get("candy_cane_pole");
            candyCaneHook = new BlockData[4];
            for (int i = 0; i < 4; i++) {
                candyCaneHook[i] = palette.get("candy_cane_pole", HORIZONTAL_NAMES[i]);
            }
            strippedCaneBlock = palette.get("stripped_candy_cane_block", "y");
            smallPeppermint = new BlockData[6];
            largePeppermint = new BlockData[6];
            for (int i = 0; i < 6; i++) {
                smallPeppermint[i] = palette.get("small_peppermint", ALL_NAMES[i]);
                largePeppermint[i] = palette.get("large_peppermint", ALL_NAMES[i]);
            }
            for (String colour : new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
                rockCandy.put(colour, palette.get("rock_candy_" + colour));
            }
            frostmintTop = palette.get("frostmint", "top");
            frostmintDouble = palette.get("frostmint", "double");
        }

        boolean isChocolate(BlockData data) {
            return data != null && chocolate.contains(data);
        }

        boolean isCake(BlockData data) {
            return data != null && cake.contains(data);
        }

        BlockData stairs(int facing, boolean top, boolean outerLeft) {
            return waferStairs.get(facing + ":" + (top ? 1 : 0) + ":" + (outerLeft ? 1 : 0));
        }
    }

    static List<ChunkFeature> create(ModBlockPalette palette, BowlFeature cakeCave) {
        Blocks blocks = new Blocks(palette);
        Set<BlockData> ruinFloor = new HashSet<>(blocks.chocolate);
        ruinFloor.add(blocks.frosted);
        List<ChunkFeature> features = new ArrayList<>();
        // Mod step LOCAL_MODIFICATIONS.
        features.add(new RuinsFeature("candy_ruins", BIOME, 28, 5,
            StructureTemplates.load("chocolate_bar"), false, null,
            ruinFloor, true));
        features.add(new GobstopperGeode(blocks));
        // UNDERGROUND_STRUCTURES: the biome's structures.
        features.addAll(CandyStructures.create(blocks, palette, cakeCave));
        // SURFACE_STRUCTURES: the three scoops (chocolate, vanilla, sweetberry) and the ceiling cones.
        features.add(new IceCreamScoop("candy_chocolate_ice_cream_scoop", blocks, 1));
        features.add(new IceCreamScoop("candy_vanilla_ice_cream_scoop", blocks, 0));
        features.add(new IceCreamScoop("candy_sweetberry_ice_cream_scoop", blocks, 2));
        features.add(new CeilingIceCreamCone(blocks));
        // STRONGHOLDS.
        features.add(new CookieShelf(blocks));
        features.add(new Lollipop(blocks,
            // Mod config: big tops 0-3 (big_lollipop_top_4 is not used).
            StructureTemplates.load("big_lollipop_top").subList(0, 4), StructureTemplates.load("small_lollipop_top")));
        features.add(new LicorootTree(blocks));
        features.add(new FloatingGummyRing(blocks));
        features.add(new CeilingFrostmint(blocks));
        // UNDERGROUND_ORES.
        features.add(new PeppermintPatch(blocks));
        // VEGETAL_DECORATION.
        features.add(new CandyCane(blocks));
        features.add(new EncrustedPeppermint(blocks));
        features.add(new SpilledIceCreamCone(blocks));
        return features;
    }

    /** Shared helpers of the candy features. */
    abstract static class Decor implements ChunkFeature {
        private final String id;
        final Blocks b;

        Decor(String id, Blocks b) {
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

        static boolean air(Material type) {
            return type.isAir() || type == Material.LIGHT;
        }

        static boolean air(CaveChunk chunk, int x, int y, int z) {
            return air(chunk.blocks().getType(x, y, z));
        }

        /** Vanilla {@code canBeReplaced}: air, fluids and soft plants. */
        static boolean replaceable(CaveChunk chunk, int x, int y, int z) {
            Material type = chunk.blocks().getType(x, y, z);
            return type.isAir() || !type.isSolid();
        }

        /** Vanilla {@code isFaceSturdy}: full solid blocks. */
        static boolean sturdy(CaveChunk chunk, int x, int y, int z) {
            Material type = chunk.blocks().getType(x, y, z);
            return type.isSolid() && type.isOccluding();
        }

        static boolean solid(CaveChunk chunk, int x, int y, int z) {
            return chunk.blocks().getType(x, y, z).isSolid();
        }

        BlockData data(CaveChunk chunk, int x, int y, int z) {
            return chunk.blocks().getBlockData(x, y, z);
        }

        boolean frosted(CaveChunk chunk, int x, int y, int z) {
            return Objects.equals(b.frosted, data(chunk, x, y, z));
        }

        static void set(CaveChunk chunk, int x, int y, int z, BlockData data) {
            chunk.blocks().setBlock(x, y, z, data);
        }

        /** {@code height_range} uniform from the bottom of the world up to {@code top} (absolute). */
        static int height(CaveChunk chunk, SplittableRandom random, int top) {
            return ModPlacement.uniform(random, chunk.minY(), top);
        }

        /** {@code environment_scan} down to the first block that is not air: that block's y, or MIN_VALUE. */
        static int scanDown(CaveChunk chunk, int x, int y, int z) {
            return ModPlacement.scanDown(chunk, x, y, z, 32, ModPlacement::notAir);
        }

        /**
         * {@code environment_scan} up through air to a block with a sturdy bottom face: that block's y, or
         * MIN_VALUE.
         */
        static int scanUpToCeiling(CaveChunk chunk, int x, int y, int z) {
            if (!air(chunk, x, y, z)) {
                return Integer.MIN_VALUE;
            }
            for (int i = 0; i < 32; i++) {
                if (sturdy(chunk, x, y, z)) {
                    return y;
                }
                y++;
                if (y >= ModPlacement.TOP + 64) {
                    return Integer.MIN_VALUE;
                }
                if (!air(chunk, x, y, z)) {
                    break;
                }
            }
            return sturdy(chunk, x, y, z) ? y : Integer.MIN_VALUE;
        }

        /** Mod {@code selectDirection}: with several, one of all but the last. */
        static int selectDirection(List<Integer> options, SplittableRandom random) {
            if (options.isEmpty()) {
                return -1;
            }
            return options.size() <= 1 ? options.get(0) : options.get(random.nextInt(options.size() - 1));
        }

        static boolean sameChunk(int x, int z, int ox, int oz) {
            return x >> 4 == ox >> 4 && z >> 4 == oz >> 4;
        }

        static int middleX(CaveChunk chunk) {
            return chunk.minBlockX() + 8;
        }

        static int middleZ(CaveChunk chunk) {
            return chunk.minBlockZ() + 8;
        }
    }

    /**
     * Ice cream scoops (mod {@code IceCreamScoopFeature}, {@code noise_based_count} 300/0/4 at the chunk's middle):
     * a melting ball of ice cream on frosted chocolate, or a smaller one stacked on another scoop, half the time
     * with a giant sweetberry on top.
     */
    static final class IceCreamScoop extends Decor {
        private final int flavour;

        IceCreamScoop(String id, Blocks b, int flavour) {
            super(id, b);
            this.flavour = flavour;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.noiseCount(chunk, 300.0, 0.0, 4.0);
            for (int i = 0; i < count; i++) {
                int x = middleX(chunk);
                int z = middleZ(chunk);
                int y = scanDown(chunk, x, height(chunk, random, ModPlacement.TOP), z);
                if (y != Integer.MIN_VALUE && chunk.isBiome(x, y, z, BIOME)) {
                    scoop(chunk, x, y, z, random);
                }
            }
        }

        private void scoop(CaveChunk chunk, int gx, int gy, int gz, SplittableRandom random) {
            BlockData below = data(chunk, gx, gy, gz);
            int pileHeight = 6 + random.nextInt(3);
            int pileWidth = 2 + random.nextInt(3);
            if (below != null && b.anyIceCream.contains(below)) {
                pileHeight -= 2;
                // Mod: random.nextInt(1), always 0.
            } else if (!Objects.equals(below, b.frosted)) {
                return;
            }
            long seed = mthSeed(gx, 0, gz);
            int xOffset = (int) Math.max(-3.0, Math.min(3.0, ((float) (seed & 15L) / 15.0F - 0.5) * 6.0));
            int zOffset = (int) Math.max(-3.0, Math.min(3.0, ((float) (seed >> 8 & 15L) / 15.0F - 0.5) * 6.0));
            gx += xOffset;
            gy += 2;
            gz += zOffset;
            double circleFat = Math.pow(pileWidth, 2.5);
            BlockData[] ours = b.iceCream[flavour];
            for (int x = -pileWidth; x <= pileWidth; x++) {
                for (int z = -pileWidth; z <= pileWidth; z++) {
                    int yAdd = pileHeight;
                    int minFall = -3 - random.nextInt(5);
                    int px = gx + x;
                    int pz = gz + z;
                    int py = gy + pileHeight;
                    BlockData last = null;
                    while (yAdd > 0 || replaceable(chunk, px, py - 1, pz) && py > chunk.minY() && yAdd > minFall) {
                        py = gy + yAdd;
                        // Vanilla distToCenterSqr: from the middle of the block.
                        double dist = sq(px + 0.5 - gx) + (yAdd >= 0 ? sq(py + 0.5 - gy) : 0.25) + sq(pz + 0.5 - gz);
                        BlockData replacing = data(chunk, px, py, pz);
                        if ((replaceable(chunk, px, py, pz) || Objects.equals(replacing, b.sweetberry)) && dist < circleFat) {
                            set(chunk, px, py, pz, last = ours[0]);
                        }
                        yAdd--;
                    }
                    BlockData bottom = data(chunk, px, py, pz);
                    if (bottom != null && b.anyIceCream.contains(bottom)) {
                        int type = 0;
                        if (last != null) {
                            BlockData beneath = data(chunk, px, py - 1, pz);
                            if (beneath != null && isFlavour(beneath, flavour)) {
                                type = 0;
                            } else if (sturdy(chunk, px, py - 1, pz)) {
                                type = 1;
                            } else {
                                type = 2;
                            }
                        }
                        set(chunk, px, py, pz, b.iceCream[flavourOf(bottom)][type]);
                    }
                }
            }
            if (random.nextFloat() < 0.5F) {
                int py = gy - 6;
                while (isIceCream(chunk, gx, py, gz) && py < ModPlacement.TOP || py < gy) {
                    boolean on = isIceCream(chunk, gx, py, gz);
                    py++;
                    if (on && !isIceCream(chunk, gx, py, gz)) {
                        break;
                    }
                }
                set(chunk, gx, py, gz, b.sweetberry);
            }
        }

        private boolean isIceCream(CaveChunk chunk, int x, int y, int z) {
            BlockData data = data(chunk, x, y, z);
            return data != null && b.anyIceCream.contains(data);
        }

        private boolean isFlavour(BlockData data, int f) {
            for (BlockData state : b.iceCream[f]) {
                if (state.equals(data)) {
                    return true;
                }
            }
            return false;
        }

        private int flavourOf(BlockData data) {
            for (int f = 0; f < 3; f++) {
                if (isFlavour(data, f)) {
                    return f;
                }
            }
            return flavour;
        }

        private static double sq(double v) {
            return v * v;
        }

        /** Vanilla {@code Mth.getSeed}. */
        static long mthSeed(int x, int y, int z) {
            long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
            l = l * l * 42317861L + l * 11L;
            return l >> 16;
        }
    }

    /**
     * Ice cream cones hanging from cake or chocolate ceilings (mod {@code CeilingIceCreamConeFeature}, 0-3 per chunk):
     * a dome of ice cream under the ceiling and a wafer cone narrowing downwards, its rings of wafer stairs.
     */
    static final class CeilingIceCreamCone extends Decor {
        CeilingIceCreamCone(Blocks b) {
            super("candy_ceiling_ice_cream_cone", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 0, 3);
            for (int i = 0; i < count; i++) {
                // Mod order: height, biome and the ceiling scan at the chunk corner, then the constant offset to the
                // middle column, one below the ceiling.
                int y = height(chunk, random, chunk.minY() + 160);
                if (!chunk.isBiome(chunk.minBlockX(), y, chunk.minBlockZ(), BIOME)) {
                    continue;
                }
                int ceiling = scanUpToCeiling(chunk, chunk.minBlockX(), y, chunk.minBlockZ());
                if (ceiling != Integer.MIN_VALUE) {
                    cone(chunk, middleX(chunk), ceiling - 1, middleZ(chunk), random);
                }
            }
        }

        private void cone(CaveChunk chunk, int cx, int cy, int cz, SplittableRandom random) {
            int type = random.nextInt(3);
            int flavour = type == 1 ? 1 : type == 2 ? 2 : 0;
            BlockData above = data(chunk, cx, cy + 1, cz);
            if (!b.isCake(above) && !b.isChocolate(above)) {
                return;
            }
            int radius = 3 + random.nextInt(3);
            int length = radius + 8 + random.nextInt(8);
            for (int y = 5; y > -length; y--) {
                boolean stairFlag = false;
                int shrinkIncrement = radius <= 1 ? 2 : length / 3;
                if (((y - 1) % shrinkIncrement == 0 || length - y < 2) && y < 0 || y == -1) {
                    stairFlag = true;
                }
                if (y == -1) {
                    radius--;
                }
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        int px = cx + x;
                        int py = cy + y;
                        int pz = cz + z;
                        // Vanilla distToCenterSqr: from the middle of the block.
                        double distXZ = sq(x + 0.5) + (y > 1 ? sq(y + 0.5) : 0.25) + sq(z + 0.5);
                        double circleFat = radius * radius;
                        if (distXZ >= circleFat || !replaceable(chunk, px, py, pz)) {
                            continue;
                        }
                        boolean placed = false;
                        if (y < 0) {
                            if (Math.sqrt(distXZ) >= radius - 1.0) {
                                placed = true;
                                if (stairFlag && radius > 1) {
                                    float angle = 180.0F - (float) (Math.atan2(x, z) * 180.0F / (float) Math.PI);
                                    boolean outer = degreesDifference(angle % 90.0F, 45.0F) < 10.0F;
                                    set(chunk, px, py, pz, b.stairs(fromYRot(angle), true, outer));
                                } else {
                                    set(chunk, px, py, pz, b.wafer);
                                }
                            }
                        } else {
                            if (y != 5 || random.nextBoolean()) {
                                set(chunk, px, py, pz, b.iceCream[flavour][2]);
                            }
                            placed = true;
                        }
                        BlockData up = data(chunk, px, py + 1, pz);
                        if (placed && up != null && isFlavour(up, flavour)) {
                            set(chunk, px, py + 1, pz, b.iceCream[flavour][y >= 0 ? 0 : 1]);
                        }
                    }
                }
                if (stairFlag) {
                    radius--;
                }
            }
        }

        private boolean isFlavour(BlockData data, int flavour) {
            for (BlockData state : b.iceCream[flavour]) {
                if (state.equals(data)) {
                    return true;
                }
            }
            return false;
        }

        private static double sq(double v) {
            return v * v;
        }

        /** Vanilla {@code Direction.fromYRot} as an index of {@link #HORIZONTAL}. */
        static int fromYRot(float angle) {
            // 2D data values: south, west, north, east.
            int[] byData = {2, 3, 0, 1};
            return byData[(int) Math.floor(angle / 90.0 + 0.5) & 3];
        }

        /** Vanilla {@code Mth.degreesDifferenceAbs}. */
        static float degreesDifference(float a, float b) {
            float difference = (b - a) % 360.0F;
            if (difference >= 180.0F) {
                difference -= 360.0F;
            }
            if (difference < -180.0F) {
                difference += 360.0F;
            }
            return Math.abs(difference);
        }
    }

    /**
     * Cookie shelves (mod {@code CookieShelfFeature}, 10-60 tries per chunk below y=64): a round cookie sticking out
     * of a cake or chocolate wall, sometimes with more above and below.
     */
    static final class CookieShelf extends Decor {
        CookieShelf(Blocks b) {
            super("candy_cookie_shelf", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 10, 60);
            for (int i = 0; i < count; i++) {
                int y = height(chunk, random, 64);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                boolean first = cookie(chunk, x, y, z, random);
                if (first && random.nextBoolean()) {
                    cookie(chunk, x, y + 2 + random.nextInt(3), z, random);
                }
                if (first && random.nextBoolean()) {
                    cookie(chunk, x, y - 2 - random.nextInt(3), z, random);
                }
            }
        }

        private boolean cookie(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            if (!air(chunk, x, y, z)) {
                return false;
            }
            List<Integer> options = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nx = x + HORIZONTAL[d][0];
                int nz = z + HORIZONTAL[d][2];
                BlockData neighbour = data(chunk, nx, y, nz);
                if (sameChunk(x, z, nx, nz) && (b.isCake(neighbour) || b.isChocolate(neighbour))) {
                    options.add((d + 2) % 4);
                }
            }
            int direction = selectDirection(options, random);
            if (direction < 0) {
                return false;
            }
            int radius = 4 + random.nextInt(3);
            int towards = (direction + 2) % 4;
            int ox = x + HORIZONTAL[towards][0] * (radius / 3);
            int oz = z + HORIZONTAL[towards][2] * (radius / 3);
            double radiusSq = Math.pow(radius, 1.5);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radiusSq && chunk.blocks().getType(ox + dx, y, oz + dz) != Material.BEDROCK
                        && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        set(chunk, ox + dx, y, oz + dz, b.cookie);
                    }
                }
            }
            return true;
        }
    }

    /**
     * Lollipops (mod {@code LollipopFeature}, one try per chunk at its middle): a stripped candy cane stick on frosted
     * chocolate with a rock candy top, big 40% of the time; the top's grey placeholders take one of the mod's colour
     * palettes ({@code LollipopProcessor}).
     */
    static final class Lollipop extends Decor {
        /** Mod {@code LollipopProcessor.BLOCK_COLOR_PALETTES}: replacements for black, gray, light gray, white. */
        private static final String[][] PALETTES = {
            {"light_blue", "green", "orange", "red"}, {"yellow", "pink", "blue", "red"},
            {"white", "cyan", "green", "yellow"}, {"white", "cyan", "green", "yellow"},
            {"pink", "magenta", "light_blue", "blue"}, {"red", "pink", "magenta", "purple"},
            {"white", "pink", "magenta", "purple"}, {"white", "pink", "magenta", "purple"},
            {"white", "yellow", "orange", "red"}, {"white", "cyan", "light_blue", "blue"},
            {"white", "cyan", "lime", "green"}, {"yellow", "cyan", "pink", "purple"},
            {"pink", "blue", "purple", "black"}};
        private static final String[] PLACEHOLDERS = {"black", "gray", "light_gray", "white"};
        private static final StructureRotation[] ROTATIONS = StructureRotation.values();
        private final List<Template> big;
        private final List<Template> small;

        Lollipop(Blocks b, List<Structure> big, List<Structure> small) {
            super("candy_lollipop", b);
            this.big = big.stream().map(Template::of).toList();
            this.small = small.stream().map(Template::of).toList();
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int x = middleX(chunk);
            int z = middleZ(chunk);
            int y = scanDown(chunk, x, height(chunk, random, ModPlacement.TOP), z);
            if (y == Integer.MIN_VALUE || !chunk.isBiome(x, y, z, BIOME) || !frosted(chunk, x, y, z)) {
                return;
            }
            boolean isBig = random.nextFloat() < 0.4F;
            y++;
            int poleHeight = 3 + random.nextInt(3);
            if (isBig) {
                poleHeight += 4 + random.nextInt(2);
            }
            int clearance = poleHeight + (isBig ? 10 : 6);
            for (int i = 1; i <= clearance; i++) {
                if (!replaceable(chunk, x, y + i, z)) {
                    return;
                }
            }
            for (int i = 0; i < poleHeight; i++) {
                set(chunk, x, y + i, z, b.strippedCaneBlock);
            }
            StructureRotation rotation = ROTATIONS[random.nextInt(4)];
            List<Template> pool = isBig ? big : small;
            Template template = pool.get(random.nextInt(pool.size()));
            int palette = Math.min(random.nextInt(PALETTES.length - 1), PALETTES.length - 1);
            boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
            int rotatedX = quarter ? template.sizeZ : template.sizeX;
            int rotatedZ = quarter ? template.sizeX : template.sizeZ;
            int cornerX = x - Math.round(rotatedX / 2.0F - 1.0F);
            int cornerZ = z - (int) Math.ceil(rotatedZ / 2.0F - 1.0F);
            int[] zero = RuinsFeature.zeroCorner(cornerX, cornerZ, template.sizeX - 1, template.sizeZ - 1, rotation);
            int baseY = y + poleHeight;
            for (Template.Entry entry : template.blocks) {
                int[] at = StructureRotations.rotate(entry.x(), entry.z(), rotation);
                set(chunk, zero[0] + at[0], baseY + entry.y(), zero[1] + at[1], recolour(entry.data(), palette));
            }
        }

        private BlockData recolour(BlockData data, int palette) {
            for (int i = 0; i < PLACEHOLDERS.length; i++) {
                if (data.equals(b.rockCandy.get(PLACEHOLDERS[i]))) {
                    return b.rockCandy.get(PALETTES[palette][i]);
                }
            }
            return data;
        }
    }

    /** A structure template read once on the main thread, for features that place it block by block. */
    record Template(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        /** One block of the template; {@code lootTable} is set for loot containers. */
        record Entry(int x, int y, int z, BlockData data, org.bukkit.loot.LootTable lootTable) {
        }

        static Template of(Structure structure) {
            List<Entry> entries = new ArrayList<>();
            for (org.bukkit.block.BlockState state : structure.getPalettes().get(0).getBlocks()) {
                org.bukkit.loot.LootTable loot = state instanceof org.bukkit.loot.Lootable lootable ? lootable.getLootTable() : null;
                entries.add(new Entry(state.getX(), state.getY(), state.getZ(), state.getBlockData(), loot));
            }
            return new Template(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ(),
                List.copyOf(entries));
        }
    }

    /**
     * Licoroot trees (mod {@code LicorootTreeWithSproutsFeature}, 1 chunk in 2): a licoroot trunk on chocolate or cake
     * with side roots and branches that spiral up or down, dropping vines.
     */
    static final class LicorootTree extends Decor {
        /** Mod {@code SPIRAL_UP_MAP}/{@code SPIRAL_DOWN_MAP} per horizontal direction: out, up/down, back, down/up. */
        private static final int UP = 4;
        private static final int DOWN = 5;
        private static final int[][] MOVES = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

        LicorootTree(Blocks b) {
            super("candy_licoroot_tree", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 2)) {
                return;
            }
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int y = scanDown(chunk, x, height(chunk, random, ModPlacement.TOP), z);
            if (y != Integer.MIN_VALUE && chunk.isBiome(x, y, z, BIOME)) {
                if (tree(chunk, x, y, z, random)) {
                    sprouts(chunk, x, y, z, random);
                }
            }
        }

        /**
         * Mod {@code LicorootTreeWithSproutsFeature}, after the tree: 3-6 tries (the bound is drawn again on every
         * pass) at a spot 6 blocks around and 0-11 above the origin, dropped to the floor; a sprout goes on frosted
         * chocolate under a replaceable block.
         */
        private void sprouts(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                int sx = x + random.nextInt(12) - 6;
                int sy = y + random.nextInt(12);
                int sz = z + random.nextInt(12) - 6;
                while (chunk.blocks().isInRegion(sx, sy, sz) && air(chunk, sx, sy, sz) && sy > chunk.minY()) {
                    sy--;
                }
                if (b.licorootSprout != null && chunk.blocks().isInRegion(sx, sy + 1, sz) && sturdy(chunk, sx, sy, sz)
                    && replaceable(chunk, sx, sy + 1, sz) && frosted(chunk, sx, sy, sz)) {
                    set(chunk, sx, sy + 1, sz, b.licorootSprout);
                }
            }
        }

        private boolean tree(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            int centerAbove = 5 + random.nextInt(5);
            int height = centerAbove + 4 + random.nextInt(7);
            if (!canPlace(chunk, x, y, z, height)) {
                return false;
            }
            int centerY = y + centerAbove;
            for (int[] d : HORIZONTAL) {
                if (random.nextBoolean()) {
                    int ex = x + d[0];
                    int ey = centerY - 1;
                    int ez = z + d[2];
                    for (int lengths = 0; canReplace(chunk, ex, ey, ez) && lengths < centerAbove + 2; lengths++) {
                        set(chunk, ex, ey, ez, b.licorootY);
                        ey--;
                    }
                }
            }
            for (int i = 0; i < height; i++) {
                set(chunk, x, y + i, z, b.licorootY);
            }
            int top = y + height;
            boolean branched = false;
            for (int d = 0; d < 4; d++) {
                if (random.nextBoolean()) {
                    int length = random.nextInt(8) + 8;
                    int from = top - 1 - random.nextInt(2);
                    branch(chunk, x, from, z, random, length, d, random.nextInt(2) == 0, random.nextBoolean());
                    branched = true;
                }
            }
            if (!branched) {
                int length = random.nextInt(8) + 8;
                int from = top - 1 - random.nextInt(2);
                branch(chunk, x, from, z, random, length, random.nextInt(3), random.nextInt(2) == 0, random.nextBoolean());
            }
            return true;
        }

        private void branch(CaveChunk chunk, int sx, int sy, int sz, SplittableRandom random, int length, int original,
                            boolean spiralUp, boolean propagate) {
            int back = (original + 2) % 4;
            int[] directions = spiralUp ? new int[] {original, UP, back, DOWN} : new int[] {original, DOWN, back, UP};
            int ordinal = 0;
            int move = directions[0];
            int px = sx + MOVES[move][0];
            int py = sy + MOVES[move][1];
            int pz = sz + MOVES[move][2];
            int movesInDirection = 0;
            int movesToSpiral = (int) Math.floor(length / 4.0F);
            boolean split = false;
            int i = 0;
            while (i < length && canReplace(chunk, px, py, pz)) {
                if (propagate && move < 4 && !split && random.nextFloat() < 0.6F) {
                    split = true;
                    branch(chunk, px, py, pz, random, Math.max(2, length - 2), original, !spiralUp, false);
                }
                int needed = ordinal == 0 ? movesToSpiral + 1 : movesToSpiral;
                if (random.nextFloat() < 0.8F && movesInDirection >= needed) {
                    movesInDirection = 0;
                    ordinal = (ordinal + 1) % 4;
                    move = directions[ordinal];
                } else {
                    movesInDirection++;
                    dropVines(chunk, px, py, pz, random);
                    set(chunk, px, py, pz, move >= 4 ? b.licorootY : MOVES[move][0] != 0 ? b.licorootX : b.licorootZ);
                }
                i++;
                px += MOVES[move][0];
                py += MOVES[move][1];
                pz += MOVES[move][2];
            }
        }

        /** Mod {@code dropVinesFrom}: 80% of the branch blocks drop a vine of 1-4 blocks. */
        private void dropVines(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            if (random.nextFloat() >= 0.8F) {
                return;
            }
            int length = 1 + random.nextInt(4);
            int vy = y - 1;
            int i = 0;
            for (; i < length && replaceable(chunk, x, vy, z); i++) {
                set(chunk, x, vy, z, b.vine);
                vy--;
            }
            if (i > 0) {
                set(chunk, x, vy + 1, z, b.vineEnd);
            }
        }

        private boolean canPlace(CaveChunk chunk, int x, int y, int z, int height) {
            BlockData below = data(chunk, x, y - 1, z);
            if (!Objects.equals(below, b.frosted) && !b.isChocolate(below) && !b.isCake(below)) {
                return false;
            }
            for (int i = 0; i < height; i++) {
                if (!canReplace(chunk, x, y + i, z)) {
                    return false;
                }
            }
            int top = y + height;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (!canReplace(chunk, x + dx, top + dy, z + dz)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /** Mod {@code canReplace}: soft blocks, chocolate and the tree's own vines. */
        private boolean canReplace(CaveChunk chunk, int x, int y, int z) {
            Material type = chunk.blocks().getType(x, y, z);
            if (type == Material.BEDROCK) {
                return false;
            }
            BlockData data = data(chunk, x, y, z);
            return replaceable(chunk, x, y, z) || Objects.equals(data, b.frosted) || b.isChocolate(data);
        }
    }

    /**
     * Gummy rings floating on purple soda (mod {@code FloatingGummyRingFeature}, 30-70 tries per chunk from the
     * ceilings down), or sunk to its bottom half the time.
     */
    static final class FloatingGummyRing extends Decor {
        FloatingGummyRing(Blocks b) {
            super("candy_floating_gummy_ring", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 30, 70);
            for (int i = 0; i < count; i++) {
                int y = height(chunk, random, chunk.minY() + 160);
                if (!chunk.isBiome(chunk.minBlockX(), y, chunk.minBlockZ(), BIOME)) {
                    continue;
                }
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int ceiling = scanUpToCeiling(chunk, x, y, z);
                if (ceiling != Integer.MIN_VALUE) {
                    ring(chunk, x, ceiling, z, random);
                }
            }
        }

        private void ring(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            boolean aboveSoda = false;
            while (y > chunk.minY()) {
                if (soda(chunk, x, y, z)) {
                    aboveSoda = true;
                    break;
                }
                y--;
            }
            if (!aboveSoda) {
                return;
            }
            boolean sunk = random.nextBoolean();
            if (sunk) {
                while (y > chunk.minY() && soda(chunk, x, y, z)) {
                    y--;
                }
            }
            y++;
            if (replaceable(chunk, x, y, z)) {
                // Mod: random.nextInt(4), so the pink ring (case 4) never generates.
                set(chunk, x, y, z, (sunk ? b.sunkGummyRings : b.gummyRings)[random.nextInt(4)]);
            }
        }

        private static boolean soda(CaveChunk chunk, int x, int y, int z) {
            return chunk.blocks().getType(x, y, z) == Material.WATER && chunk.isBiome(x, y, z, BIOME);
        }
    }

    /**
     * Frostmint hanging from the ceilings over purple soda (mod {@code CeilingFrostmintFeature}, 2-8 tries per chunk
     * from the lowest 160 blocks, scanned up through air to a ceiling): only where purple soda lies somewhere straight
     * below. Three blobs of rock candy replace the cake layer and chocolate of the ceiling (cyan inside, white
     * outside, a quarter of the blocks left alone), and up to four columns of frostmint hang from it, 3 to 6 blocks
     * from one block above the ceiling block (so 1 to 4 blocks show), double slabs but for the last one, which is a
     * top slab half of the time.
     */
    static final class CeilingFrostmint implements ChunkFeature {
        private final Predicate<BlockData> softCeiling;
        private final BlockData cyan;
        private final BlockData white;
        private final BlockData top;
        private final BlockData doubleSlab;
        private final Predicate<Material> sturdy;

        CeilingFrostmint(Blocks b) {
            this(data -> b.isCake(data) || b.isChocolate(data), b.rockCandy.get("cyan"), b.rockCandy.get("white"),
                b.frostmintTop, b.frostmintDouble, type -> type.isSolid() && type.isOccluding());
        }

        CeilingFrostmint(Predicate<BlockData> softCeiling, BlockData cyan, BlockData white, BlockData top, BlockData doubleSlab,
                         Predicate<Material> sturdy) {
            this.softCeiling = softCeiling;
            this.cyan = cyan;
            this.white = white;
            this.top = top;
            this.doubleSlab = doubleSlab;
            this.sturdy = sturdy;
        }

        @Override
        public String id() {
            return "candy_ceiling_frostmint";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 2, 8);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                int ceiling = scanUp(chunk, x, y, z);
                if (ceiling != Integer.MIN_VALUE && sodaBelow(chunk, x, ceiling, z)) {
                    grow(chunk, x, ceiling, z, random);
                }
            }
        }

        /**
         * {@code environment_scan} up (32 steps) through air, from a start that is air, to a block with a sturdy bottom
         * face: that block's y, or MIN_VALUE.
         */
        private int scanUp(CaveChunk chunk, int x, int y, int z) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            if (!blocks.isInRegion(x, y, z) || !airLike(blocks.getType(x, y, z))) {
                return Integer.MIN_VALUE;
            }
            for (int step = 0; step < 32; step++) {
                y++;
                if (!blocks.isInRegion(x, y, z)) {
                    return Integer.MIN_VALUE;
                }
                Material type = blocks.getType(x, y, z);
                if (!airLike(type)) {
                    return sturdy.test(type) ? y : Integer.MIN_VALUE;
                }
            }
            return Integer.MIN_VALUE;
        }

        private static boolean airLike(Material type) {
            return FeatureShapes.isAir(type.name()) || type == Material.LIGHT;
        }

        /** Purple soda (the biome's water) anywhere straight below, down to the bottom of the world. */
        private boolean sodaBelow(CaveChunk chunk, int x, int top, int z) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int y = top; y > chunk.minY(); y--) {
                if (blocks.isInRegion(x, y, z) && blocks.getType(x, y, z) == Material.WATER && chunk.isBiome(x, y, z, BIOME)) {
                    return true;
                }
            }
            return false;
        }

        private void grow(CaveChunk chunk, int x, int ceiling, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int blob = 0; blob < 3; blob++) {
                int a = 1 + random.nextInt(2);
                int b = 1 + random.nextInt(2);
                int c = 1 + random.nextInt(2);
                double radiusSquared = FrostmintRules.blobRadiusSquared(a, b, c);
                for (int dx = -a; dx <= a; dx++) {
                    for (int dy = -b; dy <= b; dy++) {
                        for (int dz = -c; dz <= c; dz++) {
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (distance > radiusSquared || random.nextFloat() >= 0.75F
                                || !blocks.isInRegion(x + dx, ceiling + dy, z + dz)
                                || !softCeiling.test(blocks.getBlockData(x + dx, ceiling + dy, z + dz))) {
                                continue;
                            }
                            blocks.setBlock(x + dx, ceiling + dy, z + dz,
                                FrostmintRules.cyanRockCandy(distance, radiusSquared) ? cyan : white);
                        }
                    }
                }
            }
            int columns = random.nextInt(5);
            for (int column = 0; column < columns; column++) {
                int cx = x + random.nextInt(2) - 1;
                int cz = z + random.nextInt(2) - 1;
                int length = FrostmintRules.hangingLength(random.nextInt(4));
                hang(blocks, cx, ceiling, cz, length, random.nextBoolean());
            }
        }

        /**
         * A column of double slabs from one block above the ceiling block down {@code length} more blocks, its last
         * block a top slab when {@code topEnd}. Frostmint already there stays as it is; anything else is replaced.
         */
        void hang(CaveChunk.BlockSink blocks, int x, int ceiling, int z, int length, boolean topEnd) {
            for (int i = 0; i <= length; i++) {
                int y = ceiling + 1 - i;
                if (!blocks.isInRegion(x, y, z)) {
                    continue;
                }
                BlockData there = blocks.getBlockData(x, y, z);
                if (!top.equals(there) && !doubleSlab.equals(there)) {
                    blocks.setBlock(x, y, z, i == length && topEnd ? top : doubleSlab);
                }
            }
        }
    }

    /**
     * Peppermint patches (mod {@code PeppermintPileFeature}, 1 chunk in 2): small and large peppermints standing on
     * frosted chocolate around a spot.
     */
    static final class PeppermintPatch extends Decor {
        PeppermintPatch(Blocks b) {
            super("candy_peppermint_patch", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 2)) {
                return;
            }
            int y = height(chunk, random, chunk.minY() + 160);
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            int floor = scanDown(chunk, x, y, z);
            if (floor == Integer.MIN_VALUE || !chunk.isBiome(x, floor + 1, z, BIOME) || !solid(chunk, x, floor, z)) {
                return;
            }
            int below = floor + 1;
            // Mod: the bound is drawn again on every pass.
            for (int i = 0; i < 4 + random.nextInt(6); i++) {
                int ox = x + random.nextInt(6) - 3;
                int oy = below + 1;
                int oz = z + random.nextInt(6) - 3;
                while (air(chunk, ox, oy, oz) && oy > chunk.minY()) {
                    oy--;
                }
                if (sturdy(chunk, ox, oy, oz) && frosted(chunk, ox, oy, oz) && air(chunk, ox, oy + 1, oz)) {
                    float f = random.nextFloat();
                    if (f < 0.1F) {
                        continue; // Mod: a licoroot sprout (a plant the module cannot draw yet).
                    }
                    set(chunk, ox, oy + 1, oz, f < 0.6F ? b.smallPeppermint[1] : b.largePeppermint[1]);
                }
            }
        }
    }

    /**
     * Candy canes (mod {@code CandyCaneFeature}, {@code noise_based_count} 65/0/12): a candy cane pole 3-5 tall on
     * frosted chocolate with its hook bent to one side.
     */
    static final class CandyCane extends Decor {
        CandyCane(Blocks b) {
            super("candy_candy_cane", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.noiseCount(chunk, 65.0, 0.0, 12.0);
            for (int i = 0; i < count; i++) {
                int y = height(chunk, random, chunk.minY() + 160);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int floor = scanDown(chunk, x, y, z);
                if (floor != Integer.MIN_VALUE && chunk.isBiome(x, floor, z, BIOME) && frosted(chunk, x, floor, z)) {
                    cane(chunk, x, floor + 1, z, random);
                }
            }
        }

        private void cane(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            int direction = random.nextInt(4);
            int height = 3 + random.nextInt(3);
            int[] d = HORIZONTAL[direction];
            for (int i = 0; i <= height; i++) {
                if (!replaceable(chunk, x + d[0] * i, y + height, z + d[2] * i)) {
                    return;
                }
            }
            for (int i = 0; i < height; i++) {
                set(chunk, x, y + i, z, b.candyCanePole);
            }
            set(chunk, x, y + height, z, b.candyCaneHook[direction]);
            set(chunk, x + d[0], y + height, z + d[2], b.candyCaneHook[(direction + 2) % 4]);
            // The mod also scatters small candy canes around (a plant the module cannot draw yet).
        }
    }

    /**
     * Encrusted peppermints (mod {@code EncrustedPeppermintFeature}, 40-120 tries per chunk below y=64): a peppermint
     * stuck on a cake wall, floor or ceiling.
     */
    static final class EncrustedPeppermint extends Decor {
        EncrustedPeppermint(Blocks b) {
            super("candy_encrusted_peppermint", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            int count = ModPlacement.uniform(random, 40, 120);
            for (int i = 0; i < count; i++) {
                int y = height(chunk, random, 64);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!chunk.isBiome(x, y, z, BIOME) || !air(chunk, x, y, z)) {
                    continue;
                }
                List<Integer> options = new ArrayList<>();
                for (int d = 0; d < 6; d++) {
                    int nx = x + ALL_DIRECTIONS[d][0];
                    int nz = z + ALL_DIRECTIONS[d][2];
                    if (sameChunk(x, z, nx, nz) && b.isCake(data(chunk, nx, y + ALL_DIRECTIONS[d][1], nz))) {
                        options.add(d ^ 1);
                    }
                }
                int direction = selectDirection(options, random);
                if (direction >= 0) {
                    set(chunk, x, y, z, random.nextFloat() > 0.7F ? b.largePeppermint[direction] : b.smallPeppermint[direction]);
                }
            }
        }
    }

    /**
     * Spilled ice cream cones (mod {@code SpilledIceCreamConeFeature}, 2-3 tries in 1 chunk of 10): a scoop melted on
     * frosted chocolate with its wafer cone upright, lying or tipped over.
     */
    static final class SpilledIceCreamCone extends Decor {
        SpilledIceCreamCone(Blocks b) {
            super("candy_spilled_ice_cream_cone", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 10)) {
                return;
            }
            int y = height(chunk, random, chunk.minY() + 160);
            int count = ModPlacement.uniform(random, 2, 3);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int floor = scanDown(chunk, x, y, z);
                if (floor != Integer.MIN_VALUE && chunk.isBiome(x, floor + 1, z, BIOME)) {
                    cone(chunk, x, floor + 1, z, random);
                }
            }
        }

        private void cone(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            if (!frosted(chunk, x, y - 1, z)) {
                return;
            }
            int shape = 0;
            int type = random.nextInt(3);
            int direction = random.nextInt(4);
            int flavour = type == 2 ? 2 : type == 1 ? 1 : 0;
            if (random.nextFloat() < 0.33F && clear(chunk, x, y, z, direction, 2, false)) {
                shape = 1;
            } else if (random.nextFloat() < 0.66F && clear(chunk, x, y, z, direction, 3, true)) {
                shape = 2;
            }
            int[] d = HORIZONTAL[direction];
            int back = (direction + 2) % 4;
            set(chunk, x, y, z, b.iceCream[flavour][1]);
            switch (shape) {
                case 0 -> {
                    set(chunk, x, y + 1, z, b.wafer);
                    set(chunk, x, y + 2, z, b.waferWall);
                }
                case 1 -> {
                    set(chunk, x + d[0], y, z + d[2], b.wafer);
                    set(chunk, x + d[0] * 2, y, z + d[2] * 2, b.stairs(back, false, false));
                }
                default -> {
                    set(chunk, x + d[0], y, z + d[2], b.stairs(back, true, false));
                    set(chunk, x + d[0], y + 1, z + d[2], b.stairs(direction, false, false));
                    set(chunk, x + d[0] * 2, y + 1, z + d[2] * 2, b.stairs(back, true, false));
                }
            }
        }

        private static boolean clear(CaveChunk chunk, int x, int y, int z, int direction, int amount, boolean stepUp) {
            int[] d = HORIZONTAL[direction];
            for (int i = 1; i <= amount; i++) {
                if (!replaceable(chunk, x + d[0] * i, y + (stepUp ? i / 2 : 0), z + d[2] * i)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Gobstopper geodes (mod {@code GobstopperGeodeFeature}, 1 chunk in 12 at least 13 under the surface): a ball of
     * rock candy rings from red to orange inside a white shell with coloured sprinkles, one quarter bitten off.
     */
    static final class GobstopperGeode extends Decor {
        private static final String[] GRADIENT = {"red", "purple", "cyan", "lime", "yellow", "orange"};

        GobstopperGeode(Blocks b) {
            super("candy_gobstopper_geode", b);
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 12)) {
                return;
            }
            int x = middleX(chunk);
            int z = middleZ(chunk);
            int y = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 128);
            // Mod order: the surface filter runs before the offset, on the chunk corner.
            if (!ModPlacement.belowSurface(chunk, chunk.minBlockX(), y, chunk.minBlockZ(), -13) || !chunk.isBiome(x, y, z, BIOME) || air(chunk, x, y, z)) {
                return;
            }
            boolean eatenX = random.nextBoolean();
            boolean eatenY = y < -10;
            boolean eatenZ = random.nextBoolean();
            int width = 7;
            for (int dx = -width; dx <= width; dx++) {
                for (int dy = -width; dy <= width; dy++) {
                    for (int dz = -width; dz <= width; dz++) {
                        int px = x + dx;
                        int py = y + dy;
                        int pz = z + dz;
                        float sprinkle = Math.abs(SimplexNoise3D.sample(px + 231, py + 221, pz + 3211, 3.0F));
                        int shell = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                        int taxicab = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        boolean kept = (eatenX ? dx <= 0 : dx >= 0) || (eatenY ? dy <= 0 : dy >= 0) || (eatenZ ? dz <= 0 : dz >= 0);
                        if (!kept || chunk.blocks().getType(px, py, pz) == Material.BEDROCK) {
                            continue;
                        }
                        if (shell < width) {
                            int gradient = (int) ((float) shell / width * GRADIENT.length);
                            set(chunk, px, py, pz, b.rockCandy.get(GRADIENT[Math.max(0, Math.min(GRADIENT.length - 1, gradient))]));
                        } else if (random.nextFloat() < 0.95F && sprinkle < 0.9 && taxicab < width * 3 - 1
                            && (Math.abs(dx) != Math.abs(dy) || Math.abs(dx) != width)
                            && (Math.abs(dx) != Math.abs(dz) || Math.abs(dx) != width)
                            && (Math.abs(dy) != Math.abs(dz) || Math.abs(dy) != width)) {
                            String colour = "white";
                            if (random.nextFloat() < 0.035F) {
                                float c = random.nextFloat();
                                colour = c < 0.33F ? "light_blue" : c < 0.66F ? "pink" : "yellow";
                            }
                            set(chunk, px, py, pz, b.rockCandy.get(colour));
                        }
                    }
                }
            }
        }
    }
}
