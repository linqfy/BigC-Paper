package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.Arrays;

/**
 * Where the Sift's main portal goes and how it sits there: port of the site selection of the
 * mod's {@code SiftPortalStructure} ({@code findBestCandidate}, {@code searchArea},
 * {@code sampleFootprintWithoutLoadingChunks}, {@code score}) and its geometry constants.
 *
 * <p>The mod asks the chunk generator for the height of the untouched noise terrain. Paper has no
 * such call without generating the chunk, so the "generated surface" is derived from generated
 * chunks instead: {@link #generatedSurface} scans a column down from the top past everything that is
 * not Sift terrain (plants, trees, water, structure blocks). Terrain-coloured features (monoliths,
 * arches, dry spikes) therefore count as ground; the roughness score keeps the search away from them.</p>
 */
public final class SiftMainPortalSite {

    /** Template path of the portal ({@code the_sift:main_portal}). */
    public static final String TEMPLATE = "main_portal";
    public static final int SIZE_X = 9;
    public static final int SIZE_Y = 19;
    public static final int SIZE_Z = 40;
    /** The template's jigsaw marker, which becomes reinforced siftslate. */
    public static final int MARKER_X = 3;
    public static final int MARKER_Y = 4;
    public static final int MARKER_Z = 19;
    /** Where players arrive, relative to the origin: the lowest portal block at the middle of the portal. */
    public static final int PLAYER_X = 3;
    public static final int PLAYER_Y = 5;
    public static final int PLAYER_Z = 19;
    /** Lowest block of the portal's frame row, relative to the origin; the origin is at the natural ground. */
    static final int GROUND_RELATIVE_Y = MARKER_Y - 4;

    static final int SEARCH_RADIUS = 96;
    static final int COARSE_STEP = 16;
    static final int REFINE_RADIUS = 16;
    static final int REFINE_STEP = 4;
    static final int OUTER_SAMPLE_MARGIN = 12;
    /** Terrain blended into the structure beyond its footprint. */
    static final int BLEND_RADIUS = 52;

    private SiftMainPortalSite() {
    }

    /** The generated surface: Y of the top block of the column's terrain, see the class comment. */
    @FunctionalInterface
    public interface Heights {
        int surfaceY(int x, int z);
    }

    /** A place for the portal: where the marker column stands, the ground there and how rough it is. */
    public record Candidate(int anchorX, int anchorZ, int groundY, double roughness) {
    }

    /** Lowest corner of the portal's volume. */
    public record Origin(int x, int y, int z) {

        /** Where players arrive. */
        public Anchor anchor() {
            return new Anchor(x + PLAYER_X, y + PLAYER_Y, z + PLAYER_Z);
        }
    }

    public record Anchor(int x, int y, int z) {

        public Origin origin() {
            return new Origin(x - PLAYER_X, y - PLAYER_Y, z - PLAYER_Z);
        }
    }

    /** An inclusive block rectangle. */
    public record Window(int minX, int maxX, int minZ, int maxZ) {

        public int minChunkX() {
            return minX >> 4;
        }

        public int maxChunkX() {
            return maxX >> 4;
        }

        public int minChunkZ() {
            return minZ >> 4;
        }

        public int maxChunkZ() {
            return maxZ >> 4;
        }

        public int chunkCount() {
            return (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1);
        }
    }

    /** {@code generatedSurfaceBlockY} over a region (see the class comment). */
    public static int generatedSurface(WorldRegion region, int x, int z) {
        int top = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1;
        for (int y = top; y >= region.minY(); y--) {
            if (SiftTerrain.TERRAIN.contains(region.state(x, y, z))) {
                return y;
            }
        }
        return region.minY();
    }

    public static Heights heights(WorldRegion region) {
        return (x, z) -> generatedSurface(region, x, z);
    }

    /** Every column {@link #find} may read, for loading the chunks beforehand. */
    public static Window searchWindow() {
        int farthest = SEARCH_RADIUS + REFINE_RADIUS;
        return new Window(-farthest - MARKER_X - OUTER_SAMPLE_MARGIN, farthest + (SIZE_X - 1 - MARKER_X) + OUTER_SAMPLE_MARGIN,
            -farthest - MARKER_Z - OUTER_SAMPLE_MARGIN, farthest + (SIZE_Z - 1 - MARKER_Z) + OUTER_SAMPLE_MARGIN);
    }

    /** Every column {@link SiftMainPortalBuilder} reads or writes for an origin. */
    public static Window buildWindow(Origin origin) {
        return new Window(origin.x() - BLEND_RADIUS, origin.x() + SIZE_X - 1 + BLEND_RADIUS,
            origin.z() - BLEND_RADIUS, origin.z() + SIZE_Z - 1 + BLEND_RADIUS);
    }

    /** {@code findBestCandidate}: a coarse search over +-96 blocks around 0,0, then a fine one around its best. */
    public static Candidate find(Heights heights) {
        Candidate coarse = searchArea(heights, -SEARCH_RADIUS, SEARCH_RADIUS, -SEARCH_RADIUS, SEARCH_RADIUS, COARSE_STEP);
        if (coarse == null) {
            return new Candidate(0, 0, heights.surfaceY(0, 0), 999.0);
        }
        Candidate refined = searchArea(heights, coarse.anchorX() - REFINE_RADIUS, coarse.anchorX() + REFINE_RADIUS,
            coarse.anchorZ() - REFINE_RADIUS, coarse.anchorZ() + REFINE_RADIUS, REFINE_STEP);
        return refined != null ? refined : coarse;
    }

    static Candidate searchArea(Heights heights, int minSearchX, int maxSearchX, int minSearchZ, int maxSearchZ, int step) {
        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int x = minSearchX; x <= maxSearchX; x += step) {
            for (int z = minSearchZ; z <= maxSearchZ; z += step) {
                TerrainSample sample = sample(heights, x, z);
                Candidate candidate = new Candidate(x, z, sample.medianHeight(), sample.roughness());
                double candidateScore = score(candidate, x, z) + sample.range() * 135.0 + sample.outerRange() * 75.0;
                if (sample.medianHeight() < 12 || sample.medianHeight() > 232) {
                    candidateScore += 10000.0;
                }
                if (candidateScore < bestScore) {
                    bestScore = candidateScore;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** The score of a candidate before the height-range penalties: rough ground and distance from 0,0 cost. */
    static double score(Candidate candidate, int x, int z) {
        double distancePenalty = Math.sqrt((double) x * (double) x + (double) z * (double) z) * 0.8;
        return candidate.roughness() * 18.0 + distancePenalty;
    }

    record TerrainSample(int medianHeight, int range, int outerRange, double roughness) {
    }

    /**
     * Nine heights over the footprint (corners, edge middles, centre) and eight on a ring 12 blocks
     * around it. Integer division truncates towards zero, as in the mod, which matters for the middles
     * at negative coordinates.
     */
    static TerrainSample sample(Heights heights, int anchorX, int anchorZ) {
        int minX = anchorX - MARKER_X;
        int minZ = anchorZ - MARKER_Z;
        int maxX = minX + SIZE_X - 1;
        int maxZ = minZ + SIZE_Z - 1;
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        int[] core = {heights.surfaceY(minX, minZ), heights.surfaceY(midX, minZ), heights.surfaceY(maxX, minZ),
            heights.surfaceY(minX, midZ), heights.surfaceY(midX, midZ), heights.surfaceY(maxX, midZ),
            heights.surfaceY(minX, maxZ), heights.surfaceY(midX, maxZ), heights.surfaceY(maxX, maxZ)};
        int outerMinX = minX - OUTER_SAMPLE_MARGIN;
        int outerMaxX = maxX + OUTER_SAMPLE_MARGIN;
        int outerMinZ = minZ - OUTER_SAMPLE_MARGIN;
        int outerMaxZ = maxZ + OUTER_SAMPLE_MARGIN;
        int outerMidX = (outerMinX + outerMaxX) / 2;
        int outerMidZ = (outerMinZ + outerMaxZ) / 2;
        int[] outer = {heights.surfaceY(outerMinX, outerMinZ), heights.surfaceY(outerMidX, outerMinZ),
            heights.surfaceY(outerMaxX, outerMinZ), heights.surfaceY(outerMinX, outerMidZ),
            heights.surfaceY(outerMaxX, outerMidZ), heights.surfaceY(outerMinX, outerMaxZ),
            heights.surfaceY(outerMidX, outerMaxZ), heights.surfaceY(outerMaxX, outerMaxZ)};
        int[] sorted = core.clone();
        Arrays.sort(sorted);
        int median = sorted[sorted.length / 2];
        int coreMin = Arrays.stream(core).min().orElse(median);
        int coreMax = Arrays.stream(core).max().orElse(median);
        int outerMin = Arrays.stream(outer).min().orElse(median);
        int outerMax = Arrays.stream(outer).max().orElse(median);
        double roughness = 0.0;
        for (int height : core) {
            roughness += Math.abs(height - median);
        }
        for (int height : outer) {
            roughness += Math.abs(height - median) * 0.45;
        }
        roughness /= core.length + outer.length * 0.45;
        return new TerrainSample(median, coreMax - coreMin, Math.max(coreMax, outerMax) - Math.min(coreMin, outerMin), roughness);
    }

    /**
     * The portal's origin for a candidate: its lowest layer sits on the natural ground (the marker
     * column's frame row is at the origin's Y), kept inside the build height.
     */
    public static Origin origin(Candidate candidate, int minY, int maxY) {
        int originY = candidate.groundY() - GROUND_RELATIVE_Y;
        originY = Math.max(minY, Math.min(maxY - SIZE_Y, originY));
        return new Origin(candidate.anchorX() - MARKER_X, originY, candidate.anchorZ() - MARKER_Z);
    }
}
