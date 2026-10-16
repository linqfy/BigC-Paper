package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.Random;

/**
 * Port of the mod's {@code SiftMonolithFeature} ({@code the_sift:bare_monolith} and
 * {@code the_sift:lush_monolith}): a tall, tapering siftslate pillar with terrace
 * ledges, a leaning crown, a root flare that blends into the ground and a few
 * smaller satellite pillars. The lush variant is wider, taller, has more satellites
 * and caps its pillars with siftslate growth that is planted via the surface
 * decorator.
 *
 * <p>Differences: the mod's {@code SiftFeaturePlacementGuard} reservations (portal
 * avoidance and the global "solid landmark" footprint reservation) are not ported
 * because they depend on generation order, which would make output
 * non-deterministic. The shared static helpers ({@code isSiftTerrain},
 * {@code canReplace}, {@code findTerrainSurface}, {@code isDrySupportedSurface},
 * {@code hash01}) live in {@link SiftTerrain}. The mod's {@code level.getMaxY()} is
 * the inclusive top block, i.e. {@code region.maxY() - 1}.</p>
 */
final class SiftMonolithFeature implements SiftFeature {

    private final boolean lush;

    SiftMonolithFeature(boolean lush) {
        this.lush = lush;
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int centerX = (x & ~15) + 8 + random.nextInt(5) - 2;
        int centerZ = (z & ~15) + 8 + random.nextInt(5) - 2;
        int centerY = SiftTerrain.findTerrainSurface(region, centerX, centerZ);
        if (centerY < 0) {
            return false;
        }
        Pos origin = new Pos(centerX, centerY, centerZ);
        if (!SiftTerrain.isDrySupportedSurface(region, centerX, centerY, centerZ, 6)) {
            return false;
        }
        int radius = lush ? 5 + random.nextInt(3) : 4 + random.nextInt(3);
        if (!hasStableFootprint(region, origin, radius, lush ? 14 : 12)) {
            return false;
        }
        int maxBuildY = region.maxY() - 1;
        int requestedHeight = lush ? 31 + random.nextInt(25) : 24 + random.nextInt(21);
        int height = Math.min(requestedHeight, maxBuildY - origin.y() - 3);
        if (height < 18) {
            return false;
        }
        int placed = placePillar(region, random, origin, radius, height, lush, true);
        int satellites = lush ? 1 + random.nextInt(3) : random.nextInt(2);
        for (int i = 0; i < satellites; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = radius + 4 + random.nextInt(4);
            int satelliteX = origin.x() + (int) Math.round(Math.cos(angle) * (double) distance);
            int satelliteZ = origin.z() + (int) Math.round(Math.sin(angle) * (double) distance);
            int satelliteY = SiftTerrain.findTerrainSurface(region, satelliteX, satelliteZ);
            if (satelliteY < 0) {
                continue;
            }
            Pos satelliteOrigin = new Pos(satelliteX, satelliteY, satelliteZ);
            int satelliteRadius = 2 + random.nextInt(2);
            if (!SiftTerrain.isDrySupportedSurface(region, satelliteX, satelliteY, satelliteZ, 6)
                || !hasStableFootprint(region, satelliteOrigin, satelliteRadius, 10)) {
                continue;
            }
            int satelliteHeight = Math.min(13 + random.nextInt(Math.max(7, height / 2)), maxBuildY - satelliteY - 3);
            if (satelliteHeight < 10) {
                continue;
            }
            // Short-circuit like the mod: the growth roll is only drawn for lush monoliths.
            boolean withGrowth = lush && random.nextFloat() < 0.72F;
            placed += placePillar(region, random, satelliteOrigin, satelliteRadius, satelliteHeight, withGrowth, false);
        }
        return placed > 0;
    }

    private static int placePillar(WorldRegion region, Random random, Pos origin, int baseRadius, int height,
                                   boolean withGrowth, boolean mainPillar) {
        int placed = 0;
        int leanX = random.nextInt(5) - 2;
        int leanZ = random.nextInt(5) - 2;
        int baseY = origin.y() - 2;
        long salt = random.nextLong();
        placed += placeRootFlare(region, origin, baseRadius, salt);
        for (int dy = 0; dy <= height; dy++) {
            double progress = (double) dy / (double) height;
            int centerX = origin.x() + (int) Math.round((double) leanX * progress * progress);
            int centerZ = origin.z() + (int) Math.round((double) leanZ * progress * progress);
            double taper = (double) baseRadius - ((double) baseRadius - 2.25) * progress;
            double ledge = terraceBoost(progress, mainPillar);
            double radius = taper + ledge;
            int limit = (int) Math.ceil(radius + 1.0);
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double edgeNoise = (SiftTerrain.hash01(centerX + dx, baseY + dy, centerZ + dz, salt) - 0.5) * 0.82;
                    double edgeRadius = radius + edgeNoise;
                    if ((double) (dx * dx + dz * dz) > edgeRadius * edgeRadius) {
                        continue;
                    }
                    if (replace(region, centerX + dx, baseY + dy, centerZ + dz, SiftTerrain.SIFTSLATE)) {
                        placed++;
                    }
                }
            }
        }
        int topX = origin.x() + leanX;
        int topZ = origin.z() + leanZ;
        int topY = baseY + height;
        int topRadius = Math.max(2, baseRadius - 2) + (mainPillar ? 1 : 0);
        BlockStateSpec cap = withGrowth ? SiftTerrain.SIFTSLATE_GROWTH : SiftTerrain.SIFTSLATE;
        for (int dx = -topRadius; dx <= topRadius; dx++) {
            for (int dz = -topRadius; dz <= topRadius; dz++) {
                double edgeRadius = (double) topRadius
                    + (SiftTerrain.hash01(topX + dx, topY, topZ + dz, salt ^ 0x6A09E667L) - 0.5) * 0.7;
                if ((double) (dx * dx + dz * dz) > edgeRadius * edgeRadius) {
                    continue;
                }
                int capX = topX + dx;
                int capZ = topZ + dz;
                if (replace(region, capX, topY, capZ, cap)) {
                    placed++;
                }
                if (withGrowth) {
                    SiftSurfaceDecorator.decorate(region, random, new Pos(capX, topY, capZ), 1.08F);
                }
            }
        }
        return placed;
    }

    private static int placeRootFlare(WorldRegion region, Pos origin, int baseRadius, long salt) {
        int placed = 0;
        int rootRadius = baseRadius + 2;
        for (int dx = -rootRadius; dx <= rootRadius; dx++) {
            for (int dz = -rootRadius; dz <= rootRadius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                double irregularEdge = (SiftTerrain.hash01(origin.x() + dx, origin.y(), origin.z() + dz,
                    salt ^ 0xC6BC279692B5CC83L) - 0.5) * 0.9;
                double edgeRadius = (double) rootRadius + irregularEdge;
                if ((double) distanceSquared > edgeRadius * edgeRadius) {
                    continue;
                }
                int blockX = origin.x() + dx;
                int blockZ = origin.z() + dz;
                int localSurface = SiftTerrain.findTerrainSurface(region, blockX, blockZ);
                if (localSurface < 0 || Math.abs(localSurface - origin.y()) > 8) {
                    continue;
                }
                double distance = Math.sqrt(distanceSquared);
                double centerStrength = 1.0 - Math.min(1.0, distance / (double) rootRadius);
                int targetTop = origin.y() + (int) Math.round(centerStrength * 2.0);
                if (localSurface > targetTop + 1) {
                    continue;
                }
                for (int blockY = localSurface - 2; blockY <= targetTop; blockY++) {
                    if (replace(region, blockX, blockY, blockZ, SiftTerrain.SIFTSLATE)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /**
     * The mod's {@code canReplace(state) && ensureCanWrite(pos)} followed by
     * {@code setBlock}: writes only into writable air, Sift terrain or Sift plants.
     */
    static boolean replace(WorldRegion region, int x, int y, int z, BlockStateSpec state) {
        return region.isWritable(x, y, z) && SiftTerrain.canReplace(region, x, y, z) && region.set(x, y, z, state);
    }

    private static boolean hasStableFootprint(WorldRegion region, Pos origin, int radius, int maximumRelief) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int samples = 0;
        int valid = 0;
        int sampleRadius = radius + 2;
        for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
            for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
                if (dx * dx + dz * dz > sampleRadius * sampleRadius) {
                    continue;
                }
                samples++;
                int sampleX = origin.x() + dx;
                int sampleZ = origin.z() + dz;
                int surface = SiftTerrain.findTerrainSurface(region, sampleX, sampleZ);
                if (surface < 0 || !SiftTerrain.isDrySupportedSurface(region, sampleX, surface, sampleZ, 6)) {
                    continue;
                }
                valid++;
                minY = Math.min(minY, surface);
                maxY = Math.max(maxY, surface);
            }
        }
        return (double) valid >= Math.ceil((double) samples * 0.84) && maxY - minY <= maximumRelief;
    }

    private static double terraceBoost(double progress, boolean mainPillar) {
        double boost = 0.0;
        if (Math.abs(progress - 0.28) < 0.035) {
            boost += mainPillar ? 1.35 : 0.75;
        }
        if (Math.abs(progress - 0.58) < 0.03) {
            boost += mainPillar ? 1.05 : 0.55;
        }
        if (Math.abs(progress - 0.81) < 0.025) {
            boost += mainPillar ? 0.75 : 0.35;
        }
        return boost;
    }
}
