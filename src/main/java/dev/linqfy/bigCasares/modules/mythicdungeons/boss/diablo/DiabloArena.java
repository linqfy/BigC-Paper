package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import java.util.Arrays;

/**
 * La sala de Diablo, sin Bukkit: solo cuenta quien esta adentro (la audiencia del runtime llega a 40 bloques y pasa por
 * pasillos y paredes). Se arma una vez al empezar desde el punto de aparicion: hasta donde llega la sala en
 * {@link #SECTORS} direcciones (sondeo de paredes, con tope), una franja de altura alrededor del piso y, si se conoce,
 * la caja de la mazmorra. Un punto esta adentro si cumple las tres cosas; el alcance entre dos sondeos se interpola.
 *
 * <p>Limitacion: una columna gruesa cerca del centro acorta su sector (quien quede detras cuenta como afuera hasta que
 * se mueva); por eso hay un alcance minimo.</p>
 */
final class DiabloArena {

    static final int SECTORS = 16;
    /** Media sala de 33x33 mas un poco. */
    static final double MAXIMUM_REACH = 17.0;
    static final double MINIMUM_REACH = 5.0;
    static final double BELOW = 3.0;
    static final double ABOVE = 7.0;

    /** Caja de la mazmorra en coordenadas de bloque, extremos incluidos (como {@code DungeonBounds}). */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1.0 && y >= minY && y < maxY + 1.0 && z >= minZ && z < maxZ + 1.0;
        }
    }

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double[] reach;
    private final Box box;

    /**
     * @param wallDistances distancia horizontal a la primera pared en cada sector (el sector {@code i} apunta al angulo
     *                      {@code 2*PI*i/SECTORS} medido desde +X hacia +Z); se recorta a
     *                      [{@link #MINIMUM_REACH}, {@link #MAXIMUM_REACH}]
     * @param box           la caja de la mazmorra o {@code null}
     */
    DiabloArena(double centerX, double centerY, double centerZ, double[] wallDistances, Box box) {
        if (wallDistances.length != SECTORS) {
            throw new IllegalArgumentException("expected " + SECTORS + " wall distances");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.reach = Arrays.stream(wallDistances)
            .map(distance -> Double.isFinite(distance) ? distance : MAXIMUM_REACH)
            .map(distance -> Math.max(MINIMUM_REACH, Math.min(MAXIMUM_REACH, distance)))
            .toArray();
        this.box = box;
    }

    /** Si el bloque de esas coordenadas corta el paso (lo responde el mundo; en tests, una falsa). */
    @FunctionalInterface
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /** Alturas (sobre el piso del centro) a las que se sondea: una puerta de 1 a 3 bloques no abre el sector. */
    private static final int[] PROBE_HEIGHTS = {1, 3, 5};

    /**
     * Sondea la pared mas cercana en cada sector, cada medio bloque y a varias alturas; sin pared hasta el tope
     * devuelve {@link #MAXIMUM_REACH}.
     */
    static double[] probeWalls(double centerX, double centerY, double centerZ, Solid solid) {
        double[] distances = new double[SECTORS];
        int floorY = (int) Math.floor(centerY + 0.01);
        for (int index = 0; index < SECTORS; index++) {
            double dirX = Math.cos(sectorAngle(index));
            double dirZ = Math.sin(sectorAngle(index));
            distances[index] = MAXIMUM_REACH;
            outer:
            for (double step = 0.5; step <= MAXIMUM_REACH; step += 0.5) {
                int x = (int) Math.floor(centerX + dirX * step);
                int z = (int) Math.floor(centerZ + dirZ * step);
                for (int height : PROBE_HEIGHTS) {
                    if (solid.at(x, floorY + height, z)) {
                        distances[index] = step;
                        break outer;
                    }
                }
            }
        }
        return distances;
    }

    /** El angulo del sector {@code index}, en radianes. */
    static double sectorAngle(int index) {
        return Math.PI * 2.0 * index / SECTORS;
    }

    boolean contains(double x, double y, double z) {
        if (y < centerY - BELOW || y > centerY + ABOVE) {
            return false;
        }
        if (box != null && !box.contains(x, y, z)) {
            return false;
        }
        double dx = x - centerX;
        double dz = z - centerZ;
        double distance = Math.hypot(dx, dz);
        return distance <= reachAt(Math.atan2(dz, dx));
    }

    /** Hasta donde llega la sala en esa direccion (interpolado entre los dos sondeos vecinos). */
    double reachAt(double angle) {
        double sector = angle / (Math.PI * 2.0) * SECTORS;
        double wrapped = ((sector % SECTORS) + SECTORS) % SECTORS;
        int low = (int) Math.floor(wrapped) % SECTORS;
        int high = (low + 1) % SECTORS;
        double t = wrapped - Math.floor(wrapped);
        return reach[low] * (1.0 - t) + reach[high] * t;
    }
}
