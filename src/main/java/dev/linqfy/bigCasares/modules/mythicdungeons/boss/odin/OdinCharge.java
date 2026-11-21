package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * La embestida de Odín sin Bukkit: el recorrido por el terreno (pared, borde, paso), quien queda en el camino, cuanto
 * saca el golpe y a quien apunta.
 */
public final class OdinCharge {

    /** Bloques que avanza por tick (17 bloques por segundo). */
    public static final double SPEED = 0.85;
    /** Largo maximo de una embestida. */
    public static final double MAX_DISTANCE = 26.0;
    /** Medio ancho del cuerpo para el golpe (y para la linea del aviso). */
    public static final double HIT_RADIUS = 2.0;
    /** Alto libre que necesita (el modelo mide ~3,3 bloques; el Warden ~2,9). */
    public static final int CLEARANCE = 3;
    /** Cuanto puede subir de un paso sin que cuente como pared. */
    public static final int STEP_UP = 1;
    /** Cuanto puede bajar siguiendo el suelo antes de frenar en el borde. */
    public static final int STEP_DOWN = 3;

    /** Si un bloque frena el paso (solido, no pasable). */
    @FunctionalInterface
    public interface Terrain {
        boolean solid(int x, int y, int z);
    }

    public enum Outcome {
        /** Avanza a la posicion del paso. */
        MOVE,
        /** Choco contra una pared: queda aturdido. */
        WALL,
        /** Se termina el piso (o el radio de la sala): frena sin aturdirse. */
        EDGE
    }

    /** Un paso: el resultado y donde quedan sus pies (para WALL y EDGE, la posicion de antes). */
    public record Step(Outcome outcome, double x, double y, double z) {

        public Step {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Que le hace el golpe a un jugador: la mitad de su vida maxima, sin contar armadura. */
    public record Impact(boolean lethal, double remainingHealth) {

        public static Impact against(double health, double maximumHealth) {
            if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0.0) {
                throw new IllegalArgumentException("health must be finite and the maximum positive");
            }
            double remaining = health - maximumHealth / 2.0;
            return remaining > 0.0 ? new Impact(false, remaining) : new Impact(true, 0.0);
        }
    }

    private OdinCharge() {
    }

    /**
     * Un paso de largo {@code distance} desde los pies ({@code x}, {@code y}, {@code z}) en la direccion horizontal
     * ({@code dirX}, {@code dirZ}) unitaria. Mira la columna que toca el frente del cuerpo: si esta tapada y no se
     * puede subir un escalon, es pared; si no hay piso a {@link #STEP_DOWN} bloques, es borde.
     */
    public static Step step(Terrain terrain, double x, double y, double z, double dirX, double dirZ, double distance,
                            double arenaCenterX, double arenaCenterZ, double arenaRadius) {
        Objects.requireNonNull(terrain, "terrain");
        double nextX = x + dirX * distance;
        double nextZ = z + dirZ * distance;
        double dx = nextX - arenaCenterX;
        double dz = nextZ - arenaCenterZ;
        if (dx * dx + dz * dz > arenaRadius * arenaRadius) {
            return new Step(Outcome.EDGE, x, y, z);
        }
        int feet = (int) Math.floor(y + 1.0e-6);
        int frontX = (int) Math.floor(nextX + dirX * 0.9);
        int frontZ = (int) Math.floor(nextZ + dirZ * 0.9);
        int targetFeet;
        if (free(terrain, frontX, feet, frontZ)) {
            targetFeet = Integer.MIN_VALUE;
            for (int candidate = feet; candidate >= feet - STEP_DOWN; candidate--) {
                if (terrain.solid(frontX, candidate - 1, frontZ)) {
                    targetFeet = candidate;
                    break;
                }
            }
            if (targetFeet == Integer.MIN_VALUE) {
                return new Step(Outcome.EDGE, x, y, z);
            }
        } else if (STEP_UP > 0 && free(terrain, frontX, feet + STEP_UP, frontZ)) {
            targetFeet = feet + STEP_UP;
        } else {
            return new Step(Outcome.WALL, x, y, z);
        }
        int centreX = (int) Math.floor(nextX);
        int centreZ = (int) Math.floor(nextZ);
        if (!free(terrain, centreX, targetFeet, centreZ)) {
            return new Step(Outcome.WALL, x, y, z);
        }
        return new Step(Outcome.MOVE, nextX, targetFeet, nextZ);
    }

    /** Hasta donde llegaria una embestida (para dibujar el aviso): metros recorridos antes de pared, borde o tope. */
    public static double reach(Terrain terrain, double x, double y, double z, double dirX, double dirZ,
                               double arenaCenterX, double arenaCenterZ, double arenaRadius) {
        double travelled = 0.0;
        double cx = x;
        double cy = y;
        double cz = z;
        while (travelled + SPEED <= MAX_DISTANCE) {
            Step step = step(terrain, cx, cy, cz, dirX, dirZ, SPEED, arenaCenterX, arenaCenterZ, arenaRadius);
            if (step.outcome() != Outcome.MOVE) {
                break;
            }
            cx = step.x();
            cy = step.y();
            cz = step.z();
            travelled += SPEED;
        }
        return travelled;
    }

    /** El jugador (sus pies) queda bajo el cuerpo de Odín (sus pies en {@code bossX/Y/Z}). */
    public static boolean hits(double bossX, double bossY, double bossZ, double playerX, double playerY,
                               double playerZ) {
        double dx = playerX - bossX;
        double dz = playerZ - bossZ;
        double dy = playerY - bossY;
        return dx * dx + dz * dz <= HIT_RADIUS * HIT_RADIUS && dy >= -1.5 && dy <= 3.3;
    }

    /**
     * Elige a quien embestir: al azar, pero si hay mas de uno evita repetir al ultimo.
     *
     * @param random devuelve un entero en [0, n) para el n que recibe
     */
    public static UUID pickTarget(List<UUID> candidates, UUID last, IntUnaryOperator random) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(random, "random");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no candidates");
        }
        List<UUID> pool = candidates.size() > 1 && last != null && candidates.contains(last)
            ? candidates.stream().filter(id -> !id.equals(last)).toList()
            : candidates;
        int index = Math.floorMod(random.applyAsInt(pool.size()), pool.size());
        return pool.get(index);
    }

    private static boolean free(Terrain terrain, int x, int feet, int z) {
        for (int dy = 0; dy < CLEARANCE; dy++) {
            if (terrain.solid(x, feet + dy, z)) {
                return false;
            }
        }
        return true;
    }
}
