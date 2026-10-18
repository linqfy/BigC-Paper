package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Origin;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Template;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.TemplateBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Port of the building half of the mod's {@code SiftPortalStructure.ensurePortal}: clears the plants
 * and trees around the site, blends the terrain into the portal (the ground profile inside the
 * footprint is cut down to, the cliff profile outside it is eroded away from), seals the foundation,
 * places the {@code the_sift:main_portal} template, replaces the jigsaw marker, turns covered growth
 * into plain siftslate and plants the footprint. Written against {@link WorldRegion} so that it runs
 * on a snapshot ({@link OverlayRegion}) and in tests; where the portal goes is {@link SiftMainPortalSite}.
 *
 * <p>Differences from the mod:</p>
 * <ul>
 *   <li>"Generated surface" is the top Sift terrain block of a column (see {@link SiftMainPortalSite}),
 *       not the noise height. The mod's clearing of features above it therefore removes plants and
 *       trees only; monoliths and arches made of Sift terrain blocks count as ground and are cut or
 *       built over by the blending instead of removed.</li>
 *   <li>Terrain also includes dry healthy sculk growth, which the mod's {@code isSiftTerrain} lists
 *       leave out (it would stop the lowering of a column at such a block).</li>
 *   <li>Plants are the vanilla stand-ins of the mod's plants (see {@code SiftBlocks}); blocks are
 *       written without the mod's update flags, which Paper's API does not offer, and the portal
 *       interior is the light-block stand-in of {@code sift_portal}.</li>
 *   <li>The mod's feature placement guard is not ported (see {@code AbandonedMainPortalFeature}); the
 *       chunks around the site are generated before the portal is built, so no feature is placed
 *       over it afterwards.</li>
 *   <li>The planting uses {@link java.util.Random} instead of the game's random source, so the exact
 *       plants differ from the mod's for the same seed.</li>
 * </ul>
 */
public final class SiftMainPortalBuilder {

    /** What was built. */
    public record Result(Origin origin, int placedBlocks, int rejectedWrites) {
    }

    private static final int FOUNDATION_BLEND_RADIUS = 15;
    private static final int FEATURE_CLEAR_MARGIN = 15;
    private static final int TERRAIN_SEARCH_DEPTH = 48;

    private static final int[][] GROUND_PROFILE = {
        {-1, -1, -1, -1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1},
        {-1, -1, -1, -1, 1, 7, 14, 1, 6, 4, 3, 3, 3, 2, -1, -1, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 4, -1, 1, 12, 16, 14, 2, -1, -1, -1, -1},
        {-1, -1, -1, -1, 0, 17, 17, 12, 18, 6, 5, 4, 3, 3, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 6, 7, 17, 17, 17, 14, 14, -1, -1, -1, -1},
        {-1, -1, -1, -1, 0, 17, 18, 18, 18, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 16, 17, 17, 15, 14, -1, -1, -1, -1},
        {-1, -1, -1, -1, 0, 17, 17, 18, 18, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 5, 5, 6, 16, 17, 17, 15, 15, -1, -1, -1, -1},
        {-1, -1, -1, -1, 0, 17, 17, 2, 6, 4, 3, 4, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 4, 6, 17, 16, 15, 15, -1, -1, -1, -1},
        {-1, -1, -1, -1, 0, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 1, 0, 2, 2, 2, 3, 3, 5, 5, 5, 4, -1, -1, -1, -1},
        {-1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, -1, 1, 2, 2, -1, -1, -1, -1, -1, -1, -1, -1},
        {-1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}
    };

    private static final int[][] CLIFF_PROFILE = {
        {-1, -1, -1, -1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1},
        {-1, -1, -1, -1, 15, 17, 17, 17, 17, 17, 17, 17, 17, 16, 16, 16, 17, 16, 16, 17, 17, 17, 17, 17, 17, 17, 16, 16, 17, 17, 17, 17, 17, 16, 14, 14, -1, -1, -1, -1},
        {9, 10, 10, 9, 16, 17, 17, 17, 18, 18, 17, 17, 17, 16, 16, 16, 17, 16, 16, 17, 17, 17, 18, 17, 17, 17, 17, 17, 17, 17, 16, 17, 17, 17, 14, 14, 12, 12, 12, 11},
        {10, 10, 10, 10, 17, 17, 18, 18, 18, 18, 18, 17, 17, 16, 17, 16, 17, 17, 16, 17, 17, 18, 18, 18, 18, 18, 17, 17, 17, 16, 16, 16, 17, 17, 15, 14, 12, 12, 12, 12},
        {10, 10, 10, 10, 17, 17, 17, 18, 18, 18, 17, 17, 17, 16, 16, 16, 17, 17, 17, 17, 17, 18, 17, 17, 18, 18, 17, 17, 17, 17, 17, 16, 17, 17, 15, 15, 12, 12, 12, 12},
        {-1, -1, -1, -1, 17, 17, 17, 17, 17, 17, 17, 17, 16, 16, 16, 17, 17, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 16, 15, 15, 3, -1, -1, -1},
        {-1, -1, -1, -1, 0, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 1, 0, 2, 2, 2, 3, 3, 5, 5, 5, 4, -1, -1, -1, -1},
        {-1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, -1, 1, 2, 2, -1, -1, -1, -1, -1, -1, -1, -1},
        {-1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}
    };

    private static final ProfilePoint[] GROUND_POINTS = collectProfilePoints(GROUND_PROFILE);
    private static final ProfilePoint[] CLIFF_POINTS = collectProfilePoints(CLIFF_PROFILE);

    private static final BlockStateSpec OVERGROWN_WILLOW_LOG_X = SiftBlocks.remap("overgrown_willow_log", Map.of("axis", "x")).orElseThrow();
    private static final BlockStateSpec OVERGROWN_WILLOW_LOG_Z = SiftBlocks.remap("overgrown_willow_log", Map.of("axis", "z")).orElseThrow();
    /** {@code isSiftPlant}: the willow blocks that are custom blocks here; the other plants are vanilla stand-ins. */
    private static final Set<BlockStateSpec> PLANT_STATES = Set.of(SiftTerrain.OVERGROWN_WILLOW_LOG, OVERGROWN_WILLOW_LOG_X,
        OVERGROWN_WILLOW_LOG_Z, SiftTerrain.OVERGROWN_WILLOW_WOOD, SiftTerrain.OVERGROWN_WILLOW_FOLIAGE);
    private static final Set<String> PLANT_TYPES = Set.of("minecraft:fern", "minecraft:bush", "minecraft:short_grass",
        "minecraft:crimson_roots", "minecraft:short_dry_grass", "minecraft:vine");

    private record ProfilePoint(int x, int z, int height) {
    }

    private SiftMainPortalBuilder() {
    }

    /**
     * Builds the portal with its lowest corner at {@code origin}. Every column the work reads or writes
     * lies inside {@link SiftMainPortalSite#buildWindow}; writes the region refuses are counted in the result.
     */
    public static Result build(WorldRegion region, Template template, Origin origin) {
        if (template.sizeX() != SiftMainPortalSite.SIZE_X || template.sizeY() != SiftMainPortalSite.SIZE_Y
            || template.sizeZ() != SiftMainPortalSite.SIZE_Z) {
            throw new IllegalArgumentException("The main portal template must be " + SiftMainPortalSite.SIZE_X + "x"
                + SiftMainPortalSite.SIZE_Y + "x" + SiftMainPortalSite.SIZE_Z + ", not " + template.sizeX() + "x"
                + template.sizeY() + "x" + template.sizeZ());
        }
        long seed = region.seed();
        clearLargeFeatures(region, origin);
        blendTerrainIntoPortal(region, origin, seed);
        sealPortalFoundation(region, origin);
        int[] counts = placeTemplate(region, template, origin);
        normalizeCoveredGrowth(region, origin);
        decoratePortal(region, origin, seed);
        return new Result(origin, counts[0], counts[1]);
    }

    // --- plants and terrain ----------------------------------------------------------------

    static boolean isTerrain(BlockStateSpec state) {
        return SiftTerrain.TERRAIN.contains(state);
    }

    static boolean isPlant(BlockStateSpec state) {
        return PLANT_STATES.contains(state) || PLANT_TYPES.contains(state.block().asString());
    }

    private static boolean isAir(BlockStateSpec state) {
        String id = state.block().asString();
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** {@code findTerrainSurfaceBlockY}: the topmost terrain block within 48 blocks below the surface, or -1. */
    static int findTerrainSurface(WorldRegion region, int x, int z) {
        int top = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1;
        int minimum = Math.max(region.minY() + 1, top - TERRAIN_SEARCH_DEPTH);
        for (int y = top; y >= minimum; y--) {
            if (isTerrain(region.state(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    private static void setDuringPortalBuild(WorldRegion region, int x, int y, int z, BlockStateSpec state) {
        if (!region.state(x, y, z).equals(state)) {
            region.set(x, y, z, state);
        }
    }

    /** {@code clearLargeFeaturesAroundPortal}: removes terrain and plants above the generated surface. */
    private static void clearLargeFeatures(WorldRegion region, Origin origin) {
        int minX = origin.x() - FEATURE_CLEAR_MARGIN;
        int maxX = origin.x() + SiftMainPortalSite.SIZE_X - 1 + FEATURE_CLEAR_MARGIN;
        int minZ = origin.z() - FEATURE_CLEAR_MARGIN;
        int maxZ = origin.z() + SiftMainPortalSite.SIZE_Z - 1 + FEATURE_CLEAR_MARGIN;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int generatedTop = SiftMainPortalSite.generatedSurface(region, x, z);
                int actualTop = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = actualTop; y > generatedTop; y--) {
                    BlockStateSpec state = region.state(x, y, z);
                    if (isAir(state)) {
                        continue;
                    }
                    if (!isTerrain(state) && !isPlant(state)) {
                        break;
                    }
                    setDuringPortalBuild(region, x, y, z, SiftTerrain.AIR);
                }
            }
        }
    }

    // --- blending --------------------------------------------------------------------------

    /** {@code blendTerrainIntoPortal}. */
    private static void blendTerrainIntoPortal(WorldRegion region, Origin origin, long seed) {
        int structureMinX = origin.x();
        int structureMaxX = origin.x() + SiftMainPortalSite.SIZE_X - 1;
        int structureMinZ = origin.z();
        int structureMaxZ = origin.z() + SiftMainPortalSite.SIZE_Z - 1;
        int minX = structureMinX - SiftMainPortalSite.BLEND_RADIUS;
        int maxX = structureMaxX + SiftMainPortalSite.BLEND_RADIUS;
        int minZ = structureMinZ - SiftMainPortalSite.BLEND_RADIUS;
        int maxZ = structureMaxZ + SiftMainPortalSite.BLEND_RADIUS;
        int lowest = region.minY() + 2;
        int highest = region.maxY() - 3;
        buildNaturalPortalFoundation(region, origin, seed);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - structureMinX;
                int localZ = z - structureMinZ;
                boolean inside = localX >= 0 && localX < SiftMainPortalSite.SIZE_X && localZ >= 0 && localZ < SiftMainPortalSite.SIZE_Z;
                int naturalY = findTerrainSurface(region, x, z);
                if (naturalY <= 1 || naturalY >= region.maxY() - 2) {
                    continue;
                }
                BlockStateSpec originalTop = region.state(x, naturalY, z);
                if (!isTerrain(originalTop)) {
                    continue;
                }
                if (inside) {
                    int exactRelativeY = GROUND_PROFILE[localX][localZ];
                    if (exactRelativeY < 0) {
                        continue;
                    }
                    int desiredY = Math.max(lowest, Math.min(highest, origin.y() + exactRelativeY));
                    if (naturalY <= desiredY) {
                        continue;
                    }
                    lowerTerrainColumn(region, x, z, naturalY, desiredY, originalTop, false);
                    continue;
                }
                double rectangleDistance = distanceOutsideRectangle(x, z, structureMinX, structureMaxX, structureMinZ, structureMaxZ);
                if (rectangleDistance > SiftMainPortalSite.BLEND_RADIUS) {
                    continue;
                }
                double envelopeRelativeY = portalTerrainEnvelope(localX, localZ, seed);
                double approach = portalApproachMask(localX, localZ);
                if (approach > 0.0) {
                    double lowForecourt = 3.6 + terrainBlendNoise(seed ^ 0xD1B54A32D192ED03L, localX, localZ) * 0.55;
                    envelopeRelativeY = envelopeRelativeY * (1.0 - approach) + Math.min(envelopeRelativeY, lowForecourt) * approach;
                }
                if (envelopeRelativeY <= 0.0) {
                    continue;
                }
                int desiredY = origin.y() + (int) Math.floor(envelopeRelativeY);
                desiredY = Math.max(lowest, Math.min(highest, desiredY));
                if (desiredY <= naturalY) {
                    continue;
                }
                raiseTerrainColumn(region, x, z, naturalY, desiredY, originalTop, true);
            }
        }
    }

    /** {@code sealPortalFoundation}: fills every air or plant cell between the terrain and the portal's floor. */
    private static void sealPortalFoundation(WorldRegion region, Origin origin) {
        int foundationTopY = Math.max(2, origin.y() - 1);
        for (int localX = 0; localX < SiftMainPortalSite.SIZE_X; localX++) {
            for (int localZ = 0; localZ < SiftMainPortalSite.SIZE_Z; localZ++) {
                if (!isPortalBaseColumn(localX, localZ)) {
                    continue;
                }
                int x = origin.x() + localX;
                int z = origin.z() + localZ;
                int terrainY = findTerrainSurface(region, x, z);
                if (terrainY < 1) {
                    terrainY = Math.max(1, SiftMainPortalSite.generatedSurface(region, x, z));
                }
                BlockStateSpec surface = region.state(x, terrainY, z);
                if (terrainY < foundationTopY) {
                    raiseTerrainColumn(region, x, z, terrainY, foundationTopY, surface, false);
                }
                for (int y = foundationTopY; y > terrainY; y--) {
                    BlockStateSpec state = region.state(x, y, z);
                    if (!isAir(state) && !isPlant(state)) {
                        continue;
                    }
                    setDuringPortalBuild(region, x, y, z, SiftTerrain.SIFTSLATE);
                }
            }
        }
    }

    /** {@code buildNaturalPortalFoundation}: a skirt of eroded ground up to 15 blocks around the portal's base. */
    private static void buildNaturalPortalFoundation(WorldRegion region, Origin origin, long seed) {
        int foundationTopY = Math.max(2, origin.y() - 1);
        int minX = origin.x() - FOUNDATION_BLEND_RADIUS;
        int maxX = origin.x() + SiftMainPortalSite.SIZE_X - 1 + FOUNDATION_BLEND_RADIUS;
        int minZ = origin.z() - FOUNDATION_BLEND_RADIUS;
        int maxZ = origin.z() + SiftMainPortalSite.SIZE_Z - 1 + FOUNDATION_BLEND_RADIUS;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - origin.x();
                int localZ = z - origin.z();
                boolean core = isPortalBaseColumn(localX, localZ);
                double broadNoise = terrainBlendNoise(seed ^ 0x6A09E667F3BCC909L, localX * 3 + 11, localZ * 3 - 17);
                double fineNoise = terrainBlendNoise(seed ^ 0xBB67AE8584CAA73BL, localX * 9 - 23, localZ * 9 + 31);
                double distance = core ? 0.0 : distanceToPortalBase(localX + broadNoise * 1.45, localZ - broadNoise * 1.15);
                if (distance > FOUNDATION_BLEND_RADIUS) {
                    continue;
                }
                double erosion = distance * (0.7 + fineNoise * 0.08) + distance * distance * 0.016;
                int targetY = core ? foundationTopY : foundationTopY - Math.max(1, (int) Math.floor(erosion));
                int naturalY = findTerrainSurface(region, x, z);
                if (naturalY <= 1 || naturalY >= targetY) {
                    continue;
                }
                BlockStateSpec originalTop = region.state(x, naturalY, z);
                if (!isTerrain(originalTop)) {
                    continue;
                }
                boolean preserveBiomeSurface = !core && distance > 2.25;
                raiseTerrainColumn(region, x, z, naturalY, targetY, originalTop, preserveBiomeSurface);
            }
        }
    }

    /** Height above the origin the ground profile gives a footprint column, or -1 where the terrain is left alone. */
    static int groundHeight(int localX, int localZ) {
        return localX >= 0 && localX < SiftMainPortalSite.SIZE_X && localZ >= 0 && localZ < SiftMainPortalSite.SIZE_Z
            ? GROUND_PROFILE[localX][localZ] : -1;
    }

    static boolean isPortalBaseColumn(int localX, int localZ) {
        return localX >= 0 && localX < SiftMainPortalSite.SIZE_X && localZ >= 0 && localZ < SiftMainPortalSite.SIZE_Z
            && GROUND_PROFILE[localX][localZ] >= 0;
    }

    private static double distanceToPortalBase(double localX, double localZ) {
        double bestSquared = Double.POSITIVE_INFINITY;
        for (ProfilePoint point : GROUND_POINTS) {
            double dx = localX - point.x();
            double dz = localZ - point.z();
            bestSquared = Math.min(bestSquared, dx * dx + dz * dz);
        }
        return Math.sqrt(bestSquared);
    }

    /** Height above the origin that the cliff profile lets the terrain have at a column, eroded with distance. */
    static double portalTerrainEnvelope(int localX, int localZ, long seed) {
        double broadWarp = terrainBlendNoise(seed ^ 0x51F7A11DL, localX * 3 + 17, localZ * 3 - 29);
        double fineWarp = terrainBlendNoise(seed ^ 0x7C19E5A3L, localX * 7 - 41, localZ * 7 + 13);
        double warpedX = localX + broadWarp * 1.65 + fineWarp * 0.35;
        double warpedZ = localZ - broadWarp * 1.1 + fineWarp * 0.45;
        double columnNoise = terrainBlendNoise(seed ^ 0xA24BAED4963EE407L, localX * 5 + 31, localZ * 5 - 47);
        double erosionPerBlock = 0.405 + columnNoise * 0.022;
        double best = Double.NEGATIVE_INFINITY;
        for (ProfilePoint point : CLIFF_POINTS) {
            double dx = (warpedX - point.x()) * 0.76;
            double dz = (warpedZ - point.z()) * 1.08;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double candidate = point.height() - distance * erosionPerBlock;
            if (candidate > best) {
                best = candidate;
            }
        }
        best += columnNoise * 0.55;
        if (!Double.isFinite(best)) {
            return -1.0;
        }
        return best;
    }

    /** 1 on the strips in front of and behind the portal's ends, fading to 0 away from them: the forecourt stays low. */
    static double portalApproachMask(int localX, int localZ) {
        int distanceZ = localZ < 9 ? 9 - localZ : (localZ > 30 ? localZ - 30 : 0);
        int distanceX;
        if (localX < 0) {
            distanceX = -localX;
        } else if (localX >= SiftMainPortalSite.SIZE_X) {
            distanceX = localX - 8;
        } else {
            return 0.0;
        }
        double zMask = 1.0 - smootherstep(distanceZ / 6.0);
        double xMask = 1.0 - smootherstep(Math.max(0.0, distanceX - 2.0) / 18.0);
        return clamp01(zMask * xMask);
    }

    /** {@code lowerTerrainColumn}: removes terrain above {@code desiredY}, then re-covers the new top. */
    private static void lowerTerrainColumn(WorldRegion region, int x, int z, int naturalY, int desiredY, BlockStateSpec originalTop,
                                           boolean preserveSurface) {
        for (int y = naturalY; y > desiredY; y--) {
            if (!isTerrain(region.state(x, y, z))) {
                break;
            }
            setDuringPortalBuild(region, x, y, z, SiftTerrain.AIR);
        }
        BlockStateSpec exposed = region.state(x, desiredY, z);
        if (!preserveSurface && isTerrain(exposed)) {
            setDuringPortalBuild(region, x, desiredY, z, SiftTerrain.SIFTSLATE);
        } else if (exposed.equals(SiftTerrain.SIFTSLATE)) {
            restoreSurface(region, x, desiredY, z, originalTop);
        }
    }

    /** {@code raiseTerrainColumn}: fills up to {@code desiredY} with siftslate; blends keep the original surface type. */
    private static void raiseTerrainColumn(WorldRegion region, int x, int z, int naturalY, int desiredY, BlockStateSpec originalTop,
                                           boolean preserveSurface) {
        for (int y = naturalY + 1; y <= desiredY; y++) {
            setDuringPortalBuild(region, x, y, z, SiftTerrain.SIFTSLATE);
        }
        if (!preserveSurface) {
            setDuringPortalBuild(region, x, desiredY, z, SiftTerrain.SIFTSLATE);
        } else {
            restoreSurface(region, x, desiredY, z, originalTop);
        }
    }

    /** The mod puts growth or healthy sculk back on top when that is what the column's surface was. */
    private static void restoreSurface(WorldRegion region, int x, int y, int z, BlockStateSpec originalTop) {
        if (SiftTerrain.isGrowth(originalTop)) {
            setDuringPortalBuild(region, x, y, z, SiftTerrain.SIFTSLATE_GROWTH);
        } else if (originalTop.equals(SiftTerrain.HEALTHY_SCULK)) {
            setDuringPortalBuild(region, x, y, z, SiftTerrain.HEALTHY_SCULK);
        }
    }

    // --- structure ---------------------------------------------------------------------------

    /**
     * {@code StructureTemplate.placeInWorld}: every block of the template. Template air is not stored, so
     * terrain the profiles leave in place shows through; the jigsaw marker becomes its final state,
     * reinforced siftslate, as the mod's follow-up {@code setBlock} does.
     *
     * @return {placed, rejected}
     */
    private static int[] placeTemplate(WorldRegion region, Template template, Origin origin) {
        int placed = 0;
        int rejected = 0;
        for (TemplateBlock block : template.blocks()) {
            BlockStateSpec state = stateOf(block);
            if (state == null) {
                continue;
            }
            if (region.set(origin.x() + block.x(), origin.y() + block.y(), origin.z() + block.z(), state)) {
                placed++;
            } else {
                rejected++;
            }
        }
        return new int[]{placed, rejected};
    }

    static BlockStateSpec stateOf(TemplateBlock block) {
        String id = block.block();
        if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) {
            return null;
        }
        if (id.equals("minecraft:jigsaw")) {
            return SiftTerrain.REINFORCED_SIFTSLATE;
        }
        if (id.startsWith("the_sift:")) {
            return SiftBlocks.remap(id.substring("the_sift:".length()), block.properties())
                .orElseThrow(() -> new IllegalStateException("Unmapped template block " + id));
        }
        return BlockStateSpec.of(id, block.properties());
    }

    /** {@code normalizeCoveredGrowth}: growth under a full block is plain siftslate. */
    private static void normalizeCoveredGrowth(WorldRegion region, Origin origin) {
        for (int x = 0; x < SiftMainPortalSite.SIZE_X; x++) {
            for (int z = 0; z < SiftMainPortalSite.SIZE_Z; z++) {
                for (int y = 0; y < SiftMainPortalSite.SIZE_Y - 1; y++) {
                    int worldX = origin.x() + x;
                    int worldY = origin.y() + y;
                    int worldZ = origin.z() + z;
                    if (SiftTerrain.isGrowth(region.state(worldX, worldY, worldZ)) && region.isSolid(worldX, worldY + 1, worldZ)) {
                        setDuringPortalBuild(region, worldX, worldY, worldZ, SiftTerrain.SIFTSLATE);
                    }
                }
            }
        }
    }

    /** {@code decoratePortalAndBlend}: plants on every floor of the footprint except the arrival strip. */
    private static void decoratePortal(WorldRegion region, Origin origin, long seed) {
        Random random = new Random(seed ^ 0x504C414E54534CL);
        SiftMainPortalSite.Anchor anchor = origin.anchor();
        for (int x = origin.x(); x < origin.x() + SiftMainPortalSite.SIZE_X; x++) {
            for (int z = origin.z(); z < origin.z() + SiftMainPortalSite.SIZE_Z; z++) {
                for (int y = origin.y(); y < origin.y() + SiftMainPortalSite.SIZE_Y; y++) {
                    if (isPortalApproach(x, z, anchor)) {
                        continue;
                    }
                    SiftSurfaceDecorator.decorateStructureSurface(region, random, new Pos(x, y, z), 0.92F);
                }
            }
        }
    }

    /** The strip around the arrival point that is left free of plants. */
    static boolean isPortalApproach(int x, int z, SiftMainPortalSite.Anchor anchor) {
        return Math.abs(x - anchor.x()) <= 4 && Math.abs(z - anchor.z()) <= 5;
    }

    // --- maths -------------------------------------------------------------------------------

    private static ProfilePoint[] collectProfilePoints(int[][] profile) {
        List<ProfilePoint> points = new ArrayList<>();
        for (int x = 0; x < SiftMainPortalSite.SIZE_X; x++) {
            for (int z = 0; z < SiftMainPortalSite.SIZE_Z; z++) {
                if (profile[x][z] >= 0) {
                    points.add(new ProfilePoint(x, z, profile[x][z]));
                }
            }
        }
        return points.toArray(new ProfilePoint[0]);
    }

    static double distanceOutsideRectangle(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        int dx = x < minX ? minX - x : Math.max(0, x - maxX);
        int dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
        return Math.sqrt((double) dx * (double) dx + (double) dz * (double) dz);
    }

    static double terrainBlendNoise(long seed, int x, int z) {
        double phase = (double) (seed & 0xFFFFL) * 1.73E-4;
        double a = Math.sin(x * 0.083 + z * 0.031 + phase);
        double b = Math.cos(x * 0.037 - z * 0.071 - phase * 0.73);
        double c = Math.sin((x + z) * 0.021 + phase * 1.91);
        return a * 0.5 + b * 0.33 + c * 0.17;
    }

    private static double smootherstep(double value) {
        double t = clamp01(value);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
