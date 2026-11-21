package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

/**
 * Las dos fases de Zeus y que golpes le entran, sin Bukkit.
 * <ul>
 *   <li>Fase 1 (tormenta): solo golpes cuerpo a cuerpo de un jugador; flechas y cualquier proyectil no hacen nada.
 *   La vida no baja de la mitad en esta fase: al llegar ahi empieza la transicion.</li>
 *   <li>Transicion: unos segundos invulnerable mientras se enfurece.</li>
 *   <li>Fase 2 (lanzas): solo cuerpo a cuerpo y solo con una lanza clavada en el piso.</li>
 * </ul>
 */
public final class ZeusFightRules {

    public enum Stage {
        STORM,
        TRANSITION,
        SPEARS
    }

    /** Que clase de golpe es, segun el evento de dano. */
    public enum HitKind {
        /** Golpe directo de un jugador (ENTITY_ATTACK / ENTITY_SWEEP_ATTACK). */
        MELEE,
        PROJECTILE,
        OTHER
    }

    public enum Verdict {
        ADMITTED,
        PROJECTILE_IMMUNE,
        NOT_MELEE,
        SHIELDED
    }

    public record Decision(Verdict verdict, double damage) {
    }

    /** La vida (fraccion) donde empieza la fase 2. */
    public static final double PHASE_TWO_AT = 0.5;
    /** Ticks de la transicion (invulnerable, animacion rage). */
    public static final int TRANSITION_TICKS = 60;
    /**
     * Distancia maxima jugador-Zeus para contar un golpe como cuerpo a cuerpo (alcance vanilla mas el ancho del
     * modelo, con margen). Es una red de seguridad: cualquier dano "de jugador" que no venga de un proyectil (los
     * dobles de Bedrock ya reenvian los proyectiles como proyectiles) pero llegue desde lejos no es un golpe a mano.
     */
    public static final double MAX_MELEE_DISTANCE = 6.5;

    private Stage stage = Stage.STORM;
    private int transitionEndsAt;

    public Stage stage() {
        return stage;
    }

    public int phase() {
        return stage == Stage.STORM ? 1 : 2;
    }

    /**
     * Pasa de fase segun la vida y el tiempo. Devuelve la etapa nueva si cambio, o {@code null}.
     */
    public Stage update(int tick, double healthFraction) {
        if (stage == Stage.STORM && healthFraction <= PHASE_TWO_AT + 1.0e-9) {
            stage = Stage.TRANSITION;
            transitionEndsAt = tick + TRANSITION_TICKS;
            return stage;
        }
        if (stage == Stage.TRANSITION && tick >= transitionEndsAt) {
            stage = Stage.SPEARS;
            return stage;
        }
        return null;
    }

    /**
     * Cuanto de un golpe entra en la vida.
     *
     * @param distance   distancia del atacante a Zeus (bloques)
     * @param health     vida actual
     * @param maximum    vida maxima
     * @param spearStuck hay una lanza clavada (ventana de la fase 2)
     */
    public Decision admit(HitKind kind, double damage, double distance, double health, double maximum,
                          boolean spearStuck) {
        if (kind == HitKind.PROJECTILE) {
            return new Decision(Verdict.PROJECTILE_IMMUNE, 0.0);
        }
        if (kind != HitKind.MELEE || !(distance <= MAX_MELEE_DISTANCE)) {
            return new Decision(Verdict.NOT_MELEE, 0.0);
        }
        if (!Double.isFinite(damage) || damage <= 0.0) {
            return new Decision(Verdict.NOT_MELEE, 0.0);
        }
        return switch (stage) {
            case STORM -> {
                // no pasa de la mitad en un golpe: el cambio de fase siempre se ve
                double room = health - maximum * PHASE_TWO_AT;
                yield room <= 0.0 ? new Decision(Verdict.SHIELDED, 0.0)
                    : new Decision(Verdict.ADMITTED, Math.min(damage, room));
            }
            case TRANSITION -> new Decision(Verdict.SHIELDED, 0.0);
            case SPEARS -> spearStuck ? new Decision(Verdict.ADMITTED, damage) : new Decision(Verdict.SHIELDED, 0.0);
        };
    }
}
