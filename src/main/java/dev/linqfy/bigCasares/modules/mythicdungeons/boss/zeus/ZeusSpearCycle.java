package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.ArrayList;
import java.util.List;

/**
 * El ciclo de lanzas de la fase 2, sin Bukkit. Cada ciclo: Zeus se echa para atras ({@link Event#WINDUP}, se marca
 * el punto de impacto en el piso), suelta la lanza ({@link Event#THROW}), la lanza se clava ({@link Event#IMPACT}) y
 * mientras sigue clavada Zeus se puede golpear ({@link #vulnerable}); al soltarla ({@link Event#WINDOW_CLOSE}) se
 * recupera y vuelve a empezar. Solo en esa ventana entra el dano.
 */
public final class ZeusSpearCycle {

    public enum Event {
        WINDUP,
        THROW,
        IMPACT,
        WINDOW_CLOSE
    }

    /** Desde que Zeus empieza a echarse atras hasta que suelta la lanza. */
    public static final int THROW_AT = 18;
    /** Desde el inicio del ciclo hasta que la lanza se clava: el aviso en el piso dura esto (1,5 s). */
    public static final int IMPACT_AT = 30;
    /** Cuanto queda clavada la lanza: la ventana para pegarle a Zeus (5 s). */
    public static final int WINDOW = 100;
    /** Pausa despues de la ventana, invulnerable, antes de la proxima lanza. */
    public static final int RECOVER = 40;
    public static final int FIRST_DELAY = 40;

    private final int firstCycleAt;
    private int lastTick;

    public ZeusSpearCycle(int startTick) {
        this.firstCycleAt = startTick + FIRST_DELAY;
        this.lastTick = startTick;
    }

    public static int cycleLength() {
        return IMPACT_AT + WINDOW + RECOVER;
    }

    /** Eventos con tick en {@code (anterior, nowTick]}, en orden. */
    public List<Event> advance(int nowTick) {
        if (nowTick <= lastTick) {
            return List.of();
        }
        List<Event> events = new ArrayList<>();
        for (int tick = lastTick + 1; tick <= nowTick; tick++) {
            int offset = offset(tick);
            if (offset < 0) {
                continue;
            }
            if (offset == 0) {
                events.add(Event.WINDUP);
            } else if (offset == THROW_AT) {
                events.add(Event.THROW);
            } else if (offset == IMPACT_AT) {
                events.add(Event.IMPACT);
            } else if (offset == IMPACT_AT + WINDOW) {
                events.add(Event.WINDOW_CLOSE);
            }
        }
        lastTick = nowTick;
        return events;
    }

    /** La lanza esta clavada: Zeus recibe golpes. */
    public boolean vulnerable(int tick) {
        int offset = offset(tick);
        return offset >= IMPACT_AT && offset < IMPACT_AT + WINDOW;
    }

    /** Ticks que le quedan a la ventana abierta (0 si esta cerrada). */
    public int windowRemaining(int tick) {
        return vulnerable(tick) ? IMPACT_AT + WINDOW - offset(tick) : 0;
    }

    /** Zeus esta echado atras preparando la lanza (para la animacion y el aviso del impacto). */
    public boolean windingUp(int tick) {
        int offset = offset(tick);
        return offset >= 0 && offset < IMPACT_AT;
    }

    /** Avance del vuelo de la lanza: 0 al soltarla, 1 al clavarse (fuera del vuelo queda en 0 o 1). */
    public double flight(int tick) {
        int offset = offset(tick);
        if (offset < THROW_AT) {
            return 0.0;
        }
        return Math.min(1.0, (double) (offset - THROW_AT) / (IMPACT_AT - THROW_AT));
    }

    private int offset(int tick) {
        if (tick < firstCycleAt) {
            return -1;
        }
        return (tick - firstCycleAt) % cycleLength();
    }
}
