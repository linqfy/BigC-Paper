package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import org.bukkit.GameMode;

import java.util.Objects;

/**
 * El ciclo de la fase 2: Diablo se enoja, se sienta y estira los brazos (aviso), agarra a todos, y queda aturdido
 * cuando ya no le queda nadie agarrado (todos completaron su QTE, o murieron o se fueron). Si el agarre dura demasiado
 * golpea a los que siguen agarrados y queda aturdido igual, agotado: la ventana de dano siempre llega, completar el QTE
 * solo la adelanta (si no, un jugador AFK la bloquearia para siempre). Despues del aturdimiento se levanta, descansa y
 * repite hasta morir. Solo mientras esta aturdido se le puede pegar. Sin Bukkit (salvo el enum {@link GameMode}).
 */
final class DiabloGrabCycle {

    enum Stage { IDLE, ENRAGED, REACHING, HOLDING, STUNNED, RECOVERING }

    enum Event {
        NONE,
        /** Se sienta y estira los brazos: aviso del agarre. */
        REACH,
        /** El agarre llega: cada jugador queda sujeto y empieza su QTE. */
        GRAB,
        /** Nadie quedo agarrado: queda aturdido. */
        STUN,
        /** Se acabo el tiempo del agarre: golpea a los que siguen agarrados, los suelta y queda aturdido (agotado). */
        SLAM,
        /** Termina el aturdimiento: se levanta. */
        STAND_UP
    }

    /**
     * @param enragedTicks  el enojo inicial antes del primer agarre
     * @param reachTicks    aviso entre que estira los brazos y el agarre
     * @param holdCapTicks  tope del agarre
     * @param stunTicks     ventana de dano
     * @param recoverTicks  pausa antes de volver a sentarse
     */
    record Settings(int enragedTicks, int reachTicks, int holdCapTicks, int stunTicks, int recoverTicks) {

        Settings {
            if (enragedTicks < 0 || reachTicks < 1 || holdCapTicks < 1 || stunTicks < 1 || recoverTicks < 0) {
                throw new IllegalArgumentException("invalid grab cycle timings");
            }
        }
    }

    private final Settings settings;
    private Stage stage = Stage.IDLE;
    private long stageStart;
    private int grabs;

    DiabloGrabCycle(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    void start(long tick) {
        enter(Stage.ENRAGED, tick);
        grabs = 0;
    }

    /**
     * @param nobodyHeld {@code true} cuando no queda ningun jugador agarrado sin completar su QTE
     */
    Event tick(long tick, boolean nobodyHeld) {
        long elapsed = tick - stageStart;
        switch (stage) {
            case ENRAGED -> {
                if (elapsed >= settings.enragedTicks()) {
                    enter(Stage.REACHING, tick);
                    return Event.REACH;
                }
            }
            case REACHING -> {
                if (elapsed >= settings.reachTicks()) {
                    enter(Stage.HOLDING, tick);
                    grabs++;
                    return Event.GRAB;
                }
            }
            case HOLDING -> {
                if (nobodyHeld) {
                    enter(Stage.STUNNED, tick);
                    return Event.STUN;
                }
                if (elapsed >= settings.holdCapTicks()) {
                    enter(Stage.STUNNED, tick);
                    return Event.SLAM;
                }
            }
            case STUNNED -> {
                if (elapsed >= settings.stunTicks()) {
                    enter(Stage.RECOVERING, tick);
                    return Event.STAND_UP;
                }
            }
            case RECOVERING -> {
                if (elapsed >= settings.recoverTicks()) {
                    enter(Stage.REACHING, tick);
                    return Event.REACH;
                }
            }
            case IDLE -> {
                return Event.NONE;
            }
        }
        return Event.NONE;
    }

    /** Solo pelean (los agarra, cuentan para el aturdimiento) los vivos en supervivencia o aventura. */
    static boolean fights(GameMode mode, boolean dead) {
        return !dead && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    Stage stage() {
        return stage;
    }

    /** Solo aturdido recibe dano. */
    boolean vulnerable() {
        return stage == Stage.STUNNED;
    }

    /** Cuantos agarres hubo (el primero es 1); sirve para endurecer el QTE. */
    int grabs() {
        return grabs;
    }

    /** Ticks que quedan del aturdimiento (0 fuera de el). */
    long stunTicksLeft(long tick) {
        return stage == Stage.STUNNED ? Math.max(0L, settings.stunTicks() - (tick - stageStart)) : 0L;
    }

    private void enter(Stage next, long tick) {
        stage = next;
        stageStart = tick;
    }
}
