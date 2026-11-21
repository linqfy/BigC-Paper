package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El Sueño sale de la semilla: mismo lugar, mismos bloques, y las puertas sueltas son coherentes. */
class DreamLayoutTest {

    private static final long SEED = 2024L;

    private static final class Capture implements DreamLayout.Sink {
        final List<String> log = new ArrayList<>();

        @Override
        public void set(int x, int y, int z, Material material) {
            log.add("s " + x + " " + y + " " + z + " " + material);
        }

        @Override
        public void door(int x, int y, int z, Material door, int facing) {
            log.add("d " + x + " " + y + " " + z + " " + door + " " + facing);
        }
    }

    private static List<String> run(long seed, int cx, int cz) {
        Capture capture = new Capture();
        DreamLayout.generate(seed, cx, cz, capture);
        return capture.log;
    }

    @Test
    void generateIsDeterministic() {
        for (int cx = -3; cx <= 3; cx += 3) {
            assertEquals(run(SEED, cx, 1), run(SEED, cx, 1), "chunk " + cx);
        }
    }

    @Test
    void themeAndHeightAreDeterministic() {
        for (int i = -300; i <= 300; i += 37) {
            assertEquals(DreamLayout.theme(SEED, i, -i), DreamLayout.theme(SEED, i, -i));
            assertEquals(DreamLayout.height(SEED, i, 2 * i), DreamLayout.height(SEED, i, 2 * i));
            assertTrue(DreamLayout.height(SEED, i, 2 * i) >= DreamLayout.FLOOR);
        }
    }

    @Test
    void standaloneDoorsAreConsistent() {
        int found = 0;
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                for (DreamLayout.DoorSpot spot : DreamLayout.doorsInChunk(SEED, cx, cz)) {
                    found++;
                    assertEquals(cx, spot.x() >> 4, "puerta fuera de su chunk");
                    assertEquals(cz, spot.z() >> 4, "puerta fuera de su chunk");
                    assertTrue(spot.facing() >= 0 && spot.facing() <= 3);
                    assertTrue(DreamLayout.isStandaloneDoor(SEED, spot.x(), spot.y(), spot.z()), "mitad de abajo");
                    assertTrue(DreamLayout.isStandaloneDoor(SEED, spot.x(), spot.y() + 1, spot.z()), "mitad de arriba");
                    assertFalse(DreamLayout.isStandaloneDoor(SEED, spot.x(), spot.y() + 2, spot.z()), "y+2");
                    assertFalse(DreamLayout.isStandaloneDoor(SEED, spot.x() + 1, spot.y(), spot.z()), "bloque vecino x");
                    assertFalse(DreamLayout.isStandaloneDoor(SEED, spot.x(), spot.y(), spot.z() + 1), "bloque vecino z");
                }
            }
        }
        assertTrue(found > 0, "no hay ninguna puerta suelta en los chunks -20..20 con la semilla " + SEED);
    }
}
