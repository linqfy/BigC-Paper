package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import java.util.Optional;

/**
 * La fase 1 (el juego de pelota) como maquina de estados pura: presentacion, aviso del tiro (la pelota en la mano del
 * que tira), vuelo, descanso y vuelta a tirar, alternando entre Hun y Vucub, hasta {@link CameHoop#GOALS_TO_WIN} goles.
 * Los ticks son del encuentro (uno por tick del servidor).
 */
public final class CameBallGame {

    public enum Stage { INTRO, WINDUP, FLIGHT, COOLDOWN, DONE }

    public static final long INTRO_TICKS = 70L;
    /** Aviso del tiro: 1.5 s con la pelota en alto (el minimo pedido es 0.8 s). */
    public static final long WINDUP_TICKS = 30L;
    public static final long COOLDOWN_TICKS = 40L;
    public static final long GOAL_COOLDOWN_TICKS = 60L;

    private Stage stage = Stage.INTRO;
    private long stageStart;
    private long cooldown = COOLDOWN_TICKS;
    private int goals;
    private int throwsStarted;

    public CameBallGame(long now) {
        this.stageStart = now;
    }

    public Stage stage() {
        return stage;
    }

    public int goals() {
        return goals;
    }

    public long ticksInStage(long now) {
        return now - stageStart;
    }

    /** Quien tira (o tiro) la pelota actual: primero Hun, despues se alternan. */
    public CameLord thrower() {
        return throwsStarted % 2 == 1 ? CameLord.HUN : CameLord.VUCUB;
    }

    /**
     * Avanza el reloj. Sin jugadores en la sala no se tira (la pelota que estaba en la mano se cancela con
     * {@link #resolve}).
     *
     * @return la etapa nueva, si cambio
     */
    public Optional<Stage> update(long now, boolean anyTarget) {
        long elapsed = now - stageStart;
        return switch (stage) {
            case INTRO -> elapsed >= INTRO_TICKS && anyTarget ? Optional.of(enterWindup(now)) : Optional.empty();
            case WINDUP -> elapsed >= WINDUP_TICKS ? Optional.of(enter(Stage.FLIGHT, now)) : Optional.empty();
            case COOLDOWN -> elapsed >= cooldown && anyTarget ? Optional.of(enterWindup(now)) : Optional.empty();
            case FLIGHT, DONE -> Optional.empty();
        };
    }

    /** Termino la pelota en juego (o se cancelo el tiro). Devuelve la etapa nueva. */
    public Stage resolve(long now, CameBall.Event outcome) {
        if (stage != Stage.WINDUP && stage != Stage.FLIGHT) {
            return stage;
        }
        if (outcome == CameBall.Event.GOAL) {
            goals++;
            if (goals >= CameHoop.GOALS_TO_WIN) {
                return enter(Stage.DONE, now);
            }
            cooldown = GOAL_COOLDOWN_TICKS;
        } else {
            cooldown = COOLDOWN_TICKS;
        }
        return enter(Stage.COOLDOWN, now);
    }

    private Stage enterWindup(long now) {
        throwsStarted++;
        return enter(Stage.WINDUP, now);
    }

    private Stage enter(Stage next, long now) {
        stage = next;
        stageStart = now;
        return next;
    }
}
