package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

/**
 * Cuantas nubes electricas mantiene Zeus en la fase 2 y cuando repone, sin Bukkit. La vida de una nube es la mitad
 * de la de un zombie: la entidad vanilla tiene un colchon de vida grande y la nube "muere" cuando el dano recibido
 * llega a {@link #CLOUD_HEALTH}, asi nunca suelta botin ni experiencia vanilla.
 */
public final class ZeusCloudRoster {

    /** Vida de una nube: la mitad de un zombie. */
    public static final double CLOUD_HEALTH = 10.0;
    /** Vida vanilla de la entidad de base (el colchon). */
    public static final double BUFFER_HEALTH = 100.0;
    /** Dano de un golpe de nube: el de un zombie en dificultad normal. */
    public static final double CLOUD_ATTACK = 3.0;
    public static final int MAXIMUM_CLOUDS = 6;
    /** Cada cuanto repone una nube si faltan. */
    public static final int REFILL_TICKS = 160;

    private int lastRefill;

    public ZeusCloudRoster(int startTick) {
        this.lastRefill = startTick;
    }

    /** Nubes que quiere tener: tres para uno solo, una mas por cada jugador extra, hasta seis. */
    public static int target(int players) {
        return Math.max(3, Math.min(MAXIMUM_CLOUDS, 2 + Math.max(1, players)));
    }

    /** Cuantas crear ahora: todas las que faltan al empezar, despues de a una cada {@link #REFILL_TICKS}. */
    public int toSpawn(int tick, int alive, int players, boolean firstWave) {
        int missing = Math.max(0, target(players) - alive);
        if (missing == 0) {
            lastRefill = tick;
            return 0;
        }
        if (firstWave) {
            lastRefill = tick;
            return missing;
        }
        if (tick - lastRefill >= REFILL_TICKS) {
            lastRefill = tick;
            return 1;
        }
        return 0;
    }

    /** Vida que le queda a una nube segun la vida vanilla de su entidad. */
    public static double remaining(double vanillaHealth) {
        double taken = Math.max(0.0, BUFFER_HEALTH - vanillaHealth);
        return Math.max(0.0, CLOUD_HEALTH - taken);
    }
}
