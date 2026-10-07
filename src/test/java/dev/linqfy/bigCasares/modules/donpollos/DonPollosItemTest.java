package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DonPollosItemTest {

    @Test
    void normalizesMaterialToUpperCase() {
        assertEquals("BREAD", new DonPollosItem(" bread ", 3).material());
    }

    @Test
    void rejectsBlankMaterial() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosItem(" ", 1));
    }

    @Test
    void rejectsAmountsOutsideOneStack() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosItem("BREAD", 0));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosItem("BREAD", 641));
    }

    @Test
    void tradeRequiresCostAndResult() {
        assertThrows(NullPointerException.class, () -> new DonPollosTrade(null, new DonPollosItem("BREAD", 1)));
        assertThrows(NullPointerException.class, () -> new DonPollosTrade(new DonPollosItem("BREAD", 1), null));
    }
}
