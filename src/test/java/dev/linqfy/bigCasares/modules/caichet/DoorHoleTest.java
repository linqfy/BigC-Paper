package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El panel del hueco de la puerta queda dentro del bloque y es fino. */
class DoorHoleTest {

    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final double EPS = 1e-6;

    @Test
    void fromIndexGoesNorthEastSouthWest() {
        for (int i = 0; i < 4; i++) {
            assertEquals(FACES[i], DoorHole.fromIndex(i));
        }
    }

    @Test
    void thePanelStaysInsideTheDoorBlockAndIsThin() {
        for (BlockFace face : FACES) {
            Transformation t = DoorHole.panel(face);
            double x0 = t.getTranslation().x();
            double y0 = t.getTranslation().y();
            double z0 = t.getTranslation().z();
            double sx = t.getScale().x();
            double sy = t.getScale().y();
            double sz = t.getScale().z();
            assertTrue(x0 >= -EPS && x0 + sx <= 1 + EPS, face + " x fuera del bloque");
            assertTrue(z0 >= -EPS && z0 + sz <= 1 + EPS, face + " z fuera del bloque");
            assertTrue(y0 >= -EPS, face + " y negativo");
            assertEquals(2.0, sy, 0.05, face + " alto");
            double thickness = face == BlockFace.NORTH || face == BlockFace.SOUTH ? sz : sx;
            assertTrue(thickness > 0 && thickness < 0.25, face + " espesor " + thickness);
        }
    }

    @Test
    void everyFaceHasTwoPaintingSpots() {
        for (BlockFace face : FACES) {
            assertEquals(2, DoorHole.faces(face).length);
        }
    }
}
