package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * La vida de los dos señores, sin Bukkit. La barra del jefe (el pool del runtime) es la SUMA de las dos vidas: cada
 * señor tiene la mitad del maximo. Mientras uno siga en pie la suma es positiva, asi que la barra nunca mata al jefe
 * antes de tiempo; solo con los dos caidos a la vez la pelea termina.
 *
 * <ul>
 *   <li>Fase 1: cada gol le saca {@link #GOAL_SHARE} de su vida a cada uno (nunca los deja en cero).</li>
 *   <li>Fase 2: cada golpe baja la vida de ese señor. El que llega a cero cae; el otro lo revive a los
 *   {@link #REVIVE_TICKS} ticks con una parte de su vida (cada revivida un poco menos). Mientras revive recibe
 *   {@link #REVIVING_MULTIPLIER} veces el daño. Si cae el que revivia, caen los dos.</li>
 * </ul>
 */
public final class CameDuoHealth {

    public static final double GOAL_SHARE = 0.10;
    public static final long REVIVE_TICKS = 200L;
    public static final double REVIVING_MULTIPLIER = 1.5;
    private static final double[] REVIVE_SHARES = {0.5, 0.4, 0.3, 0.25};

    /** Resultado de un golpe: cuanto bajo la barra y si alguien cayo. */
    public record Hit(double removed, boolean downed, boolean bothDown) {
        static final Hit NOTHING = new Hit(0.0, false, false);
    }

    /** Un señor que volvio de Xibalbá con {@code restored} de vida. */
    public record Revival(CameLord lord, double restored) {
    }

    private final double lordMaximum;
    private final Map<CameLord, Double> health = new EnumMap<>(CameLord.class);
    private final Map<CameLord, Long> downAt = new EnumMap<>(CameLord.class);
    private int revives;

    /** @param total el maximo de la barra (ya escalado por jugadores) */
    public CameDuoHealth(double total) {
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("total health must be positive");
        }
        lordMaximum = total / 2.0;
        health.put(CameLord.HUN, lordMaximum);
        health.put(CameLord.VUCUB, lordMaximum);
    }

    public double lordMaximum() {
        return lordMaximum;
    }

    public double health(CameLord lord) {
        return health.get(lord);
    }

    public double total() {
        return health.get(CameLord.HUN) + health.get(CameLord.VUCUB);
    }

    public boolean down(CameLord lord) {
        return downAt.containsKey(lord);
    }

    public boolean bothDown() {
        return downAt.size() == 2;
    }

    /** El señor caido que esta esperando que lo revivan, si hay uno (y el otro sigue en pie). */
    public Optional<CameLord> awaitingRevive() {
        if (downAt.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(downAt.keySet().iterator().next());
    }

    /** Ticks que faltan para que el caido vuelva (0 si no hay nadie esperando). */
    public long reviveTicksLeft(long now) {
        return awaitingRevive().map(lord -> Math.max(0L, downAt.get(lord) + REVIVE_TICKS - now)).orElse(0L);
    }

    /** Un gol en el aro: les saca a los dos y devuelve cuanto bajo la barra. */
    public double goal() {
        double removed = 0.0;
        for (CameLord lord : CameLord.values()) {
            double current = health.get(lord);
            double cut = Math.min(lordMaximum * GOAL_SHARE, Math.max(0.0, current - 1.0));
            health.put(lord, current - cut);
            removed += cut;
        }
        return removed;
    }

    /** Un golpe de fase 2 a {@code lord}. A un caido no le pasa nada. */
    public Hit hit(CameLord lord, double damage, long now) {
        if (!(damage > 0.0) || !Double.isFinite(damage) || down(lord)) {
            return Hit.NOTHING;
        }
        double effective = down(lord.other()) ? damage * REVIVING_MULTIPLIER : damage;
        double current = health.get(lord);
        double removed = Math.min(current, effective);
        health.put(lord, current - removed);
        if (current - removed > 1.0e-9) {
            return new Hit(removed, false, false);
        }
        health.put(lord, 0.0);
        downAt.put(lord, now);
        return new Hit(removed, true, bothDown());
    }

    /** Revive al caido cuando se cumplen los diez segundos (si el otro sigue en pie). */
    public Optional<Revival> tick(long now) {
        Optional<CameLord> waiting = awaitingRevive();
        if (waiting.isEmpty() || now < downAt.get(waiting.get()) + REVIVE_TICKS) {
            return Optional.empty();
        }
        CameLord lord = waiting.get();
        double restored = lordMaximum * REVIVE_SHARES[Math.min(revives, REVIVE_SHARES.length - 1)];
        revives++;
        downAt.remove(lord);
        health.put(lord, restored);
        return Optional.of(new Revival(lord, restored));
    }

    public int revives() {
        return revives;
    }
}
