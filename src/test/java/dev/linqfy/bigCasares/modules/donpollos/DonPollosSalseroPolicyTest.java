package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DonPollosSalseroPolicyTest {

    private final DonPollosSalseroPolicy policy = new DonPollosSalseroPolicy(12.0, 2.5);

    @Test
    void idlesWithoutNearbyPlayers() {
        assertEquals(DonPollosSalseroAction.IDLE, policy.decide(OptionalDouble.empty()));
        assertEquals(DonPollosSalseroAction.IDLE, policy.decide(OptionalDouble.of(13.0 * 13.0)));
    }

    @Test
    void followsPlayersInsideRadius() {
        assertEquals(DonPollosSalseroAction.FOLLOW, policy.decide(OptionalDouble.of(10.0 * 10.0)));
        assertEquals(DonPollosSalseroAction.FOLLOW, policy.decide(OptionalDouble.of(12.0 * 12.0)));
    }

    @Test
    void stopsAndDancesWhenClose() {
        assertEquals(DonPollosSalseroAction.STOP, policy.decide(OptionalDouble.of(2.5 * 2.5)));
        assertEquals(DonPollosSalseroAction.STOP, policy.decide(OptionalDouble.of(1.0)));
    }

    @Test
    void rejectsInvalidDistances() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosSalseroPolicy(0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosSalseroPolicy(5.0, 6.0));
    }
}
