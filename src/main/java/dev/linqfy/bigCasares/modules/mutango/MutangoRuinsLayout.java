package dev.linqfy.bigCasares.modules.mutango;

import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.IntPredicate;

/**
 * Donde caen las ruinas (sin Bukkit), igual que las ciudades de Don Pollos: el mundo se divide en celdas de
 * spacing x spacing bloques; cada celda tiene como mucho unas ruinas, en un lugar al azar fijo segun la semilla del
 * mundo. Se construyen cuando se genera por primera vez su chunk central ("chunk ancla").
 */
final class MutangoRuinsLayout {

    /** Ruinas planeadas: esquina noroeste ya rotada, giro (0-3, horario) y medidas en el mundo. */
    record Plan(int cellX, int cellZ, int x, int z, int rotation, int sizeX, int sizeZ) {
        int centerX() {
            return x + sizeX / 2;
        }

        int centerZ() {
            return z + sizeZ / 2;
        }

        int anchorChunkX() {
            return Math.floorDiv(centerX(), 16);
        }

        int anchorChunkZ() {
            return Math.floorDiv(centerZ(), 16);
        }

        String key() {
            return cellX + ";" + cellZ;
        }
    }

    private static final int MARGIN = 16;
    /** Distinta a la de las ciudades de Don Pollos, asi no caen en los mismos lugares. */
    private static final long SALT = 0x52_55_49_4E_41_53_4D_55L;

    private final long seed;
    private final int spacing;
    private final double chance;
    private final int minDistanceFromSpawn;
    private final int width;
    private final int length;

    MutangoRuinsLayout(long seed, int spacing, double chance, int minDistanceFromSpawn, int width, int length) {
        if (spacing < Math.max(width, length) + 2 * MARGIN) {
            throw new IllegalArgumentException("la separacion de las ruinas tiene que ser mayor a las ruinas + margenes");
        }
        this.seed = seed;
        this.spacing = spacing;
        this.chance = chance;
        this.minDistanceFromSpawn = Math.max(0, minDistanceFromSpawn);
        this.width = width;
        this.length = length;
    }

    Optional<Plan> inCell(int cellX, int cellZ) {
        SplittableRandom random = new SplittableRandom(seed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ SALT);
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        int rotation = random.nextInt(4);
        int sizeX = rotation % 2 == 0 ? width : length;
        int sizeZ = rotation % 2 == 0 ? length : width;
        int x = cellX * spacing + MARGIN + random.nextInt(spacing - 2 * MARGIN - sizeX + 1);
        int z = cellZ * spacing + MARGIN + random.nextInt(spacing - 2 * MARGIN - sizeZ + 1);
        Plan plan = new Plan(cellX, cellZ, x, z, rotation, sizeX, sizeZ);
        long dx = plan.centerX();
        long dz = plan.centerZ();
        if (dx * dx + dz * dz < (long) minDistanceFromSpawn * minDistanceFromSpawn) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    /** Las ruinas cuyo chunk ancla es este chunk, si hay. */
    Optional<Plan> anchoredAt(int chunkX, int chunkZ) {
        int cellX = Math.floorDiv(chunkX * 16 + 8, spacing);
        int cellZ = Math.floorDiv(chunkZ * 16 + 8, spacing);
        return inCell(cellX, cellZ).filter(plan -> plan.anchorChunkX() == chunkX && plan.anchorChunkZ() == chunkZ);
    }

    /** Las ruinas planeadas mas cercanas a (x, z), buscando en las celdas de alrededor. */
    Optional<Plan> nearest(int x, int z, int radiusCells) {
        int cx = Math.floorDiv(x, spacing);
        int cz = Math.floorDiv(z, spacing);
        Plan best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = -radiusCells; i <= radiusCells; i++) {
            for (int j = -radiusCells; j <= radiusCells; j++) {
                Optional<Plan> plan = inCell(cx + i, cz + j);
                if (plan.isPresent()) {
                    long dx = plan.get().centerX() - x;
                    long dz = plan.get().centerZ() - z;
                    long distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = plan.get();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Cuantos bloques hay que rellenar en una columna: desde ground para abajo, los que no son suelo firme hasta tocar
     * suelo (isGround). Si faltan maxDepth bloques o mas sin tocar suelo (un barranco o una cueva enorme debajo) devuelve
     * -1: ahi no se construye, para no llenar de piedra una cueva entera.
     */
    static int fillDepth(int ground, int floor, int maxDepth, IntPredicate isGround) {
        int depth = 0;
        for (int y = ground; y > floor; y--) {
            if (isGround.test(y)) {
                return depth;
            }
            depth++;
            if (depth >= maxDepth) {
                return -1;
            }
        }
        return depth;
    }
}
