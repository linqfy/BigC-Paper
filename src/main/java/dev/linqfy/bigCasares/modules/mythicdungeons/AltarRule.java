package dev.linqfy.bigCasares.modules.mythicdungeons;

/**
 * Cual es el altar del jefe. La sala del jefe es la unica pieza de la mazmorra con planta de 35x35; el altar es el
 * bloque que la generacion deja parado sobre su piso, cerca del centro. Asi un bloque de altar puesto por un jugador
 * en un pasillo no sirve para invocar: fuera de la sala no hay lugar para la pelea.
 */
public final class AltarRule {

    /** Lado de la planta de la sala del jefe (pieza "arena" del datapack). */
    public static final int ARENA_SIDE = 35;
    /** Hasta donde puede estar el altar del centro de la sala, en bloques (la generacion lo deja a 3). */
    public static final int CENTRE_REACH = 4;

    private AltarRule() {
    }

    /** {@code true} si una pieza con esa caja (maximos inclusivos) es la sala del jefe. */
    public static boolean isArena(int minX, int minZ, int maxX, int maxZ) {
        return maxX - minX + 1 == ARENA_SIDE && maxZ - minZ + 1 == ARENA_SIDE;
    }

    /** {@code true} si el bloque esta donde la generacion deja el altar dentro de esa sala. */
    public static boolean isAltarSpot(int minX, int minY, int minZ, int maxX, int maxZ, int x, int y, int z) {
        if (!isArena(minX, minZ, maxX, maxZ) || y != minY + 1) {
            return false;
        }
        int centreX = minX + ARENA_SIDE / 2;
        int centreZ = minZ + ARENA_SIDE / 2;
        return Math.abs(x - centreX) <= CENTRE_REACH && Math.abs(z - centreZ) <= CENTRE_REACH;
    }
}
