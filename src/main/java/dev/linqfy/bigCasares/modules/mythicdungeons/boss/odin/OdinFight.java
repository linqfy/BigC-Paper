package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import java.util.Objects;
import java.util.Optional;

/**
 * Las decisiones de la pelea contra Odín, sin Bukkit: en que etapa esta, cuando cambia, cuando se le puede pegar.
 *
 * <p>Fase 1 ({@link Stage#GUARDIANS}): Odín es invulnerable mientras viva alguno de sus guardianes (cuervos, lobos y
 * einherjar). Cuando ya los invoco y no queda ninguno pasa a la fase 2: se pone el casco ({@link Stage#AWAKENING}) y
 * repite apuntar ({@link Stage#WINDUP}), embestir ({@link Stage#CHARGING}) y recuperarse ({@link Stage#RECOVERING}).
 * Cada {@link Timings#chargesPerStun()} embestidas, o apenas choca contra una pared, queda aturdido
 * ({@link Stage#STUNNED}): es la unica ventana en la que recibe daño.</p>
 *
 * <p>El tiempo son ticks del servidor que pasa quien lo usa; la clase no lee relojes.</p>
 */
public final class OdinFight {

    public enum Stage {
        GUARDIANS,
        AWAKENING,
        WINDUP,
        CHARGING,
        RECOVERING,
        STUNNED
    }

    /**
     * Duraciones en ticks.
     *
     * @param awakeningTicks  el casco y el grito de la fase 2 (invulnerable)
     * @param windupTicks     de que empieza a apuntar a que sale disparado
     * @param aimTicks        la parte del windup en la que todavia sigue al objetivo; el resto la linea queda fija
     * @param recoverTicks    pausa despues de una embestida o de un aturdimiento
     * @param stunTicks       la ventana para pegarle
     * @param chargesPerStun  embestidas completas que llevan al aturdimiento
     * @param maxChargeTicks  tope de una embestida por si el mundo nunca la corta
     */
    public record Timings(int awakeningTicks, int windupTicks, int aimTicks, int recoverTicks, int stunTicks,
                          int chargesPerStun, int maxChargeTicks) {

        public static final Timings DEFAULT = new Timings(50, 30, 10, 18, 90, 2, 45);

        public Timings {
            if (awakeningTicks < 1 || windupTicks < 1 || recoverTicks < 1 || stunTicks < 1 || maxChargeTicks < 1) {
                throw new IllegalArgumentException("durations must be positive");
            }
            if (aimTicks < 0 || aimTicks >= windupTicks) {
                throw new IllegalArgumentException("aim must leave part of the windup locked");
            }
            if (chargesPerStun < 1) {
                throw new IllegalArgumentException("chargesPerStun must be at least one");
            }
        }

        /** Ticks en los que la linea de la embestida ya no se mueve antes de salir (el aviso real). */
        public int lockedTicks() {
            return windupTicks - aimTicks;
        }
    }

    private final Timings timings;
    private Stage stage = Stage.GUARDIANS;
    private long stageStartedAt;
    private boolean guardiansSummoned;
    private int chargesSinceStun;
    private boolean lastStunFromWall;

    public OdinFight(Timings timings) {
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    public OdinFight() {
        this(Timings.DEFAULT);
    }

    public Timings timings() {
        return timings;
    }

    public Stage stage() {
        return stage;
    }

    /** 1 mientras pelean los guardianes, 2 desde que se pone el casco. */
    public int phase() {
        return stage == Stage.GUARDIANS ? 1 : 2;
    }

    /** Solo aturdido recibe daño. */
    public boolean vulnerable() {
        return stage == Stage.STUNNED;
    }

    public boolean guardiansSummoned() {
        return guardiansSummoned;
    }

    /** Cuantas embestidas faltan para el proximo aturdimiento. */
    public int chargesUntilStun() {
        return timings.chargesPerStun() - chargesSinceStun;
    }

    /** {@code true} si el ultimo aturdimiento fue por chocar contra una pared. */
    public boolean lastStunFromWall() {
        return lastStunFromWall;
    }

    public long ticksInStage(long tick) {
        return Math.max(0L, tick - stageStartedAt);
    }

    /** Ticks que le quedan a la etapa actual (0 para las que no terminan solas). */
    public long ticksLeft(long tick) {
        int length = switch (stage) {
            case GUARDIANS -> 0;
            case AWAKENING -> timings.awakeningTicks();
            case WINDUP -> timings.windupTicks();
            case CHARGING -> timings.maxChargeTicks();
            case RECOVERING -> timings.recoverTicks();
            case STUNNED -> timings.stunTicks();
        };
        return Math.max(0L, length - ticksInStage(tick));
    }

    /** En el windup: {@code true} cuando la direccion ya no sigue al objetivo. */
    public boolean aimLocked(long tick) {
        return stage == Stage.WINDUP && ticksInStage(tick) >= timings.aimTicks();
    }

    /** Los guardianes ya estan en el mundo (aunque hayan nacido cero: entonces la fase 1 termina enseguida). */
    public void markGuardiansSummoned() {
        if (stage == Stage.GUARDIANS) {
            guardiansSummoned = true;
        }
    }

    /**
     * Avanza la etapa por tiempo.
     *
     * @param guardiansAlive guardianes vivos (solo importa en la fase 1)
     * @param hasTargets     hay al menos un jugador al que embestir; sin nadie se queda recuperandose
     * @return la etapa nueva si cambio
     */
    public Optional<Stage> update(long tick, int guardiansAlive, boolean hasTargets) {
        long elapsed = ticksInStage(tick);
        return switch (stage) {
            case GUARDIANS -> guardiansSummoned && guardiansAlive <= 0 ? enter(Stage.AWAKENING, tick) : Optional.empty();
            case AWAKENING -> elapsed >= timings.awakeningTicks() ? enter(Stage.RECOVERING, tick) : Optional.empty();
            case WINDUP -> elapsed >= timings.windupTicks() ? enter(Stage.CHARGING, tick) : Optional.empty();
            case CHARGING -> elapsed >= timings.maxChargeTicks() ? Optional.of(chargeEnded(tick, false)) : Optional.empty();
            case STUNNED -> elapsed >= timings.stunTicks() ? enter(Stage.RECOVERING, tick) : Optional.empty();
            case RECOVERING -> {
                if (elapsed < timings.recoverTicks()) {
                    yield Optional.empty();
                }
                if (!hasTargets) {
                    stageStartedAt = tick; // nadie a quien embestir: sigue esperando
                    yield Optional.empty();
                }
                yield enter(Stage.WINDUP, tick);
            }
        };
    }

    /**
     * La embestida termino: contra una pared queda aturdido enseguida (y la cuenta vuelve a cero); si no, suma una
     * y al llegar a {@link Timings#chargesPerStun()} tambien queda aturdido.
     *
     * @return la etapa en la que queda (sin cambios si no estaba embistiendo)
     */
    public Stage chargeEnded(long tick, boolean hitWall) {
        if (stage != Stage.CHARGING) {
            return stage;
        }
        if (hitWall) {
            lastStunFromWall = true;
            chargesSinceStun = 0;
            enter(Stage.STUNNED, tick);
            return stage;
        }
        chargesSinceStun++;
        if (chargesSinceStun >= timings.chargesPerStun()) {
            lastStunFromWall = false;
            chargesSinceStun = 0;
            enter(Stage.STUNNED, tick);
        } else {
            enter(Stage.RECOVERING, tick);
        }
        return stage;
    }

    private Optional<Stage> enter(Stage next, long tick) {
        stage = next;
        stageStartedAt = tick;
        return Optional.of(next);
    }
}
