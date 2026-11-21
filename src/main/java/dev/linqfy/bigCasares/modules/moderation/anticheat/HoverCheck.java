package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Vuelo / levitacion: se muestrea periodicamente (no por PlayerMoveEvent, que Paper no dispara si el jugador queda quieto
 * en el aire). Solo cuenta cuando no hay nada solido, liquido ni trepable en un margen amplio alrededor y debajo del
 * jugador; si en varias muestras seguidas no cae, la gravedad no se esta aplicando.
 */
public final class HoverCheck {
    /** Cuanto tiene que bajar entre dos muestras para considerar que cae. */
    static final double MIN_DROP = 0.1;
    /** Muestras seguidas sin caer antes de reportar (con muestreo cada 5 ticks, 6 = 1.5 segundos). */
    static final int SAMPLES_TO_FLAG = 6;

    private final Map<UUID, State> states = new HashMap<>();

    /**
     * @param y        altura actual del jugador
     * @param airborne no hay ningun apoyo posible cerca (bloque, liquido, trepable, entidad plataforma)
     * @param exempt   cualquier gracia o estado que el check no mide (vuelo permitido, elytra, efectos, gravedad custom...)
     */
    public Optional<Detection> sample(UUID playerId, double y, boolean airborne, boolean exempt) {
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        if (exempt || !airborne) {
            state.lastY = Double.NaN;
            state.samples = 0;
            return Optional.empty();
        }
        if (!Double.isNaN(state.lastY) && y >= state.lastY - MIN_DROP) {
            state.samples++;
        } else {
            state.samples = 0;
        }
        state.lastY = y;
        if (state.samples >= SAMPLES_TO_FLAG) {
            int samples = state.samples;
            state.samples = 0;
            return Optional.of(new Detection("hover-flight", String.format(Locale.ROOT,
                "%d muestras seguidas en el aire sin caer (y=%.2f) y sin nada debajo", samples, y)));
        }
        return Optional.empty();
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private double lastY = Double.NaN;
        private int samples;
    }
}
