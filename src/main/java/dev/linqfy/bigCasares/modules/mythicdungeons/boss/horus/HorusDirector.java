package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

/**
 * El guion de la pelea de Horus, tick a tick y sin Bukkit.
 *
 * <p>Fase 1 (el planeo): despega, apunta desde el aire (aviso en el piso), planea en picada sobre un jugador y,
 * tras 2 o 3 planeos, baja y queda en el piso unos segundos (ventana para pegarle) con un aletazo avisado al final.
 * Cuando la vida llega a {@link #PHASE_TWO_AT} pasa a la fase 2: sube al centro (invulnerable), saca los Ojos del
 * piso por oleadas, espera a que los maten a todos, se enfurece y se deshace en polvo ({@link Stage#DONE}).</p>
 */
public final class HorusDirector {

    /** Fraccion de vida que abre la fase 2. */
    public static final double PHASE_TWO_AT = 0.5;
    /** Debajo de esta fraccion hace tres planeos por vuelo en lugar de dos. */
    public static final double THIRD_GLIDE_BELOW = 0.75;

    public static final int LANDED_TICKS = 72;
    /** Tick de {@link Stage#LANDED} en que empieza el aviso del aletazo. */
    public static final int BUFFET_WARN_AT = 40;
    /** Tick de {@link Stage#LANDED} en que pega el aletazo (0,9 s despues del aviso). */
    public static final int BUFFET_HIT_AT = 58;
    public static final int TAKEOFF_TICKS = 20;
    /** El aviso del planeo en el piso dura esto (1,3 s) antes de lanzarse. */
    public static final int AIM_TICKS = 26;
    public static final int DESCEND_TICKS = 16;
    public static final int ASCEND_TICKS = 60;
    /** Cada cuanto sale una oleada de Ojos. */
    public static final int WAVE_EVERY = 16;
    public static final int WRATH_TICKS = 50;

    public enum Stage {
        LANDED, TAKEOFF, AIM, GLIDE, DESCEND, ASCEND, SUMMON, GUARD, WRATH, DONE;

        public int phase() {
            return ordinal() >= ASCEND.ordinal() ? 2 : 1;
        }
    }

    private Stage stage = Stage.TAKEOFF;
    private int ticks;
    private boolean fresh = true;
    private int glidesLeft;
    private int glideTicks;

    public Stage stage() {
        return stage;
    }

    /** Ticks desde que entro a la etapa actual (0 en el tick de entrada). */
    public int ticksInStage() {
        return ticks;
    }

    public int phase() {
        return stage.phase();
    }

    /** En la fase 1 se le puede pegar (en el piso o al pasar); en la fase 2 no. */
    public boolean vulnerable() {
        return stage.phase() == 1;
    }

    public int glidesLeft() {
        return glidesLeft;
    }

    /** Lo llama el encuentro al entrar a {@link Stage#AIM}: cuanto dura el planeo planeado (0 = no hay a quien). */
    public void glidePlanned(int durationTicks) {
        glideTicks = Math.max(0, durationTicks);
    }

    public boolean buffetWarning() {
        return stage == Stage.LANDED && ticks == BUFFET_WARN_AT;
    }

    public boolean buffetStrike() {
        return stage == Stage.LANDED && ticks == BUFFET_HIT_AT;
    }

    public static int glidesFor(double healthFraction) {
        return healthFraction > THIRD_GLIDE_BELOW ? 2 : 3;
    }

    /**
     * Avanza un tick.
     *
     * @param healthFraction  vida actual / maxima de Horus
     * @param eyesAllSpawned  si ya salieron (y siguen) todos los Ojos
     * @param eyesCleared     si ya mataron a todos los Ojos
     * @return {@code true} si en este tick entro a una etapa nueva (el primer tick tambien cuenta)
     */
    public boolean tick(double healthFraction, boolean eyesAllSpawned, boolean eyesCleared) {
        if (fresh) {
            fresh = false;
            enter(stage, healthFraction);
            return true;
        }
        if (stage.phase() == 1 && healthFraction <= PHASE_TWO_AT + 1.0E-9) {
            return enter(Stage.ASCEND, healthFraction);
        }
        ticks++;
        return switch (stage) {
            case LANDED -> ticks >= LANDED_TICKS && enter(Stage.TAKEOFF, healthFraction);
            case TAKEOFF -> ticks >= TAKEOFF_TICKS && enter(Stage.AIM, healthFraction);
            case AIM -> ticks >= AIM_TICKS && enter(glideTicks > 0 ? Stage.GLIDE : Stage.AIM, healthFraction);
            case GLIDE -> ticks >= glideTicks && afterGlide(healthFraction);
            case DESCEND -> ticks >= DESCEND_TICKS && enter(Stage.LANDED, healthFraction);
            case ASCEND -> ticks >= ASCEND_TICKS && enter(Stage.SUMMON, healthFraction);
            case SUMMON -> eyesAllSpawned && enter(Stage.GUARD, healthFraction);
            case GUARD -> eyesCleared && enter(Stage.WRATH, healthFraction);
            case WRATH -> ticks >= WRATH_TICKS && enter(Stage.DONE, healthFraction);
            case DONE -> false;
        };
    }

    private boolean afterGlide(double healthFraction) {
        glidesLeft--;
        return enter(glidesLeft > 0 ? Stage.AIM : Stage.DESCEND, healthFraction);
    }

    private boolean enter(Stage next, double healthFraction) {
        stage = next;
        ticks = 0;
        if (next == Stage.TAKEOFF) {
            glidesLeft = glidesFor(healthFraction);
        }
        if (next == Stage.AIM) {
            glideTicks = 0;
        }
        return true;
    }
}
