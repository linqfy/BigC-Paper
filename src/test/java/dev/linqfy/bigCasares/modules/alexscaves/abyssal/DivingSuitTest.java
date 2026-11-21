package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivingSuitTest {
    @Test
    void helmetOnlyRenewsAShorterBreathing() {
        assertTrue(DivingRules.shouldRenew(0, 100));
        assertFalse(DivingRules.shouldRenew(0, 3600));
        assertFalse(DivingRules.shouldRenew(1, 100));
    }
}
