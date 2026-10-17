package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.modules.sift.SifterSpawning.Anchor;
import dev.linqfy.bigCasares.modules.sift.SifterSpawning.Feet;
import dev.linqfy.bigCasares.modules.sift.SifterSpawning.Profile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SifterSpawningTest {

    /** A random whose draws are scripted: doubles in order (the last repeats), a fixed float, ints by bound. */
    private static final class ScriptedRandom extends Random {
        private final double[] doubles;
        private final float floatValue;
        private final IntUnaryOperator ints;
        private int next;

        ScriptedRandom(float floatValue, IntUnaryOperator ints, double... doubles) {
            this.doubles = doubles;
            this.floatValue = floatValue;
            this.ints = ints;
        }

        @Override
        public double nextDouble() {
            return doubles[Math.min(next++, doubles.length - 1)];
        }

        @Override
        public float nextFloat() {
            return floatValue;
        }

        @Override
        public int nextInt(int bound) {
            return ints.applyAsInt(bound);
        }
    }

    /** Flat ground whose top block (y {@code solidTop}) is a spawn floor, with knobs for the cases. */
    private static final class FakeLevel implements SifterSpawning.Level {
        int minY = 0;
        int solidTop = 63;
        boolean groundIsFloor = true;
        int height = 64;
        int population;
        java.util.function.BiPredicate<Integer, Integer> loaded = (x, z) -> true;
        BiFunction<Integer, Integer, Profile> profile = (x, z) -> Profile.OVERGROWN_FOREST;
        final Set<String> floors = new HashSet<>();
        final Set<String> solids = new HashSet<>();
        final List<Feet> populationQueries = new ArrayList<>();
        final List<int[]> loadedQueries = new ArrayList<>();
        int heightCalls;

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public boolean isLoaded(int x, int z) {
            loadedQueries.add(new int[]{x, z});
            return loaded.test(x, z);
        }

        @Override
        public int height(int x, int z) {
            heightCalls++;
            return height;
        }

        @Override
        public boolean isSpawnFloor(int x, int y, int z) {
            return groundIsFloor && y == solidTop || floors.contains(key(x, y, z));
        }

        @Override
        public boolean hasEmptyCollision(int x, int y, int z) {
            return y > solidTop && !solids.contains(key(x, y, z));
        }

        @Override
        public Profile profileAt(int x, int y, int z) {
            return profile.apply(x, z);
        }

        @Override
        public int sifterCount(Feet around) {
            populationQueries.add(around);
            return population;
        }
    }

    private static List<Feet> spawnAll(FakeLevel level, Random random, int aroundX, int aroundZ, List<Float> yaws) {
        List<Feet> spawned = new ArrayList<>();
        SifterSpawning.trySpawnNear(level, aroundX, aroundZ, random, (feet, yaw) -> {
            spawned.add(feet);
            yaws.add(yaw);
            return true;
        });
        return spawned;
    }

    // --- profiles and floors -------------------------------------------------------------

    @Test
    void profilesFollowTheBiomeLikeTheMod() {
        assertEquals(Profile.OVERGROWN_FOREST, Profile.ofBiome("the_sift:overgrown_forest"));
        for (String overgrown : List.of("overgrown_clearing", "overgrown_forest_slopes", "overgrown_slopes", "overgrown_peaks")) {
            assertEquals(Profile.OVERGROWN, Profile.ofBiome("the_sift:" + overgrown), overgrown);
        }
        assertEquals(Profile.WASTES, Profile.ofBiome("the_sift:sift_wastes"));
        for (String other : List.of("siftslate_slopes", "ichor_snowy_peaks", "siftslate_peaks", "sift_deep_dark")) {
            assertEquals(Profile.OTHER_SIFT, Profile.ofBiome("the_sift:" + other), other);
        }
        assertEquals(Profile.OTHER_SIFT, Profile.ofBiome("minecraft:plains"));
    }

    @Test
    void profileChancesAndCapsAreTheMods() {
        assertEquals(0.36, Profile.OVERGROWN_FOREST.chance());
        assertEquals(8, Profile.OVERGROWN_FOREST.localCap());
        assertEquals(0.28, Profile.OVERGROWN.chance());
        assertEquals(6, Profile.OVERGROWN.localCap());
        assertEquals(0.22, Profile.WASTES.chance());
        assertEquals(5, Profile.WASTES.localCap());
        assertEquals(0.18, Profile.OTHER_SIFT.chance());
        assertEquals(5, Profile.OTHER_SIFT.localCap());
    }

    @Test
    void sifterFloorsAreTheModsFiveBlocksAndTheSnowyGrowth() {
        Set<MushroomState> expected = new HashSet<>();
        for (String path : List.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk", "dry_healthy_sculk_growth")) {
            expected.add(SiftBlocks.custom(path).orElseThrow().state());
        }
        expected.add(SiftBlocks.custom("siftslate_growth[snowy=true]").orElseThrow().state());
        assertEquals(expected, SifterSpawning.FLOORS);
        for (String other : List.of("cobbled_siftslate", "soul_block", "sift_rubble", "siftslate_bricks")) {
            assertFalse(SifterSpawning.FLOORS.contains(SiftBlocks.custom(other).orElseThrow().state()), other);
        }
    }

    // --- finding a spot ------------------------------------------------------------------

    @Test
    void surfaceSpotIsTheFeetOverTheTopFloor() {
        FakeLevel level = new FakeLevel();
        assertEquals(new Feet(5, 64, 7), SifterSpawning.findSpawnFeet(level, 5, 7, true, new Random(1)));
    }

    @Test
    void surfaceSearchLooksTenBlocksUpFromTheTopBlock() {
        FakeLevel level = new FakeLevel();
        level.groundIsFloor = false;
        level.solidTop = -1;
        // The top block is y 63; a floor at 73 is the tenth step up, 74 is out of reach.
        level.floors.add(FakeLevel.key(0, 73, 0));
        level.floors.add(FakeLevel.key(1, 74, 0));
        assertEquals(new Feet(0, 74, 0), SifterSpawning.findSpawnFeet(level, 0, 0, true, new Random(1)));
        assertNull(SifterSpawning.findSpawnFeet(level, 1, 0, true, new Random(1)));
    }

    @Test
    void aSpotNeedsTwoCollisionFreeBlocks() {
        FakeLevel level = new FakeLevel();
        level.solids.add(FakeLevel.key(0, 65, 0));
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, true, new Random(1)), "blocked above the head");
        level.solids.clear();
        level.solids.add(FakeLevel.key(0, 64, 0));
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, true, new Random(1)), "blocked at the feet");
        level.solids.clear();
        level.solids.add(FakeLevel.key(0, 66, 0));
        assertEquals(new Feet(0, 64, 0), SifterSpawning.findSpawnFeet(level, 0, 0, true, new Random(1)),
            "two blocks are enough");
    }

    @Test
    void aFloorThatIsNotAFloorBlockIsSkipped() {
        FakeLevel level = new FakeLevel();
        level.groundIsFloor = false;
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, true, new Random(1)));
    }

    @Test
    void caveSearchStartsAtARandomHeightAndLooksEighteenDown() {
        FakeLevel level = new FakeLevel();
        level.groundIsFloor = false;
        level.solidTop = -1;
        level.height = 100;
        level.floors.add(FakeLevel.key(0, 30, 0));
        // highestCaveFloor 94, minY + 2 = 2: the start is 2 + nextInt(93).
        assertEquals(new Feet(0, 31, 0), SifterSpawning.findSpawnFeet(level, 0, 0, false,
            new ScriptedRandom(0.5F, bound -> {
                assertEquals(93, bound);
                return 38; // start 40, 30 is ten steps down
            }, 0.0)));
        assertEquals(new Feet(0, 31, 0), SifterSpawning.findSpawnFeet(level, 0, 0, false,
            new ScriptedRandom(0.5F, bound -> 46, 0.0)), "start 48: exactly eighteen steps down");
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, false, new ScriptedRandom(0.5F, bound -> 47, 0.0)),
            "start 49: nineteen steps down is out of reach");
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, false, new ScriptedRandom(0.5F, bound -> 0, 0.0)),
            "start 2: the floor is above it");
    }

    @Test
    void caveSearchNeedsRoomBelowTheSurface() {
        FakeLevel level = new FakeLevel();
        level.height = 8; // highestCaveFloor 2 is not above minY + 2
        assertNull(SifterSpawning.findSpawnFeet(level, 0, 0, false, new Random(1)));
        level.height = 9;
        level.groundIsFloor = false;
        level.floors.add(FakeLevel.key(0, 2, 0));
        level.solidTop = -1;
        assertNotNull(SifterSpawning.findSpawnFeet(level, 0, 0, false, new Random(1)));
    }

    // --- one spawn attempt ---------------------------------------------------------------

    @Test
    void attemptsSpiralOutByTheGoldenAngleAtLeastThirtyTwoBlocks() {
        FakeLevel level = new FakeLevel();
        level.loaded = (x, z) -> false;
        ScriptedRandom random = new ScriptedRandom(0.5F, bound -> 0, 0.0);
        assertFalse(SifterSpawning.trySpawnNear(level, 100, -50, random, (feet, yaw) -> true));
        assertEquals(SifterSpawning.ATTEMPTS, level.loadedQueries.size());
        for (int attempt = 0; attempt < SifterSpawning.ATTEMPTS; attempt++) {
            double angle = attempt * 2.399963229728653;
            int[] query = level.loadedQueries.get(attempt);
            assertEquals(100 + (int) Math.round(Math.cos(angle) * 32), query[0], "x of attempt " + attempt);
            assertEquals(-50 + (int) Math.round(Math.sin(angle) * 32), query[1], "z of attempt " + attempt);
        }
        assertEquals(0, level.heightCalls, "unloaded columns are never read");
    }

    @Test
    void radiusReachesSeventySixBlocks() {
        FakeLevel level = new FakeLevel();
        level.loaded = (x, z) -> false;
        ScriptedRandom random = new ScriptedRandom(0.5F, bound -> {
            assertEquals(45, bound);
            return bound - 1;
        }, 0.0);
        SifterSpawning.trySpawnNear(level, 0, 0, random, (feet, yaw) -> true);
        int[] first = level.loadedQueries.get(0);
        assertEquals(76, first[0]);
        assertEquals(0, first[1]);
    }

    @Test
    void aSpotInAGoodBiomeSpawnsAGroupAroundIt() {
        FakeLevel level = new FakeLevel();
        List<Float> yaws = new ArrayList<>();
        // Phase 0, radius 32, surface, chance roll 0.0, group size 1 + 0, member offset -4.
        List<Feet> spawned = spawnAll(level, new ScriptedRandom(0.5F, bound -> 0, 0.0, 0.0), 10, 20, yaws);
        assertEquals(List.of(new Feet(42 - 4, 64, 20 - 4)), spawned);
        assertEquals(List.of(new Feet(42, 64, 20)), level.populationQueries);
        assertEquals(List.of(180.0F), yaws);
    }

    @Test
    void theChanceOfTheProfileDecidesWhetherASpotIsUsed() {
        FakeLevel level = new FakeLevel();
        List<Float> yaws = new ArrayList<>();
        // 0.36 is not enough for a roll of 0.36, enough for 0.3599.
        assertTrue(spawnAll(level, new ScriptedRandom(0.5F, bound -> 0, 0.0, 0.3599), 0, 0, yaws).size() > 0);
        level.populationQueries.clear();
        assertEquals(0, spawnAll(level, new ScriptedRandom(0.5F, bound -> 0, 0.0, 0.36), 0, 0, yaws).size());
        assertEquals(0, level.populationQueries.size(), "a failed roll does not even count the population");
        // A lower chance profile rejects the same roll.
        level.profile = (x, z) -> Profile.OTHER_SIFT;
        assertEquals(0, spawnAll(level, new ScriptedRandom(0.5F, bound -> 0, 0.0, 0.2), 0, 0, yaws).size());
        assertTrue(spawnAll(level, new ScriptedRandom(0.5F, bound -> 0, 0.0, 0.1799), 0, 0, yaws).size() > 0);
    }

    @Test
    void theSpotCountsSiftersAroundItAndAFullAreaEndsTheAttempt() {
        FakeLevel level = new FakeLevel();
        level.population = Profile.OVERGROWN_FOREST.localCap();
        List<Float> yaws = new ArrayList<>();
        Random random = new Random(7);
        assertEquals(0, spawnAll(level, random, 0, 0, yaws).size());
        // The first usable spot decides: no further attempts once the area is full.
        assertEquals(1, level.populationQueries.size());
    }

    @Test
    void groupsHoldOneToFiveAndNeverMoreThanTheRemainingRoom() {
        for (int room : new int[]{1, 2, 3, 5, 8}) {
            FakeLevel level = new FakeLevel();
            level.population = Profile.OVERGROWN_FOREST.localCap() - room;
            Set<Integer> sizes = new HashSet<>();
            for (long seed = 0; seed < 400; seed++) {
                level.populationQueries.clear();
                List<Feet> spawned = new ArrayList<>();
                SifterSpawning.trySpawnNear(level, 0, 0, new Random(seed), (feet, yaw) -> spawned.add(feet));
                // A member that finds no spot of its own spawns nothing, so sizes may fall short.
                assertTrue(spawned.size() <= Math.min(SifterSpawning.MAXIMUM_GROUP, room), "room " + room + " seed " + seed);
                if (!spawned.isEmpty()) {
                    sizes.add(spawned.size());
                }
            }
            assertTrue(sizes.contains(Math.min(SifterSpawning.MAXIMUM_GROUP, room)), "room " + room + " reaches its largest group: " + sizes);
            assertTrue(sizes.contains(1), "room " + room + " has single Sifters: " + sizes);
            assertTrue(sizes.stream().allMatch(size -> size >= 1));
        }
    }

    @Test
    void membersNeedTheSameProfileAsTheSpot() {
        FakeLevel level = new FakeLevel();
        Set<Feet> everyMember = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            // First find where the spot of this seed is, with every column of the same profile.
            level.profile = (x, z) -> Profile.OVERGROWN_FOREST;
            level.populationQueries.clear();
            SifterSpawning.trySpawnNear(level, 0, 0, new Random(seed), (feet, yaw) -> false);
            if (level.populationQueries.isEmpty()) {
                continue;
            }
            Feet spot = level.populationQueries.get(0);
            // Then let only the spot's own column keep its profile: members can only join it there.
            level.profile = (x, z) -> x == spot.x() && z == spot.z() ? Profile.OVERGROWN_FOREST : Profile.WASTES;
            List<Feet> spawned = new ArrayList<>();
            SifterSpawning.trySpawnNear(level, 0, 0, new Random(seed), (feet, yaw) -> spawned.add(feet));
            everyMember.addAll(spawned);
            for (Feet feet : spawned) {
                assertEquals(new Feet(spot.x(), 64, spot.z()), feet, "seed " + seed);
            }
        }
        assertFalse(everyMember.isEmpty());
    }

    @Test
    void membersSpreadOverNineByNineBlocks() {
        FakeLevel level = new FakeLevel();
        Set<Integer> dx = new HashSet<>();
        Set<Integer> dz = new HashSet<>();
        for (long seed = 0; seed < 600; seed++) {
            level.populationQueries.clear();
            List<Feet> spawned = new ArrayList<>();
            SifterSpawning.trySpawnNear(level, 0, 0, new Random(seed), (feet, yaw) -> spawned.add(feet));
            if (spawned.isEmpty()) {
                continue;
            }
            Feet spot = level.populationQueries.get(0);
            for (Feet feet : spawned) {
                assertTrue(Math.abs(feet.x() - spot.x()) <= 4 && Math.abs(feet.z() - spot.z()) <= 4);
                dx.add(feet.x() - spot.x());
                dz.add(feet.z() - spot.z());
            }
        }
        assertEquals(9, dx.size());
        assertEquals(9, dz.size());
    }

    @Test
    void aSpawnerThatRefusesSpawnsNothing() {
        FakeLevel level = new FakeLevel();
        for (long seed = 0; seed < 50; seed++) {
            assertFalse(SifterSpawning.trySpawnNear(level, 0, 0, new Random(seed), (feet, yaw) -> false));
        }
    }

    // --- one pass ------------------------------------------------------------------------

    @Test
    void peacefulSpawnsNothing() {
        FakeLevel level = new FakeLevel();
        assertFalse(SifterSpawning.pass(true, List.of(new Anchor(0, 0)), level, new Random(1), (feet, yaw) -> {
            throw new AssertionError("spawned on Peaceful");
        }));
        assertEquals(0, level.loadedQueries.size());
    }

    @Test
    void theFirstPlayerWhoGetsASpawnEndsThePass() {
        FakeLevel level = new FakeLevel();
        List<Feet> spawned = new ArrayList<>();
        boolean result = SifterSpawning.pass(false, List.of(new Anchor(0, 0), new Anchor(5000, 0), new Anchor(-5000, 0)),
            level, new Random(3), (feet, yaw) -> spawned.add(feet));
        assertTrue(result);
        assertFalse(spawned.isEmpty());
        // 76 blocks of radius plus 4 of spread: nobody spawned near the other players.
        assertTrue(spawned.stream().allMatch(feet -> Math.abs(feet.x()) <= 80 && Math.abs(feet.z()) <= 80), spawned.toString());
    }

    @Test
    void nextPlayerIsTriedWhenTheFirstOneGetsNothing() {
        FakeLevel level = new FakeLevel();
        // Only the second player's surroundings are loaded.
        level.loaded = (x, z) -> x > 4000;
        List<Feet> spawned = new ArrayList<>();
        assertTrue(SifterSpawning.pass(false, List.of(new Anchor(0, 0), new Anchor(5000, 0)), level, new Random(3),
            (feet, yaw) -> spawned.add(feet)));
        assertTrue(spawned.stream().allMatch(feet -> feet.x() > 4000));
    }

    @Test
    void noPlayersNoSpawns() {
        assertFalse(SifterSpawning.pass(false, List.of(), new FakeLevel(), new Random(1), (feet, yaw) -> true));
    }
}
