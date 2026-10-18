package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SiftSurfaceSculkRegionFeature} ({@code the_sift:surface_sculk_region}):
 * one elliptical sculk region of 50 to 120 blocks in every 512 x 512 cell, made of 6 to 11
 * overlapping blobs. Inside a blob the top 6 to 9 blocks of Sift terrain become sculk, with
 * rare catalysts, shriekers, sensors and veins on top; a two-block fringe around the blobs
 * only gets veins. The regions are pure functions of the world seed, so every chunk agrees on
 * them without any shared state, and the feature only writes its own chunk.
 *
 * <p>Each region marks one or two of its blobs for a Dark Sniffer, which the owning chunk (the
 * one holding the blob's centre) spawns on up to 64 undecorated sculk surface columns of the blob.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>The region layout is drawn with {@link Random} instead of Minecraft's
 *       {@code RandomSource}, so a seed gives a different (but equally shaped and
 *       deterministic) layout than the mod. The position hashes are the mod's.</li>
 *   <li>The dark sniffers (the mod's {@code DARK_SNIFFER} entity, queued in
 *       {@code SiftDeferredSnifferSpawner} for a later tick) are spawned directly at generation
 *       time as sniffers marked to become Dark Sniffers when they load; see {@link SiftSnifferSpawner}.</li>
 *   <li>The surface is found below the heightmap's top when that is air or a plant (see {@link SiftTerrain#motionBlockingTop}).</li>
 *   <li>The mod's plants are the single-block vanilla stand-ins of {@code SiftBlocks}, and
 *       {@code Math.sin}/{@code Math.cos} are their {@code StrictMath} versions so that
 *       neighbouring chunks agree on region borders on every JVM.</li>
 * </ul>
 */
final class SiftSurfaceSculkRegionFeature implements SiftFeature {

    static final int CELL_SIZE = 512;
    private static final int MIN_DIAMETER = 50;
    private static final int MAX_DIAMETER = 120;
    private static final int MAX_RADIUS = 60;
    private static final int CENTER_MARGIN = 76;
    private static final int MIN_SCULK_DEPTH = 6;
    private static final int SCULK_DEPTH_VARIATION = 4;
    private static final int MAX_DARK_SITES = 64;
    private static final long DARK_SNIFFER_SALT = 0x4441524B534E4946L;

    private static final BlockStateSpec SCULK = BlockStateSpec.of("minecraft:sculk");
    private static final BlockStateSpec SCULK_CATALYST = BlockStateSpec.of("minecraft:sculk_catalyst");
    private static final BlockStateSpec SCULK_SHRIEKER = BlockStateSpec.of("minecraft:sculk_shrieker",
        Map.of("can_summon", "true"));
    private static final BlockStateSpec SCULK_SENSOR = BlockStateSpec.of("minecraft:sculk_sensor");
    private static final BlockStateSpec SCULK_VEIN = BlockStateSpec.of("minecraft:sculk_vein", Map.of("down", "true"));

    /** The mod's {@code isReplaceableSiftPlant}, by the block ids of the stand-ins. */
    private static final Set<String> REPLACEABLE_PLANTS = Set.copyOf(List.of(
        SiftTerrain.block("overgrown_chard").block().asString(),
        SiftTerrain.block("overgrown_stalks").block().asString(),
        SiftTerrain.block("overgrown_fronds").block().asString(),
        SiftTerrain.block("overgrown_lotus").block().asString(),
        SiftTerrain.block("sunburst_plant").block().asString(),
        SiftTerrain.block("whisperbloom").block().asString(),
        SiftTerrain.block("siftslate_stalks").block().asString(),
        SiftTerrain.block("healthy_sculk_sprouts").block().asString(),
        SiftTerrain.block("dry_healthy_sculk_sprouts").block().asString(),
        "minecraft:wildflowers", "minecraft:torchflower", "minecraft:torchflower_crop", "minecraft:pitcher_crop",
        "minecraft:pitcher_plant"));

    /** One sculk region: an ellipse (rotated by {@code cos}/{@code sin}) holding the blobs. */
    record SculkRegion(double centerX, double centerZ, double majorRadius, double minorRadius, double cos, double sin,
                       long seed, List<SculkBlob> blobs) {

        boolean intersects(int minX, int minZ, int maxX, int maxZ) {
            return centerX + majorRadius >= (double) minX && centerX - majorRadius <= (double) maxX
                && centerZ + majorRadius >= (double) minZ && centerZ - majorRadius <= (double) maxZ;
        }

        boolean contains(int x, int z) {
            if (!insideEnvelope(x, z)) {
                return false;
            }
            for (SculkBlob blob : blobs) {
                if (blob.contains(x, z)) {
                    return true;
                }
            }
            return false;
        }

        boolean isVeinFringe(int x, int z) {
            if (!insideEnvelope(x, z)) {
                return false;
            }
            return contains(x + 1, z) || contains(x - 1, z) || contains(x, z + 1) || contains(x, z - 1)
                || contains(x + 2, z) || contains(x - 2, z) || contains(x, z + 2) || contains(x, z - 2);
        }

        boolean touchesBlobEdge(int x, int z) {
            return !contains(x + 1, z) || !contains(x - 1, z) || !contains(x, z + 1) || !contains(x, z - 1);
        }

        private boolean insideEnvelope(int x, int z) {
            double dx = (double) x + 0.5 - centerX;
            double dz = (double) z + 0.5 - centerZ;
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            double ellipse = localX * localX / (majorRadius * majorRadius) + localZ * localZ / (minorRadius * minorRadius);
            return ellipse <= 1.0;
        }
    }

    /** One rotated elliptical blob with a noisy edge. */
    record SculkBlob(double centerX, double centerZ, double radiusX, double radiusZ, double cos, double sin, long seed) {

        boolean contains(int x, int z) {
            double dx = (double) x + 0.5 - centerX;
            double dz = (double) z + 0.5 - centerZ;
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            double ellipse = localX * localX / (radiusX * radiusX) + localZ * localZ / (radiusZ * radiusZ);
            double edgeNoise = (unit(mix(seed ^ positionSalt(x, z))) - 0.5) * 0.22;
            return ellipse <= 1.0 + edgeNoise;
        }
    }

    private record DarkSpawnPlan(SculkBlob blob, List<Pos> sites) {
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        int chunkMinX = x & ~15;
        int chunkMinZ = z & ~15;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        // WorldRegion.maxY() is exclusive, the mod's level.getMaxY() the highest block.
        int topY = region.maxY() - 1;
        int minCellX = Math.floorDiv(chunkMinX - MAX_RADIUS, CELL_SIZE);
        int maxCellX = Math.floorDiv(chunkMaxX + MAX_RADIUS, CELL_SIZE);
        int minCellZ = Math.floorDiv(chunkMinZ - MAX_RADIUS, CELL_SIZE);
        int maxCellZ = Math.floorDiv(chunkMaxZ + MAX_RADIUS, CELL_SIZE);
        boolean changed = false;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                SculkRegion sculk = createRegion(region.seed(), cellX, cellZ);
                if (!sculk.intersects(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
                    continue;
                }
                List<DarkSpawnPlan> darkSpawnPlans = planDarkSnifferBlobs(sculk, chunkMinX, chunkMinZ);
                for (int columnX = chunkMinX; columnX <= chunkMaxX; columnX++) {
                    for (int columnZ = chunkMinZ; columnZ <= chunkMaxZ; columnZ++) {
                        boolean core = sculk.contains(columnX, columnZ);
                        boolean fringe = !core && sculk.isVeinFringe(columnX, columnZ);
                        if (!core && !fringe) {
                            continue;
                        }
                        int surfaceY = SiftTerrain.motionBlockingTop(region, WorldRegion.Heightmap.MOTION_BLOCKING_NO_LEAVES,
                            columnX, columnZ);
                        if (surfaceY < region.minY() || surfaceY >= topY - 2
                            || !isNaturalSiftTerrain(region.state(columnX, surfaceY, columnZ))) {
                            continue;
                        }
                        int aboveY = surfaceY + 1;
                        String aboveId = region.blockId(columnX, aboveY, columnZ);
                        boolean aboveAir = region.isAir(columnX, aboveY, columnZ);
                        if (REPLACEABLE_PLANTS.contains(aboveId)) {
                            clearUpperPitcherHalf(region, columnX, aboveY, columnZ, aboveId);
                            region.set(columnX, aboveY, columnZ, SiftTerrain.AIR);
                            aboveAir = true;
                        }
                        if (core) {
                            boolean replaced = replaceSurface(region, columnX, surfaceY, columnZ, sculk.seed());
                            changed |= replaced;
                            if (!aboveAir) {
                                continue;
                            }
                            boolean decorated = placeSculkFamilyBlock(region, columnX, aboveY, columnZ, sculk);
                            changed |= decorated;
                            if (!replaced || decorated || columnX <= chunkMinX || columnX >= chunkMaxX
                                || columnZ <= chunkMinZ || columnZ >= chunkMaxZ) {
                                continue;
                            }
                            for (DarkSpawnPlan plan : darkSpawnPlans) {
                                if (plan.sites().size() >= MAX_DARK_SITES || !plan.blob().contains(columnX, columnZ)) {
                                    continue;
                                }
                                plan.sites().add(new Pos(columnX, surfaceY, columnZ));
                            }
                            continue;
                        }
                        if (!aboveAir || !region.isWritable(columnX, aboveY, columnZ)) {
                            continue;
                        }
                        region.set(columnX, aboveY, columnZ, SCULK_VEIN);
                        changed = true;
                    }
                }
                for (DarkSpawnPlan plan : darkSpawnPlans) {
                    SiftSnifferSpawner.spawnDark(region, plan.sites(), plan.blob().seed());
                }
            }
        }
        return changed;
    }

    /** The one or two blobs of the region that get a dark sniffer, if this chunk holds their centre. */
    private static List<DarkSpawnPlan> planDarkSnifferBlobs(SculkRegion sculk, int chunkMinX, int chunkMinZ) {
        List<DarkSpawnPlan> plans = new ArrayList<>();
        int blobCount = sculk.blobs().size();
        int darkSnifferCount = 1 + (int) (mix(sculk.seed() ^ DARK_SNIFFER_SALT) & 1L);
        int firstDarkBlob = Math.floorMod(mix(sculk.seed() ^ DARK_SNIFFER_SALT ^ 0x19A7D44B5E3C021FL), blobCount);
        int secondDarkBlob = Math.floorMod(mix(sculk.seed() ^ DARK_SNIFFER_SALT ^ 0x735DA273B948F10DL), blobCount - 1);
        if (secondDarkBlob >= firstDarkBlob) {
            secondDarkBlob++;
        }
        for (int blobIndex = 0; blobIndex < blobCount; blobIndex++) {
            SculkBlob blob = sculk.blobs().get(blobIndex);
            boolean ownerChunk = Math.floorDiv((int) Math.floor(blob.centerX()), 16) == Math.floorDiv(chunkMinX, 16)
                && Math.floorDiv((int) Math.floor(blob.centerZ()), 16) == Math.floorDiv(chunkMinZ, 16);
            boolean selected = blobIndex == firstDarkBlob || darkSnifferCount == 2 && blobIndex == secondDarkBlob;
            if (ownerChunk && selected) {
                plans.add(new DarkSpawnPlan(blob, new ArrayList<>(32)));
            }
        }
        return plans;
    }

    /** Turns the top 6 to 9 blocks of natural Sift terrain of the column into sculk. */
    private static boolean replaceSurface(WorldRegion region, int x, int surfaceY, int z, long regionSeed) {
        int depth = MIN_SCULK_DEPTH + Math.floorMod(mix(regionSeed ^ positionSalt(x, z)), SCULK_DEPTH_VARIATION);
        boolean changed = false;
        for (int offset = 0; offset < depth; offset++) {
            int targetY = surfaceY - offset;
            if (!region.isWritable(x, targetY, z) || !isNaturalSiftTerrain(region.state(x, targetY, z))) {
                break;
            }
            region.set(x, targetY, z, SCULK);
            changed = true;
        }
        return changed;
    }

    /** Rarely a catalyst, shrieker, sensor or (on blob edges) vein on top of the sculk. */
    private static boolean placeSculkFamilyBlock(WorldRegion region, int x, int y, int z, SculkRegion sculk) {
        double roll = unit(mix(sculk.seed() ^ positionSalt(x, z) ^ 0x4450434F52415445L));
        BlockStateSpec decoration;
        if (roll < 0.0015) {
            decoration = SCULK_CATALYST;
        } else if (roll < 0.0055) {
            decoration = SCULK_SHRIEKER;
        } else if (roll < 0.021) {
            decoration = SCULK_SENSOR;
        } else if (roll < 0.115 && sculk.touchesBlobEdge(x, z)) {
            decoration = SCULK_VEIN;
        } else {
            return false;
        }
        if (!region.isWritable(x, y, z)) {
            return false;
        }
        region.set(x, y, z, decoration);
        return true;
    }

    private static boolean isNaturalSiftTerrain(BlockStateSpec state) {
        return SiftTerrain.TERRAIN.contains(state) || state.equals(SCULK);
    }

    /** A pitcher plant is two blocks tall: removing the lower half must not leave the upper one floating. */
    private static void clearUpperPitcherHalf(WorldRegion region, int x, int lowerY, int z, String lowerId) {
        if (!SiftSnifferPlantPlacement.isPitcher(lowerId)) {
            return;
        }
        int upperY = lowerY + 1;
        if (SiftSnifferPlantPlacement.isPitcher(region.blockId(x, upperY, z)) && region.isWritable(x, upperY, z)) {
            region.set(x, upperY, z, SiftTerrain.AIR);
        }
    }

    /** The region of a 512-block cell; every chunk derives the same one from the world seed. */
    static SculkRegion createRegion(long worldSeed, int cellX, int cellZ) {
        long seed = mix(worldSeed ^ 0x5343554C4B524547L ^ (long) cellX * -7046029254386353131L ^ (long) cellZ * -4417276706812531889L);
        Random random = new Random(seed);
        int availableCenterRange = CELL_SIZE - 2 * CENTER_MARGIN;
        double centerX = (double) cellX * CELL_SIZE + CENTER_MARGIN + (double) random.nextInt(availableCenterRange + 1);
        double centerZ = (double) cellZ * CELL_SIZE + CENTER_MARGIN + (double) random.nextInt(availableCenterRange + 1);
        int diameter = MIN_DIAMETER + random.nextInt(MAX_DIAMETER - MIN_DIAMETER + 1);
        double majorRadius = (double) diameter * 0.5;
        double minorRadius = majorRadius * (0.72 + random.nextDouble() * 0.23);
        double angle = random.nextDouble() * Math.PI;
        int blobCount = 6 + random.nextInt(6);
        List<SculkBlob> blobs = new ArrayList<>(blobCount);
        double cosRegion = StrictMath.cos(angle);
        double sinRegion = StrictMath.sin(angle);
        for (int index = 0; index < blobCount; index++) {
            double direction = random.nextDouble() * Math.PI * 2.0;
            double distance = index == 0 ? 0.0 : Math.sqrt(random.nextDouble()) * majorRadius * 0.68;
            double localX = StrictMath.cos(direction) * distance;
            double localZ = StrictMath.sin(direction) * distance * (minorRadius / majorRadius);
            double blobCenterX = centerX + localX * cosRegion - localZ * sinRegion;
            double blobCenterZ = centerZ + localX * sinRegion + localZ * cosRegion;
            double blobRadius = majorRadius * (0.18 + random.nextDouble() * 0.18);
            double blobAspect = 0.62 + random.nextDouble() * 0.36;
            double blobAngle = random.nextDouble() * Math.PI;
            blobs.add(new SculkBlob(blobCenterX, blobCenterZ, blobRadius, blobRadius * blobAspect,
                StrictMath.cos(blobAngle), StrictMath.sin(blobAngle), random.nextLong()));
        }
        return new SculkRegion(centerX, centerZ, majorRadius, minorRadius, cosRegion, sinRegion, seed, List.copyOf(blobs));
    }

    private static long positionSalt(int x, int z) {
        return (long) x * 7146057691288625177L ^ (long) z * -7046029288634856825L;
    }

    private static long mix(long value) {
        return SiftSnifferSpawner.mix(value);
    }

    private static double unit(long value) {
        return (double) (value >>> 11) * (double) 1.110223E-16F;
    }
}
