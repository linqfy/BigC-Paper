package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.EchoGolemSpawning.Reason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoGolemSpawningTest {

    /** Flat ground whose top block is y 63, with knobs. */
    private static class Flat implements EchoGolemSpawning.Level {
        boolean allLoaded = true;
        final Set<SiftPos> golems = new HashSet<>();
        final Set<SiftPos> solid = new HashSet<>();

        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { return allLoaded; }
        @Override public int height(int x, int z) { return 64; }
        @Override public boolean isFullBlock(SiftPos pos) { return pos.y() == 63 || solid.contains(pos); }
        @Override public boolean isEmptySpace(SiftPos pos) { return pos.y() >= 64 && !solid.contains(pos); }

        @Override
        public boolean golemNear(SiftPos around, double horizontal, double vertical) {
            return golems.stream().anyMatch(golem -> Math.abs(golem.x() - around.x()) <= horizontal
                && Math.abs(golem.y() - around.y()) <= vertical && Math.abs(golem.z() - around.z()) <= horizontal);
        }
    }

    private record Spawn(SiftPos feet, Reason reason) {
    }

    @Test
    void aPlayerWithoutAGolemNearbyGetsOneBetween82And160BlocksAway() {
        Flat level = new Flat();
        List<Spawn> spawned = new ArrayList<>();
        SiftPos player = new SiftPos(1000, 64, -500);
        boolean placed = EchoGolemSpawning.pass(level, List.of(), entrance -> { }, List.of(player), new Random(5), (feet, reason) -> {
            spawned.add(new Spawn(feet, reason));
            return true;
        });
        assertTrue(placed);
        assertEquals(1, spawned.size());
        assertEquals(Reason.NATURAL, spawned.get(0).reason());
        double distance = Math.sqrt(spawned.get(0).feet().horizontalDistanceSqr(player));
        assertTrue(distance >= 81.0 && distance <= 162.0, "distance " + distance);
        assertEquals(64, spawned.get(0).feet().y());
    }

    @Test
    void nothingSpawnsWhileAGolemIsWithin165BlocksOfThePlayer() {
        Flat level = new Flat();
        level.golems.add(new SiftPos(1100, 64, -500));
        boolean placed = EchoGolemSpawning.pass(level, List.of(), entrance -> { }, List.of(new SiftPos(1000, 64, -500)),
            new Random(5), (feet, reason) -> true);
        assertFalse(placed);
        level.golems.clear();
        level.golems.add(new SiftPos(1000, 64 + 97, -500));
        assertTrue(EchoGolemSpawning.pass(level, List.of(), entrance -> { }, List.of(new SiftPos(1000, 64, -500)), new Random(5),
            (feet, reason) -> true), "a golem 97 blocks higher is out of the 96 block box");
    }

    @Test
    void aSpotNeedsLoadedChunksAroundItAndKeepsApartFromGolemsThereToo() {
        Flat unloaded = new Flat();
        unloaded.allLoaded = false;
        assertFalse(EchoGolemSpawning.pass(unloaded, List.of(), entrance -> { }, List.of(new SiftPos(0, 64, 0)), new Random(2),
            (feet, reason) -> true));
        // A golem 200 blocks from the player and 120 from the spot's side leaves some spots free, others not.
        Flat crowded = new Flat();
        crowded.golems.add(new SiftPos(200, 64, 0));
        List<SiftPos> spawned = new ArrayList<>();
        EchoGolemSpawning.pass(crowded, List.of(), entrance -> { }, List.of(new SiftPos(0, 64, 0)), new Random(2), (feet, reason) -> {
            spawned.add(feet);
            return true;
        });
        assertEquals(1, spawned.size());
        assertTrue(Math.abs(spawned.get(0).x() - 200) > 165 || Math.abs(spawned.get(0).z()) > 165);
    }

    @Test
    void aFailedSpawnTriesTheNextSpotUpTo28Times() {
        int[] attempts = {0};
        boolean placed = EchoGolemSpawning.pass(new Flat(), List.of(), entrance -> { }, List.of(new SiftPos(0, 64, 0)), new Random(2),
            (feet, reason) -> {
                attempts[0]++;
                return false;
            });
        assertFalse(placed);
        assertEquals(28, attempts[0]);
    }

    @Test
    void aCanyonEntranceGetsItsGolemFirstAndIsForgotten() {
        Flat level = new Flat();
        SiftPos entrance = new SiftPos(50, 62, 50);
        List<Spawn> spawned = new ArrayList<>();
        List<SiftPos> forgotten = new ArrayList<>();
        boolean placed = EchoGolemSpawning.pass(level, List.of(entrance), forgotten::add, List.of(new SiftPos(0, 64, 0)),
            new Random(2), (feet, reason) -> {
                spawned.add(new Spawn(feet, reason));
                return true;
            });
        assertTrue(placed);
        assertEquals(List.of(entrance), forgotten);
        assertEquals(1, spawned.size(), "the pass ends with the first golem");
        assertEquals(Reason.STRUCTURE, spawned.get(0).reason());
        // The first ring that works is the entrance column itself, 2 blocks above... within 6 of the entrance's Y.
        assertEquals(new SiftPos(50, 64, 50), spawned.get(0).feet());
    }

    @Test
    void anEntranceTooFarAboveOrBelowTheGroundIsNotUsedAndStaysPending() {
        Flat level = new Flat();
        SiftPos entrance = new SiftPos(50, 40, 50);
        List<SiftPos> forgotten = new ArrayList<>();
        int[] attempts = {0};
        boolean placed = EchoGolemSpawning.pass(level, List.of(entrance), forgotten::add, List.of(), new Random(2), (feet, reason) -> {
            attempts[0]++;
            return true;
        });
        assertFalse(placed);
        assertEquals(0, attempts[0]);
        assertTrue(forgotten.isEmpty());
    }

    @Test
    void anUnloadedEntranceWaits() {
        Flat level = new Flat();
        level.allLoaded = false;
        List<SiftPos> forgotten = new ArrayList<>();
        assertFalse(EchoGolemSpawning.pass(level, List.of(new SiftPos(0, 64, 0)), forgotten::add, List.of(), new Random(1),
            (feet, reason) -> true));
        assertTrue(forgotten.isEmpty());
    }

    @Test
    void theFloorSearchGoesDownTwelveBlocksAndNeedsThreeFreeBlocks() {
        Flat level = new Flat();
        assertEquals(new SiftPos(0, 64, 0), EchoGolemSpawning.findSpawnFeet(level, 0, 0));
        // A roof over the surface two blocks up: no room at 64, a floor 1 lower is not full, so none.
        level.solid.add(new SiftPos(0, 66, 0));
        assertNull(EchoGolemSpawning.findSpawnFeet(level, 0, 0));
        // Ground at 50 (14 under the top block) is beyond the search; at 51 (12 under it) it is not.
        Flat deep = new Flat() {
            @Override public boolean isFullBlock(SiftPos pos) { return pos.y() == 50; }
            @Override public boolean isEmptySpace(SiftPos pos) { return pos.y() >= 51; }
        };
        assertNull(EchoGolemSpawning.findSpawnFeet(deep, 0, 0));
        Flat reachable = new Flat() {
            @Override public boolean isFullBlock(SiftPos pos) { return pos.y() == 51; }
            @Override public boolean isEmptySpace(SiftPos pos) { return pos.y() >= 52; }
        };
        assertEquals(new SiftPos(0, 52, 0), EchoGolemSpawning.findSpawnFeet(reachable, 0, 0));
    }
}
