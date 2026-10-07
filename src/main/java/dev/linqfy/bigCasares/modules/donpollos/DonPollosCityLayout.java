package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Donde caen las ciudades (sin Bukkit). El mundo se divide en celdas de spacing x spacing bloques;
 * cada celda tiene como mucho una ciudad, en un lugar al azar fijo segun la semilla del mundo.
 * La ciudad se construye cuando se genera por primera vez su chunk central ("chunk ancla").
 */
public final class DonPollosCityLayout {

    /** Ciudad planeada: esquina noroeste ya rotada, giro (0-3, horario) y medidas en el mundo. */
    public record CityPlan(int cellX, int cellZ, int x, int z, int rotation, int sizeX, int sizeZ) {
        public int centerX() {
            return x + sizeX / 2;
        }

        public int centerZ() {
            return z + sizeZ / 2;
        }

        public int anchorChunkX() {
            return Math.floorDiv(centerX(), 16);
        }

        public int anchorChunkZ() {
            return Math.floorDiv(centerZ(), 16);
        }

        public String key() {
            return cellX + ";" + cellZ;
        }
    }

    private static final int MARGIN = 32;

    private final long seed;
    private final int spacing;
    private final double chance;
    private final int minDistanceFromSpawn;
    private final int width;
    private final int length;

    public DonPollosCityLayout(long seed, int spacing, double chance, int minDistanceFromSpawn, int width, int length) {
        if (spacing < Math.max(width, length) + 2 * MARGIN) {
            throw new IllegalArgumentException("ciudades.spacing tiene que ser mayor a la ciudad + margenes");
        }
        if (chance < 0 || chance > 1) {
            throw new IllegalArgumentException("ciudades.chance tiene que estar entre 0 y 1");
        }
        this.seed = seed;
        this.spacing = spacing;
        this.chance = chance;
        this.minDistanceFromSpawn = Math.max(0, minDistanceFromSpawn);
        this.width = width;
        this.length = length;
    }

    public Optional<CityPlan> cityInCell(int cellX, int cellZ) {
        SplittableRandom random = new SplittableRandom(seed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L)
            ^ 0x44_4F_4E_50_4F_4C_4C_4FL);
        if (random.nextDouble() >= chance) {
            return Optional.empty();
        }
        int rotation = random.nextInt(4);
        int sizeX = rotation % 2 == 0 ? width : length;
        int sizeZ = rotation % 2 == 0 ? length : width;
        int x = cellX * spacing + MARGIN + random.nextInt(spacing - 2 * MARGIN - sizeX + 1);
        int z = cellZ * spacing + MARGIN + random.nextInt(spacing - 2 * MARGIN - sizeZ + 1);
        CityPlan plan = new CityPlan(cellX, cellZ, x, z, rotation, sizeX, sizeZ);
        long dx = plan.centerX();
        long dz = plan.centerZ();
        if (dx * dx + dz * dz < (long) minDistanceFromSpawn * minDistanceFromSpawn) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    /** La ciudad cuyo chunk ancla es este chunk, si hay. */
    public Optional<CityPlan> cityAnchoredAt(int chunkX, int chunkZ) {
        int cellX = Math.floorDiv(chunkX * 16 + 8, spacing);
        int cellZ = Math.floorDiv(chunkZ * 16 + 8, spacing);
        return cityInCell(cellX, cellZ)
            .filter(plan -> plan.anchorChunkX() == chunkX && plan.anchorChunkZ() == chunkZ);
    }

    /** La ciudad planeada mas cercana a (x, z), buscando en las celdas de alrededor. */
    public Optional<CityPlan> nearest(int x, int z, int radiusCells) {
        int cx = Math.floorDiv(x, spacing);
        int cz = Math.floorDiv(z, spacing);
        CityPlan best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = -radiusCells; i <= radiusCells; i++) {
            for (int j = -radiusCells; j <= radiusCells; j++) {
                Optional<CityPlan> plan = cityInCell(cx + i, cz + j);
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

    public int spacing() {
        return spacing;
    }
}
