package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Velocidad horizontal y ascenso imposibles, medidos contra la fisica vanilla con margenes amplios.
 *
 * <p>No intenta predecir el movimiento exacto (eso es lo que da falsos positivos): solo acota lo que un cliente legitimo
 * puede hacer en un tick con su atributo de velocidad, su salto y su escalon, y exige que el exceso se repita. Todo lo que
 * altera esa fisica (hielo, slime, agua, empujes de modulos, explosiones...) llega como {@code exempt} y vacia el estado.
 */
public final class MovementCheck {
    /** Velocidad estable en suelo comun por tick por cada unidad del atributo (0.1 caminando = 0.216 bloques/tick). */
    static final double GROUND_FACTOR = 2.16;
    /** Multiplicador de sprint, por si el servidor todavia no vio el sprint que el cliente ya empezo. */
    static final double SPRINT_FACTOR = 1.3;
    /** Lo maximo que suma el salto con sprint mas la inercia del aire. */
    static final double AIR_BONUS = 0.36;
    /** Eventos seguidos en el suelo antes de exigir el limite de suelo (al aterrizar se conserva la inercia del aire). */
    static final int SETTLE_SAMPLES = 4;
    static final int MAX_ELAPSED_TICKS = 6;
    /** Gravedad vanilla por tick; con ella se acota cuantos ticks puede subir un salto. */
    static final double GRAVITY_PER_TICK = 0.075;

    private final Map<UUID, State> states = new HashMap<>();

    public Optional<Detection> sample(UUID playerId, MovementSample sample) {
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        if (sample.exempt() || sample.elapsedTicks() > MAX_ELAPSED_TICKS) {
            state.calm();
            return Optional.empty();
        }
        int elapsed = Math.max(1, sample.elapsedTicks());
        state.groundSamples = sample.onGround() ? state.groundSamples + 1 : 0;

        Optional<Detection> vertical = checkVertical(state, sample, elapsed);
        if (vertical.isPresent()) {
            return vertical;
        }
        return checkHorizontal(state, sample, elapsed);
    }

    private Optional<Detection> checkHorizontal(State state, MovementSample sample, int elapsed) {
        double perTick = groundVelocity(sample);
        boolean settled = state.groundSamples >= SETTLE_SAMPLES;
        double limit = settled ? perTick * 1.25 + 0.1 : (perTick + AIR_BONUS) * 1.15 + 0.05;
        double allowed = limit * elapsed;
        if (sample.horizontal() <= allowed) {
            state.speed.decay();
            return Optional.empty();
        }
        double excess = (sample.horizontal() - allowed) / allowed;
        if (state.speed.add(1 + Math.min(2, excess * 2))) {
            return Optional.of(new Detection("movement-speed", String.format(Locale.ROOT,
                "Velocidad horizontal sostenida: %.3f bloques en %d tick(s), limite %.3f (%s)",
                sample.horizontal(), elapsed, allowed, settled ? "suelo" : "aire")));
        }
        return Optional.empty();
    }

    private Optional<Detection> checkVertical(State state, MovementSample sample, int elapsed) {
        double rise = sample.vertical();
        if (sample.onGround() || rise <= 0.005) {
            state.risingTicks = 0;
        } else {
            state.risingTicks += elapsed;
        }
        double maxRise = Math.max(sample.jumpVelocity(), sample.stepHeight()) + 0.1;
        double allowedRise = maxRise * Math.min(elapsed, 3);
        int allowedRisingTicks = (int) Math.ceil(sample.jumpVelocity() / GRAVITY_PER_TICK) + 4;
        String evidence = null;
        if (rise > allowedRise) {
            evidence = String.format(Locale.ROOT, "Ascenso de %.3f bloques en %d tick(s), limite %.3f",
                rise, elapsed, allowedRise);
        } else if (state.risingTicks > allowedRisingTicks) {
            evidence = "Subiendo en el aire durante " + state.risingTicks + " ticks, un salto dura como mucho "
                + allowedRisingTicks;
            state.risingTicks = 0;
        }
        if (evidence == null) {
            if (state.risingTicks == 0) {
                state.upward.decay(); // mientras sigue subiendo no se olvida el ascenso anterior
            }
            return Optional.empty();
        }
        return state.upward.add(1) ? Optional.of(new Detection("upward-movement", evidence)) : Optional.empty();
    }

    static double groundVelocity(MovementSample sample) {
        double speed = Math.max(0, sample.movementSpeed()) * (sample.sprinting() ? 1.0 : SPRINT_FACTOR);
        return speed * GROUND_FACTOR;
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private final ViolationBuffer speed = new ViolationBuffer(6, 0.2, 3);
        private final ViolationBuffer upward = new ViolationBuffer(2, 0.05, 0.5);
        private int groundSamples;
        private int risingTicks;

        private void calm() {
            speed.reset();
            upward.reset();
            risingTicks = 0;
            groundSamples = 0;
        }
    }
}
