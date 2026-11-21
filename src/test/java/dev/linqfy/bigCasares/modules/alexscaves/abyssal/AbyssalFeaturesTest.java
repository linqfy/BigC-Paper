package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbyssalFeaturesTest {

    @Test
    void trenchOnlyDigsUnderTheSea() {
        // Mod calcYDist: nothing above sea level + 1, below y=-64 or where the sea floor is within 3 of the sea.
        assertEquals(0.0, AbyssalFeatures.Trench.yDist(0, 70, 0, 20));
        assertEquals(0.0, AbyssalFeatures.Trench.yDist(0, -65, 0, 20));
        assertEquals(0.0, AbyssalFeatures.Trench.yDist(0, 0, 0, 61));
        int positive = 0;
        for (int x = -500; x <= 500; x += 25) {
            if (AbyssalFeatures.Trench.yDist(x, -16, x, 20) > 0.0) {
                positive++;
            }
        }
        assertTrue(positive > 20, "a deep sea floor is dug at mid depth: " + positive);
    }

    @Test
    void columnsNearAStartNeedLessWidth() {
        List<int[]> start = List.<int[]>of(new int[] {0, 0, 150});
        double near = AbyssalFeatures.Trench.threshold(start, 5, 5);
        double far = AbyssalFeatures.Trench.threshold(start, 100, 0);
        assertTrue(near < far, near + " < " + far);
        assertEquals(Double.MAX_VALUE, AbyssalFeatures.Trench.threshold(start, 400, 400), "outside the trench");
    }

    @Test
    void seaMountsAreDeterministicAndSomewhere() {
        int mounts = 0;
        for (int x = -300; x <= 300; x += 10) {
            for (int z = -300; z <= 300; z += 10) {
                if (AbyssalFeatures.Trench.seaMount(x, -50, z)) {
                    mounts++;
                }
                assertEquals(AbyssalFeatures.Trench.seaMount(x, -50, z), AbyssalFeatures.Trench.seaMount(x, -50, z));
            }
        }
        assertTrue(mounts > 0 && mounts < 61 * 61, "mounts: " + mounts);
    }
}
