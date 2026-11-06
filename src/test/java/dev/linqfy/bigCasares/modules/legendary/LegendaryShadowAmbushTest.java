package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegendaryShadowAmbushTest {
    @Test
    void legacyHighDamageIsCappedAndMalformedConfigurationUsesTheDefault() {
        assertEquals(6, LegendaryShadowAmbush.damage(14));
        assertEquals(6, LegendaryShadowAmbush.damage(Double.NaN));
        assertEquals(6, LegendaryShadowAmbush.damage(Double.POSITIVE_INFINITY));
        assertEquals(0, LegendaryShadowAmbush.damage(-1));
        assertEquals(2, LegendaryShadowAmbush.damage(2));
    }

    @Test
    void fallbackTravelUsesYawEvenWhenLookingStraightUpOrDown() {
        for (float pitch : new float[]{-90, 0, 90}) {
            var origin = new Location(null, 10, 20, 30, 90, pitch);
            var destination = LegendaryShadowAmbush.destination(origin, null);
            assertEquals(6, destination.getX(), .000001);
            assertEquals(20, destination.getY());
            assertEquals(30, destination.getZ(), .000001);
            assertEquals(10, origin.getX());
        }
    }

    @Test
    void ambushEndsBehindTheTargetFacingBackTowardThem() {
        var owner = new Location(null, 0, 20, 0, 0, 15);
        var victim = new Location(null, 5, 20, 5, 180, 0);
        var destination = LegendaryShadowAmbush.destination(owner, victim);
        assertEquals(5, destination.getX(), .000001);
        assertEquals(6.5, destination.getZ(), .000001);
        assertEquals(180, destination.getYaw());
        assertEquals(0, destination.getPitch());
        assertEquals(5, victim.getZ());
    }
}
