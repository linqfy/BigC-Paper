package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * El "hueco" de una puerta abierta: un panel que ocupa exactamente el lugar de la puerta cerrada (3/16 de grosor,
 * pegado al borde del bloque que le corresponde), asi la puerta abierta se ve como un vano lleno de negro (o rojo).
 */
final class DoorHole {

    private static final float THICK = 3f / 16f;
    /** Un poquito mas chico que el bloque para que no titile contra la puerta. */
    private static final float INSET = 0.004f;

    private DoorHole() {
    }

    /** Transformacion de un BlockDisplay puesto en la esquina del bloque de abajo de la puerta (2 de alto). */
    static Transformation panel(BlockFace facing) {
        float h = 2f - 2 * INSET;
        return switch (facing) {
            // misma posicion que la puerta cerrada en el juego
            case NORTH -> box(INSET, 1f - THICK, 1f - 2 * INSET, THICK - INSET, h);
            case SOUTH -> box(INSET, INSET, 1f - 2 * INSET, THICK - INSET, h);
            case EAST -> box(INSET, INSET, THICK - INSET, 1f - 2 * INSET, h);
            default -> box(1f - THICK, INSET, THICK - INSET, 1f - 2 * INSET, h); // WEST
        };
    }

    private static Transformation box(float x, float z, float sx, float sz, float h) {
        return new Transformation(new Vector3f(x, INSET, z), new AxisAngle4f(), new Vector3f(sx, h, sz), new AxisAngle4f());
    }

    /**
     * Las dos caras del panel, para pintar algo encima (el "?"): {x, z, yaw} de cada cara, relativos a la esquina del
     * bloque, un pelito afuera del panel.
     */
    static double[][] faces(BlockFace facing) {
        double e = 0.01;
        return switch (facing) {
            case NORTH -> new double[][]{{0.5, 1 - THICK - e, 180}, {0.5, 1 + e, 0}};
            case SOUTH -> new double[][]{{0.5, -e, 180}, {0.5, THICK + e, 0}};
            case EAST -> new double[][]{{-e, 0.5, 90}, {THICK + e, 0.5, -90}};
            default -> new double[][]{{1 - THICK - e, 0.5, 90}, {1 + e, 0.5, -90}};
        };
    }

    static BlockFace fromIndex(int facing) {
        return new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}[facing];
    }
}
