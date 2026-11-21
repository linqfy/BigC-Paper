package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import org.bukkit.GameMode;

/** Cuanto de cada golpe le llega a Horus y a quien ataca, sin objetos del servidor. */
public final class HorusDamageRules {

    /** Dano del cuerpo de Horus al pasar planeando (cuatro corazones antes de armadura). */
    public static final double GLIDE_DAMAGE = 8.0;
    /** Dano del aletazo cuando esta en el piso. */
    public static final double BUFFET_DAMAGE = 5.0;
    public static final double BUFFET_RADIUS = 4.5;

    private HorusDamageRules() {
    }

    /** Solo se ataca a jugadores vivos en supervivencia o aventura (no al staff en creativo o espectador). */
    public static boolean targetable(GameMode mode, boolean dead) {
        return !dead && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    /**
     * En la fase 1 un golpe no puede bajarlo de la mitad (ahi empieza la fase 2: el resto se lo sacan los Ojos);
     * en la fase 2 es invulnerable.
     */
    public static double admitted(int phase, double health, double maximumHealth, double damage) {
        if (phase != 1 || !Double.isFinite(damage) || damage <= 0.0) {
            return 0.0;
        }
        double floor = maximumHealth * HorusDirector.PHASE_TWO_AT;
        return Math.max(0.0, Math.min(damage, health - floor));
    }
}
