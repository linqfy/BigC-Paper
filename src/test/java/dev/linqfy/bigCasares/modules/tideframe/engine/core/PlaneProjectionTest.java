package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaneProjectionTest {
    private static final Vec3 EYE = new Vec3(12, 70, -40);
    @Test void minecraftZeroYawPointsSouthAndRightPointsWest() {
        var plane = PlaneProjection.create(EYE, 0, 0, 2);
        assertEquals(new Vec3(0, 0, 1), plane.forward());
        assertEquals(new Vec3(-1, 0, 0), plane.right());
        assertEquals(new Vec3(0, 1, 0), plane.up());
        assertEquals(EYE.add(new Vec3(0, 0, 2)), PlaneProjection.world(plane, new PlaneProjection.Point(0, 0)));
    }
    @Test void yawNinetyPointsWestAndPositivePitchLooksDown() {
        var plane = PlaneProjection.create(EYE, 90, 30, 2);
        assertEquals(-Math.sqrt(.75), plane.forward().x(), 1e-12);
        assertEquals(-.5, plane.forward().y(), 1e-12);
        assertEquals(0, plane.forward().z(), 1e-12);
        assertEquals(0, plane.right().dot(plane.forward()), 1e-12);
        assertEquals(0, plane.up().dot(plane.forward()), 1e-12);
        assertEquals(1, plane.up().dot(plane.up()), 1e-12);
    }
    @Test void projectsCenterAndKnownHorizontalOffset() {
        var plane = PlaneProjection.create(EYE, 0, 0, 2);
        assertPoint(0, 0, PlaneProjection.project(plane, EYE, 0, 0).orElseThrow());
        double yaw = Math.toDegrees(Math.atan(.6 / 2));
        assertPoint(.5, 0, PlaneProjection.project(plane, EYE, yaw, 0).orElseThrow());
    }
    @Test void projectsUpwardLookToPositiveY() {
        var plane = PlaneProjection.create(EYE, 0, 0, 2);
        double pitch = -Math.toDegrees(Math.atan(.35 / 2));
        assertPoint(0, .5, PlaneProjection.project(plane, EYE, 0, pitch).orElseThrow());
    }
    @Test void worldAndRayProjectionRoundTripForRotatedPlaneAndShiftedEye() {
        var plane = PlaneProjection.create(EYE, 132, -27, 3.25);
        var point = new PlaneProjection.Point(.7, -.3);
        Vec3 shiftedEye = EYE.add(new Vec3(.1, .05, -.2));
        Vec3 direction = PlaneProjection.world(plane, point).add(shiftedEye.multiply(-1)).normalized();
        double yaw = Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
        double pitch = -Math.toDegrees(Math.asin(direction.y()));
        var actual = PlaneProjection.project(plane, shiftedEye, yaw, pitch).orElseThrow();
        assertPoint(point.x(), point.y(), actual);
    }
    @Test void acceptsEdgesAndRejectsOutsideParallelAndBackwardRays() {
        var plane = PlaneProjection.create(EYE, 0, 0, 2);
        double edgeYaw = Math.toDegrees(Math.atan(1.2 / 2));
        assertPoint(1, 0, PlaneProjection.project(plane, EYE, edgeYaw, 0).orElseThrow());
        assertTrue(PlaneProjection.project(plane, EYE, edgeYaw + .01, 0).isEmpty());
        assertTrue(PlaneProjection.project(plane, EYE, 90, 0).isEmpty());
        assertTrue(PlaneProjection.project(plane, EYE, 180, 0).isEmpty());
    }
    @Test void supportsVerticalCameraWithoutDegenerateBasis() {
        var plane = PlaneProjection.create(EYE, 50, 90, 2);
        assertEquals(1, plane.right().dot(plane.right()), 1e-12);
        assertEquals(1, plane.up().dot(plane.up()), 1e-12);
        assertPoint(0, 0, PlaneProjection.project(plane, EYE, 50, 90).orElseThrow());
    }
    @Test void rejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> PlaneProjection.create(EYE, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PlaneProjection.create(EYE, Double.NaN, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new PlaneProjection.Point(0, Double.NaN));
    }
    static void assertPoint(double x, double y, PlaneProjection.Point actual) {
        assertEquals(x, actual.x(), 1e-10);
        assertEquals(y, actual.y(), 1e-10);
    }
}
