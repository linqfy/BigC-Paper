package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La altura del techo del tren segun la posicion a lo largo. */
class TrainPassengersTest {

    @Test
    void inFrontOfTheCabThresholdTheRoofIsTheCabHeight() {
        assertEquals(4.65, TrainPassengers.roofAt(1.45 + 0.01), 1e-9);
        assertEquals(4.65, TrainPassengers.roofAt(3.0), 1e-9);
    }

    @Test
    void behindItTheRoofIsTheBoilerHeight() {
        assertEquals(3.45, TrainPassengers.roofAt(1.44), 1e-9);
        assertEquals(3.45, TrainPassengers.roofAt(-2.0), 1e-9);
    }
}
