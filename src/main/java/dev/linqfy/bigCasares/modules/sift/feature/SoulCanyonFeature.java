package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SoulCanyonFeature}: a landmark in one seed-selected chunk
 * per 12x12 cell that carves a winding, diagonal canyon (45-47 blocks long, about 9
 * wide, up to 18-20 deep) with a ramp-like walkable floor, and fills the deepest
 * reachable part of it with veins and pockets of Soul Blocks. Planning tries up to
 * eight orientations/phases and gives up if the terrain is not Sift ground, the
 * route would not be walkable, a block cannot be replaced or fewer than eight soul
 * blocks would fit.
 *
 * <p>Differences: the mod's global reservations are not ported because they depend
 * on generation order and would make output non-deterministic: the "no canyon near
 * a portal" check ({@code SiftFeaturePlacementGuard.intersectsPortal}), the carver
 * reservation, and the {@code SiftLandmarkTracker} records of the entrance, midpoint,
 * soul cluster and pending echo golem. Ground and replaceable blocks are matched
 * through the stand-in rules of {@link SoulCanyonTerrain}.</p>
 */
final class SoulCanyonFeature implements SiftFeature {

    private static final int BASE_RADIUS = 4;
    private static final int ATTEMPTS = 8;
    private static final int MINIMUM_SOUL_BLOCKS = 8;
    private static final double DIAGONAL = 0.7071067811865476;

    private record CanyonPlan(Map<Long, CanyonColumn> columns, Pos entrance, Pos midpoint, int length, int baseRadius) {
    }

    private record CanyonColumn(int x, int z, int surfaceY, int floorY, int depth, double progress, int alongStep,
                                int sideDistance, double signedSide) {
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        if (!SiftLandmarkPlacement.isSelectedChunk(region.seed(), x, z, SiftLandmarkPlacement.SOUL_CANYON_CELL_CHUNKS,
            SiftLandmarkPlacement.SOUL_CANYON_SALT, SiftLandmarkPlacement.SOUL_CANYON_CANDIDATES)) {
            return false;
        }
        int centerX = (x & ~15) + 8;
        int centerZ = (z & ~15) + 8;
        SiftWorldgenBounds bounds = SiftWorldgenBounds.around(x, z);
        int length = 45 + random.nextInt(3);
        int maximumDepth = 18 + random.nextInt(3);
        double phase = random.nextDouble() * Math.PI * 2.0;
        int firstOrientation = random.nextInt(4);

        CanyonPlan plan = null;
        List<Pos> soulBlocks = null;
        for (int attempt = 0; attempt < ATTEMPTS && plan == null; attempt++) {
            double candidatePhase = phase + (double) (attempt / 4) * 1.941611038725466;
            int orientation = (firstOrientation + attempt) & 3;
            CanyonPlan candidate = buildPlan(region, bounds, centerX, centerZ, length, BASE_RADIUS, maximumDepth,
                candidatePhase, orientation);
            if (candidate == null || !canCarve(region, candidate.columns().values())) {
                continue;
            }
            List<Pos> candidateSoulBlocks = selectSoulVeins(candidate, candidatePhase, maximumDepth);
            if (candidateSoulBlocks.size() < MINIMUM_SOUL_BLOCKS) {
                continue;
            }
            plan = candidate;
            soulBlocks = candidateSoulBlocks;
        }
        if (plan == null) {
            return false;
        }
        for (CanyonColumn column : plan.columns().values()) {
            for (int cy = column.floorY() - 2; cy <= column.floorY(); cy++) {
                BlockStateSpec state = region.state(column.x(), cy, column.z());
                if (SoulCanyonTerrain.isGround(state) && !state.equals(SiftTerrain.SIFT_RUBBLE)) {
                    continue;
                }
                region.set(column.x(), cy, column.z(), SiftTerrain.SIFTSLATE);
            }
            for (int cy = column.floorY() + 1; cy <= Math.max(column.surfaceY() + 3, column.floorY() + 3); cy++) {
                region.set(column.x(), cy, column.z(), SiftTerrain.AIR);
            }
        }
        for (Pos soul : soulBlocks) {
            region.set(soul.x(), soul.y(), soul.z(), SiftTerrain.SOUL_BLOCK);
        }
        return true;
    }

    private static CanyonPlan buildPlan(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ,
                                        int length, int baseRadius, int maximumDepth, double phase, int orientation) {
        double directionX = (orientation & 1) == 0 ? DIAGONAL : -DIAGONAL;
        double directionZ = (orientation & 2) == 0 ? DIAGONAL : -DIAGONAL;
        double sideX = -directionZ;
        double sideZ = directionX;
        int halfLength = length / 2;
        Map<Long, CanyonColumn> columns = new HashMap<>();
        Set<Long> routeColumns = new HashSet<>();
        Pos entrance = null;
        Pos midpoint = null;
        int previousCenterFloor = Integer.MIN_VALUE;
        int previousCenterSurface = Integer.MIN_VALUE;
        for (int step = 0; step < length; step++) {
            int offset = step - halfLength;
            double meander = Math.sin(phase + (double) step * 0.22) * 1.65
                + Math.sin(phase * 1.73 + (double) step * 0.071) * 0.7;
            int radius = Math.max(3, baseRadius + (int) Math.round(Math.sin(phase * 0.63 + (double) step * 0.39)));
            double exactCenterX = (double) centerX + directionX * (double) offset + sideX * meander;
            double exactCenterZ = (double) centerZ + directionZ * (double) offset + sideZ * meander;
            int centerXAtStep = (int) Math.round(exactCenterX);
            int centerZAtStep = (int) Math.round(exactCenterZ);
            int centerSurface = SoulCanyonTerrain.surfaceBlockY(region, centerXAtStep, centerZAtStep);
            if (centerSurface == SoulCanyonTerrain.NO_SURFACE) {
                return null;
            }
            if (previousCenterSurface != Integer.MIN_VALUE && Math.abs(centerSurface - previousCenterSurface) > 8) {
                return null;
            }
            previousCenterSurface = centerSurface;
            double progress = (double) step / (double) Math.max(1, length - 1);
            int rampDepth = Math.min(maximumDepth, (int) Math.round((double) step * 0.6));
            double farTaper = smootherStep(clamp01((1.0 - progress) / 0.34));
            int centerFloorDepth = (int) Math.round((double) rampDepth * farTaper);
            int desiredCenterFloor = centerSurface - centerFloorDepth;
            int intendedCenterFloor = previousCenterFloor == Integer.MIN_VALUE
                ? desiredCenterFloor
                : Math.max(previousCenterFloor - 1, Math.min(previousCenterFloor + 1, desiredCenterFloor));
            if (centerSurface + 2 < intendedCenterFloor) {
                return null;
            }
            if (centerSurface - intendedCenterFloor > maximumDepth + 14) {
                return null;
            }
            previousCenterFloor = intendedCenterFloor;
            Pos routeFeet = new Pos(centerXAtStep, intendedCenterFloor + 1, centerZAtStep);
            if (step == 0) {
                entrance = routeFeet;
            }
            if (step == Math.round((float) (length - 1) * 0.38F)) {
                midpoint = routeFeet;
            }
            int rasterRadius = radius + 2;
            int minX = (int) Math.floor(exactCenterX) - rasterRadius;
            int maxX = (int) Math.ceil(exactCenterX) + rasterRadius;
            int minZ = (int) Math.floor(exactCenterZ) - rasterRadius;
            int maxZ = (int) Math.ceil(exactCenterZ) + rasterRadius;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double relativeX = (double) x - exactCenterX;
                    double relativeZ = (double) z - exactCenterZ;
                    double alongDistance = Math.abs(relativeX * directionX + relativeZ * directionZ);
                    double signedSide = relativeX * sideX + relativeZ * sideZ;
                    double sideDistance = Math.abs(signedSide);
                    if (alongDistance > 0.82 || sideDistance > (double) radius + 0.35) {
                        continue;
                    }
                    if (!bounds.contains(x, z)) {
                        return null;
                    }
                    double sideRatio = sideDistance / ((double) radius + 0.35);
                    double arch = Math.sqrt(Math.max(0.0, 1.0 - sideRatio * sideRatio));
                    int surfaceY = SoulCanyonTerrain.surfaceBlockY(region, x, z);
                    if (surfaceY == SoulCanyonTerrain.NO_SURFACE) {
                        return null;
                    }
                    boolean routeCorridor = sideDistance <= 2.25;
                    if (routeCorridor && (surfaceY + 2 < intendedCenterFloor || Math.abs(surfaceY - centerSurface) > 9)) {
                        return null;
                    }
                    int centerDepth = surfaceY - intendedCenterFloor;
                    int depth = Math.max(1, (int) Math.round((double) centerDepth * arch));
                    if (sideDistance > (double) radius - 0.35 && hashUnit(x, z, maximumDepth) < 0.18) {
                        continue;
                    }
                    int floorY = routeCorridor ? intendedCenterFloor : surfaceY - depth;
                    if (floorY <= region.minY() + 3) {
                        return null;
                    }
                    CanyonColumn column = new CanyonColumn(x, z, surfaceY, floorY, surfaceY - floorY,
                        (double) step / (double) Math.max(1, length - 1), step, (int) Math.round(sideDistance), signedSide);
                    long key = key(x, z);
                    if (routeCorridor) {
                        routeColumns.add(key);
                    }
                    CanyonColumn previous = columns.get(key);
                    if (previous != null && column.floorY() >= previous.floorY()) {
                        continue;
                    }
                    columns.put(key, column);
                }
            }
        }
        if (!smoothRoute(columns, routeColumns)) {
            return null;
        }
        Pos verifiedEntrance = routeFeet(columns, entrance);
        Pos verifiedMidpoint = routeFeet(columns, midpoint);
        return verifiedEntrance == null || verifiedMidpoint == null
            ? null
            : new CanyonPlan(columns, verifiedEntrance, verifiedMidpoint, length, baseRadius);
    }

    /**
     * Raises route floors so neighbouring route columns differ by at most one block;
     * the result is the same fixpoint whatever the visiting order.
     */
    private static boolean smoothRoute(Map<Long, CanyonColumn> columns, Set<Long> route) {
        ArrayDeque<Long> queue = new ArrayDeque<>(route);
        while (!queue.isEmpty()) {
            CanyonColumn current = columns.get(queue.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long key = key(current.x() + dx, current.z() + dz);
                    if (dx == 0 && dz == 0 || !route.contains(key)) {
                        continue;
                    }
                    CanyonColumn next = columns.get(key);
                    int floor = current.floorY() - 1;
                    if (next.floorY() >= floor) {
                        continue;
                    }
                    if (floor > next.surfaceY() + 2) {
                        return false;
                    }
                    columns.put(key, new CanyonColumn(next.x(), next.z(), next.surfaceY(), floor, next.surfaceY() - floor,
                        next.progress(), next.alongStep(), next.sideDistance(), next.signedSide()));
                    queue.addLast(key);
                }
            }
        }
        return true;
    }

    private static Pos routeFeet(Map<Long, CanyonColumn> columns, Pos marker) {
        if (marker == null) {
            return null;
        }
        CanyonColumn column = columns.get(key(marker.x(), marker.z()));
        return column == null ? null : new Pos(marker.x(), column.floorY() + 1, marker.z());
    }

    private static List<Pos> selectSoulVeins(CanyonPlan plan, double phase, int maximumDepth) {
        List<Pos> result = new ArrayList<>();
        Set<Long> reachableFloor = reachableFloorFromRamp(plan);
        if (!reachableFloor.contains(key(plan.midpoint().x(), plan.midpoint().z()))) {
            return result;
        }
        for (CanyonColumn column : plan.columns().values()) {
            if ((double) column.depth() < (double) maximumDepth * 0.5
                || column.floorY() > plan.entrance().y() - 5
                || column.progress() < 0.53
                || column.progress() > 0.86
                || column.sideDistance() > plan.baseRadius()
                || !reachableFloor.contains(key(column.x(), column.z()))
                || !hasGolemSizedApproach(plan, column)) {
                continue;
            }
            double along = column.alongStep();
            double veinCenter = Math.sin(phase + along * 0.31) * 1.15 + Math.sin(phase * 0.71 + along * 0.13) * 0.48;
            double width = 0.82 + (Math.sin(phase * 1.37 + along * 0.43) + 1.0) * 0.24;
            double edgeRatio = Math.abs(column.signedSide() - veinCenter) / width;
            double grain = hashUnit(column.x(), column.z(), maximumDepth * 31);
            boolean windingVein = edgeRatio <= 1.0 && (edgeRatio < 0.48 || grain > 0.2 + edgeRatio * 0.42);
            double pocketOne = square((column.progress() - 0.63) / 0.055)
                + square((column.signedSide() + 1.65 + Math.sin(phase) * 0.55) / 1.55);
            double pocketTwo = square((column.progress() - 0.77) / 0.048)
                + square((column.signedSide() - 1.35 - Math.cos(phase) * 0.45) / 1.35);
            boolean pocket = Math.min(pocketOne, pocketTwo) <= 1.0 && grain > 0.16;
            if (!windingVein && !pocket) {
                continue;
            }
            result.add(new Pos(column.x(), column.floorY(), column.z()));
        }
        return result;
    }

    /** Floor columns a walker can reach from the entrance ramp over 2x2 pads whose floors differ by at most one. */
    private static Set<Long> reachableFloorFromRamp(CanyonPlan plan) {
        Set<Long> visited = new HashSet<>();
        Set<Long> reachable = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        for (int x = plan.entrance().x() - 1; x <= plan.entrance().x(); x++) {
            for (int z = plan.entrance().z() - 1; z <= plan.entrance().z(); z++) {
                int floor = walkablePadFloor(plan, x, z);
                if (floor == Integer.MIN_VALUE) {
                    continue;
                }
                visited.add(key(x, z));
                queue.add(new Pos(x, floor, z));
            }
        }
        int[][] horizontal = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            Pos current = queue.removeFirst();
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    reachable.add(key(current.x() + dx, current.z() + dz));
                }
            }
            for (int[] direction : horizontal) {
                int x = current.x() + direction[0];
                int z = current.z() + direction[1];
                long key = key(x, z);
                if (visited.contains(key)) {
                    continue;
                }
                int floor = walkablePadFloor(plan, x, z);
                if (floor == Integer.MIN_VALUE || Math.abs(floor - current.y()) > 1) {
                    continue;
                }
                visited.add(key);
                queue.addLast(new Pos(x, floor, z));
            }
        }
        return reachable;
    }

    private static int walkablePadFloor(CanyonPlan plan, int x, int z) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                CanyonColumn column = plan.columns().get(key(x + dx, z + dz));
                if (column == null) {
                    return Integer.MIN_VALUE;
                }
                lowest = Math.min(lowest, column.floorY());
                highest = Math.max(highest, column.floorY());
            }
        }
        return highest - lowest <= 1 ? highest : Integer.MIN_VALUE;
    }

    /** Whether a 2x2 pad containing the target has floors at its height or one lower, plus a neighbouring column. */
    private static boolean hasGolemSizedApproach(CanyonPlan plan, CanyonColumn target) {
        for (int startX : new int[]{target.x() - 1, target.x()}) {
            for (int startZ : new int[]{target.z() - 1, target.z()}) {
                if (isApproachPad(plan, target, startX, startZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isApproachPad(CanyonPlan plan, CanyonColumn target, int startX, int startZ) {
        boolean hasStep = false;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                CanyonColumn column = plan.columns().get(key(startX + dx, startZ + dz));
                if (column == null || column.floorY() > target.floorY() || column.floorY() < target.floorY() - 1) {
                    return false;
                }
                if (column.x() != target.x() || column.z() != target.z()) {
                    hasStep = true;
                }
            }
        }
        return hasStep;
    }

    private static boolean canCarve(WorldRegion region, Iterable<CanyonColumn> columns) {
        for (CanyonColumn column : columns) {
            for (int y = column.floorY() - 2; y <= Math.max(column.surfaceY() + 3, column.floorY() + 3); y++) {
                if (!region.isWritable(column.x(), y, column.z()) || !SoulCanyonTerrain.canReplace(region, column.x(), y, column.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code BlockPos.asLong(x, 0, z)}: the mod's column key, kept so map iteration order matches. */
    private static long key(int x, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double smootherStep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double hashUnit(int x, int z, int salt) {
        long value = (long) x * -7046029254386353131L ^ (long) z * -4417276706812531889L ^ (long) salt * -7723592293110705685L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        return (double) ((value ^ value >>> 31) >>> 11) * (double) 1.110223E-16F;
    }
}
