package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulExchangeTest {

    /** Flat ground at y 63 (surface at 64) with knobs. */
    private static final class Flat implements SoulExchange.Level {
        boolean loaded = true;
        final Set<SiftPos> blocked = new HashSet<>();
        boolean groundIsFull = true;

        @Override public int height(int x, int z) { return 64; }
        @Override public boolean isLoaded(SiftPos pos) { return loaded; }
        @Override public boolean isFullBlock(SiftPos pos) { return groundIsFull && pos.y() == 63; }
        @Override public boolean isAir(SiftPos pos) { return !blocked.contains(pos) && pos.y() >= 64; }
    }

    @Test
    void theSpawnIsSevenToTwelveBlocksFromTheGolemOnTheSurface() {
        SiftPos golem = new SiftPos(100, 64, -40);
        for (long seed = 0; seed < 50; seed++) {
            SiftPos spot = SoulExchange.findNaturalSpawn(new Flat(), golem, null, new Random(seed)).orElseThrow();
            double distance = Math.sqrt(spot.horizontalDistanceSqr(golem));
            assertTrue(distance >= 6.5 && distance <= 12.8, "distance " + distance);
            assertEquals(64, spot.y());
        }
    }

    @Test
    void aSpotNeedsAFullBlockUnderItFiveBlocksOfAirAndALoadedChunk() {
        Flat noGround = new Flat();
        noGround.groundIsFull = false;
        assertTrue(SoulExchange.findNaturalSpawn(noGround, new SiftPos(0, 64, 0), null, new Random(1)).isEmpty());
        Flat unloaded = new Flat();
        unloaded.loaded = false;
        assertTrue(SoulExchange.findNaturalSpawn(unloaded, new SiftPos(0, 64, 0), null, new Random(1)).isEmpty());

        Flat roofed = new Flat();
        // Only four blocks of headroom everywhere around: the fifth block (dy 4) is not air.
        SoulExchange.Level lowCeiling = new SoulExchange.Level() {
            @Override public int height(int x, int z) { return roofed.height(x, z); }
            @Override public boolean isLoaded(SiftPos pos) { return true; }
            @Override public boolean isFullBlock(SiftPos pos) { return roofed.isFullBlock(pos); }
            @Override public boolean isAir(SiftPos pos) { return pos.y() >= 64 && pos.y() < 68; }
        };
        assertTrue(SoulExchange.findNaturalSpawn(lowCeiling, new SiftPos(0, 64, 0), null, new Random(1)).isEmpty());
    }

    @Test
    void anActiveCanyonKeepsHerAtLeastTwentyFourBlocksAway() {
        SiftPos canyon = new SiftPos(0, 40, 0);
        for (long seed = 0; seed < 30; seed++) {
            // The golem stands 30 blocks from the canyon: spots on the canyon's side are rejected, the others kept.
            Optional<SiftPos> spot = SoulExchange.findNaturalSpawn(new Flat(), new SiftPos(30, 64, 0), canyon, new Random(seed));
            assertTrue(spot.isPresent(), "seed " + seed);
            assertFalse(spot.get().horizontalDistanceSqr(canyon) < 576.0);
        }
        assertTrue(SoulExchange.findNaturalSpawn(new Flat(), new SiftPos(0, 64, 0), canyon, new Random(3)).isEmpty(),
            "with the golem on top of the canyon no spot is 24 blocks away");
    }

    @Test
    void theSearchIsDeterministicForASeed() {
        SiftPos golem = new SiftPos(5, 64, 5);
        assertEquals(SoulExchange.findNaturalSpawn(new Flat(), golem, null, new Random(9)),
            SoulExchange.findNaturalSpawn(new Flat(), golem, null, new Random(9)));
    }
}
