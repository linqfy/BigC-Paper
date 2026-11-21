package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Carve;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForlornGenerationTest {
    @Test
    void canyonIsOpenNearTheCentreAndNeverPastItsBounds() {
        CaveBowl canyon = new CaveBowl(Kind.FORLORN_CANYON, 0, -10, 0, 80, 100);
        int open = 0;
        for (int x = -140; x <= 140; x += 2) {
            for (int z = -140; z <= 140; z += 2) {
                for (int y = -70; y <= 50; y += 3) {
                    if (canyon.carve(x, y, z) == Carve.AIR) {
                        open++;
                        assertTrue(canyon.columnMayCarve(x, z), "column bound at " + x + "," + z);
                        assertTrue(y >= canyon.carveMinY() && y <= canyon.carveMaxY(), "height bound at y=" + y);
                    }
                }
            }
        }
        assertTrue(open > 1000, "the canyon carves a large volume: " + open);
    }

    @Test
    void canyonIsWiderHigherUp() {
        CaveBowl canyon = new CaveBowl(Kind.FORLORN_CANYON, 0, -10, 0, 80, 100);
        assertTrue(count(canyon, 20) > count(canyon, -40), "the walls step out with height");
    }

    private static int count(CaveBowl canyon, int y) {
        int open = 0;
        for (int x = -120; x <= 120; x += 2) {
            for (int z = -120; z <= 120; z += 2) {
                if (canyon.carve(x, y, z) == Carve.AIR) {
                    open++;
                }
            }
        }
        return open;
    }

    @Test
    void bridgeSpansTheBiomeInSixBlockSections() {
        Optional<List<ForlornBridges.Section>> plan = ForlornBridges.plan(42L, 10, -3, (x, z) -> true);
        assertTrue(plan.isPresent());
        List<ForlornBridges.Section> sections = plan.get();
        ForlornBridges.Section first = sections.get(0);
        assertEquals(first.max() + 1, sections.size());
        for (int i = 1; i < sections.size(); i++) {
            ForlornBridges.Section section = sections.get(i);
            assertEquals(ForlornBridges.SECTION_LENGTH,
                Math.abs(section.x() - sections.get(i - 1).x()) + Math.abs(section.z() - sections.get(i - 1).z()));
            assertEquals(first.y(), section.y());
        }
        assertEquals(plan, ForlornBridges.plan(42L, 10, -3, (x, z) -> true), "deterministic");
        assertFalse(ForlornBridges.plan(42L, 10, -3, (x, z) -> false).isPresent(), "needs the biome at the start");
    }

    @Test
    void directionHelpersFollowVanilla() {
        // north -> east -> south -> west
        assertEquals(5, ForlornFeatures.clockWise(2));
        assertEquals(3, ForlornFeatures.clockWise(5));
        assertEquals(4, ForlornFeatures.counterClockWise(2));
        assertEquals("x", ForlornFeatures.axis(5));
        assertEquals(3, ForlornFeatures.opposite(2));
    }

    @Test
    void biomeContinuesStopsAtTheCutoff() {
        assertEquals(32, ForlornBridges.continues(32, step -> true));
        assertEquals(16, ForlornBridges.continues(80, step -> step < 16));
    }
}
