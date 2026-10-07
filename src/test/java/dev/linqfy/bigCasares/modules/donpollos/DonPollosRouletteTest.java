package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosRouletteTest {

    private final DonPollosRoulette roulette = DonPollosRoulette.standard();

    @Test
    void chancesAreTwentyFourTwentyFourAndTwoOutOfFifty() {
        assertEquals(50, roulette.totalWeight());
        assertEquals(24, roulette.weight(DonPollosRouletteColor.RED));
        assertEquals(24, roulette.weight(DonPollosRouletteColor.BLACK));
        assertEquals(2, roulette.weight(DonPollosRouletteColor.GREEN));
    }

    @Test
    void rollsMapToColorsByCumulativeWeight() {
        assertEquals(DonPollosRouletteColor.RED, roulette.spin(bound -> 0));
        assertEquals(DonPollosRouletteColor.RED, roulette.spin(bound -> 23));
        assertEquals(DonPollosRouletteColor.BLACK, roulette.spin(bound -> 24));
        assertEquals(DonPollosRouletteColor.BLACK, roulette.spin(bound -> 47));
        assertEquals(DonPollosRouletteColor.GREEN, roulette.spin(bound -> 48));
        assertEquals(DonPollosRouletteColor.GREEN, roulette.spin(bound -> 49));
    }

    @Test
    void winningDoublesTheBetAndLosingPaysNothing() {
        assertEquals(32, roulette.payout(16, DonPollosRouletteColor.RED, DonPollosRouletteColor.RED));
        assertEquals(2, roulette.payout(1, DonPollosRouletteColor.GREEN, DonPollosRouletteColor.GREEN));
        assertEquals(0, roulette.payout(16, DonPollosRouletteColor.RED, DonPollosRouletteColor.BLACK));
    }

    @Test
    void wheelHasTwoGreensAndAlternatingRedAndBlack() {
        List<DonPollosRouletteColor> wheel = roulette.wheel();
        assertEquals(DonPollosRoulette.POCKETS, wheel.size());
        assertEquals(2, wheel.stream().filter(c -> c == DonPollosRouletteColor.GREEN).count());
        assertEquals(12, wheel.stream().filter(c -> c == DonPollosRouletteColor.RED).count());
        assertEquals(12, wheel.stream().filter(c -> c == DonPollosRouletteColor.BLACK).count());
        for (int index = 0; index < wheel.size(); index++) {
            assertNotEquals(wheel.get(index), wheel.get((index + 1) % wheel.size()), "pocket " + index);
        }
    }

    @Test
    void ballStopsOnAPocketOfTheResultColor() {
        for (DonPollosRouletteColor color : DonPollosRouletteColor.values()) {
            for (int pick = 0; pick < 12; pick++) {
                int chosen = pick;
                int pocket = roulette.stopPocket(color, bound -> chosen % bound);
                assertEquals(color, roulette.wheel().get(pocket));
            }
        }
    }

    @Test
    void ballDoesTwoFullLapsThenStopsAndSlowsDown() {
        List<Integer> delays = DonPollosRoulette.stepDelays(7);
        assertEquals(2 * DonPollosRoulette.POCKETS + 7, delays.size());
        assertEquals(7, delays.size() % DonPollosRoulette.POCKETS);
        assertEquals(1, delays.getFirst());
        assertTrue(delays.getLast() > 5);
        for (int index = 1; index < delays.size(); index++) {
            assertTrue(delays.get(index) >= delays.get(index - 1));
        }
    }

    @Test
    void rejectsInvalidWeightsAndMultipliers() {
        Map<DonPollosRouletteColor, Integer> ok = Map.of(
            DonPollosRouletteColor.RED, 1, DonPollosRouletteColor.BLACK, 1, DonPollosRouletteColor.GREEN, 1);
        assertThrows(IllegalArgumentException.class, () -> new DonPollosRoulette(
            Map.of(DonPollosRouletteColor.RED, 0, DonPollosRouletteColor.BLACK, 1, DonPollosRouletteColor.GREEN, 1), ok));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosRoulette(ok,
            Map.of(DonPollosRouletteColor.RED, 2, DonPollosRouletteColor.BLACK, 0, DonPollosRouletteColor.GREEN, 2)));
    }
}
