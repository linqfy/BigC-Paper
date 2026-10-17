package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Port of the mod's {@code IchorLakeFeature}: one lake per chunk that is shaped from a noisy
 * elliptical water mask, a smoothed beach ring and a sculk/siftslate basin. Bigger lakes get rock
 * formations or a willow island, and steep terrain can produce a two-level cascade (an upper pond
 * on a mountain shelf, a falling-ichor channel and a lower lake). Shores are planted with the
 * Sift's lake flowers and plants.
 *
 * <p>Ichor is placed as water ({@link SiftTerrain#ICHOR}); flowing ichor is water with the same
 * {@code level} (the falling channel uses {@code level=8}). Like every worldgen fluid, the lake
 * does not flow until a block update reaches it.</p>
 *
 * <p>Differences: the mod's {@code SiftFeaturePlacementGuard} is not ported, neither the portal
 * proximity checks nor the global carver reservations, because both depend on generation order and
 * would make the output non-deterministic (lakes in neighbouring chunks are only kept apart by the
 * mod's own {@code hasNearbyIchor} scan of the region). Fluid ticks are not scheduled (there is no
 * equivalent while generating). The lake's willow island uses the port's willow template
 * placement ({@link OvergrownWillowTreeFeature#placeAt}), whose vines and plants are vanilla
 * stand-ins. Ichor and ichor snow are recognised by their stand-ins (water and snow layers), and
 * the random generator is {@link Random}, so a given seed does not reproduce the mod's lakes.
 * The mod's {@code HashMap} beach ring is a {@code LinkedHashMap} here so iteration order never
 * depends on hashing.</p>
 */
final class IchorLakeFeature implements SiftFeature {

    private static final int MAX_SAFE_EXTENT = 24;
    private static final BlockStateSpec FALLING_ICHOR = SiftTerrain.ICHOR.with("level", "8");
    private static final int[] STEPS = {-1, 0, 1, 0, -1};

    private final OvergrownWillowTreeFeature willow;

    IchorLakeFeature(OvergrownWillowTreeFeature willow) {
        this.willow = willow;
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        SiftWorldgenBounds bounds = SiftWorldgenBounds.aroundWithNeighbourMargin(x, z);
        int centerX = (x & ~15) + 9;
        int centerZ = (z & ~15) + 9;
        int centerSurface = SiftTerrain.findTerrainSurface(region, centerX, centerZ);
        if (centerSurface < 0) {
            return false;
        }
        int sampledRelief = sampleRelief(region, bounds, centerX, centerZ, 16);
        if (sampledRelief < 0) {
            return false;
        }
        CascadeSite cascade = sampledRelief >= 9 ? findCascadeSite(region, bounds, centerX, centerZ) : null;
        if (cascade != null && random.nextFloat() < 0.82F) {
            LakePalette palette = choosePalette(region, cascade.lowerCenter(), random);
            return placeCascade(region, random, bounds, centerX, centerZ, cascade, palette) > 0;
        }
        LakeVariant variant = chooseVariant(random, sampledRelief);
        if (hasNearbyIchor(region, bounds, centerX, centerZ, variant == LakeVariant.MEGA ? 20 : 15)) {
            return false;
        }
        boolean expandedMega = variant == LakeVariant.MEGA && random.nextFloat() < 0.68F;
        int radiusX = expandedMega
            ? 15 + random.nextInt(2)
            : variant.minimumRadius + random.nextInt(variant.maximumRadius - variant.minimumRadius + 1);
        int radiusZ = expandedMega
            ? clamp(radiusX + random.nextInt(5) - 2, 14, 16)
            : clamp(radiusX + random.nextInt(7) - 3, variant.minimumRadius, variant.maximumRadius);
        int beachWidth = expandedMega ? 5 : variant.minimumBeach + random.nextInt(variant.maximumBeach - variant.minimumBeach + 1);
        int extent = (int) Math.ceil(Math.max(radiusX, radiusZ) * 1.14) + beachWidth;
        if (extent > MAX_SAFE_EXTENT) {
            return false;
        }
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        long salt = random.nextLong();
        Map<Xz, LakeColumn> water = buildWaterMask(region, bounds, centerX, centerZ, radiusX, radiusZ, angle, cos, sin, salt);
        if (water.size() < variant.minimumArea) {
            return false;
        }
        Map<Xz, Integer> beach = buildBeachMask(water, beachWidth);
        List<Integer> waterSurfaces = new ArrayList<>(water.size());
        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (LakeColumn column : water.values()) {
            waterSurfaces.add(column.surfaceY());
            minimumSurface = Math.min(minimumSurface, column.surfaceY());
            maximumSurface = Math.max(maximumSurface, column.surfaceY());
        }
        if (maximumSurface - minimumSurface > variant.maximumRelief) {
            return false;
        }
        waterSurfaces.sort(Comparator.naturalOrder());
        int waterY = waterSurfaces.get((int) Math.floor((waterSurfaces.size() - 1) * 0.36)) - 1;
        if (waterY < region.minY() + 6 || waterY > topY(region) - 7) {
            return false;
        }
        Map<Xz, LakeColumn> beachColumns = validateBeach(region, bounds, beach, beachWidth, waterY, variant.maximumRelief);
        if (beachColumns.isEmpty()) {
            return false;
        }
        Map<Xz, Integer> shoreTargets = buildShoreTargets(region, water, beach, beachColumns, waterY, beachWidth);
        if (shoreTargets.isEmpty()) {
            return false;
        }
        LakePalette palette = choosePalette(region, centerX, waterY, centerZ, random);
        int placed = 0;
        for (Map.Entry<Xz, LakeColumn> entry : beachColumns.entrySet()) {
            placed += shapeBeach(region, entry.getValue(), shoreTargets.get(entry.getKey()), palette, bounds);
        }
        for (LakeColumn column : water.values()) {
            placed += carveAndFill(region, column, waterY, palette.basin(), salt, variant.maximumDepth, bounds);
        }
        if (variant == LakeVariant.MEGA || variant.rocks && random.nextFloat() < 0.82F) {
            placed += placeRockFormations(region, random, water, waterY, palette, salt, bounds, centerX, centerZ);
        }
        decorateLakeShore(region, random, water, shoreTargets, bounds);
        return placed >= variant.minimumArea;
    }

    /** {@code Level.getMaxY()} of the mod is the inclusive top block; the port's {@code maxY()} is exclusive. */
    private static int topY(WorldRegion region) {
        return region.maxY() - 1;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static LakeVariant chooseVariant(Random random, int relief) {
        if (relief <= 3) {
            return LakeVariant.MEGA;
        }
        int roll = random.nextInt(100);
        if (relief <= 5 && roll < 72) {
            return LakeVariant.LARGE;
        }
        if (relief <= 7 && roll < 82) {
            return LakeVariant.MEDIUM;
        }
        return LakeVariant.SMALL;
    }

    private static int sampleRelief(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ, int radius) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                if (!bounds.contains(centerX + dx, centerZ + dz)) {
                    return -1;
                }
                int y = SiftTerrain.findTerrainSurface(region, centerX + dx, centerZ + dz);
                if (y < 0) {
                    return -1;
                }
                minimum = Math.min(minimum, y);
                maximum = Math.max(maximum, y);
            }
        }
        return maximum - minimum;
    }

    private static Map<Xz, LakeColumn> buildWaterMask(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ,
                                                      int radiusX, int radiusZ, double angle, double cos, double sin, long salt) {
        Map<Xz, LakeColumn> water = new LinkedHashMap<>();
        int limit = (int) Math.ceil(Math.max(radiusX, radiusZ) * 1.14);
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!bounds.contains(x, z)) {
                    continue;
                }
                double localX = (dx * cos - dz * sin) / radiusX;
                double localZ = (dx * sin + dz * cos) / radiusZ;
                double warpX = localX + Math.sin(localZ * 2.7 + angle * 1.9) * 0.045;
                double warpZ = localZ + Math.sin(localX * 2.2 - angle * 1.3) * 0.04;
                double distance = Math.sqrt(warpX * warpX + warpZ * warpZ);
                double polar = Math.atan2(localZ, localX);
                double shoreline = 0.95 + Math.sin(polar * 3.0 + angle * 1.7) * 0.09 + Math.sin(polar * 5.0 - angle * 0.8) * 0.055
                    + Math.cos(polar * 7.0 + angle * 0.37) * 0.028 + (SiftTerrain.hash01(x >> 2, 0, z >> 2, salt) - 0.5) * 0.028;
                if (distance > shoreline) {
                    continue;
                }
                int surfaceY = SiftTerrain.findTerrainSurface(region, x, z);
                if (surfaceY < 0 || !SiftTerrain.isTerrain(region, x, surfaceY - 1, z)) {
                    return Map.of();
                }
                water.put(new Xz(x, z), new LakeColumn(x, z, surfaceY, distance / Math.max(0.01, shoreline)));
            }
        }
        return water;
    }

    /** Ring of non-water cells within {@code width} of the water, mapped to their ring layer (1 = touching). */
    private static Map<Xz, Integer> buildBeachMask(Map<Xz, LakeColumn> water, int width) {
        Map<Xz, Integer> beach = new LinkedHashMap<>();
        for (Xz source : water.keySet()) {
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    int layer = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
                    if (layer == 0 || layer > width) {
                        continue;
                    }
                    Xz candidate = new Xz(source.x() + dx, source.z() + dz);
                    if (water.containsKey(candidate)) {
                        continue;
                    }
                    beach.merge(candidate, layer, Math::min);
                }
            }
        }
        return beach;
    }

    private static LakePalette choosePalette(WorldRegion region, Pos center, Random random) {
        return choosePalette(region, center.x(), center.y(), center.z(), random);
    }

    private static LakePalette choosePalette(WorldRegion region, int x, int y, int z, Random random) {
        boolean overgrown = SiftTerrain.isOvergrownBiome(region.biome(x, y, z));
        int basinRoll = random.nextInt(100);
        BlockStateSpec basin = basinRoll < 70 ? SiftTerrain.DRY_HEALTHY_SCULK
            : basinRoll < 91 ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.SIFTSLATE;
        int shoreRoll = random.nextInt(100);
        BlockStateSpec shore = overgrown && shoreRoll < 58 ? SiftTerrain.SIFTSLATE_GROWTH
            : shoreRoll < (overgrown ? 79 : 72) ? SiftTerrain.DRY_HEALTHY_SCULK
            : shoreRoll < 93 ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.SIFTSLATE;
        return new LakePalette(basin, shore);
    }

    private static boolean writable(WorldRegion region, SiftWorldgenBounds bounds, int x, int y, int z) {
        return bounds.contains(x, z) && region.isWritable(x, y, z);
    }

    private static boolean isIchor(WorldRegion region, int x, int y, int z) {
        return region.blockId(x, y, z).equals("minecraft:water");
    }

    private static boolean isIchorSnow(WorldRegion region, int x, int y, int z) {
        return region.blockId(x, y, z).equals("minecraft:snow") || region.is(x, y, z, SiftTerrain.ICHOR_SNOW_BLOCK);
    }

    /** What the mod's lake carving may replace with air: replaceable blocks, ichor and ichor snow. */
    private static boolean canClear(WorldRegion region, int x, int y, int z) {
        return SiftTerrain.canReplace(region, x, y, z) || isIchor(region, x, y, z) || isIchorSnow(region, x, y, z);
    }

    /** As {@link #canClear} but keeping existing ichor (mountain shelves are carved above the water). */
    private static boolean canClearDry(WorldRegion region, int x, int y, int z) {
        return SiftTerrain.canReplace(region, x, y, z) || isIchorSnow(region, x, y, z);
    }

    private static int shapeBeach(WorldRegion region, LakeColumn column, int targetTop, LakePalette palette, SiftWorldgenBounds bounds) {
        int x = column.x();
        int z = column.z();
        int naturalTop = column.surfaceY() - 1;
        int placed = 0;
        for (int y = targetTop + 1; y <= Math.max(column.surfaceY() + 3, targetTop + 3); y++) {
            if (!writable(region, bounds, x, y, z) || !canClear(region, x, y, z)) {
                continue;
            }
            region.set(x, y, z, SiftTerrain.AIR);
        }
        int supportBottom = Math.min(naturalTop, targetTop) - 2;
        for (int y = supportBottom; y < targetTop; y++) {
            if (!writable(region, bounds, x, y, z)) {
                continue;
            }
            region.set(x, y, z, y >= targetTop - 2 ? palette.basin() : SiftTerrain.SIFTSLATE);
            placed++;
        }
        BlockStateSpec below = region.state(x, targetTop - 1, z);
        BlockStateSpec top = shorelineState(below, palette);
        if (writable(region, bounds, x, targetTop, z)) {
            region.set(x, targetTop, z, top);
            placed++;
        }
        return placed;
    }

    private static int targetShoreTop(int naturalTop, int waterY, int layer, int width) {
        if (layer <= 1 || width <= 1) {
            return waterY;
        }
        double outward = clamp((double) (layer - 1) / (double) (width - 1), 0.0, 1.0);
        double smooth = outward * outward * (3.0 - 2.0 * outward);
        return floor(lerp(smooth, waterY, naturalTop) + 0.5);
    }

    /** The mod's {@code buildBeachMask} + checks for the columns around the water ({@code validateBeach}). */
    private static Map<Xz, LakeColumn> validateBeach(WorldRegion region, SiftWorldgenBounds bounds, Map<Xz, Integer> beach,
                                                     int width, int waterY, int maximumRelief) {
        Map<Xz, LakeColumn> columns = new LinkedHashMap<>();
        for (Map.Entry<Xz, Integer> entry : beach.entrySet()) {
            Xz cell = entry.getKey();
            if (!bounds.contains(cell.x(), cell.z())) {
                return Map.of();
            }
            int surfaceY = SiftTerrain.findTerrainSurface(region, cell.x(), cell.z());
            if (surfaceY < 0 || !SiftTerrain.isTerrain(region, cell.x(), surfaceY - 1, cell.z())) {
                return Map.of();
            }
            int naturalTop = surfaceY - 1;
            int layer = entry.getValue();
            int targetTop = targetShoreTop(naturalTop, waterY, layer, width);
            if (naturalTop < waterY - width || naturalTop > waterY + maximumRelief + width
                || Math.abs(targetTop - naturalTop) > width || !hasNaturalSupport(region, cell.x(), naturalTop, cell.z(), 3)) {
                return Map.of();
            }
            columns.put(cell, new LakeColumn(cell.x(), cell.z(), surfaceY, 1.0 + (double) layer / (double) width));
        }
        return columns;
    }

    private static Map<Xz, Integer> buildShoreTargets(WorldRegion region, Map<Xz, LakeColumn> water, Map<Xz, Integer> beach,
                                                      Map<Xz, LakeColumn> beachColumns, int waterY, int width) {
        Map<Xz, Integer> targets = new LinkedHashMap<>();
        for (Map.Entry<Xz, LakeColumn> entry : beachColumns.entrySet()) {
            int layer = beach.get(entry.getKey());
            int naturalTop = entry.getValue().surfaceY() - 1;
            targets.put(entry.getKey(), layer == width ? naturalTop : targetShoreTop(naturalTop, waterY, layer, width));
        }
        for (int pass = 0; pass < width * 6; pass++) {
            Map<Xz, Integer> next = new LinkedHashMap<>(targets.size());
            for (Map.Entry<Xz, LakeColumn> entry : beachColumns.entrySet()) {
                Xz cell = entry.getKey();
                int layer = beach.get(cell);
                if (layer == 1) {
                    next.put(cell, waterY);
                    continue;
                }
                if (layer == width) {
                    next.put(cell, entry.getValue().surfaceY() - 1);
                    continue;
                }
                int desired = targetShoreTop(entry.getValue().surfaceY() - 1, waterY, layer, width);
                int sum = desired * 3;
                int weight = 3;
                for (int direction = 0; direction < 4; direction++) {
                    Xz neighbour = new Xz(cell.x() + STEPS[direction], cell.z() + STEPS[direction + 1]);
                    if (water.containsKey(neighbour)) {
                        sum += waterY * 3;
                        weight += 3;
                        continue;
                    }
                    if (targets.containsKey(neighbour)) {
                        sum += targets.get(neighbour);
                        weight++;
                        continue;
                    }
                    if (layer != width) {
                        continue;
                    }
                    int surface = SiftTerrain.findTerrainSurface(region, neighbour.x(), neighbour.z());
                    if (surface < 0) {
                        return Map.of();
                    }
                    sum += (surface - 1) * 2;
                    weight += 2;
                }
                int smoothed = floor((double) sum / (double) weight + 0.5);
                next.put(cell, clamp(smoothed, waterY - layer, waterY + layer));
            }
            targets = next;
        }
        for (Map.Entry<Xz, Integer> entry : targets.entrySet()) {
            Xz cell = entry.getKey();
            int layer = beach.get(cell);
            for (int direction = 0; direction < 4; direction++) {
                int neighbourTop;
                Xz neighbour = new Xz(cell.x() + STEPS[direction], cell.z() + STEPS[direction + 1]);
                if (water.containsKey(neighbour)) {
                    neighbourTop = waterY;
                } else if (targets.containsKey(neighbour)) {
                    neighbourTop = targets.get(neighbour);
                } else {
                    if (layer != width) {
                        continue;
                    }
                    int surface = SiftTerrain.findTerrainSurface(region, neighbour.x(), neighbour.z());
                    if (surface < 0) {
                        return Map.of();
                    }
                    neighbourTop = surface - 1;
                }
                if (Math.abs(entry.getValue() - neighbourTop) <= 1) {
                    continue;
                }
                return Map.of();
            }
        }
        return targets;
    }

    private static BlockStateSpec shorelineState(BlockStateSpec below, LakePalette palette) {
        if (!palette.shore().equals(SiftTerrain.SIFTSLATE_GROWTH)) {
            return palette.shore();
        }
        return below.equals(SiftTerrain.DRY_HEALTHY_SCULK) ? SiftTerrain.DRY_HEALTHY_SCULK_GROWTH : SiftTerrain.SIFTSLATE_GROWTH;
    }

    private static void decorateLakeShore(WorldRegion region, Random random, Map<Xz, LakeColumn> water,
                                          Map<Xz, Integer> shoreTargets, SiftWorldgenBounds bounds) {
        Map<Xz, Integer> oasisBand = buildBeachMask(water, 4);
        List<Pos> shore = new ArrayList<>(oasisBand.size());
        for (Xz cell : oasisBand.keySet()) {
            if (!bounds.contains(cell.x(), cell.z())) {
                continue;
            }
            Integer shapedTop = shoreTargets.get(cell);
            int floorY = shapedTop != null ? shapedTop : SiftTerrain.findTerrainSurface(region, cell.x(), cell.z()) - 1;
            if (floorY < region.minY()) {
                continue;
            }
            shore.add(new Pos(cell.x(), floorY, cell.z()));
        }
        SiftSurfaceDecorator.decorateLakeShore(region, random, shore);
    }

    private static int carveAndFill(WorldRegion region, LakeColumn column, int waterY, BlockStateSpec lining, long salt,
                                    int maximumDepth, SiftWorldgenBounds bounds) {
        int x = column.x();
        int z = column.z();
        double centerStrength = 1.0 - clamp(column.normalizedDistance(), 0.0, 1.0);
        int depth = clamp(1 + (int) Math.floor(centerStrength * (maximumDepth - 0.55)
            + SiftTerrain.hash01(x, waterY, z, salt ^ 0xA24BAED4963EE407L) * 0.7), 1, maximumDepth);
        int floorY = waterY - depth;
        for (int y = floorY + 1; y <= Math.max(column.surfaceY() + 2, waterY + 1); y++) {
            if (!writable(region, bounds, x, y, z) || !canClear(region, x, y, z)) {
                continue;
            }
            region.set(x, y, z, SiftTerrain.AIR);
        }
        for (int y = floorY - 1; y <= floorY; y++) {
            if (!writable(region, bounds, x, y, z)) {
                continue;
            }
            region.set(x, y, z, lining);
        }
        int placed = 0;
        for (int y = floorY + 1; y <= waterY; y++) {
            if (!writable(region, bounds, x, y, z)) {
                continue;
            }
            region.set(x, y, z, SiftTerrain.ICHOR);
            placed++;
        }
        return placed;
    }

    private int placeRockFormations(WorldRegion region, Random random, Map<Xz, LakeColumn> water, int waterY,
                                           LakePalette palette, long salt, SiftWorldgenBounds bounds, int anchorX, int anchorZ) {
        List<LakeColumn> candidates = new ArrayList<>();
        for (LakeColumn column : water.values()) {
            if (column.normalizedDistance() > 0.2 && column.normalizedDistance() < 0.72) {
                candidates.add(column);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        boolean islandScale = water.size() > 400;
        LakeColumn lakeSample = candidates.get(0);
        boolean overgrown = SiftTerrain.isOvergrownBiome(region.biome(lakeSample.x(), waterY, lakeSample.z()));
        boolean willowVariant = islandScale && overgrown && random.nextFloat() < 0.27F;
        if (willowVariant) {
            LakeColumn islandCenter = findWillowIslandCenter(candidates, random);
            if (islandCenter != null) {
                return placeWillowIsland(region, random, water, waterY, palette, salt, bounds, islandCenter, anchorX, anchorZ);
            }
        }
        int formations = islandScale ? 3 + random.nextInt(2) : 1 + random.nextInt(3);
        List<Xz> usedCenters = new ArrayList<>();
        int placed = 0;
        for (int formation = 0; formation < formations; formation++) {
            LakeColumn center = null;
            for (int attempt = 0; attempt < 24 && center == null; attempt++) {
                LakeColumn candidate = candidates.get(random.nextInt(candidates.size()));
                boolean separated = true;
                for (Xz used : usedCenters) {
                    int dx = candidate.x() - used.x();
                    int dz = candidate.z() - used.z();
                    if (dx * dx + dz * dz >= (islandScale ? 49 : 16)) {
                        continue;
                    }
                    separated = false;
                    break;
                }
                if (!separated) {
                    continue;
                }
                center = candidate;
            }
            if (center == null) {
                continue;
            }
            usedCenters.add(new Xz(center.x(), center.z()));
            int radius = islandScale ? 2 + random.nextInt(3) : 1 + random.nextInt(2);
            int peak = islandScale ? 2 + random.nextInt(4) : 1 + random.nextInt(4);
            long formationSalt = salt ^ (long) formation * -7046029254386353131L;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double irregularRadius = radius
                        + (SiftTerrain.hash01(center.x() + dx, waterY, center.z() + dz, formationSalt) - 0.5) * 0.85;
                    if (distance > irregularRadius || !water.containsKey(new Xz(center.x() + dx, center.z() + dz))) {
                        continue;
                    }
                    int x = center.x() + dx;
                    int z = center.z() + dz;
                    int height = Math.max(0, peak - floor(distance * 1.35));
                    height += SiftTerrain.hash01(x, waterY, z, formationSalt) > 0.78 ? 1 : 0;
                    int bottom = waterY;
                    while (bottom > waterY - 7 && !SiftTerrain.isTerrain(region, x, bottom, z)) {
                        bottom--;
                    }
                    for (int y = bottom; y <= waterY + height; y++) {
                        if (!writable(region, bounds, x, y, z)) {
                            continue;
                        }
                        BlockStateSpec belowTop = y >= waterY - 1 ? palette.basin() : SiftTerrain.SIFTSLATE;
                        BlockStateSpec requestedTop = palette.shore();
                        BlockStateSpec rock = y == waterY + height ? islandSurfaceState(requestedTop, region.state(x, y - 1, z)) : belowTop;
                        region.set(x, y, z, rock);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static LakeColumn findWillowIslandCenter(List<LakeColumn> candidates, Random random) {
        LakeColumn best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int attempt = 0; attempt < 36; attempt++) {
            LakeColumn candidate = candidates.get(random.nextInt(candidates.size()));
            double distance = candidate.normalizedDistance();
            if (distance < 0.2 || distance > 0.46) {
                continue;
            }
            double targetDistance = Math.abs(distance - 0.32);
            if (!(targetDistance < bestDistance)) {
                continue;
            }
            best = candidate;
            bestDistance = targetDistance;
        }
        return best;
    }

    private int placeWillowIsland(WorldRegion region, Random random, Map<Xz, LakeColumn> water, int waterY, LakePalette palette,
                                  long salt, SiftWorldgenBounds bounds, LakeColumn center, int anchorX, int anchorZ) {
        double radiusX = 6.7 + random.nextDouble() * 0.9;
        double radiusZ = 5.5 + random.nextDouble() * 0.9;
        double rotation = random.nextDouble() * Math.PI;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double outlinePhase = random.nextDouble() * Math.PI * 2.0;
        long islandSalt = salt ^ 0x57494C4C4F574953L;
        int limit = (int) Math.ceil(Math.max(radiusX, radiusZ) + 1.0);
        Map<Xz, Integer> surface = new LinkedHashMap<>();
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                Xz cell = new Xz(x, z);
                if (!water.containsKey(cell) || !bounds.contains(x, z)) {
                    continue;
                }
                double localX = dx * cos - dz * sin;
                double localZ = dx * sin + dz * cos;
                double normalized = Math.sqrt(localX * localX / (radiusX * radiusX) + localZ * localZ / (radiusZ * radiusZ));
                double theta = Math.atan2(localZ, localX);
                double edge = 1.0 + Math.sin(theta * 3.0 + outlinePhase) * 0.075 + Math.sin(theta * 5.0 - outlinePhase * 0.63) * 0.045;
                if (normalized > edge) {
                    continue;
                }
                double inward = 1.0 - normalized / edge;
                int height = floor(inward * 3.15);
                surface.put(cell, waterY + clamp(height, 0, 3));
            }
        }
        if (surface.size() < 70) {
            return 0;
        }
        int placed = 0;
        for (Map.Entry<Xz, Integer> entry : surface.entrySet()) {
            Xz cell = entry.getKey();
            int topY = entry.getValue();
            int bottomY = waterY;
            while (bottomY > waterY - 7 && !SiftTerrain.isTerrain(region, cell.x(), bottomY, cell.z())) {
                bottomY--;
            }
            double patch = SiftTerrain.hash01(cell.x() >> 1, topY, cell.z() >> 1, islandSalt);
            BlockStateSpec surfaceState = patch < 0.13 ? SiftTerrain.DRY_HEALTHY_SCULK
                : patch < 0.38 ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.SIFTSLATE_GROWTH;
            for (int y = bottomY; y <= topY; y++) {
                if (!writable(region, bounds, cell.x(), y, cell.z())) {
                    continue;
                }
                BlockStateSpec state;
                if (y == topY) {
                    state = islandSurfaceState(surfaceState, region.state(cell.x(), y - 1, cell.z()));
                } else if (y >= waterY && patch < 0.38) {
                    state = patch < 0.13 ? SiftTerrain.DRY_HEALTHY_SCULK : SiftTerrain.HEALTHY_SCULK;
                } else {
                    state = y >= topY - 2 ? palette.basin() : SiftTerrain.SIFTSLATE;
                }
                region.set(cell.x(), y, cell.z(), state);
                placed++;
            }
            if (!writable(region, bounds, cell.x(), topY + 1, cell.z()) || !canClear(region, cell.x(), topY + 1, cell.z())) {
                continue;
            }
            region.set(cell.x(), topY + 1, cell.z(), SiftTerrain.AIR);
        }
        Xz rootCell = new Xz(center.x(), center.z());
        Integer rootTop = surface.get(rootCell);
        if (rootTop == null) {
            rootCell = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Xz cell : surface.keySet()) {
                int dx = cell.x() - center.x();
                int dz = cell.z() - center.z();
                int distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    rootCell = cell;
                }
            }
            rootTop = rootCell == null ? null : surface.get(rootCell);
        }
        if (rootCell != null && rootTop != null) {
            willow.placeAt(region, random, anchorX, anchorZ, rootCell.x(), rootTop + 1, rootCell.z(), false);
        }
        for (Map.Entry<Xz, Integer> entry : surface.entrySet()) {
            Xz cell = entry.getKey();
            SiftSurfaceDecorator.decorate(region, random, new Pos(cell.x(), entry.getValue(), cell.z()), 2.85F);
        }
        return placed;
    }

    private static BlockStateSpec islandSurfaceState(BlockStateSpec requestedTop, BlockStateSpec below) {
        if (requestedTop.equals(SiftTerrain.SIFTSLATE_GROWTH) && below.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
            return SiftTerrain.DRY_HEALTHY_SCULK_GROWTH;
        }
        return requestedTop;
    }

    private static CascadeSite findCascadeSite(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ) {
        CascadeSite best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI * 2.0 / 16.0;
            double forwardX = Math.cos(angle);
            double forwardZ = Math.sin(angle);
            int upperX = centerX - (int) Math.round(forwardX * 7.0);
            int upperZ = centerZ - (int) Math.round(forwardZ * 7.0);
            int lowerX = centerX + (int) Math.round(forwardX * 9.0);
            int lowerZ = centerZ + (int) Math.round(forwardZ * 9.0);
            if (!bounds.contains(upperX, upperZ) || !bounds.contains(lowerX, lowerZ)) {
                continue;
            }
            int upperSurface = SiftTerrain.findTerrainSurface(region, upperX, upperZ);
            int lowerSurface = SiftTerrain.findTerrainSurface(region, lowerX, lowerZ);
            if (upperSurface < 0 || lowerSurface < 0) {
                continue;
            }
            int drop = upperSurface - lowerSurface;
            if (drop < 10 || drop > 34) {
                continue;
            }
            int upperRelief = sampleRelief(region, bounds, upperX, upperZ, 4);
            int lowerRelief = sampleRelief(region, bounds, lowerX, lowerZ, 7);
            if (upperRelief < 0 || lowerRelief < 0 || upperRelief > 4 || lowerRelief > 5) {
                continue;
            }
            int minX = Math.min(upperX - 8, lowerX - 12);
            int maxX = Math.max(upperX + 8, lowerX + 12);
            int minZ = Math.min(upperZ - 8, lowerZ - 12);
            int maxZ = Math.max(upperZ + 8, lowerZ + 12);
            if (!bounds.contains(minX, minZ) || !bounds.contains(maxX, maxZ)) {
                continue;
            }
            int score = drop * 8 - upperRelief * 3 - lowerRelief * 2;
            if (score <= bestScore) {
                continue;
            }
            bestScore = score;
            best = new CascadeSite(new Pos(upperX, upperSurface - 1, upperZ), new Pos(lowerX, lowerSurface - 1, lowerZ),
                forwardX, forwardZ);
        }
        return best;
    }

    private int placeCascade(WorldRegion region, Random random, SiftWorldgenBounds bounds, int anchorX, int anchorZ,
                             CascadeSite site, LakePalette palette) {
        long salt = random.nextLong();
        int lowerRadiusX = 10 + random.nextInt(2);
        int lowerRadiusZ = 9 + random.nextInt(2);
        int beachWidth = 4;
        double angle = Math.atan2(site.forwardZ(), site.forwardX());
        Map<Xz, LakeColumn> lowerWater = buildWaterMask(region, bounds, site.lowerCenter().x(), site.lowerCenter().z(),
            lowerRadiusX, lowerRadiusZ, angle, Math.cos(angle), Math.sin(angle), salt);
        if (lowerWater.size() < 135) {
            return 0;
        }
        List<Integer> lowerSurfaces = new ArrayList<>(lowerWater.size());
        for (LakeColumn column : lowerWater.values()) {
            lowerSurfaces.add(column.surfaceY());
        }
        lowerSurfaces.sort(Comparator.naturalOrder());
        int lowerWaterY = lowerSurfaces.get(lowerSurfaces.size() / 2) - 1;
        if (lowerSurfaces.get(lowerSurfaces.size() - 1) - lowerSurfaces.get(0) > 5) {
            return 0;
        }
        Map<Xz, Integer> lowerBeach = buildBeachMask(lowerWater, beachWidth);
        Map<Xz, LakeColumn> lowerBeachColumns = validateBeach(region, bounds, lowerBeach, beachWidth, lowerWaterY, 5);
        if (lowerBeachColumns.isEmpty()) {
            return 0;
        }
        Map<Xz, Integer> lowerShoreTargets = buildShoreTargets(region, lowerWater, lowerBeach, lowerBeachColumns, lowerWaterY, beachWidth);
        if (lowerShoreTargets.isEmpty()) {
            return 0;
        }
        Map<Xz, ShelfColumn> shelf = buildShelfMask(region, bounds, site, salt);
        if (shelf.size() < 155) {
            return 0;
        }
        Map<Xz, Double> upperPond = buildUpperPondMask(site, shelf, salt);
        if (upperPond.size() < 52) {
            return 0;
        }
        int placed = 0;
        for (Map.Entry<Xz, LakeColumn> entry : lowerBeachColumns.entrySet()) {
            placed += shapeBeach(region, entry.getValue(), lowerShoreTargets.get(entry.getKey()), palette, bounds);
        }
        for (LakeColumn column : lowerWater.values()) {
            placed += carveAndFill(region, column, lowerWaterY, palette.basin(), salt ^ 0x4C4F574552504F4EL, 5, bounds);
        }
        placed += shapeMountainShelf(region, shelf, site.upperCenter().y(), palette, bounds);
        placed += carveUpperPond(region, upperPond, site.upperCenter().y(), palette.basin(), salt, bounds);
        placed += carveCascadeChannel(region, site, lowerWaterY, palette.basin(), bounds);
        if (random.nextFloat() < 0.88F) {
            placed += placeRockFormations(region, random, lowerWater, lowerWaterY, palette, salt ^ 0x434153434144454CL, bounds,
                anchorX, anchorZ);
        }
        decorateLakeShore(region, random, lowerWater, lowerShoreTargets, bounds);
        return placed;
    }

    private static Map<Xz, ShelfColumn> buildShelfMask(WorldRegion region, SiftWorldgenBounds bounds, CascadeSite site, long salt) {
        Map<Xz, ShelfColumn> shelf = new LinkedHashMap<>();
        int centerX = site.upperCenter().x();
        int centerZ = site.upperCenter().z();
        int topY = site.upperCenter().y();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double forward = dx * site.forwardX() + dz * site.forwardZ();
                double forwardRadius = forward >= 0.0 ? 8.5 : 8.8;
                double side = (double) (-dx) * site.forwardZ() + dz * site.forwardX();
                double distance = Math.sqrt(forward * forward / (forwardRadius * forwardRadius) + side * side / 77.44000000000001);
                double edge = 1.0 + (SiftTerrain.hash01(centerX + dx >> 1, topY, centerZ + dz >> 1, salt) - 0.5) * 0.13;
                if (distance > edge) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!bounds.contains(x, z)) {
                    return Map.of();
                }
                int surfaceY = SiftTerrain.findTerrainSurface(region, x, z);
                if (surfaceY < 0 || !hasNaturalSupport(region, x, surfaceY - 1, z, 3)) {
                    return Map.of();
                }
                int naturalTop = surfaceY - 1;
                if (naturalTop > topY + 5 || topY - naturalTop > 26) {
                    return Map.of();
                }
                shelf.put(new Xz(x, z), new ShelfColumn(x, z, naturalTop, distance, forward, side));
            }
        }
        return shelf;
    }

    private static Map<Xz, Double> buildUpperPondMask(CascadeSite site, Map<Xz, ShelfColumn> shelf, long salt) {
        Map<Xz, Double> pond = new LinkedHashMap<>();
        double centerX = site.upperCenter().x() - site.forwardX() * 1.8;
        double centerZ = site.upperCenter().z() - site.forwardZ() * 1.8;
        for (ShelfColumn column : shelf.values()) {
            double dx = column.x() - centerX;
            double dz = column.z() - centerZ;
            double forward = dx * site.forwardX() + dz * site.forwardZ();
            double side = -dx * site.forwardZ() + dz * site.forwardX();
            double distance = Math.sqrt(forward * forward / 32.49 + side * side / 27.040000000000003);
            double edge = 1.0 + (SiftTerrain.hash01(column.x(), site.upperCenter().y(), column.z(), salt ^ 0x5550504552504F4EL) - 0.5) * 0.12;
            if (!(distance <= edge)) {
                continue;
            }
            pond.put(new Xz(column.x(), column.z()), distance / edge);
        }
        return pond;
    }

    private static int shapeMountainShelf(WorldRegion region, Map<Xz, ShelfColumn> shelf, int topY, LakePalette palette,
                                          SiftWorldgenBounds bounds) {
        int placed = 0;
        for (ShelfColumn column : shelf.values()) {
            int x = column.x();
            int z = column.z();
            double edgeStrength = smoothstep(1.0, 0.7, column.normalizedDistance());
            boolean channelCore = column.forward() >= -3.0 && column.forward() <= 7.6 && Math.abs(column.side()) <= 3.8;
            double strength = channelCore ? 1.0 : edgeStrength;
            int targetTop = floor(lerp(strength, column.naturalTop(), topY) + 0.5);
            for (int y = targetTop + 1; y <= Math.max(column.naturalTop() + 3, targetTop + 3); y++) {
                if (!writable(region, bounds, x, y, z) || !canClearDry(region, x, y, z)) {
                    continue;
                }
                region.set(x, y, z, SiftTerrain.AIR);
            }
            int bottom = Math.min(column.naturalTop(), targetTop) - 2;
            for (int y = bottom; y <= targetTop; y++) {
                if (!writable(region, bounds, x, y, z)) {
                    continue;
                }
                BlockStateSpec state = y == targetTop ? palette.shore() : y <= targetTop - 2 ? SiftTerrain.SIFTSLATE : palette.basin();
                region.set(x, y, z, state);
                placed++;
            }
        }
        return placed;
    }

    private static int carveUpperPond(WorldRegion region, Map<Xz, Double> pond, int waterY, BlockStateSpec lining, long salt,
                                      SiftWorldgenBounds bounds) {
        int placed = 0;
        for (Map.Entry<Xz, Double> entry : pond.entrySet()) {
            Xz cell = entry.getKey();
            double centerStrength = 1.0 - clamp(entry.getValue(), 0.0, 1.0);
            int depth = clamp(1 + floor(centerStrength * 2.8 + SiftTerrain.hash01(cell.x(), waterY, cell.z(), salt) * 0.55), 1, 4);
            int floorY = waterY - depth;
            for (int y = floorY - 1; y <= floorY; y++) {
                if (!writable(region, bounds, cell.x(), y, cell.z())) {
                    continue;
                }
                region.set(cell.x(), y, cell.z(), lining);
                placed++;
            }
            for (int y = floorY + 1; y <= waterY; y++) {
                if (!writable(region, bounds, cell.x(), y, cell.z())) {
                    continue;
                }
                region.set(cell.x(), y, cell.z(), SiftTerrain.ICHOR);
                placed++;
            }
            if (!writable(region, bounds, cell.x(), waterY + 1, cell.z())) {
                continue;
            }
            region.set(cell.x(), waterY + 1, cell.z(), SiftTerrain.AIR);
        }
        return placed;
    }

    private static int carveCascadeChannel(WorldRegion region, CascadeSite site, int lowerWaterY, BlockStateSpec lining,
                                           SiftWorldgenBounds bounds) {
        int placed = 0;
        int upperWaterY = site.upperCenter().y();
        double sideX = -site.forwardZ();
        double sideZ = site.forwardX();
        int lipX = site.upperCenter().x() + (int) Math.round(site.forwardX() * 7.0);
        int lipZ = site.upperCenter().z() + (int) Math.round(site.forwardZ() * 7.0);
        for (int step = 1; step <= 7; step++) {
            int centerX = site.upperCenter().x() + (int) Math.round(site.forwardX() * (double) step);
            int centerZ = site.upperCenter().z() + (int) Math.round(site.forwardZ() * (double) step);
            int halfWidth = step < 3 ? 2 : 3;
            for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                int x = centerX + (int) Math.round(sideX * (double) lateral);
                int z = centerZ + (int) Math.round(sideZ * (double) lateral);
                placed += placeChannelCell(region, x, upperWaterY, z, lining, bounds);
            }
        }
        for (int lateral = -3; lateral <= 3; lateral++) {
            int x = lipX + (int) Math.round(sideX * (double) lateral);
            int z = lipZ + (int) Math.round(sideZ * (double) lateral);
            for (int y = lowerWaterY; y <= upperWaterY; y++) {
                if (!writable(region, bounds, x, y, z)) {
                    continue;
                }
                region.set(x, y, z, y == upperWaterY ? SiftTerrain.ICHOR : FALLING_ICHOR);
                placed++;
            }
        }
        double dx = site.lowerCenter().x() - lipX;
        double dz = site.lowerCenter().z() - lipZ;
        int length = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
        for (int step = 0; step <= length; step++) {
            double progress = (double) step / (double) length;
            int x = floor(lerp(progress, lipX, site.lowerCenter().x()) + 0.5);
            int z = floor(lerp(progress, lipZ, site.lowerCenter().z()) + 0.5);
            placed += placeChannelCell(region, x, lowerWaterY, z, lining, bounds);
        }
        return placed;
    }

    private static int placeChannelCell(WorldRegion region, int x, int y, int z, BlockStateSpec lining, SiftWorldgenBounds bounds) {
        if (!writable(region, bounds, x, y, z)) {
            return 0;
        }
        if (writable(region, bounds, x, y - 1, z)) {
            region.set(x, y - 1, z, lining);
        }
        region.set(x, y, z, SiftTerrain.ICHOR);
        if (writable(region, bounds, x, y + 1, z)) {
            region.set(x, y + 1, z, SiftTerrain.AIR);
        }
        return 1;
    }

    private static boolean hasNearbyIchor(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ, int radius) {
        for (int dx = -radius; dx <= radius; dx += 5) {
            for (int dz = -radius; dz <= radius; dz += 5) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (dx * dx + dz * dz > radius * radius || !bounds.contains(x, z)) {
                    continue;
                }
                int surface = SiftTerrain.findTerrainSurface(region, x, z);
                if (surface < 0) {
                    continue;
                }
                for (int dy = -6; dy <= 1; dy++) {
                    if (isIchor(region, x, surface + dy, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasNaturalSupport(WorldRegion region, int x, int y, int z, int depth) {
        for (int offset = 0; offset < depth; offset++) {
            if (!SiftTerrain.isTerrain(region, x, y - offset, z)) {
                return false;
            }
        }
        return true;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private record CascadeSite(Pos upperCenter, Pos lowerCenter, double forwardX, double forwardZ) {
    }

    private record LakePalette(BlockStateSpec basin, BlockStateSpec shore) {
    }

    private enum LakeVariant {
        SMALL(4, 7, 1, 2, 3, 4, 28, false),
        MEDIUM(8, 12, 2, 3, 4, 4, 90, false),
        LARGE(12, 15, 4, 5, 5, 5, 300, true),
        MEGA(14, 14, 6, 6, 6, 4, 450, true);

        private final int minimumRadius;
        private final int maximumRadius;
        private final int minimumBeach;
        private final int maximumBeach;
        private final int maximumDepth;
        private final int maximumRelief;
        private final int minimumArea;
        private final boolean rocks;

        LakeVariant(int minimumRadius, int maximumRadius, int minimumBeach, int maximumBeach, int maximumDepth,
                    int maximumRelief, int minimumArea, boolean rocks) {
            this.minimumRadius = minimumRadius;
            this.maximumRadius = maximumRadius;
            this.minimumBeach = minimumBeach;
            this.maximumBeach = maximumBeach;
            this.maximumDepth = maximumDepth;
            this.maximumRelief = maximumRelief;
            this.minimumArea = minimumArea;
            this.rocks = rocks;
        }
    }

    private record LakeColumn(int x, int z, int surfaceY, double normalizedDistance) {
    }

    private record Xz(int x, int z) {
    }

    private record ShelfColumn(int x, int z, int naturalTop, double normalizedDistance, double forward, double side) {
    }
}
