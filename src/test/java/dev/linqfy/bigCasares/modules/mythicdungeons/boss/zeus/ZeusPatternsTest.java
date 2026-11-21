package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusPatternsTest {

    private static final int RADIUS = 6;
    private static final int TELEGRAPH = ZeusStorm.MINIMUM_TELEGRAPH;
    private static final int CELLS = (2 * RADIUS + 1) * (2 * RADIUS + 1);

    @Test
    void everyWaveIsTelegraphedLongEnoughStaysOnTheGridAndLeavesRoomToDodge() {
        for (long seed = 0; seed < 40; seed++) {
            for (ZeusPatternKind kind : ZeusPatternKind.values()) {
                ZeusPattern pattern = ZeusPatterns.build(kind, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
                    List.of(new ZeusCell(3, -2), new ZeusCell(-5, 5)), new SplittableRandom(seed));
                assertEquals(kind, pattern.kind());
                for (ZeusStrikeWave wave : pattern.waves()) {
                    assertTrue(wave.telegraphTicks() >= 16, kind + " telegraph >= 0.8 s");
                    assertFalse(wave.cells().isEmpty(), kind + " empty wave");
                    for (ZeusCell cell : wave.cells()) {
                        assertTrue(Math.abs(cell.x()) <= RADIUS && Math.abs(cell.z()) <= RADIUS, kind + " " + cell);
                    }
                    assertTrue(wave.cells().size() <= CELLS * 0.6,
                        kind + " strikes " + wave.cells().size() + " of " + CELLS + " cells at once");
                }
            }
        }
    }

    @Test
    void wavesThatStrikeCloseTogetherNeverCoverTheWholeGridBetweenThem() {
        // dos tandas que caen a menos de 12 ticks no pueden tapar todo: siempre hay donde estar parado
        for (long seed = 0; seed < 20; seed++) {
            for (ZeusPatternKind kind : ZeusPatternKind.values()) {
                ZeusPattern pattern = ZeusPatterns.build(kind, RADIUS, TELEGRAPH, new ZeusCell(1, 1),
                    List.of(new ZeusCell(0, 0)), new SplittableRandom(seed));
                for (ZeusStrikeWave a : pattern.waves()) {
                    Set<ZeusCell> union = new HashSet<>(a.cells());
                    for (ZeusStrikeWave b : pattern.waves()) {
                        if (b != a && Math.abs(b.strikeTick() - a.strikeTick()) < 12) {
                            union.addAll(b.cells());
                        }
                    }
                    assertTrue(union.size() < CELLS, kind + " seed " + seed);
                }
            }
        }
    }

    @Test
    void checkerboardAndRingsAlternateSoTheLastStruckTilesAreSafeForTheNextWave() {
        ZeusPattern board = ZeusPatterns.build(ZeusPatternKind.CHECKERBOARD, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
            List.of(), new SplittableRandom(1));
        assertEquals(3, board.waves().size());
        Set<ZeusCell> first = board.waves().get(0).cells();
        Set<ZeusCell> second = board.waves().get(1).cells();
        assertTrue(first.stream().noneMatch(second::contains));
        assertEquals(CELLS, first.size() + second.size());
        assertTrue(board.waves().get(1).strikeTick() - board.waves().get(0).strikeTick() >= 20);

        ZeusPattern rings = ZeusPatterns.build(ZeusPatternKind.RINGS, RADIUS, TELEGRAPH, new ZeusCell(2, 0),
            List.of(), new SplittableRandom(1));
        for (ZeusStrikeWave wave : rings.waves()) {
            assertFalse(wave.cells().contains(new ZeusCell(2, 0)), "Zeus's own tile is never struck");
        }
        assertTrue(rings.waves().get(0).cells().stream().noneMatch(rings.waves().get(1).cells()::contains));
    }

    @Test
    void crossIsCenteredOnAPlayer() {
        ZeusCell target = new ZeusCell(-2, 3);
        ZeusPattern cross = ZeusPatterns.build(ZeusPatternKind.CROSS, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
            List.of(target), new SplittableRandom(3));
        Set<ZeusCell> first = cross.waves().getFirst().cells();
        assertTrue(first.contains(target));
        assertTrue(first.contains(new ZeusCell(-2, -6)));
        assertTrue(first.contains(new ZeusCell(6, 3)));
        assertFalse(first.contains(new ZeusCell(-1, 2)));
        assertEquals(4 * RADIUS + 1, first.size());
    }

    @Test
    void sweepWallsAlwaysKeepAThreeTileGap() {
        for (long seed = 0; seed < 30; seed++) {
            ZeusPattern sweep = ZeusPatterns.build(ZeusPatternKind.SWEEP, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
                List.of(), new SplittableRandom(seed));
            assertEquals(2 * (2 * RADIUS + 1), sweep.waves().size());
            for (ZeusStrikeWave wave : sweep.waves()) {
                assertEquals(2 * RADIUS + 1 - 3, wave.cells().size());
            }
        }
    }

    @Test
    void rainAimsItsFirstWaveAtThePlayersAndSpiralSparesTheCenter() {
        ZeusCell player = new ZeusCell(4, -4);
        ZeusPattern rain = ZeusPatterns.build(ZeusPatternKind.RAIN, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
            List.of(player), new SplittableRandom(9));
        assertTrue(rain.waves().getFirst().cells().contains(player));
        assertFalse(ZeusPatterns.onSpiralArm(new ZeusCell(0, 0), new ZeusCell(0, 0), 0.0));
        assertTrue(ZeusPatterns.onSpiralArm(new ZeusCell(1, 0), new ZeusCell(0, 0), -0.35));
    }

    @Test
    void outOfGridTargetsAreClampedAndBadInputsRejected() {
        ZeusPattern cross = ZeusPatterns.build(ZeusPatternKind.CROSS, RADIUS, TELEGRAPH, new ZeusCell(0, 0),
            List.of(new ZeusCell(40, -40)), new SplittableRandom(2));
        assertTrue(cross.waves().getFirst().cells().contains(new ZeusCell(RADIUS, -RADIUS)));
        assertThrows(IllegalArgumentException.class, () -> ZeusPatterns.build(
            ZeusPatternKind.LINES, 1, TELEGRAPH, new ZeusCell(0, 0), List.of(), new SplittableRandom(1)));
    }
}
