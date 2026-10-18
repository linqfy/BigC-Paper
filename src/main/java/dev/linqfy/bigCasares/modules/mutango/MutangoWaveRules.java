package dev.linqfy.bigCasares.modules.mutango;

/** Hasta que altura sobre el piso le pega la onda de las manos (sin Bukkit). */
final class MutangoWaveRules {

    /** Si saltas mas alto que esto sobre el piso, la onda pasa por abajo. */
    static final double JUMP_CLEARANCE = 0.45;
    /** Tolerancia hacia abajo (media losa): mas abajo que esto no es el piso donde pasa la onda. */
    static final double BELOW_TOLERANCE = 0.6;

    private MutangoWaveRules() {
    }

    /**
     * La onda alcanza a quien tiene los pies a esa altura (pies menos piso). No le pega a quien esta saltando ni a quien
     * esta muy abajo del piso (minando, en una cueva): antes cualquier altura negativa pasaba.
     */
    static boolean reaches(double heightAboveFloor) {
        return heightAboveFloor >= -BELOW_TOLERANCE && heightAboveFloor < JUMP_CLEARANCE;
    }
}
