package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.feature.SiftSnifferPlantPlacement.PlantType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SnifferCaveFeature} ({@code the_sift:sniffer_cave_overgrown} and
 * {@code the_sift:sniffer_cave_wastes}): in seed-selected landmark chunks it carves a winding
 * tunnel from the surface down to a nest chamber, lines it with healthy and dry healthy sculk,
 * digs an ichor pool in a side chamber, plants torchflowers or pitcher-plant bulbs, builds a
 * willow-foliage nest with sniffer eggs and plants, and puts a sniffer family in it.
 *
 * <p>The two variants only differ in their landmark grid (see {@link SiftLandmarkPlacement}).
 * The cave needs 40% Sift terrain in its volume and refuses to cut through reinforced
 * siftslate, portal blocks, sonorous deepslate or willow wood.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>The mod's {@code SiftFeaturePlacementGuard.intersectsPortal} check (a global,
 *       generation-order-dependent reservation around the last abandoned portal) and its
 *       {@code SiftLandmarkTracker.record} (a global persistent registry) are not ported;
 *       either would make the output depend on which chunks generated first. The protected
 *       block check still keeps the cave out of the portal's blocks.</li>
 *   <li>Sniffers are spawned directly by {@link SiftSnifferSpawner} instead of being queued
 *       for a later server tick; the family's baby spawns as an adult.</li>
 *   <li>The mod's hash sets are iterated in whatever order {@code HashSet} and
 *       {@code Set.copyOf} give, so its random draws (lining colours, plant spots) depend on
 *       the JVM; the port iterates insertion-ordered sets so one seed always gives one cave.</li>
 *   <li>Ichor is water, the nest foliage has no {@code persistent} flag, sniffer-egg and
 *       plant survival follow the shared stand-in rules, and the mod's plants are the
 *       single-block vanilla stand-ins of {@code SiftBlocks}. {@code Math.sin} is
 *       {@code StrictMath.sin} so a seed gives the same cave on every JVM.</li>
 * </ul>
 */
final class SnifferCaveFeature implements SiftFeature {

    private enum Facing {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        private static final Facing[] ALL = values();

        private final int dx;
        private final int dz;

        Facing(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        Facing clockWise() {
            return ALL[(ordinal() + 1) & 3];
        }

        Facing opposite() {
            return ALL[(ordinal() + 2) & 3];
        }

        boolean alongX() {
            return dx != 0;
        }
    }

    private record IchorPoolPlan(Pos chamberCenter, Facing outward, int variant, double chamberOutwardRadius,
                                 double chamberVerticalRadius, double chamberCrossRadius) {
    }

    private record IchorBasin(Set<Pos> fluid, Set<Pos> lining, Set<Pos> clearance) {
    }

    private record DirectionScore(Facing direction, int score) {
    }

    private static final Facing[] HORIZONTAL = Facing.ALL;
    private static final String[] NEST_PLANTS = {"overgrown_fronds", "overgrown_chard", "overgrown_stalks",
        "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts"};
    private static final BlockStateSpec SNIFFER_EGG = BlockStateSpec.of("minecraft:sniffer_egg");
    private static final BlockStateSpec AIR = SiftTerrain.AIR;

    /** Blocks the cave never cuts through: the mod's reinforced, portal, sonorous and willow-wood checks. */
    private static final Set<BlockStateSpec> PROTECTED = Set.of(
        SiftTerrain.REINFORCED_SIFTSLATE,
        SiftTerrain.SONOROUS_DEEPSLATE,
        remap("sonorous_deepslate", Map.of("mode", "note")),
        remap("overgrown_willow_log", Map.of("axis", "y")),
        remap("overgrown_willow_log", Map.of("axis", "x")),
        remap("overgrown_willow_log", Map.of("axis", "z")),
        SiftTerrain.OVERGROWN_WILLOW_WOOD);
    /** The portal is a {@code minecraft:light} stand-in. */
    private static final String PORTAL_BLOCK = SiftTerrain.block("sift_portal").block().asString();
    /** The mod's {@code isCarvableVegetation}, by stand-in block id. */
    private static final Set<String> CARVABLE_VEGETATION = carvableVegetation();

    private final int cellChunks;
    private final long placementSalt;
    private final int candidateCount;

    private SnifferCaveFeature(int cellChunks, long placementSalt, int candidateCount) {
        this.cellChunks = cellChunks;
        this.placementSalt = placementSalt;
        this.candidateCount = candidateCount;
    }

    /** {@code the_sift:sniffer_cave_overgrown}: one cave per 16-chunk cell, up to four candidates. */
    static SnifferCaveFeature overgrown() {
        return new SnifferCaveFeature(SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_CELL_CHUNKS,
            SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_SALT, SiftLandmarkPlacement.SNIFFER_CAVE_OVERGROWN_CANDIDATES);
    }

    /** {@code the_sift:sniffer_cave_wastes}: 28-chunk cells with up to three candidates. */
    static SnifferCaveFeature wastes() {
        return new SnifferCaveFeature(SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_CELL_CHUNKS,
            SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_SALT, SiftLandmarkPlacement.SNIFFER_CAVE_WASTES_CANDIDATES);
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        if (!SiftLandmarkPlacement.isSelectedChunk(region.seed(), x, z, cellChunks, placementSalt, candidateCount)) {
            return false;
        }
        SiftWorldgenBounds bounds = SiftWorldgenBounds.aroundWithNeighbourMargin(x, z);
        int length = 14 + random.nextInt(3);
        Pos entrance = findEntrance(region, x, z);
        Facing direction = entrance == null ? null : chooseDirection(region, bounds, entrance, length, random);
        if (entrance == null || direction == null) {
            return false;
        }
        Set<Pos> interior = new LinkedHashSet<>();
        Set<Pos> shell = new LinkedHashSet<>();
        Facing sideways = direction.clockWise();
        double phase = random.nextDouble() * Math.PI * 2.0;
        Pos lastCenter = entrance;
        for (int distance = 0; distance <= length; distance += 2) {
            double progress = (double) distance / (double) length;
            int lateral = (int) Math.round(StrictMath.sin(phase + progress * Math.PI * 1.7) * (0.6 + progress * 1.35));
            int lift = (int) Math.round(StrictMath.sin(progress * Math.PI) * 0.9) - (int) Math.round(progress * 9.0);
            Pos center = above(relative(relative(entrance, direction, distance), sideways, lateral), lift);
            double swell = StrictMath.sin(progress * Math.PI);
            double radiusXz = 2.1 + swell * 1.65 + random.nextDouble() * 0.35;
            double radiusY = 1.9 + swell * 1.15 + random.nextDouble() * 0.25;
            addEllipsoid(center, radiusXz, radiusY, radiusXz * 0.92, interior, shell);
            lastCenter = center;
        }
        Pos nestCenter = above(relative(lastCenter, direction, 1), 1);
        addEllipsoid(nestCenter, 5.2, 3.7, 5.0, interior, shell);
        IchorPoolPlan pool = createIchorPoolPlan(nestCenter, sideways, random);
        addOrientedEllipsoid(pool.chamberCenter(), pool.chamberOutwardRadius(), pool.chamberVerticalRadius(),
            pool.chamberCrossRadius(), pool.outward(), interior, shell);
        shell.removeAll(interior);
        IchorBasin basin = createIchorBasin(pool);
        Set<Pos> writes = new LinkedHashSet<>(interior);
        writes.addAll(shell);
        writes.addAll(basin.fluid());
        writes.addAll(basin.lining());
        writes.addAll(basin.clearance());
        if (!validMass(region, bounds, nestCenter) || !canCarveAll(region, bounds, writes)) {
            return false;
        }
        for (Pos pos : shell) {
            if (!bounds.contains(pos.x(), pos.z())) {
                return false;
            }
            if (!SiftTerrain.isTerrain(region, pos.x(), pos.y(), pos.z()) || !region.isWritable(pos.x(), pos.y(), pos.z())) {
                continue;
            }
            float healthyChance = 0.18F + (distanceSquared(pos, nestCenter) < 90 ? 0.13F : 0.0F);
            BlockStateSpec lining = random.nextFloat() < healthyChance ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.DRY_HEALTHY_SCULK;
            region.set(pos.x(), pos.y(), pos.z(), lining);
        }
        for (Pos pos : interior) {
            if (!bounds.contains(pos.x(), pos.z()) || !region.isWritable(pos.x(), pos.y(), pos.z())) {
                continue;
            }
            region.set(pos.x(), pos.y(), pos.z(), AIR);
        }
        placeIchorPool(region, random, basin);
        scatterSnifferPlants(region, random, interior, nestCenter);
        List<Pos> nest = buildNest(region, random, nestCenter);
        if (nest.isEmpty()) {
            return true;
        }
        placeNestContents(region, random, nest);
        SiftSnifferSpawner.spawnFamily(region, random, nest);
        return true;
    }

    /** The surface point at the chunk centre, if it rests on Sift terrain well above the world floor. */
    private static Pos findEntrance(WorldRegion region, int originX, int originZ) {
        int x = (originX & ~15) + 8;
        int z = (originZ & ~15) + 8;
        int surface = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, x, z);
        return surface > region.minY() + 18 && SiftTerrain.isTerrain(region, x, surface - 1, z)
            ? new Pos(x, surface, z) : null;
    }

    private static Facing chooseDirection(WorldRegion region, SiftWorldgenBounds bounds, Pos entrance, int length, Random random) {
        List<DirectionScore> candidates = new ArrayList<>();
        int start = random.nextInt(HORIZONTAL.length);
        for (int index = 0; index < HORIZONTAL.length; index++) {
            Facing direction = HORIZONTAL[(start + index) % HORIZONTAL.length];
            Pos end = below(relative(entrance, direction, length + 1), 8);
            if (!bounds.contains(end.x() + 6, end.z() + 6) || !bounds.contains(end.x() - 6, end.z() - 6)) {
                continue;
            }
            int surface = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, end.x(), end.z());
            int cover = surface - end.y();
            if (cover < 5) {
                continue;
            }
            candidates.add(new DirectionScore(direction, cover + random.nextInt(4)));
        }
        // The mod's Stream.max keeps the first of equal scores; so does this scan.
        DirectionScore best = null;
        for (DirectionScore candidate : candidates) {
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }
        return best == null ? null : best.direction();
    }

    private static void addEllipsoid(Pos center, double radiusX, double radiusY, double radiusZ, Set<Pos> interior, Set<Pos> shell) {
        int maxX = (int) Math.ceil(radiusX + 1.0);
        int maxY = (int) Math.ceil(radiusY + 1.0);
        int maxZ = (int) Math.ceil(radiusZ + 1.0);
        for (int dx = -maxX; dx <= maxX; dx++) {
            for (int dy = -maxY; dy <= maxY; dy++) {
                for (int dz = -maxZ; dz <= maxZ; dz++) {
                    double inner = square((double) dx / radiusX) + square((double) dy / radiusY) + square((double) dz / radiusZ);
                    Pos pos = center.offset(dx, dy, dz);
                    if (inner <= 1.0) {
                        interior.add(pos);
                        continue;
                    }
                    double outer = square((double) dx / (radiusX + 1.0)) + square((double) dy / (radiusY + 1.0))
                        + square((double) dz / (radiusZ + 1.0));
                    if (outer <= 1.0) {
                        shell.add(pos);
                    }
                }
            }
        }
    }

    private static void addOrientedEllipsoid(Pos center, double outwardRadius, double verticalRadius, double crossRadius,
                                             Facing outward, Set<Pos> interior, Set<Pos> shell) {
        double radiusX = outward.alongX() ? outwardRadius : crossRadius;
        double radiusZ = outward.alongX() ? crossRadius : outwardRadius;
        addEllipsoid(center, radiusX, verticalRadius, radiusZ, interior, shell);
    }

    private static IchorPoolPlan createIchorPoolPlan(Pos nestCenter, Facing sideways, Random random) {
        int variant = random.nextInt(4);
        Facing outward = random.nextBoolean() ? sideways : sideways.opposite();
        double chamberOutward = switch (variant) {
            case 1 -> 4.4;
            case 2 -> 3.8;
            case 3 -> 4.1;
            default -> 4.0;
        };
        double chamberCross = switch (variant) {
            case 1 -> 2.7;
            case 2 -> 3.5;
            case 3 -> 3.1;
            default -> 3.0;
        };
        double chamberVertical = variant == 3 ? 3.0 : 2.7;
        Pos center = below(relative(nestCenter, outward, 5), 1);
        return new IchorPoolPlan(center, outward, variant, chamberOutward, chamberVertical, chamberCross);
    }

    private static IchorBasin createIchorBasin(IchorPoolPlan plan) {
        int outwardRadius = switch (plan.variant()) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 3;
            default -> 2;
        };
        int crossRadius = switch (plan.variant()) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 2;
            default -> 2;
        };
        Facing cross = plan.outward().clockWise();
        Set<Pos> surface = new LinkedHashSet<>();
        Set<Pos> fluid = new LinkedHashSet<>();
        Set<Pos> lining = new LinkedHashSet<>();
        Set<Pos> clearance = new LinkedHashSet<>();
        for (int along = -outwardRadius; along <= outwardRadius; along++) {
            for (int across = -crossRadius; across <= crossRadius; across++) {
                double normalized = square((double) along / ((double) outwardRadius + 0.35))
                    + square((double) across / ((double) crossRadius + 0.35));
                double edgeWobble = StrictMath.sin((double) along * 2.17 + (double) across * 1.41 + (double) plan.variant() * 0.83) * 0.11;
                if (normalized > 1.0 + edgeWobble) {
                    continue;
                }
                Pos top = relative(relative(below(plan.chamberCenter(), 3), plan.outward(), along), cross, across);
                surface.add(top);
                fluid.add(top);
                if (plan.variant() == 3 && normalized < 0.55) {
                    fluid.add(below(top, 1));
                }
            }
        }
        for (Pos wet : fluid) {
            lining.add(below(wet, 1));
            for (Facing side : HORIZONTAL) {
                lining.add(relative(wet, side, 1));
            }
        }
        lining.removeAll(fluid);
        for (Pos top : surface) {
            for (int dy = 1; dy <= 3; dy++) {
                clearance.add(above(top, dy));
            }
            for (Facing side : HORIZONTAL) {
                Pos bank = relative(top, side, 1);
                if (surface.contains(bank)) {
                    continue;
                }
                lining.add(below(bank, 1));
                lining.add(below(bank, 2));
                clearance.add(above(bank, 1));
                clearance.add(above(bank, 2));
            }
        }
        lining.removeAll(fluid);
        return new IchorBasin(Collections.unmodifiableSet(fluid), Collections.unmodifiableSet(lining),
            Collections.unmodifiableSet(clearance));
    }

    private static void placeIchorPool(WorldRegion region, Random random, IchorBasin basin) {
        for (Pos pos : basin.clearance()) {
            region.set(pos.x(), pos.y(), pos.z(), AIR);
        }
        for (Pos pos : basin.lining()) {
            region.set(pos.x(), pos.y(), pos.z(),
                random.nextFloat() < 0.55F ? SiftTerrain.HEALTHY_SCULK : SiftTerrain.DRY_HEALTHY_SCULK);
        }
        for (Pos pos : basin.fluid()) {
            region.set(pos.x(), pos.y(), pos.z(), SiftTerrain.ICHOR);
        }
    }

    /** Every touched block must be writable, only terrain, air or cave vegetation, and mostly Sift terrain. */
    private static boolean canCarveAll(WorldRegion region, SiftWorldgenBounds bounds, Set<Pos> writes) {
        int siftMass = 0;
        for (Pos pos : writes) {
            if (!bounds.contains(pos.x(), pos.z()) || !region.isWritable(pos.x(), pos.y(), pos.z())) {
                return false;
            }
            BlockStateSpec state = region.state(pos.x(), pos.y(), pos.z());
            String id = state.block().asString();
            if (PROTECTED.contains(state) || id.equals(PORTAL_BLOCK)) {
                return false;
            }
            if (SiftTerrain.TERRAIN.contains(state)) {
                siftMass++;
                continue;
            }
            if (CARVABLE_VEGETATION.contains(id) || isAirId(id)) {
                continue;
            }
            return false;
        }
        return (double) siftMass >= (double) writes.size() * 0.4;
    }

    /** At least 56% of a sample grid around the nest must be Sift terrain, and all of it inside the bounds. */
    private static boolean validMass(WorldRegion region, SiftWorldgenBounds bounds, Pos center) {
        int solid = 0;
        int total = 0;
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dy = -3; dy <= 3; dy += 2) {
                for (int dz = -5; dz <= 5; dz += 2) {
                    total++;
                    int sampleX = center.x() + dx;
                    int sampleY = center.y() + dy;
                    int sampleZ = center.z() + dz;
                    if (!bounds.contains(sampleX, sampleZ)) {
                        return false;
                    }
                    if (SiftTerrain.isTerrain(region, sampleX, sampleY, sampleZ)) {
                        solid++;
                    }
                }
            }
        }
        return (double) solid >= (double) total * 0.56;
    }

    private static void scatterSnifferPlants(WorldRegion region, Random random, Set<Pos> interior, Pos nestCenter) {
        List<Pos> sample = new ArrayList<>(interior);
        int placed = 0;
        PlantType blobType = SiftSnifferPlantPlacement.chooseBlobType(random, 0.6F);
        for (int attempt = 0; attempt < 72 && placed < 14; attempt++) {
            Pos air = sample.get(random.nextInt(sample.size()));
            Pos floor = findFloor(region, air.x(), air.z(), nestCenter.y());
            if (floor == null || !region.isAir(floor.x(), floor.y() + 1, floor.z())) {
                continue;
            }
            BlockStateSpec floorState = region.state(floor.x(), floor.y(), floor.z());
            if (!floorState.equals(SiftTerrain.HEALTHY_SCULK) && !floorState.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
                continue;
            }
            if (SiftSnifferPlantPlacement.place(region, floor.x(), floor.y() + 1, floor.z(), blobType, random)) {
                placed++;
            }
        }
    }

    /** Willow-foliage nest on the sculk floor around the chamber centre; returns the nest's floor blocks. */
    private static List<Pos> buildNest(WorldRegion region, Random random, Pos center) {
        List<Pos> nest = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double distance = square((double) dx / 3.25) + square((double) dz / 3.25);
                if (distance > 1.0) {
                    continue;
                }
                if (distance > 0.68 && random.nextFloat() < 0.34F) {
                    continue;
                }
                Pos floor = findFloor(region, center.x() + dx, center.z() + dz, center.y());
                if (floor == null || !region.isAir(floor.x(), floor.y() + 1, floor.z())) {
                    continue;
                }
                BlockStateSpec current = region.state(floor.x(), floor.y(), floor.z());
                if (!current.equals(SiftTerrain.HEALTHY_SCULK) && !current.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
                    continue;
                }
                if (region.set(floor.x(), floor.y(), floor.z(), SiftTerrain.OVERGROWN_WILLOW_FOLIAGE)) {
                    nest.add(floor);
                }
            }
        }
        return nest;
    }

    private static void placeNestContents(WorldRegion region, Random random, List<Pos> nest) {
        int eggs = random.nextFloat() < 0.5F ? 1 : 0;
        if (eggs == 1 && random.nextFloat() < 0.1F) {
            eggs++;
        }
        for (int index = 0; index < eggs; index++) {
            placeOnRandomFreeNestBlock(region, random, nest, SNIFFER_EGG);
        }
        int plants = 4 + random.nextInt(5);
        for (int index = 0; index < plants; index++) {
            String plant = NEST_PLANTS[random.nextInt(NEST_PLANTS.length)];
            placeOnRandomFreeNestBlock(region, random, nest, SiftTerrain.block(plant));
        }
    }

    /** Up to 18 tries at a random nest block; the plants survive on the foliage, the egg anywhere. */
    private static void placeOnRandomFreeNestBlock(WorldRegion region, Random random, List<Pos> nest, BlockStateSpec state) {
        for (int attempt = 0; attempt < 18; attempt++) {
            Pos pos = above(nest.get(random.nextInt(nest.size())), 1);
            if (!region.isAir(pos.x(), pos.y(), pos.z())) {
                continue;
            }
            if (state.equals(SNIFFER_EGG)) {
                if (!region.set(pos.x(), pos.y(), pos.z(), state)) {
                    continue;
                }
            } else if (!SiftTerrain.placePlant(region, pos.x(), pos.y(), pos.z(), state)) {
                continue;
            }
            return;
        }
    }

    /** The floor block under the first free block between {@code aroundY + 3} and {@code aroundY - 7}. */
    private static Pos findFloor(WorldRegion region, int x, int z, int aroundY) {
        for (int y = aroundY + 3; y >= aroundY - 7; y--) {
            if (!region.isAir(x, y, z) || !SiftTerrain.isTerrain(region, x, y - 1, z)) {
                continue;
            }
            return new Pos(x, y - 1, z);
        }
        return null;
    }

    private static Set<String> carvableVegetation() {
        Set<String> ids = new LinkedHashSet<>(SiftTerrain.PLANT_TYPES);
        ids.add(SiftTerrain.block("siftslate_hanging_roots").block().asString());
        ids.add(SiftTerrain.block("overgrown_hanging_roots").block().asString());
        return Set.copyOf(ids);
    }

    private static boolean isAirId(String id) {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    private static BlockStateSpec remap(String path, Map<String, String> properties) {
        return SiftBlocks.remap(path, properties).orElseThrow();
    }

    private static Pos relative(Pos pos, Facing direction, int steps) {
        return new Pos(pos.x() + direction.dx * steps, pos.y(), pos.z() + direction.dz * steps);
    }

    private static Pos above(Pos pos, int steps) {
        return new Pos(pos.x(), pos.y() + steps, pos.z());
    }

    private static Pos below(Pos pos, int steps) {
        return new Pos(pos.x(), pos.y() - steps, pos.z());
    }

    private static int distanceSquared(Pos first, Pos second) {
        int dx = first.x() - second.x();
        int dy = first.y() - second.y();
        int dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }
}
