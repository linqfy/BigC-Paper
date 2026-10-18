package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.RandomTickSampler.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTickSamplerTest {

    private static List<ChunkPos> chunks(int side) {
        List<ChunkPos> list = new ArrayList<>();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                list.add(new ChunkPos(x - side / 2, z - side / 2));
            }
        }
        return list;
    }

    @Test
    void aSectionGetsItsConfiguredRatePerSecond() {
        // -64..320 is 24 sections; 8 a second with a run every 5 ticks is 2 a run per section.
        RandomTickSampler sampler = new RandomTickSampler(-64, 320, 8, 5, 10_000);
        assertEquals(24, sampler.sections());
        assertEquals(48, sampler.maxPerChunk());
        List<ChunkPos> one = List.of(new ChunkPos(0, 0));
        int total = 0;
        Random random = new Random(7);
        for (int run = 0; run < 4; run++) {
            total += sampler.next(one, random).size();
        }
        assertEquals(4 * 48, total, "four runs are one second: 8 samples in each of 24 sections");
    }

    @Test
    void fractionalRatesAverageOut() {
        // 3 a second, a run every 5 ticks: 0.75 a section per run.
        RandomTickSampler sampler = new RandomTickSampler(0, 16, 3, 5, 100);
        Random random = new Random(11);
        int total = 0;
        int runs = 4000;
        for (int run = 0; run < runs; run++) {
            total += sampler.next(List.of(new ChunkPos(0, 0)), random).size();
        }
        assertEquals(0.75 * runs, total, 0.05 * 0.75 * runs);
    }

    @Test
    void positionsAreUniformInsideTheirSections() {
        RandomTickSampler sampler = new RandomTickSampler(-64, 320, 20, 20, 10_000);
        Random random = new Random(3);
        Set<Integer> sectionsSeen = new HashSet<>();
        for (int run = 0; run < 3; run++) {
            for (SiftPos pos : sampler.next(List.of(new ChunkPos(-3, 5)), random)) {
                assertTrue(pos.x() >= -48 && pos.x() < -32, "x inside chunk -3: " + pos);
                assertTrue(pos.z() >= 80 && pos.z() < 96, "z inside chunk 5: " + pos);
                assertTrue(pos.y() >= -64 && pos.y() < 320, "y inside the world: " + pos);
                sectionsSeen.add(Math.floorDiv(pos.y() + 64, 16));
            }
        }
        assertEquals(24, sectionsSeen.size(), "every section is sampled");
    }

    @Test
    void aRunNeverExceedsItsBudgetHoweverManyChunksAsk() {
        RandomTickSampler sampler = new RandomTickSampler(-64, 320, 8, 5, 500);
        List<ChunkPos> many = chunks(41);
        Random random = new Random(5);
        for (int run = 0; run < 200; run++) {
            assertTrue(sampler.next(many, random).size() <= 500, "budget held on run " + run);
        }
    }

    @Test
    void theBudgetRotatesSoEveryChunkIsEventuallyVisited() {
        RandomTickSampler sampler = new RandomTickSampler(-64, 320, 8, 5, 500);
        List<ChunkPos> many = chunks(21);
        Random random = new Random(9);
        Map<ChunkPos, Integer> visits = new HashMap<>();
        for (int run = 0; run < 400; run++) {
            for (SiftPos pos : sampler.next(many, random)) {
                visits.merge(new ChunkPos(pos.x() >> 4, pos.z() >> 4), 1, Integer::sum);
            }
        }
        assertEquals(many.size(), visits.size(), "no chunk starves");
        int min = visits.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = visits.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(max - min <= 2 * sampler.maxPerChunk(), "fair share: " + min + ".." + max);
    }

    @Test
    void anOverloadedWorldIsSampledLessNotMore() {
        RandomTickSampler sampler = new RandomTickSampler(-64, 320, 8, 5, 4000);
        Random random = new Random(1);
        int small = 0;
        int large = 0;
        for (int run = 0; run < 100; run++) {
            small += sampler.next(chunks(5), random).size();
        }
        sampler = new RandomTickSampler(-64, 320, 8, 5, 4000);
        for (int run = 0; run < 100; run++) {
            large += sampler.next(chunks(41), random).size();
        }
        assertEquals(100 * 25 * 48, small, "a few chunks get the full rate");
        assertTrue(large <= 100 * 4000, "many chunks stay inside the budget");
    }

    @Test
    void noChunksNoSamplesAndACursorPastTheEndStartsOver() {
        RandomTickSampler sampler = new RandomTickSampler(0, 64, 8, 5, 1000);
        assertTrue(sampler.next(List.of(), new Random(1)).isEmpty());
        sampler.next(chunks(9), new Random(1));
        int cursor = sampler.cursor();
        assertTrue(cursor >= 0);
        // The list shrank below the cursor between runs.
        assertTrue(sampler.next(List.of(new ChunkPos(0, 0)), new Random(1)).size() > 0);
        assertEquals(0, sampler.cursor());
    }

    @Test
    void aBudgetThatCannotHoldOneChunkIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RandomTickSampler(-64, 320, 8, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> new RandomTickSampler(10, 10, 8, 5, 100));
    }

    @Test
    void shippedLimitsHoldAChunkAtTheHighestAllowedRate() {
        // 60 a second is the setting's maximum; the production budget must still hold one chunk of the tallest world.
        new RandomTickSampler(-64, 320, SiftTickSettings.MAX_SAMPLES, SiftRandomTicks.INTERVAL_TICKS,
            SiftRandomTicks.MAX_SAMPLES_PER_RUN);
    }
}
