package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.Random;

/**
 * Port of the mod's {@code SiftDrySpikeFeature} ({@code the_sift:dry_sculk_spikes}):
 * a cluster of thin, leaning stalagmite-like spikes of dry healthy sculk with one
 * main spike, two to four satellites and a root flare that blends each spike into the
 * ground.
 *
 * <p>Differences: the mod's {@code SiftFeaturePlacementGuard} calls (portal
 * avoidance and the global solid-footprint reservation) are not ported because they
 * depend on generation order, which would make output non-deterministic. The
 * mod's {@code level.getMaxY()} is the inclusive top block, i.e.
 * {@code region.maxY() - 1}.</p>
 */
final class SiftDrySpikeFeature implements SiftFeature {

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int centerX = (x & ~15) + 8 + random.nextInt(5) - 2;
        int centerZ = (z & ~15) + 8 + random.nextInt(5) - 2;
        int centerY = SiftTerrain.findTerrainSurface(region, centerX, centerZ);
        if (centerY < 0) {
            return false;
        }
        Pos center = new Pos(centerX, centerY, centerZ);
        if (!SiftTerrain.isDrySupportedSurface(region, centerX, centerY, centerZ, 6)) {
            return false;
        }
        int mainRadius = 3 + random.nextInt(2);
        if (!hasStableFootprint(region, center, mainRadius + 2)) {
            return false;
        }
        int placed = placeSpike(region, random, center, mainRadius, 19 + random.nextInt(13));
        int satellites = 2 + random.nextInt(3);
        for (int i = 0; i < satellites; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 4 + random.nextInt(6);
            int satelliteX = centerX + (int) Math.round(Math.cos(angle) * (double) distance);
            int satelliteZ = centerZ + (int) Math.round(Math.sin(angle) * (double) distance);
            int satelliteY = SiftTerrain.findTerrainSurface(region, satelliteX, satelliteZ);
            if (satelliteY < 0) {
                continue;
            }
            // Argument order matters: radius is drawn before height, as in the mod.
            int satelliteRadius = 2 + random.nextInt(2);
            int satelliteHeight = 10 + random.nextInt(13);
            placed += placeSpike(region, random, new Pos(satelliteX, satelliteY, satelliteZ), satelliteRadius, satelliteHeight);
        }
        return placed > 0;
    }

    private static int placeSpike(WorldRegion region, Random random, Pos origin, int baseRadius, int requestedHeight) {
        if (!hasStableFootprint(region, origin, baseRadius + 2)) {
            return 0;
        }
        int height = Math.min(requestedHeight, region.maxY() - 1 - origin.y() - 2);
        if (height < 8) {
            return 0;
        }
        int leanX = random.nextInt(7) - 3;
        int leanZ = random.nextInt(7) - 3;
        long salt = random.nextLong();
        int placed = placeRootFlare(region, origin, baseRadius, salt);
        for (int dy = 0; dy <= height; dy++) {
            double progress = Math.max(0.0, (double) dy / (double) height);
            int centerX = origin.x() + (int) Math.round((double) leanX * progress * progress);
            int centerZ = origin.z() + (int) Math.round((double) leanZ * progress * progress);
            double radius = Math.max(0.32, (double) baseRadius * Math.pow(1.0 - progress, 0.72));
            int limit = Math.max(1, (int) Math.ceil(radius));
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double edge = SiftTerrain.hash01(centerX + dx, origin.y() + dy, centerZ + dz, salt) - 0.5;
                    double edgeRadius = radius + edge * 0.45;
                    if ((double) (dx * dx + dz * dz) > edgeRadius * edgeRadius) {
                        continue;
                    }
                    if (SiftMonolithFeature.replace(region, centerX + dx, origin.y() + dy, centerZ + dz,
                        SiftTerrain.DRY_HEALTHY_SCULK)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static int placeRootFlare(WorldRegion region, Pos origin, int baseRadius, long salt) {
        int rootRadius = baseRadius + 2;
        int placed = 0;
        for (int dx = -rootRadius; dx <= rootRadius; dx++) {
            for (int dz = -rootRadius; dz <= rootRadius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                double edge = (SiftTerrain.hash01(origin.x() + dx, origin.y(), origin.z() + dz,
                    salt ^ 0xD1342543DE82EF95L) - 0.5) * 0.8;
                double edgeRadius = (double) rootRadius + edge;
                if ((double) distanceSquared > edgeRadius * edgeRadius) {
                    continue;
                }
                int blockX = origin.x() + dx;
                int blockZ = origin.z() + dz;
                int localSurface = SiftTerrain.findTerrainSurface(region, blockX, blockZ);
                if (localSurface < 0 || Math.abs(localSurface - origin.y()) > 6) {
                    continue;
                }
                double distance = Math.sqrt(distanceSquared);
                double centerStrength = 1.0 - Math.min(1.0, distance / (double) rootRadius);
                int targetTop = origin.y() + (int) Math.round(centerStrength * 1.5);
                if (localSurface > targetTop + 1) {
                    continue;
                }
                for (int blockY = localSurface - 2; blockY <= targetTop; blockY++) {
                    if (SiftMonolithFeature.replace(region, blockX, blockY, blockZ, SiftTerrain.DRY_HEALTHY_SCULK)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean hasStableFootprint(WorldRegion region, Pos origin, int radius) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int samples = 0;
        int valid = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
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
        return (double) valid >= Math.ceil((double) samples * 0.82) && maxY - minY <= 12;
    }
}
