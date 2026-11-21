package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusStormTest {

    private static ZeusPattern twoWaves(ZeusPatternKind kind, int telegraph) {
        return new ZeusPattern(kind, List.of(
            new ZeusStrikeWave(0, telegraph, Set.of(new ZeusCell(0, 0))),
            new ZeusStrikeWave(10, telegraph, Set.of(new ZeusCell(1, 0)))));
    }

    @Test
    void telegraphAndRestShrinkWithHealthButNeverBelowTheMinimum() {
        assertEquals(ZeusStorm.FULL_HEALTH_TELEGRAPH, ZeusStorm.telegraphFor(1.0));
        assertEquals(ZeusStorm.MINIMUM_TELEGRAPH, ZeusStorm.telegraphFor(0.5));
        assertEquals(ZeusStorm.MINIMUM_TELEGRAPH, ZeusStorm.telegraphFor(0.1));
        assertTrue(ZeusStorm.telegraphFor(0.75) < ZeusStorm.FULL_HEALTH_TELEGRAPH);
        assertTrue(ZeusStorm.MINIMUM_TELEGRAPH >= 16, "at least 0.8 s");
        assertEquals(ZeusStorm.FULL_HEALTH_REST, ZeusStorm.restFor(1.0));
        assertEquals(ZeusStorm.LOW_HEALTH_REST, ZeusStorm.restFor(0.5));
    }

    @Test
    void runsAPatternWarnsThenStrikesEachWaveOnceThenRests() {
        List<Integer> telegraphs = new ArrayList<>();
        ZeusStorm storm = new ZeusStorm((kind, telegraph) -> {
            telegraphs.add(telegraph);
            return twoWaves(kind, telegraph);
        }, new SplittableRandom(4), 0);

        ZeusPattern started = null;
        int warned = 0;
        List<Integer> strikeTicks = new ArrayList<>();
        for (int tick = 1; tick <= ZeusStorm.FIRST_PATTERN_DELAY + 60; tick += 2) {
            ZeusStorm.Step step = storm.advance(tick, 1.0);
            if (step.started() != null) {
                assertEquals(null, started, "only one pattern in this window");
                started = step.started();
                assertTrue(tick >= ZeusStorm.FIRST_PATTERN_DELAY);
            }
            warned += step.warned().size();
            for (ZeusStrikeWave ignored : step.struck()) {
                strikeTicks.add(tick);
            }
        }
        assertNotNull(started);
        assertEquals(List.of(ZeusStorm.FULL_HEALTH_TELEGRAPH), telegraphs);
        assertEquals(2, warned);
        assertEquals(2, strikeTicks.size());
        assertFalse(storm.casting(), "resting after the last strike");
    }

    @Test
    void telegraphProgressGoesFromZeroToOne() {
        ZeusStorm storm = new ZeusStorm((kind, telegraph) -> twoWaves(kind, 20), new SplittableRandom(1), 0);
        storm.advance(ZeusStorm.FIRST_PATTERN_DELAY, 1.0);
        List<ZeusStorm.Telegraph> now = storm.telegraphs(ZeusStorm.FIRST_PATTERN_DELAY);
        assertEquals(1, now.size());
        assertEquals(0.0, now.getFirst().progress());
        List<ZeusStorm.Telegraph> later = storm.telegraphs(ZeusStorm.FIRST_PATTERN_DELAY + 15);
        assertEquals(2, later.size());
        assertEquals(0.75, later.getFirst().progress(), 1.0e-9);
    }

    @Test
    void neverRepeatsAPatternBackToBackAndStopsCleanly() {
        ZeusStorm storm = new ZeusStorm((kind, telegraph) -> twoWaves(kind, telegraph), new SplittableRandom(7), 0);
        ZeusPatternKind previous = null;
        int patterns = 0;
        for (int tick = 1; tick < 5000; tick++) {
            ZeusStorm.Step step = storm.advance(tick, 0.6);
            if (step.started() != null) {
                assertNotEquals(previous, step.started().kind());
                previous = step.started().kind();
                patterns++;
            }
        }
        assertTrue(patterns > 20);
        storm.stop();
        assertTrue(storm.stopped());
        assertTrue(storm.telegraphs(5001).isEmpty());
        assertTrue(storm.advance(9000, 0.6).struck().isEmpty());
    }
}
