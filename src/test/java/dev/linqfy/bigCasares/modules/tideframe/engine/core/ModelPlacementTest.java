package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelPlacementTest {
    @Test void theModelsCentreIsOnTheLineOfSightAtTheDistanceForAnyDirection() {
        for (float yaw : new float[] { 0, 37, 90, -135, 179.5f, -180 })
            for (float pitch : new float[] { 0, 20, -35, 60 }) {
                var spot = ModelPlacement.centered(10.5, 70.62, -4.25, yaw, pitch, 3);
                double[] d = ModelPlacement.direction(yaw, pitch);
                double cx = spot.x() - 10.5, cy = spot.y() + ModelPlacement.HEIGHT / 2 - 70.62, cz = spot.z() + 4.25;
                assertEquals(3, Math.sqrt(cx * cx + cy * cy + cz * cz), 1e-9, "distance at " + yaw + "/" + pitch);
                // Parallel to the view: no sideways component left after removing the part along it.
                double along = cx * d[0] + cy * d[1] + cz * d[2];
                assertEquals(3, along, 1e-9, "on the axis at " + yaw + "/" + pitch);
            }
    }

    @Test void minecraftAnglesPointWhereTheGameLooks() {
        assertArrayEquals(new double[] { 0, 0, 1 }, ModelPlacement.direction(0, 0), 1e-12);
        assertArrayEquals(new double[] { -1, 0, 0 }, ModelPlacement.direction(90, 0), 1e-12);
        assertArrayEquals(new double[] { 0, -1, 0 }, ModelPlacement.direction(0, 90), 1e-12);
        var level = ModelPlacement.centered(0, 64, 0, 0, 0, 3);
        assertEquals(64 - .9, level.y(), 1e-12, "a level camera sees the model's middle");
        assertEquals(3, level.z(), 1e-12);
    }

    @Test void theModelFacesTheCamera() {
        assertEquals(180 - 360, ModelPlacement.facing(0), 1e-6);
        assertEquals(-90, ModelPlacement.facing(90), 1e-6);
        assertEquals(90, ModelPlacement.facing(-90), 1e-6);
        assertEquals(10, ModelPlacement.facing(-170), 1e-6);
        assertEquals(-170, ModelPlacement.facing(370), 1e-6);
    }

    @Test void theSizeOnScreenDependsOnlyOnTheFov() {
        assertEquals(.64, ModelPlacement.screenShare(1.8, 3, 50), .005);
        assertEquals(.43, ModelPlacement.screenShare(1.8, 3, ModelPlacement.DESIGN_FOV), .005);
        assertEquals(.30, ModelPlacement.screenShare(1.8, 3, 90), .005);
        assertEquals(.21, ModelPlacement.screenShare(1.8, 3, 110), .005);
        // The demo's hole is 60 % of the height: a FOV under about 53 makes the model overflow it.
        assertTrue(ModelPlacement.screenShare(1.8, 3, 54) < .6);
        assertTrue(ModelPlacement.screenShare(1.8, 3, 52) > .6);
    }

    @Test void anOpenFieldIsClear() {
        var spot = ModelPlacement.centered(.5, 65.62, .5, 0, 0, 3);
        ModelPlacement.Solid ground = (x, y, z) -> y < 64;
        assertTrue(ModelPlacement.clear(.5, 65.62, .5, spot, ground));
    }

    @Test void aWallInBetweenOrInsideTheModelHidesIt() {
        var spot = ModelPlacement.centered(.5, 65.62, .5, 0, 0, 3);
        ModelPlacement.Solid wall = (x, y, z) -> y < 64 || z == 1 && y == 65;
        assertFalse(ModelPlacement.clear(.5, 65.62, .5, spot, wall), "a block between the camera and the model");
        ModelPlacement.Solid pillar = (x, y, z) -> y < 64 || z == 3 && x == 0 && y == 64;
        assertFalse(ModelPlacement.clear(.5, 65.62, .5, spot, pillar), "a block where the model stands");
        ModelPlacement.Solid lowCeiling = (x, y, z) -> y < 64 || y == 66 && z >= 2;
        assertFalse(ModelPlacement.clear(.5, 65.62, .5, spot, lowCeiling), "a ceiling through the model's head");
        ModelPlacement.Solid besides = (x, y, z) -> y < 64 || x == 2;
        assertTrue(ModelPlacement.clear(.5, 65.62, .5, spot, besides), "a wall to the side does not hide it");
    }
}
