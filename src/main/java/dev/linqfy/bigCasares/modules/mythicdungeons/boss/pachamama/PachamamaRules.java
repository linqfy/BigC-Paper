package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

/** Damage and phase decisions of the Pachamama fight (no server types). */
public final class PachamamaRules {

    private PachamamaRules() {
    }

    public static boolean entersPhaseTwo(double health, double maximum) {
        return maximum > 0.0 && health <= maximum * PachamamaTuning.PHASE_TWO_AT + 1.0e-9;
    }

    /**
     * Damage of one hit that reaches the shared health pool. She can be hurt at any time except while she rises and
     * during the phase change. A note of the Mandinga guitar is multiplied by {@link PachamamaTuning#NOTE_MULTIPLIER}.
     * In phase 1 a hit cannot take her below the phase-2 threshold, so Mandinga always comes out.
     */
    public static double admit(boolean phaseOne, boolean vulnerable, boolean guitarNote, double health,
                               double maximum, double damage) {
        if (!vulnerable || !Double.isFinite(damage) || damage <= 0.0) {
            return 0.0;
        }
        double dealt = guitarNote ? damage * PachamamaTuning.NOTE_MULTIPLIER : damage;
        if (phaseOne) {
            return Math.max(0.0, Math.min(dealt, health - maximum * PachamamaTuning.PHASE_TWO_AT));
        }
        return dealt;
    }

    /** Whether feet at height {@code y} count as standing on a floor whose walking level is {@code floorY}. */
    public static boolean onFloor(double y, int floorY) {
        return y >= floorY - 0.5 && y < floorY + 1.25;
    }
}
