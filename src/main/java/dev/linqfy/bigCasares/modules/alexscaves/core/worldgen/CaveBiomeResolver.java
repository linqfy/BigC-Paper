package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which cave biome, if any, a block belongs to. The climate checks of the
 * mod are evaluated at the cell centre (so a whole biome agrees), the spawn
 * distance and depth band at the block itself.
 */
public final class CaveBiomeResolver {
    /** Blocks per unit of the vanilla climate "depth" parameter. */
    public static final double BLOCKS_PER_DEPTH_UNIT = 128.0;

    private final VoronoiRarity rarity;
    private final Set<CaveBiomeId> enabled;
    private final Map<CaveBiomeId, Integer> minSpawnDistance;
    private final SurfaceProbe surface;

    public CaveBiomeResolver(
        VoronoiRarity rarity,
        Set<CaveBiomeId> enabled,
        Map<CaveBiomeId, Integer> minSpawnDistance,
        SurfaceProbe surface
    ) {
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.enabled = Set.copyOf(enabled);
        EnumMap<CaveBiomeId, Integer> distances = new EnumMap<>(CaveBiomeId.class);
        for (CaveBiomeId biome : CaveBiomeId.values()) {
            distances.put(biome, minSpawnDistance.getOrDefault(biome, biome.defaultMinSpawnDistance()));
        }
        this.minSpawnDistance = Map.copyOf(distances);
        this.surface = Objects.requireNonNull(surface, "surface");
    }

    /** Biome of the column, ignoring depth: used to decide whether a chunk needs any work. */
    public Optional<CaveBiomeId> columnBiome(int blockX, int blockZ) {
        Optional<VoronoiRarity.CellSample> cell = rarity.sample(blockX, blockZ);
        if (cell.isEmpty()) {
            return Optional.empty();
        }
        Optional<CaveBiomeId> biome = CaveBiomeId.byRarityId(cell.get().rarityId());
        if (biome.isEmpty() || !enabled.contains(biome.get())) {
            return Optional.empty();
        }
        int distance = minSpawnDistance.get(biome.get());
        if ((long) blockX * blockX + (long) blockZ * blockZ < (long) distance * distance) {
            return Optional.empty();
        }
        SurfaceKind centre = surface.surfaceAt((int) Math.floor(cell.get().centerX()), (int) Math.floor(cell.get().centerZ()));
        if (!accepts(biome.get().surface(), centre)) {
            return Optional.empty();
        }
        return biome;
    }

    /** Full check for one block, given the surface height of its column. */
    public Optional<CaveBiomeId> biomeAt(int blockX, int blockY, int blockZ, int surfaceY) {
        Optional<CaveBiomeId> biome = columnBiome(blockX, blockZ);
        if (biome.isEmpty() || !inDepthBand(biome.get(), blockY, surfaceY)) {
            return Optional.empty();
        }
        return biome;
    }

    /**
     * Nearest column of a biome, searched in square rings of {@code step} blocks around a start, up to
     * {@code maxRadius}. Returns the sample closest to the start, as {@code {x, z}}.
     */
    public Optional<int[]> nearest(CaveBiomeId biome, int startX, int startZ, int maxRadius, int step) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        int lastRing = maxRadius / step;
        for (int ring = 0; ring <= lastRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    int x = startX + dx * step;
                    int z = startZ + dz * step;
                    long distance = (long) dx * dx + (long) dz * dz;
                    if (distance < bestDistance && columnBiome(x, z).filter(found -> found == biome).isPresent()) {
                        best = new int[] {x, z};
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) {
                // Square rings: every point of ring r is at least r steps away, so a closer hit (by real
                // distance) can only sit in the rings up to the best distance found so far.
                lastRing = Math.min(lastRing, (int) Math.floor(Math.sqrt(bestDistance)));
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The centre of the biome cell a column belongs to, as {@code {x, z}}, when that centre is itself in the biome
     * (the cave is biggest there); otherwise the column.
     */
    public int[] towardsCentre(CaveBiomeId biome, int x, int z) {
        Optional<VoronoiRarity.CellSample> cell = rarity.sample(x, z);
        if (cell.isPresent()) {
            int cx = (int) Math.floor(cell.get().centerX());
            int cz = (int) Math.floor(cell.get().centerZ());
            if (columnBiome(cx, cz).filter(found -> found == biome).isPresent()) {
                return new int[] {cx, cz};
            }
        }
        return new int[] {x, z};
    }

    public static boolean inDepthBand(CaveBiomeId biome, int blockY, int surfaceY) {
        double depth = (surfaceY - blockY) / BLOCKS_PER_DEPTH_UNIT;
        return depth >= biome.minDepth() && depth <= biome.maxDepth();
    }

    static boolean accepts(CaveBiomeId.SurfaceRequirement requirement, SurfaceKind kind) {
        return switch (requirement) {
            case LAND -> kind == SurfaceKind.LAND;
            case COLD_OCEAN -> kind == SurfaceKind.OCEAN;
        };
    }

    public enum SurfaceKind {
        /** Inland surface biomes (not ocean, river or beach). */
        LAND,
        /** Ocean that is not warm or lukewarm. */
        OCEAN,
        /** Anything that satisfies no cave biome (warm oceans, rivers, beaches...). */
        OTHER
    }

    /** Classifies the vanilla surface biome of a column. */
    @FunctionalInterface
    public interface SurfaceProbe {
        SurfaceKind surfaceAt(int blockX, int blockZ);
    }
}
