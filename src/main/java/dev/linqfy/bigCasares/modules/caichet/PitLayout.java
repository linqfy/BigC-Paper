package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.Optional;

/**
 * El pozo: un hueco rectangular a cielo abierto, de piedra y marmol, con una escalera pegada a una pared y el fondo
 * negro. Todo sale de la semilla y del chunk, asi que el mismo chunk siempre da el mismo pozo. Entra entero en un
 * chunk (nunca toca a los vecinos).
 *
 * <p>Esta clase no depende del servidor: escribe en un {@link Sink} con coordenadas del mundo, igual que
 * {@link HotelLayout}. Trabaja en un marco propio (u a lo largo, v a lo ancho; la escalera baja hacia u creciente
 * pegada a v = 0) que despues se gira segun la orientacion.
 */
final class PitLayout {

    /** Lado de cada region: cada una tiene exactamente un chunk candidato. */
    static final int REGION_CHUNKS = 48;
    static final int LENGTH = 14;
    static final int WIDTH = 10;
    /** Del borde al piso (el piso es el bloque negro; el borde es el anillo a nivel del suelo). */
    static final int DEPTH = 6;
    /** Aire que se despeja sobre el borde (arboles, pasto) dentro de la huella. */
    static final int CLEAR_ABOVE = 14;
    /** Cuanto sube el hueco protegido sobre el borde, para que no lo tapen con un techo a ras. */
    static final int HOLLOW_ABOVE = 2;
    static final int ORIENTATIONS = 4;
    /** La escalera tiene un escalon por cada bloque de profundidad. */
    static final int STEPS = DEPTH;
    static final int STAIR_WIDTH = 2;
    /** Margen contra el borde del chunk, a lo largo y a lo ancho. */
    private static final int LONG_MARGIN = 1;
    private static final int SHORT_MARGIN = 3;

    private static final int SALT_CHUNK_X = 0x70;
    private static final int SALT_CHUNK_Z = 0x71;
    private static final int SALT_ORIENTATION = 0x72;
    private static final int SALT_WALL = 0x73;
    private static final long WALL_SEED = 0x504F5A4FL;
    /** Altura (bajo el borde) de la franja de marmol de las paredes. */
    private static final int MARBLE_BAND = 3;
    private static final int CRACKED_PERCENT = 10;
    private static final int MOSSY_PERCENT = 22;

    static final Material STONE = Material.STONE_BRICKS;
    static final Material CRACKED = Material.CRACKED_STONE_BRICKS;
    static final Material MOSSY = Material.MOSSY_STONE_BRICKS;
    static final Material MARBLE = Material.POLISHED_DIORITE;
    static final Material MARBLE_LIGHT = Material.SMOOTH_QUARTZ;
    static final Material STAIRS = Material.POLISHED_DIORITE_STAIRS;
    static final Material FLOOR = Material.BLACK_CONCRETE;

    interface Sink {
        void set(int x, int y, int z, Material material);

        /** Un escalon que sube mirando hacia facing. */
        void stairs(int x, int y, int z, Material material, BlockFace facing);
    }

    /**
     * Un pozo ya ubicado. Todo en coordenadas del mundo: el minimo del interior, el Y del piso, la orientacion
     * (0 a 3: hacia donde baja la escalera) y el bloque del borde donde arranca la escalera.
     */
    record Pit(int interiorMinX, int interiorMinZ, int floorY, int orientation, int stairX, int stairZ) {

        /** Hacia donde baja la escalera. */
        BlockFace descent() {
            return switch (orientation) {
                case 0 -> BlockFace.EAST;
                case 1 -> BlockFace.SOUTH;
                case 2 -> BlockFace.WEST;
                default -> BlockFace.NORTH;
            };
        }

        int rimY() {
            return floorY + DEPTH;
        }

        private boolean alongX() {
            return orientation % 2 == 0;
        }

        int footprintMinX() {
            return interiorMinX - 1;
        }

        int footprintMinZ() {
            return interiorMinZ - 1;
        }

        int footprintMaxX() {
            return footprintMinX() + (alongX() ? LENGTH : WIDTH) - 1;
        }

        int footprintMaxZ() {
            return footprintMinZ() + (alongX() ? WIDTH : LENGTH) - 1;
        }

        int interiorMaxX() {
            return footprintMaxX() - 1;
        }

        int interiorMaxZ() {
            return footprintMaxZ() - 1;
        }

        private boolean insideColumns(int x, int z) {
            return x >= interiorMinX && x <= interiorMaxX() && z >= interiorMinZ && z <= interiorMaxZ();
        }

        /** El bloque es del piso negro. */
        boolean floorContains(int x, int y, int z) {
            return y == floorY && insideColumns(x, z);
        }

        /** El bloque es del hueco (sobre el piso, hasta un poco sobre el borde). */
        boolean interiorContains(int x, int y, int z) {
            return y > floorY && y <= rimY() + HOLLOW_ABOVE && insideColumns(x, z);
        }

        /** Donde se vuelve del desierto: sobre el borde, arriba de la escalera. */
        double returnX() {
            return stairX + 0.5;
        }

        int returnY() {
            return rimY() + 1;
        }

        double returnZ() {
            return stairZ + 0.5;
        }

        /** Hacia afuera del pozo: la direccion contraria a la que baja la escalera. */
        BlockFace outward() {
            return descent().getOppositeFace();
        }

        /** Corto y sin separadores raros: se guarda en el chunk. */
        String encode() {
            return interiorMinX + "," + interiorMinZ + "," + floorY + "," + orientation + "," + stairX + "," + stairZ;
        }

        /** Lo contrario de encode. Con cualquier basura (o numeros de mas o de menos) devuelve vacio, nunca tira. */
        static Optional<Pit> decode(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String[] parts = raw.split(",", -1);
            if (parts.length != 6) {
                return Optional.empty();
            }
            try {
                int orientation = Integer.parseInt(parts[3]);
                if (orientation < 0 || orientation >= ORIENTATIONS) {
                    return Optional.empty();
                }
                return Optional.of(new Pit(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), orientation, Integer.parseInt(parts[4]), Integer.parseInt(parts[5])));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
    }

    private PitLayout() {
    }

    // ------------------------------------------------------------------ donde

    /** El unico chunk candidato de una region de REGION_CHUNKS x REGION_CHUNKS chunks. */
    static int[] candidateChunk(long seed, int regionX, int regionZ) {
        int x = regionX * REGION_CHUNKS + (int) Math.floorMod(HotelLayout.hash(seed, regionX, regionZ, SALT_CHUNK_X),
            (long) REGION_CHUNKS);
        int z = regionZ * REGION_CHUNKS + (int) Math.floorMod(HotelLayout.hash(seed, regionX, regionZ, SALT_CHUNK_Z),
            (long) REGION_CHUNKS);
        return new int[] {x, z};
    }

    static boolean isCandidate(long seed, int chunkX, int chunkZ) {
        int[] candidate = candidateChunk(seed, Math.floorDiv(chunkX, REGION_CHUNKS),
            Math.floorDiv(chunkZ, REGION_CHUNKS));
        return candidate[0] == chunkX && candidate[1] == chunkZ;
    }

    static int orientationOf(long seed, int chunkX, int chunkZ) {
        return (int) Math.floorMod(HotelLayout.hash(seed, chunkX, chunkZ, SALT_ORIENTATION), (long) ORIENTATIONS);
    }

    /** El pozo de ese chunk con el borde en rimY (la huella queda centrada, con margen contra el chunk). */
    static Pit create(long seed, int chunkX, int chunkZ, int rimY) {
        int orientation = orientationOf(seed, chunkX, chunkZ);
        boolean alongX = orientation % 2 == 0;
        int footprintX = chunkX * 16 + (alongX ? LONG_MARGIN : SHORT_MARGIN);
        int footprintZ = chunkZ * 16 + (alongX ? SHORT_MARGIN : LONG_MARGIN);
        int[] stair = cell(orientation, footprintX, footprintZ, 0, 1);
        return new Pit(footprintX + 1, footprintZ + 1, rimY - DEPTH, orientation, stair[0], stair[1]);
    }

    /** De las coordenadas del marco (u a lo largo, v a lo ancho) a las del mundo. */
    private static int[] cell(int orientation, int footprintX, int footprintZ, int u, int v) {
        return switch (orientation) {
            case 0 -> new int[] {footprintX + u, footprintZ + v};
            case 1 -> new int[] {footprintX + v, footprintZ + u};
            case 2 -> new int[] {footprintX + LENGTH - 1 - u, footprintZ + WIDTH - 1 - v};
            default -> new int[] {footprintX + WIDTH - 1 - v, footprintZ + LENGTH - 1 - u};
        };
    }

    // ------------------------------------------------------------------ construccion

    /** Emite cada bloque de la huella una sola vez: paredes, borde, piso, escalera y el aire de arriba. */
    static void build(Pit pit, Sink sink) {
        int rimY = pit.rimY();
        int floorY = pit.floorY();
        BlockFace ascent = pit.outward();
        for (int u = 0; u < LENGTH; u++) {
            for (int v = 0; v < WIDTH; v++) {
                int[] at = cell(pit.orientation(), pit.footprintMinX(), pit.footprintMinZ(), u, v);
                int x = at[0];
                int z = at[1];
                boolean ring = u == 0 || u == LENGTH - 1 || v == 0 || v == WIDTH - 1;
                if (ring) {
                    buildWall(sink, x, z, floorY, rimY, u, v);
                } else {
                    buildInterior(sink, x, z, floorY, rimY, u, v, ascent);
                }
                for (int y = rimY + 1; y <= rimY + CLEAR_ABOVE; y++) {
                    sink.set(x, y, z, Material.AIR);
                }
            }
        }
    }

    private static boolean isStairColumn(int u, int v) {
        return v >= 1 && v <= STAIR_WIDTH && u >= 1 && u <= STEPS;
    }

    private static void buildWall(Sink sink, int x, int z, int floorY, int rimY, int u, int v) {
        boolean corner = (u == 0 || u == LENGTH - 1) && (v == 0 || v == WIDTH - 1);
        sink.set(x, rimY, z, corner ? MARBLE_LIGHT : MARBLE);
        for (int y = floorY; y < rimY; y++) {
            sink.set(x, y, z, y == rimY - MARBLE_BAND ? MARBLE_LIGHT : brick(x, y, z));
        }
    }

    private static void buildInterior(Sink sink, int x, int z, int floorY, int rimY, int u, int v, BlockFace ascent) {
        // la base evita que una cueva deje el fondo colgando
        sink.set(x, floorY - 1, z, STONE);
        sink.set(x, floorY, z, FLOOR);
        if (isStairColumn(u, v)) {
            int step = u - 1;
            int stepY = rimY - step;
            for (int y = floorY + 1; y < stepY; y++) {
                sink.set(x, y, z, STONE);
            }
            sink.stairs(x, stepY, z, STAIRS, ascent);
            clearFrom(sink, x, z, stepY + 1, rimY);
            return;
        }
        clearFrom(sink, x, z, floorY + 1, rimY);
    }

    private static void clearFrom(Sink sink, int x, int z, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            sink.set(x, y, z, Material.AIR);
        }
    }

    /** Piedra con algo de grietas y musgo, elegido por posicion (siempre igual en el mismo lugar). */
    private static Material brick(int x, int y, int z) {
        int roll = (int) Math.floorMod(HotelLayout.hash(WALL_SEED, x, z, y * 31 + SALT_WALL), 100L);
        if (roll < CRACKED_PERCENT) {
            return CRACKED;
        }
        return roll < MOSSY_PERCENT ? MOSSY : STONE;
    }
}
