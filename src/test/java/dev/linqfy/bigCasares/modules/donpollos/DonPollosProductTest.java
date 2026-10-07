package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DonPollosProductTest {

    @Test
    void sauceGivesStrengthForTenSeconds() {
        assertEquals("strength", DonPollosProduct.SALSA.effect());
        assertEquals(0, DonPollosProduct.SALSA.amplifier());
        assertEquals(10, DonPollosProduct.SALSA.seconds());
    }

    @Test
    void spicyGivesSpeedThreeForTenSeconds() {
        assertEquals("speed", DonPollosProduct.PICANTE.effect());
        assertEquals(2, DonPollosProduct.PICANTE.amplifier());
        assertEquals(10, DonPollosProduct.PICANTE.seconds());
    }

    @Test
    void friedChickenIsPlainFood() {
        assertNull(DonPollosProduct.POLLO_FRITO.effect());
    }

    @Test
    void resolvesProductIds() {
        assertEquals(Optional.of(DonPollosProduct.POLLO_FRITO), DonPollosProduct.fromId(" Pollo-Frito "));
        assertEquals(Optional.empty(), DonPollosProduct.fromId("hamburguesa"));
    }
}
