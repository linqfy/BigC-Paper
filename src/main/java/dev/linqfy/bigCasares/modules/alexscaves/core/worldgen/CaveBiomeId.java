package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.Locale;
import java.util.Optional;

/**
 * The six cave biomes, with the rarity id and generation conditions of
 * Alex's Caves 2.0.2 ({@code BiomeGenerationConfig}). Depth is the vanilla
 * climate "depth" parameter, approximated here as blocks below the surface / 128.
 */
public enum CaveBiomeId {
    MAGNETIC_CAVES(0, 400, SurfaceRequirement.LAND, 0.2, 1.0),
    PRIMORDIAL_CAVES(1, 450, SurfaceRequirement.LAND, 0.15, 1.5),
    TOXIC_CAVES(2, 650, SurfaceRequirement.LAND, 0.3, 1.5),
    ABYSSAL_CHASM(3, 400, SurfaceRequirement.COLD_OCEAN, 0.2, 1.5),
    FORLORN_HOLLOWS(4, 650, SurfaceRequirement.LAND, 0.3, 1.5),
    CANDY_CAVITY(5, 500, SurfaceRequirement.LAND, 0.15, 1.5);

    public static final String NAMESPACE = "alexscaves";

    private final int rarityId;
    private final int defaultMinSpawnDistance;
    private final SurfaceRequirement surface;
    private final double minDepth;
    private final double maxDepth;

    CaveBiomeId(int rarityId, int defaultMinSpawnDistance, SurfaceRequirement surface, double minDepth, double maxDepth) {
        this.rarityId = rarityId;
        this.defaultMinSpawnDistance = defaultMinSpawnDistance;
        this.surface = surface;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    public int rarityId() {
        return rarityId;
    }

    public int defaultMinSpawnDistance() {
        return defaultMinSpawnDistance;
    }

    public SurfaceRequirement surface() {
        return surface;
    }

    public double minDepth() {
        return minDepth;
    }

    public double maxDepth() {
        return maxDepth;
    }

    /** Path of the biome key, e.g. {@code magnetic_caves}. */
    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Configuration key, e.g. {@code magnetic-caves}. */
    public String configKey() {
        return path().replace('_', '-');
    }

    public String key() {
        return NAMESPACE + ":" + path();
    }

    public static int count() {
        return values().length;
    }

    public static Optional<CaveBiomeId> byRarityId(int rarityId) {
        for (CaveBiomeId biome : values()) {
            if (biome.rarityId == rarityId) {
                return Optional.of(biome);
            }
        }
        return Optional.empty();
    }

    public static Optional<CaveBiomeId> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith(NAMESPACE + ":")) {
            normalized = normalized.substring(NAMESPACE.length() + 1);
        }
        for (CaveBiomeId biome : values()) {
            if (biome.path().equals(normalized)) {
                return Optional.of(biome);
            }
        }
        return Optional.empty();
    }

    /**
     * Paper does not expose the climate sampler, so the mod's continentalness
     * and temperature ranges are approximated with the vanilla surface biome at
     * the cell centre.
     */
    public enum SurfaceRequirement {
        /** Inland continentalness (0.4..1.0 in the mod). */
        LAND,
        /** Deep ocean continentalness (-0.95..-0.65) with temperature -1.0..0.5. */
        COLD_OCEAN
    }
}
