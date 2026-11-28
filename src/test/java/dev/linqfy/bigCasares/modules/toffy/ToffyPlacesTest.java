package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyPlacesTest {
    @Test
    void playersAreSpreadRoundRobinOverTheTwoPoints() {
        List<Integer> spots = ToffyPlaces.assign(2, 5, new Random(7));

        assertEquals(5, spots.size());
        for (int player = 0; player + 1 < spots.size(); player++) {
            assertNotEquals(spots.get(player), spots.get(player + 1), "neighbours go to different points");
        }
        assertEquals(Set.of(0, 1), Set.copyOf(spots));
    }

    @Test
    void threePointsAndThreePlayersUseEachPointOnce() {
        assertEquals(Set.of(0, 1, 2), new HashSet<>(ToffyPlaces.assign(3, 3, new Random(1))));
    }

    @Test
    void theStartingPointIsShuffled() {
        Set<Integer> firstSpots = new HashSet<>();
        for (long seed = 1; seed <= 30; seed++) {
            firstSpots.add(ToffyPlaces.assign(4, 1, new Random(seed)).getFirst());
        }

        assertTrue(firstSpots.size() > 1, "the first player does not always land on the first point");
    }

    @Test
    void aSinglePointTakesEveryone() {
        assertEquals(List.of(0, 0, 0, 0), ToffyPlaces.assign(1, 4, new Random(3)));
    }

    @Test
    void noPlacesIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> ToffyPlaces.assign(0, 1, new Random(3)));
    }
}
