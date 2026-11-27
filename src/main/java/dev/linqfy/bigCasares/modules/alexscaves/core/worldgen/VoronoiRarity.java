package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.Optional;

/**
 * Exact port of the mod's {@code ACBiomeRarity} as its {@code MultiNoiseBiomeSource} mixin uses it: per quart
 * (4x4 column), the quart position divided by the biome spacing ({@code (meanWidth + meanSeparation) / 4}) is
 * pushed by two vanilla simplex noises ({@code widthRandomness}) and looked up in the mod's
 * {@code VoronoiGenerator} (seeded with the world seed, {@code spacingRandomness} jitter). The column belongs to a
 * rare biome when the distance to the nearest cell point is below {@code meanWidth / (meanWidth + meanSeparation)};
 * the cell hash picks which biome.
 */
public final class VoronoiRarity {
    /** Mod: {@code new PerlinSimplexNoise(new XoroshiroRandomSource(1234L / 4321L), List.of(0))}. */
    private static final VanillaSimplexNoise NOISE_X = new VanillaSimplexNoise(1234L);
    private static final VanillaSimplexNoise NOISE_Z = new VanillaSimplexNoise(4321L);

    private final long seed;
    private final RarityParameters parameters;

    public VoronoiRarity(long seed, RarityParameters parameters) {
        this.seed = seed;
        this.parameters = parameters;
    }

    public RarityParameters parameters() {
        return parameters;
    }

    public Optional<CellSample> sample(double blockX, double blockZ) {
        int quartX = Math.floorDiv((int) Math.floor(blockX), 4);
        int quartZ = Math.floorDiv((int) Math.floor(blockZ), 4);
        double biomeSize = parameters.radius() * 0.25;
        double separation = biomeSize + parameters.separation() * 0.25;
        double sampleX = quartX / separation;
        double sampleZ = quartZ / separation;
        double offsetX = parameters.widthRandomness() * NOISE_X.getValue(sampleX, sampleZ);
        double offsetZ = parameters.widthRandomness() * NOISE_Z.getValue(sampleX, sampleZ);
        ModVoronoi.Info info = ModVoronoi.get2(seed, parameters.spacingRandomness(), sampleX + offsetX, sampleZ + offsetZ);
        if (!(info.distance() < biomeSize / separation)) {
            return Optional.empty();
        }
        // Mod getRareBiomeCenter (quarts) in blocks.
        double centerX = info.cellX() * separation * 4.0;
        double centerZ = info.cellZ() * separation * 4.0;
        double dx = centerX - blockX;
        double dz = centerZ - blockZ;
        return Optional.of(new CellSample(Math.round(info.cellX()), Math.round(info.cellZ()), centerX, centerZ,
            Math.sqrt(dx * dx + dz * dz), rarityId(info.hash())));
    }

    /** Mod {@code getRareBiomeOffsetId}: the cell hash (-1..1) spread over the biomes. */
    static int rarityId(double hash) {
        return Math.min(CaveBiomeId.count() - 1, (int) ((hash + 1.0) * 0.5 * CaveBiomeId.count()));
    }

    /**
     * @param radius            average biome radius in blocks (mod default 300)
     * @param separation        average gap between biomes in blocks (mod default 900)
     * @param widthRandomness   0 = round biomes, 1 = very irregular borders (default 0.15)
     * @param spacingRandomness 0 = regular grid, 1 = fully random spacing (default 0.45)
     */
    public record RarityParameters(double radius, double separation, double widthRandomness, double spacingRandomness) {
        public static final RarityParameters MOD_DEFAULTS = new RarityParameters(300.0, 900.0, 0.15, 0.45);

        public RarityParameters {
            if (!(radius > 0) || !(separation >= 0) || !Double.isFinite(radius) || !Double.isFinite(separation)) {
                throw new IllegalArgumentException("radius must be positive and separation non-negative");
            }
            if (!(widthRandomness >= 0 && widthRandomness <= 1) || !(spacingRandomness >= 0 && spacingRandomness <= 1)) {
                throw new IllegalArgumentException("randomness values must be between 0 and 1");
            }
        }

        public double cellSize() {
            return radius + separation;
        }
    }

    /**
     * @param distance distance in blocks from the column to the cell centre (the biome test itself is done on the
     *                 mod's noise-bent distance, so this can be a little larger than the radius near the border)
     */
    public record CellSample(long cellX, long cellZ, double centerX, double centerZ, double distance, int rarityId) {
    }
}
