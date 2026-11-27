package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticFeaturesTest {

    @Test
    void energizedVeinsAreThinBands() {
        int inVein = 0;
        int total = 0;
        for (int x = 0; x < 200; x++) {
            for (int y = -40; y < 40; y += 4) {
                for (int z = 0; z < 200; z += 3) {
                    total++;
                    if (MagneticFeatures.Terrain.vein(MagneticFeatures.Terrain.SCARLET, x, y, z)) {
                        inVein++;
                    }
                }
            }
        }
        double fraction = inVein / (double) total;
        assertTrue(fraction > 0.003 && fraction < 0.08, "veins should be thin but present: " + fraction);
    }

    @Test
    void shapeHelpers() {
        assertTrue(FeatureShapes.isStoneLike("deepslate"));
        assertFalse(FeatureShapes.isStoneLike("OAK_LOG"));
        assertTrue(FeatureShapes.isIronOre("DEEPSLATE_IRON_ORE"));
        assertTrue(FeatureShapes.isAir("CAVE_AIR"));
        assertTrue(FeatureShapes.inSphere(1, 1, 1, 2));
        assertFalse(FeatureShapes.inSphere(2, 2, 0, 2));
        for (int d = 0; d < 6; d++) {
            int[] a = FeatureShapes.DIRECTIONS.get(d);
            int[] b = FeatureShapes.DIRECTIONS.get(FeatureShapes.opposite(d));
            assertEquals(0, a[0] + b[0]);
            assertEquals(0, a[1] + b[1]);
            assertEquals(0, a[2] + b[2]);
        }
        assertEquals("down", FeatureShapes.DIRECTION_NAMES.get(0));
    }
}
