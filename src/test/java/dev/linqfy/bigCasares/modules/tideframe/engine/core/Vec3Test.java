package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec3Test {
    @Test void addsAndScalesCoordinates() {
        assertEquals(new Vec3(3, 2, 9), new Vec3(1, -2, 3).add(new Vec3(2, 4, 6)));
        assertEquals(new Vec3(2, -4, 6), new Vec3(1, -2, 3).multiply(2));
    }
    @Test void dotAndCrossFollowRightHandedGeometry() {
        Vec3 x = new Vec3(1, 0, 0), y = new Vec3(0, 1, 0);
        assertEquals(0, x.dot(y));
        assertEquals(new Vec3(0, 0, 1), x.cross(y));
        assertEquals(new Vec3(0, 0, -1), y.cross(x));
    }
    @Test void normalizesWithoutChangingDirection() {
        Vec3 unit = new Vec3(3, 4, 0).normalized();
        assertEquals(.6, unit.x(), 1e-12);
        assertEquals(.8, unit.y(), 1e-12);
        assertEquals(1, unit.dot(unit), 1e-12);
    }
    @Test void rejectsZeroNormalizationAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Vec3(0, 0, 0).normalized());
        assertThrows(IllegalArgumentException.class, () -> new Vec3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Vec3(0, Double.POSITIVE_INFINITY, 0));
    }
    @Test void normalizesVeryLargeFiniteVector() {
        Vec3 value = new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, 0).normalized();
        assertEquals(Math.sqrt(.5), value.x(), 1e-12);
        assertEquals(1, value.dot(value), 1e-12);
    }
}
