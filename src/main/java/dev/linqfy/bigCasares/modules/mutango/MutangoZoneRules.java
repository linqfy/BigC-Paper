package dev.linqfy.bigCasares.modules.mutango;

/** Reglas de la cuenta regresiva de la zona protegida (sin Bukkit). */
final class MutangoZoneRules {

    /** Hasta donde (bloques) sigue siendo "la pelea" de Neguechen: mas lejos, o en otro mundo, la cuenta ya no corre. */
    static final double FIGHT_RADIUS = 64.0;

    private MutangoZoneRules() {
    }

    /**
     * El jugador sigue en la pelea: en el mismo mundo que Neguechen y a menos de FIGHT_RADIUS de ella. Si se fue con un
     * teleport a otro claim u otra dimension, la cuenta se cancela y el rayo no le cae donde esta ahora.
     */
    static boolean inFight(boolean sameWorld, double distanceSquared) {
        return sameWorld && distanceSquared <= FIGHT_RADIUS * FIGHT_RADIUS;
    }
}
