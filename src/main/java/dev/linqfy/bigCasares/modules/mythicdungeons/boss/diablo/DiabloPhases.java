package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

/**
 * Las dos fases del diseño, sin Bukkit: la 1 (oscuridad) hasta que la vida baja a la fraccion configurada
 * ({@code phases.phase-2-at} del YAML) y la 2 (agarre y QTE) hasta el final; no se vuelve atras aunque se cure.
 * Tambien el parpadeo del contorno amarillo.
 */
final class DiabloPhases {

    private final double phaseTwoAt;
    private int phase = 1;

    DiabloPhases(double phaseTwoAt) {
        if (!(phaseTwoAt > 0.0 && phaseTwoAt < 1.0)) {
            throw new IllegalArgumentException("phase two threshold must be in (0, 1)");
        }
        this.phaseTwoAt = phaseTwoAt;
    }

    /** @return {@code true} solo en la llamada en que pasa a la fase 2 */
    boolean update(double healthFraction) {
        if (phase == 1 && healthFraction <= phaseTwoAt) {
            phase = 2;
            return true;
        }
        return false;
    }

    int phase() {
        return phase;
    }

    /** El contorno se ve {@code onTicks} y se apaga {@code offTicks}, en ciclo. */
    static boolean outlineLit(long tick, int onTicks, int offTicks) {
        if (onTicks < 1 || offTicks < 0) {
            throw new IllegalArgumentException("invalid blink");
        }
        return Math.floorMod(tick, (long) onTicks + offTicks) < onTicks;
    }
}
