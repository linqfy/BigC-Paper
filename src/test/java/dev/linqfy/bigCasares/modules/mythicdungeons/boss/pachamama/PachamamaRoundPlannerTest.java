package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaRoundPlannerTest {

    private static List<PachamamaTile> board(int columns, int rows) {
        List<PachamamaTile> tiles = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                tiles.add(new PachamamaTile(column, row));
            }
        }
        return tiles;
    }

    @Test
    void theColourIsRandomButNeverRepeatsTheLastRound() {
        RandomGenerator random = new Random(7);
        PachamamaColour previous = null;
        Set<PachamamaColour> seen = EnumSet.noneOf(PachamamaColour.class);
        for (int round = 0; round < 200; round++) {
            PachamamaColour colour = PachamamaRoundPlanner.colour(previous, random);
            assertNotEquals(previous, colour);
            seen.add(colour);
            previous = colour;
        }
        assertEquals(EnumSet.allOf(PachamamaColour.class), seen);
    }

    @Test
    void brownAndYellowPaintOneColourOfTheChessboard() {
        List<PachamamaTile> usable = board(11, 11);
        for (long seed = 0; seed < 40; seed++) {
            for (PachamamaColour colour : List.of(PachamamaColour.BROWN, PachamamaColour.YELLOW)) {
                Set<PachamamaTile> painted = PachamamaRoundPlanner.cells(colour, usable, false,
                    new Random(seed));
                assertFalse(painted.isEmpty());
                int parity = painted.iterator().next().parity();
                assertTrue(painted.stream().allMatch(tile -> tile.parity() == parity), "not a chessboard");
                assertTrue(painted.size() <= usable.size() * PachamamaRoundPlanner.MAX_PAINTED_SHARE);
            }
        }
    }

    @Test
    void blueScattersAboutItsShareAndLeavesMostTilesSafe() {
        List<PachamamaTile> usable = board(11, 11);
        Set<PachamamaTile> phaseOne = PachamamaRoundPlanner.cells(PachamamaColour.BLUE, usable, false,
            new Random(7));
        Set<PachamamaTile> phaseTwo = PachamamaRoundPlanner.cells(PachamamaColour.BLUE, usable, true,
            new Random(7));
        assertEquals(Math.round(usable.size() * PachamamaTuning.BLUE_SHARE_ONE), phaseOne.size());
        assertEquals(Math.round(usable.size() * PachamamaTuning.BLUE_SHARE_TWO), phaseTwo.size());
        assertTrue(phaseTwo.size() < phaseOne.size(), "phase 2 rounds are lighter");
        boolean mixedParity = phaseOne.stream().map(PachamamaTile::parity).distinct().count() == 2;
        assertTrue(mixedParity, "blue is not a strict chessboard");
    }

    @Test
    void phaseTwoChessboardsAreLighterOnAverage() {
        List<PachamamaTile> usable = board(11, 11);
        RandomGenerator random = new Random(7);
        int one = 0;
        int two = 0;
        for (int round = 0; round < 100; round++) {
            one += PachamamaRoundPlanner.cells(PachamamaColour.BROWN, usable, false, random).size();
            two += PachamamaRoundPlanner.cells(PachamamaColour.BROWN, usable, true, random).size();
        }
        assertTrue(two < one * 0.8, one + " vs " + two);
    }

    @Test
    void onlyUsableTilesArePaintedAndAtLeastOne() {
        List<PachamamaTile> usable = List.of(new PachamamaTile(4, 4), new PachamamaTile(5, 4),
            new PachamamaTile(7, 1));
        RandomGenerator random = new Random(7);
        for (int round = 0; round < 50; round++) {
            PachamamaRoundPlanner.Round plan = PachamamaRoundPlanner.plan(usable, round % 2 == 0, null, random);
            assertFalse(plan.painted().isEmpty());
            assertTrue(usable.containsAll(plan.painted()));
            assertTrue(plan.painted().size() <= 1, "at most half of three tiles");
        }
        assertTrue(PachamamaRoundPlanner.cells(PachamamaColour.BLUE, List.of(), false, random).isEmpty());
    }
}
