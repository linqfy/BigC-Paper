package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/**
 * A mod-style cave: the shape of one of the mod's cave structures ({@code dino_bowl}, {@code ferrocave},
 * {@code acid_pit}) with the formulas of its structure piece. The mod sizes each cave by measuring how
 * far the biome continues around a start point ({@code AbstractCaveGenerationStructure}); {@link #plan}
 * repeats that measure and {@link #origins} the {@code random_spread} placement of its structure set.
 * Pure logic.
 */
public record CaveBowl(Kind kind, int centerX, int centerY, int centerZ, int height, int radius) {
    /** Mod {@code getYExpandUp/Down}. */
    private static final int Y_EXPAND = -5;
    private static final int MAX_Y_CHUNKS = 20;
    /** Mod {@code AcidPitStructurePiece}: below this the pit is an acid lake. */
    public static final int ACID_LEVEL = -10;

    /** What a block of the cave becomes. */
    public enum Carve {
        /** Outside the cave: left as it is. */
        NONE,
        /** Cave air. */
        AIR,
        /** Acid pit pillar: rock stays, liquids become radrock. */
        PILLAR,
        /** Acid pit lake, below {@link #ACID_LEVEL}. */
        ACID
    }

    /** The mod's cave structures and their structure-set placement. */
    public enum Kind {
        DINO_BOWL(4, 2, 1, 100),
        FERROCAVE(6, 3, 3, 45 + 24),
        ACID_PIT(6, 3, 2, 90),
        CAKE_CAVE(6, 5, 3, 100),
        FORLORN_CANYON(6, 4, 6, 100);

        private final int spacing;
        private final int separation;
        private final int salt;
        private final int maxWidthRadius;

        Kind(int spacing, int separation, int salt, int maxWidthRadius) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
            this.maxWidthRadius = maxWidthRadius;
        }

        public int spacing() {
            return spacing;
        }

        public int separation() {
            return separation;
        }

        public int salt() {
            return salt;
        }

        /** Chunks around a start that the widest cave of this kind can reach. */
        public int reachChunks() {
            return widthChunksFor(maxWidthRadius);
        }

        /** Mod {@code getGenerateYHeight}, {@code getHeightRadius}, {@code getWidthRadius}, in the mod's call order. */
        int[] sizes(Random random) {
            return switch (this) {
                case DINO_BOWL -> new int[] {-1, 80, 100};
                case FERROCAVE -> {
                    int y = random.nextInt(40) - 20;
                    int height = 80 + random.nextInt(25);
                    yield new int[] {y, height, 45 + random.nextInt(25)};
                }
                case ACID_PIT -> new int[] {random.nextInt(30) - 25, 50, 90};
                case CAKE_CAVE -> new int[] {-48, 105, 100};
                case FORLORN_CANYON -> new int[] {-10, 90, 100};
            };
        }
    }

    /** Carve test of the kind's structure piece. */
    public Carve carve(int x, int y, int z) {
        return switch (kind) {
            case DINO_BOWL -> dinoBowl(x, y, z) ? Carve.AIR : Carve.NONE;
            case FERROCAVE -> ferrocave(x, y, z) ? Carve.AIR : Carve.NONE;
            case ACID_PIT -> acidPit(x, y, z);
            case CAKE_CAVE -> cakeCave(x, y, z) ? Carve.AIR : Carve.NONE;
            case FORLORN_CANYON -> forlornCanyon(x, y, z) ? Carve.AIR : Carve.NONE;
        };
    }

    /**
     * Mod {@code ForlornCanyonStructurePiece.inCircle}: a canyon whose walls step out with height ({@code canyonStep}
     * of {@code getHeightOf}), cut into pillars by a 3D noise and with a ragged top.
     */
    private boolean forlornCanyon(int x, int y, int z) {
        float rawHeight = Math.abs(centerY - y) / (height * 0.5F);
        if (!(rawHeight < 1.0F)) {
            // The mod's yDist is NaN there: never carved.
            return false;
        }
        int halfHeight = height / 2;
        float heightOf = y <= centerY + halfHeight + 1 && y >= centerY - halfHeight
            ? 1.0F - (float) (centerY + halfHeight - y) / (height * 2) : 0.0F;
        float steps = (float) Math.pow(canyonStep(heightOf, 10), 2.5);
        if (steps <= 0.0F || !(rawHeight < 1.0F - forlornVerticalNoise(x, z))) {
            return false;
        }
        float pillarNoise = (noise(x, (int) (y * 0.4F), z, 30.0F) + 1.0F) * 0.5F;
        double yDist = smin((float) Math.pow(1.0F - rawHeight, 0.3F), 1.0F, 0.1F);
        double target = yDist * (radius * pillarNoise * steps) * radius;
        return distance(x, z) < target;
    }

    /** The forlorn canyon's {@code verticalNoise}, which depends only on the column (cached per thread). */
    static float forlornVerticalNoise(int x, int z) {
        CakeColumn column = FORLORN_COLUMN.get();
        if (!column.valid || column.x != x || column.z != z) {
            column.x = x;
            column.z = z;
            column.valid = true;
            column.ceilingNoise = (SimplexNoise3D.sample2D(x, z, 50.0F) + 1.0F) * 0.2F
                - (smin(SimplexNoise3D.sample2D(x, z, 20.0F), -0.5F, 0.1F) + 0.5F) * 0.7F;
        }
        return (float) column.ceilingNoise;
    }

    private static final ThreadLocal<CakeColumn> FORLORN_COLUMN = ThreadLocal.withInitial(CakeColumn::new);

    /** Mod {@code CakeCaveStructurePiece.inCircle}: a dome over stepped plateaus, its ceiling rippled by noise. */
    private boolean cakeCave(int x, int y, int z) {
        // The noises depend only on the column: computed once per column and thread.
        CakeColumn column = CAKE_COLUMN.get();
        if (!column.valid || column.x != x || column.z != z) {
            column.x = x;
            column.z = z;
            column.valid = true;
            column.plateau = plateauHeight(x, z, 7, true);
            column.ceilingNoise = 1.0F + (1.0F + SimplexNoise3D.sample2D(x + 9000, z - 9000, 120.0F)) * 10.0F
                + (1.0F + SimplexNoise3D.sample2D(x + 3000, z + 2000, 40.0F)) * 4.0F;
            column.wallNoise = 0.9F + SimplexNoise3D.sample2D(x + 9000, z - 9000, 120.0F) * 0.1F;
        }
        if (y < (int) column.plateau) {
            return false;
        }
        double ceilingNoise = column.ceilingNoise;
        double wallNoise = column.wallNoise;
        double ceilingHeight = height * 0.85F - ceilingNoise;
        float yDome = (float) Math.pow((float) Math.abs(centerY - y) / height, 4.0);
        double yDist = smin(1.0F - yDome, 1.0F, 0.2F);
        return distance(x, z) < yDist * (radius * wallNoise) * radius && y < centerY + ceilingHeight;
    }

    private static final ThreadLocal<CakeColumn> CAKE_COLUMN = ThreadLocal.withInitial(CakeColumn::new);

    /** Last column of {@link #cakeCave} on this thread (its values do not depend on the cave). */
    private static final class CakeColumn {
        boolean valid;
        int x;
        int z;
        double plateau;
        double ceilingNoise;
        double wallNoise;
    }

    /** Mod {@code CakeCaveStructurePiece.calculatePlateauHeight}: the floor steps of the cake cave. */
    public static double plateauHeight(int x, int z, int curvedTop, boolean terrainNoise) {
        float plateauNoise = (0.5F + SimplexNoise3D.sample2D(x, z, 100.0F)) * 0.5F;
        double slight = terrainNoise
            ? SimplexNoise3D.sample2D(x + 9000, z - 9000, 150.0F) + SimplexNoise3D.sample2D(x + 18000, z + 9000, 60.0F) : 0.0;
        float root = (float) Math.sqrt(plateauNoise);
        return -48.0F + canyonStep(root, 5) * 48 + canyonStep(root, 10) * curvedTop + slight;
    }

    /** Mod {@code ACMath.canyonStep}. */
    static float canyonStep(float heightScale, int scaleTo) {
        int clampTo100 = (int) (heightScale * scaleTo * scaleTo);
        return Math.max(0.0F, Math.min(1.0F, (float) Math.round((float) clampTo100 / scaleTo) / scaleTo));
    }

    /**
     * Whether any block of the column can be carved. Only the dino bowl and the forlorn canyon have a tight
     * bound: their wall noise is never negative, so the target never exceeds {@code radius}. In the ferrocave and the acid pit a
     * negative noise factor times a negative {@code yDist} gives a positive target, so the mod carves
     * stray pockets anywhere in the pieces; those columns are never skipped.
     */
    public boolean columnMayCarve(int x, int z) {
        return !tight() || distance(x, z) < (double) radius * radius;
    }

    private boolean tight() {
        return kind == Kind.DINO_BOWL || kind == Kind.FORLORN_CANYON;
    }

    /** Lowest y that can be carved (dino bowl: below it {@code yDist <= 0}; otherwise the pieces' range). */
    public int carveMinY() {
        return tight() ? centerY - (int) Math.ceil(height * 0.5) : Integer.MIN_VALUE;
    }

    /** Highest y that can be carved. */
    public int carveMaxY() {
        return tight() ? centerY + (int) Math.ceil(height * 0.5) : Integer.MAX_VALUE;
    }

    /** Mod {@code DinoBowlStructurePiece.inCircle}. */
    private boolean dinoBowl(int x, int y, int z) {
        float wallNoise = (noise(x, (int) (y * 0.1F), z, 40.0F) + 1.0F) * 0.5F;
        double yDist = smin(1.0F - Math.abs(centerY - y) / (height * 0.5F), 1.0F, 0.3F);
        double target = yDist * (radius * wallNoise) * radius;
        return distance(x, z) < target;
    }

    /** Mod {@code FerrocaveStructurePiece.inCircle}: a lumpy ball with hollow orbs left solid inside. */
    private boolean ferrocave(int x, int y, int z) {
        float df1 = (noise(x, y, z, 30.0F) + 1.0F) * 0.5F;
        float df2 = noise(x - 1200, y + 100, z + 120, 10.0F) * 0.15F;
        float innerOrb = noise(x + 400, y + 40, z - 600, 20.0F);
        double df1Smooth = smin(df1 + df2, 1.0F, 0.1F);
        double yDist = smin(1.0F - Math.abs(centerY - y) / (height * 0.5F), 1.0F, 0.3F);
        double target = yDist * (radius * df1Smooth) * radius;
        return distance(x, z) < target && (innerOrb > -0.5F || innerOrb < -0.75F);
    }

    /** Mod {@code AcidPitStructurePiece}: a wide pit with radrock pillars and an acid lake at the bottom. */
    private Carve acidPit(int x, int y, int z) {
        float widthNoise = Math.min(noise(x, y, z, radius) - 0.5F, 1.0F) * 0.94F;
        float heightNoise = noise(x + 440, 0, z - 440, 20.0F) * 0.5F + 0.5F;
        double yDist = smin(1.0F - Math.abs(centerY - y) / (height * heightNoise), 0.7F, 0.3F);
        double target = yDist * (radius + widthNoise * radius) * radius;
        if (distance(x, z) > target) {
            return Carve.NONE;
        }
        if (pillar(x, y, z, yDist)) {
            return Carve.PILLAR;
        }
        return y < ACID_LEVEL ? Carve.ACID : Carve.AIR;
    }

    /** Mod {@code AcidPitStructurePiece.isPillarBlocking}. */
    private static boolean pillar(int x, int y, int z, double yDist) {
        float sample = noise(x, 0, z, 40.0F) + noise(x - 440, 0, z + 412, 15.0F) * 0.2F
            + noise(x - 100, y, z - 400, 100.0F) * 0.9F + 0.6F;
        float f = smin((float) yDist / 0.67F, 1.0F, 0.2F) + 1.0F;
        return sample >= 0.35F * f && sample <= smin(1.0F, (float) yDist / 0.67F + 0.35F, 0.2F) * f;
    }

    /** Mod {@code BlockPos.distToCenterSqr} from the block to the column of the start. */
    private double distance(int x, int z) {
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        return dx * dx + 0.25 + dz * dz;
    }

    private static float noise(int x, int y, int z, float rate) {
        return SimplexNoise3D.sample(x, y, z, rate);
    }

    /** Mod {@code ACMath.smin}. */
    /** Mod {@code ACMath.smin}: a smooth minimum. */
    public static float smin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0.0F) / k;
        return Math.min(a, b) - h * h * k * 0.25F;
    }

    /** Chunks around the start in which the mod places pieces. */
    public int widthChunks() {
        return widthChunksFor(radius);
    }

    private static int widthChunksFor(int radius) {
        return (int) Math.ceil((radius + 16) / 16.0F / 2.0F) + 2;
    }

    /** Whether the chunk at an offset from the start chunk can hold part of the cave. */
    public boolean reaches(int chunkDx, int chunkDz) {
        int reach = widthChunks();
        return Math.abs(chunkDx) <= reach && Math.abs(chunkDz) <= reach;
    }

    /** Lowest block y that pieces cover (mod: vertical chunk pieces around the centre). */
    public int minY(int worldMinY) {
        return Math.max(centerY - heightChunks() * 16, (int) Math.ceil(worldMinY / 16.0F) * 16);
    }

    /** Highest block y that pieces cover. */
    public int maxY() {
        return centerY + Math.min(heightChunks(), MAX_Y_CHUNKS) * 16 + 15;
    }

    /** Mod: vertical chunks of pieces above and below the centre. */
    public int heightChunks() {
        return (int) Math.ceil((height + 16) / 16.0F / 2.0F);
    }

    /** Random of one start, as the mod's per-start {@code WorldgenRandom}. */
    public static Random startRandom(long worldSeed, int chunkX, int chunkZ) {
        return new Random(SeededHash.hash(worldSeed, chunkX, chunkZ));
    }

    /**
     * Mod {@code generatePieces}: measures the biome up, down and to the four sides of a start.
     *
     * @param inBand   whether the biome's depth band holds at a block y of the start column
     * @param columnIn whether the biome holds at a column (the depth band is checked by {@code inBand})
     */
    public static Optional<CaveBowl> plan(Kind kind, Random random, int startX, int startZ, IntPredicate inBand,
                                          BiPredicate<Integer, Integer> columnIn) {
        // Vanilla isValidBiome on the stub of AbstractCaveGenerationStructure.atYCaveBiomePoint: the biome at the start
        // chunk's middle (getMiddleBlockX/Z) at the structure's y (getGenerateYHeight).
        int[] sizes = kind.sizes(random);
        int centerY = sizes[0];
        if (!columnIn.test(startX + 8, startZ + 8) || !inBand.test(centerY)) {
            return Optional.empty();
        }
        int heightRadius = sizes[1];
        int widthRadius = sizes[2];
        int up = Y_EXPAND + continues(heightRadius, step -> inBand.test(centerY + step));
        int down = Y_EXPAND + continues(heightRadius, step -> inBand.test(centerY - step));
        int sides = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
        for (int[] dir : directions) {
            sides += continues(widthRadius, step -> columnIn.test(startX + dir[0] * step, startZ + dir[1] * step));
        }
        int width = sides / 4;
        int height = (up + down) / 2;
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        return Optional.of(new CaveBowl(kind, startX, centerY, startZ, height, width));
    }

    /** Mod {@code biomeContinuesInDirectionFor}: steps of 16 until the biome stops, at most {@code cutoff}. */
    private static int continues(int cutoff, IntPredicate holds) {
        int step;
        for (step = 0; step < cutoff; step += 16) {
            if (!holds.test(step)) {
                break;
            }
        }
        return Math.min(step, cutoff);
    }

    /**
     * Start chunks of the {@code random_spread} placement within {@code reach} chunks of a chunk:
     * one start per {@code spacing} square, offset by up to {@code spacing - separation - 1} chunks.
     * Returns {@code {chunkX, chunkZ}} pairs.
     */
    public static List<int[]> origins(long worldSeed, int chunkX, int chunkZ, int reach, int spacing,
                                      int separation, int salt) {
        return origins(worldSeed, chunkX, chunkZ, reach, spacing, separation, (long) salt);
    }

    /** Vanilla {@code random_spread} (linear) start chunks around a chunk, with a salt of any size. */
    public static List<int[]> origins(long worldSeed, int chunkX, int chunkZ, int reach, int spacing,
                                      int separation, long salt) {
        List<int[]> result = new ArrayList<>();
        int fromX = Math.floorDiv(chunkX - reach, spacing);
        int toX = Math.floorDiv(chunkX + reach, spacing);
        int fromZ = Math.floorDiv(chunkZ - reach, spacing);
        int toZ = Math.floorDiv(chunkZ + reach, spacing);
        for (int rx = fromX; rx <= toX; rx++) {
            for (int rz = fromZ; rz <= toZ; rz++) {
                Random random = new Random(rx * 341873128712L + rz * 132897987541L + worldSeed + salt);
                int ox = rx * spacing + random.nextInt(spacing - separation);
                int oz = rz * spacing + random.nextInt(spacing - separation);
                result.add(new int[] {ox, oz});
            }
        }
        return result;
    }
}
