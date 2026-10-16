package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SiftArchFeature} ({@code the_sift:cliff_arch}): a
 * natural stone bridge spanning a valley. The feature tries four axes through the
 * chunk centre, scores each by valley depth and cliff drop, then sweeps a wobbling
 * curve of organic spheres between the two rims, blends both ends into the cliffs,
 * carves gouges into the underside and, for siftslate arches on growth ground, coats
 * the exposed top with siftslate growth before decorating the crown.
 *
 * <p>Differences: the mod's {@code SiftFeaturePlacementGuard.intersectsPortal} check
 * is not ported because it depends on generation order, which would make output
 * non-deterministic. The mod's {@code SiftPackedLongSet} of arch blocks is a plain
 * insertion-ordered set of positions (results do not depend on its iteration order).
 * The mod's {@code level.getMaxY()} is the inclusive top block, i.e.
 * {@code region.maxY() - 1}.</p>
 */
final class SiftArchFeature implements SiftFeature {

    private static final int[][] AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private record Candidate(double axisX, double axisZ, int halfSpan, int leftY, int rightY, int valleyDepth) {

        Pos leftFloor(int centerX, int centerZ) {
            return new Pos(centerX - (int) Math.round(axisX * (double) halfSpan), leftY - 1,
                centerZ - (int) Math.round(axisZ * (double) halfSpan));
        }

        Pos rightFloor(int centerX, int centerZ) {
            return new Pos(centerX + (int) Math.round(axisX * (double) halfSpan), rightY - 1,
                centerZ + (int) Math.round(axisZ * (double) halfSpan));
        }
    }

    private record CurvePoint(double x, double y, double z) {
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        SiftWorldgenBounds bounds = SiftWorldgenBounds.aroundWithNeighbourMargin(x, z);
        int centerX = (x & ~15) + 8 + random.nextInt(3) - 1;
        int centerZ = (z & ~15) + 8 + random.nextInt(3) - 1;
        int centerY = SiftTerrain.findTerrainSurface(region, centerX, centerZ);
        if (centerY < 0) {
            return false;
        }
        int halfSpan = 13 + random.nextInt(3);
        Candidate best = findBestSpan(region, bounds, centerX, centerZ, centerY, halfSpan, random);
        if (best == null) {
            return false;
        }
        int baselineMiddle = (best.leftY() + best.rightY()) / 2;
        int archRise = 7 + random.nextInt(6) + Math.min(5, best.valleyDepth() / 5);
        int topAtMiddle = baselineMiddle + archRise + 5;
        int maxBuildY = region.maxY() - 1;
        if (topAtMiddle >= maxBuildY) {
            archRise -= topAtMiddle - maxBuildY + 1;
        }
        if (archRise < 6) {
            return false;
        }
        Pos leftFloorPos = best.leftFloor(centerX, centerZ);
        Pos rightFloorPos = best.rightFloor(centerX, centerZ);
        BlockStateSpec leftFloor = region.state(leftFloorPos.x(), leftFloorPos.y(), leftFloorPos.z());
        BlockStateSpec rightFloor = region.state(rightFloorPos.x(), rightFloorPos.y(), rightFloorPos.z());
        boolean healthyGround = isHealthy(leftFloor) || isHealthy(rightFloor);
        boolean growthGround = SiftTerrain.isGrowth(leftFloor) || SiftTerrain.isGrowth(rightFloor);
        float healthyChance = healthyGround ? 0.78F : growthGround ? 0.22F : 0.43F;
        boolean healthy = random.nextFloat() < healthyChance;
        BlockStateSpec body = healthy ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.SIFTSLATE;
        boolean growthVariant = !healthy && growthGround;
        double baseThickness = 2.45 + random.nextDouble() * 0.9;
        int span = best.halfSpan() * 2;
        int steps = span * 2;
        long shapeSalt = random.nextLong();
        int blocksPlaced = 0;
        Set<Pos> archBlocks = new LinkedHashSet<>(4096);
        List<Pos> crownPath = new ArrayList<>();
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / (double) steps;
            CurvePoint point = curvePoint(best, centerX, centerZ, t, archRise, shapeSalt);
            double endStrength = Math.pow(Math.abs(t * 2.0 - 1.0), 4.0);
            double thicknessWave = 0.93 + 0.09 * Math.sin(t * Math.PI * 5.0 + phase(shapeSalt, 11))
                + 0.045 * Math.sin(t * Math.PI * 9.0 + phase(shapeSalt, 29));
            double localThickness = baseThickness * thicknessWave + endStrength * 1.15;
            blocksPlaced += fillOrganicSphere(region, bounds, point, localThickness, body, healthy, step, shapeSalt, archBlocks);
            if (step % 5 != 0) {
                continue;
            }
            crownPath.add(new Pos((int) Math.round(point.x()), (int) Math.round(point.y() + localThickness),
                (int) Math.round(point.z())));
        }
        blocksPlaced += blendAnchorIntoTerrain(region, bounds, best, centerX, centerZ, true, baseThickness, body, healthy,
            shapeSalt ^ 0x4C454654L, archBlocks);
        blocksPlaced += blendAnchorIntoTerrain(region, bounds, best, centerX, centerZ, false, baseThickness, body, healthy,
            shapeSalt ^ 0x5249474854L, archBlocks);
        carveGouges(region, random, best, centerX, centerZ, archRise, baseThickness, shapeSalt, archBlocks);
        if (growthVariant) {
            coatExposedCrownWithGrowth(region, bounds, archBlocks);
        }
        for (Pos crownPos : crownPath) {
            SiftSurfaceDecorator.decorate(region, random, crownPos, healthy ? 0.82F : 0.72F);
        }
        return blocksPlaced > 0;
    }

    private static int blendAnchorIntoTerrain(WorldRegion region, SiftWorldgenBounds bounds, Candidate candidate, int centerX,
                                              int centerZ, boolean left, double baseThickness, BlockStateSpec body,
                                              boolean healthy, long salt, Set<Pos> archBlocks) {
        double t = left ? 0.0 : 1.0;
        CurvePoint endpoint = curvePoint(candidate, centerX, centerZ, t, 0, salt);
        double outward = left ? -1.0 : 1.0;
        double perpendicularX = -candidate.axisZ();
        double perpendicularZ = candidate.axisX();
        int previousSurface = left ? candidate.leftY() : candidate.rightY();
        int placed = 0;
        int samples = 5;
        for (int sample = 0; sample < samples; sample++) {
            double progress = ((double) sample + 1.0) / (double) samples;
            double distance = 0.85 + (double) sample * 0.78;
            double sideWave = Math.sin(progress * Math.PI * 2.4 + phase(salt, 13)) * 0.42 * (1.0 - progress);
            double x = endpoint.x() + candidate.axisX() * outward * distance + perpendicularX * sideWave;
            double z = endpoint.z() + candidate.axisZ() * outward * distance + perpendicularZ * sideWave;
            int sampleX = (int) Math.round(x);
            int sampleZ = (int) Math.round(z);
            if (!bounds.contains(sampleX, sampleZ)) {
                break;
            }
            int surface = SiftTerrain.findTerrainSurface(region, sampleX, sampleZ);
            if (surface < 0 || Math.abs(surface - previousSurface) > 9) {
                break;
            }
            previousSurface = surface;
            double eased = smootherstep(progress);
            double centerY = endpoint.y() + ((double) surface - 0.35 - endpoint.y()) * eased;
            double radius = baseThickness + 0.9 - progress * (baseThickness - 0.55)
                + Math.sin(progress * Math.PI * 3.0 + phase(salt, 31)) * 0.16;
            placed += fillOrganicSphere(region, bounds, new CurvePoint(x, centerY, z), Math.max(1.25, radius), body, healthy,
                left ? -sample : sample, salt, archBlocks);
        }
        return placed;
    }

    private static void coatExposedCrownWithGrowth(WorldRegion region, SiftWorldgenBounds bounds, Set<Pos> archBlocks) {
        for (Pos pos : archBlocks) {
            Pos abovePos = pos.above();
            if (bounds.contains(pos.x(), pos.z())
                && region.isWritable(pos.x(), pos.y(), pos.z())
                && !archBlocks.contains(abovePos)
                && isOpenCrownSpace(region, abovePos)
                && region.is(pos.x(), pos.y(), pos.z(), SiftTerrain.SIFTSLATE)) {
                region.set(pos.x(), pos.y(), pos.z(), SiftTerrain.SIFTSLATE_GROWTH);
            }
        }
    }

    private static boolean isOpenCrownSpace(WorldRegion region, Pos pos) {
        return region.isAir(pos.x(), pos.y(), pos.z()) || SiftTerrain.PLANT_TYPES.contains(region.blockId(pos.x(), pos.y(), pos.z()));
    }

    private static Candidate findBestSpan(WorldRegion region, SiftWorldgenBounds bounds, int centerX, int centerZ, int centerY,
                                          int halfSpan, Random random) {
        Candidate best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int[] direction : AXES) {
            double normalization = direction[0] != 0 && direction[1] != 0 ? 0.70710678118 : 1.0;
            double axisX = (double) direction[0] * normalization;
            double axisZ = (double) direction[1] * normalization;
            int leftX = centerX - (int) Math.round(axisX * (double) halfSpan);
            int leftZ = centerZ - (int) Math.round(axisZ * (double) halfSpan);
            int rightX = centerX + (int) Math.round(axisX * (double) halfSpan);
            int rightZ = centerZ + (int) Math.round(axisZ * (double) halfSpan);
            if (!bounds.contains(leftX, leftZ) || !bounds.contains(rightX, rightZ)) {
                continue;
            }
            int leftY = SiftTerrain.findTerrainSurface(region, leftX, leftZ);
            int rightY = SiftTerrain.findTerrainSurface(region, rightX, rightZ);
            if (leftY < 0 || rightY < 0) {
                continue;
            }
            int innerDistance = Math.max(7, halfSpan / 2);
            int leftInnerX = centerX - (int) Math.round(axisX * (double) (halfSpan - innerDistance));
            int leftInnerZ = centerZ - (int) Math.round(axisZ * (double) (halfSpan - innerDistance));
            int rightInnerX = centerX + (int) Math.round(axisX * (double) (halfSpan - innerDistance));
            int rightInnerZ = centerZ + (int) Math.round(axisZ * (double) (halfSpan - innerDistance));
            if (!bounds.contains(leftInnerX, leftInnerZ) || !bounds.contains(rightInnerX, rightInnerZ)) {
                continue;
            }
            int leftInnerY = SiftTerrain.findTerrainSurface(region, leftInnerX, leftInnerZ);
            int rightInnerY = SiftTerrain.findTerrainSurface(region, rightInnerX, rightInnerZ);
            if (leftInnerY < 0 || rightInnerY < 0) {
                continue;
            }
            int heightDifference = Math.abs(leftY - rightY);
            int valleyDepth = Math.min(leftY, rightY) - centerY;
            int leftDrop = leftY - leftInnerY;
            int rightDrop = rightY - rightInnerY;
            if (heightDifference > 20 || valleyDepth < 6 || leftDrop < 3 || rightDrop < 3 || leftDrop + rightDrop < 8) {
                continue;
            }
            // Only candidates that pass every check draw from the random source, like the mod.
            double score = (double) valleyDepth * 5.5 + (double) (leftDrop + rightDrop) * 2.2
                - (double) heightDifference * 0.75 + random.nextDouble();
            if (!(score > bestScore)) {
                continue;
            }
            bestScore = score;
            best = new Candidate(axisX, axisZ, halfSpan, leftY, rightY, valleyDepth);
        }
        return best;
    }

    private static CurvePoint curvePoint(Candidate candidate, int centerX, int centerZ, double t, int archRise, long salt) {
        double signedDistance = (t * 2.0 - 1.0) * (double) candidate.halfSpan();
        double archMask = Math.sin(Math.PI * t);
        double lateralWave = (Math.sin(t * Math.PI * 2.0 + phase(salt, 7)) * 0.68
            + Math.sin(t * Math.PI * 5.0 + phase(salt, 23)) * 0.32) * 1.45 * archMask;
        double verticalWave = (Math.sin(t * Math.PI * 3.0 + phase(salt, 17)) * 0.72
            + Math.sin(t * Math.PI * 7.0 + phase(salt, 37)) * 0.28) * 1.05 * archMask;
        double perpendicularX = -candidate.axisZ();
        double perpendicularZ = candidate.axisX();
        double baseY = (double) candidate.leftY() + (double) (candidate.rightY() - candidate.leftY()) * t;
        return new CurvePoint(
            (double) centerX + candidate.axisX() * signedDistance + perpendicularX * lateralWave,
            baseY + (double) archRise * 4.0 * t * (1.0 - t) + verticalWave,
            (double) centerZ + candidate.axisZ() * signedDistance + perpendicularZ * lateralWave);
    }

    private static int fillOrganicSphere(WorldRegion region, SiftWorldgenBounds bounds, CurvePoint center, double radius,
                                         BlockStateSpec mainState, boolean healthy, int stripeSeed, long salt,
                                         Set<Pos> placedBlocks) {
        int placed = 0;
        int limit = (int) Math.ceil(radius + 0.5);
        int roundedX = (int) Math.round(center.x());
        int roundedY = (int) Math.round(center.y());
        int roundedZ = (int) Math.round(center.z());
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dy = -limit; dy <= limit; dy++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double roughness = (SiftTerrain.hash01(roundedX + dx, roundedY + dy, roundedZ + dz, salt) - 0.5) * 0.62;
                    double edgeRadius = radius + roughness;
                    if ((double) (dx * dx + dy * dy + dz * dz) > edgeRadius * edgeRadius) {
                        continue;
                    }
                    int blockX = roundedX + dx;
                    int blockY = roundedY + dy;
                    int blockZ = roundedZ + dz;
                    if (!bounds.contains(blockX, blockZ) || !region.isWritable(blockX, blockY, blockZ)
                        || !SiftTerrain.canReplace(region, blockX, blockY, blockZ)) {
                        continue;
                    }
                    boolean dryVein = healthy && dy < 0 && stripeSeed / 8 % 7 == 3
                        && SiftTerrain.hash01(blockX, blockY, blockZ, salt ^ 0x9E3779B97F4A7C15L) < 0.58;
                    region.set(blockX, blockY, blockZ, dryVein ? SiftTerrain.DRY_HEALTHY_SCULK : mainState);
                    placedBlocks.add(new Pos(blockX, blockY, blockZ));
                    placed++;
                }
            }
        }
        return placed;
    }

    private static void carveGouges(WorldRegion region, Random random, Candidate candidate, int centerX, int centerZ,
                                    int archRise, double thickness, long salt, Set<Pos> placedBlocks) {
        int gouges = 3 + random.nextInt(5);
        double perpendicularX = -candidate.axisZ();
        double perpendicularZ = candidate.axisX();
        for (int i = 0; i < gouges; i++) {
            double t = 0.16 + random.nextDouble() * 0.68;
            CurvePoint curve = curvePoint(candidate, centerX, centerZ, t, archRise, salt);
            double angle = -1.0995574287564276 + random.nextDouble() * Math.PI * 1.7;
            double surfaceOffset = thickness * (0.68 + random.nextDouble() * 0.22);
            double gougeX = curve.x() + perpendicularX * Math.cos(angle) * surfaceOffset;
            double gougeY = curve.y() + Math.sin(angle) * surfaceOffset;
            double gougeZ = curve.z() + perpendicularZ * Math.cos(angle) * surfaceOffset;
            double radius = 0.82 + random.nextDouble() * 0.78;
            int limit = (int) Math.ceil(radius);
            int roundedX = (int) Math.round(gougeX);
            int roundedY = (int) Math.round(gougeY);
            int roundedZ = (int) Math.round(gougeZ);
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dy = -limit; dy <= limit; dy++) {
                    for (int dz = -limit; dz <= limit; dz++) {
                        if ((double) (dx * dx + dy * dy + dz * dz) > radius * radius) {
                            continue;
                        }
                        int blockX = roundedX + dx;
                        int blockY = roundedY + dy;
                        int blockZ = roundedZ + dz;
                        if (!placedBlocks.remove(new Pos(blockX, blockY, blockZ)) || !region.isWritable(blockX, blockY, blockZ)) {
                            continue;
                        }
                        region.set(blockX, blockY, blockZ, SiftTerrain.AIR);
                    }
                }
            }
        }
    }

    private static boolean isHealthy(BlockStateSpec state) {
        return state.equals(SiftTerrain.HEALTHY_SCULK) || state.equals(SiftTerrain.DRY_HEALTHY_SCULK);
    }

    private static double phase(long salt, int shift) {
        return (double) (salt >>> shift & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
    }

    private static double smootherstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
