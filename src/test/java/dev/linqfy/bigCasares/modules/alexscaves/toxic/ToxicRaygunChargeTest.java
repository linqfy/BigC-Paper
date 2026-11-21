package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToxicRaygunChargeTest {

    @Test
    void pendingChargeIsAddedAndClamped() {
        assertEquals(15, ToxicRaygun.withPending(10, 5));
        assertEquals(ToxicRaygun.MAX_CHARGE, ToxicRaygun.withPending(ToxicRaygun.MAX_CHARGE - 2, 5));
        assertEquals(0, ToxicRaygun.withPending(0, -3));
    }

    @Test
    void onlyFullPowerBeamsUseChargeEverySecondTick() {
        assertFalse(ToxicRaygun.usesCharge(0.5F, 4, false), "warm-up is free");
        assertTrue(ToxicRaygun.usesCharge(1.0F, 20, false));
        assertFalse(ToxicRaygun.usesCharge(1.0F, 21, false));
        assertFalse(ToxicRaygun.usesCharge(1.0F, 20, true), "creative never uses charge");
    }

    @Test
    void damageLandedOnlyWhenHealthOrAbsorptionDropped() {
        assertTrue(DamageProbe.landed(20.0, 18.5, false));
        assertTrue(DamageProbe.landed(1.0, 1.0, true));
        assertFalse(DamageProbe.landed(20.0, 20.0, false), "a cancelled hit leaves the pool untouched");
    }
}
