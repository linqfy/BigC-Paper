package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Golpes cuerpo a cuerpo reales (paquetes de ataque del cliente, nunca el dano que un modulo aplica en nombre de un
 * jugador): alcance contra la caja del objetivo, cadencia y cambios de objetivo imposibles.
 */
public final class CombatCheck {
    static final long RATE_WINDOW_MILLIS = 1_000;
    static final long STRIKE_WINDOW_MILLIS = 10_000;
    static final long MULTI_TARGET_WINDOW_MILLIS = 150;
    static final int MULTI_TARGET_COUNT = 3;

    private final int maxAttacksPerSecond;
    private final Map<UUID, State> states = new HashMap<>();

    public CombatCheck(int maxAttacksPerSecond) {
        if (maxAttacksPerSecond < 10) {
            throw new IllegalArgumentException("maxAttacksPerSecond must allow normal clicking");
        }
        this.maxAttacksPerSecond = maxAttacksPerSecond;
    }

    /**
     * @param distance distancia de los ojos del atacante a la caja del objetivo, o NaN si no se mide el alcance
     * @param allowed  alcance permitido (atributo de alcance + compensacion de latencia)
     */
    public List<Detection> attack(UUID attacker, long nowMillis, UUID target, double distance, double allowed) {
        State state = states.computeIfAbsent(attacker, ignored -> new State());
        List<Detection> detections = new ArrayList<>(1);

        if (!Double.isNaN(distance)) {
            if (distance > allowed) {
                double excess = distance - allowed;
                if (state.reach.add(1 + Math.min(1, excess))) {
                    detections.add(new Detection("melee-reach", String.format(Locale.ROOT,
                        "Golpes repetidos fuera de alcance: %.2f bloques a la caja, permitido %.2f", distance, allowed)));
                }
            } else {
                state.reach.decay();
            }
        }

        purge(state.attacks, nowMillis - RATE_WINDOW_MILLIS);
        state.attacks.addLast(nowMillis);
        if (state.attacks.size() > maxAttacksPerSecond && strike(state.rateStrikes, nowMillis)) {
            detections.add(new Detection("melee-rate",
                "Mas de " + maxAttacksPerSecond + " ataques por segundo en dos rafagas dentro de 10 s"));
        }
        if (state.attacks.size() > maxAttacksPerSecond) {
            state.attacks.clear();
        }

        state.recentTargets.entrySet().removeIf(entry -> entry.getValue() < nowMillis - MULTI_TARGET_WINDOW_MILLIS);
        state.recentTargets.put(target, nowMillis);
        if (state.recentTargets.size() >= MULTI_TARGET_COUNT) {
            state.recentTargets.clear();
            if (strike(state.multiStrikes, nowMillis)) {
                detections.add(new Detection("melee-multi-target", MULTI_TARGET_COUNT
                    + " objetivos distintos en " + MULTI_TARGET_WINDOW_MILLIS + " ms, dos veces dentro de 10 s"));
            }
        }
        return detections;
    }

    /** Registra una rafaga; devuelve true a partir de la segunda dentro de la ventana. */
    private boolean strike(Deque<Long> strikes, long nowMillis) {
        purge(strikes, nowMillis - STRIKE_WINDOW_MILLIS);
        strikes.addLast(nowMillis);
        if (strikes.size() >= 2) {
            strikes.clear();
            return true;
        }
        return false;
    }

    private static void purge(Deque<Long> times, long cutoff) {
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
    }

    /** Distancia de un punto (los ojos) a la caja de colision del objetivo; 0 si el punto esta dentro. */
    public static double distanceToBox(
        double x, double y, double z,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ
    ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dy = Math.max(0, Math.max(minY - y, y - maxY));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Alcance permitido: el atributo de alcance del atacante, un margen fijo por interpolacion del cliente (el cliente ve
     * al objetivo unos ticks atras) y lo que un objetivo corriendo se mueve durante la latencia del atacante.
     */
    public static double allowedReach(double interactionRange, int pingMillis) {
        double latencyTicks = Math.max(0, Math.min(pingMillis, 600)) / 50.0;
        return interactionRange + 1.0 + Math.min(1.75, latencyTicks * 0.3);
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private final ViolationBuffer reach = new ViolationBuffer(3, 0.35, 1);
        private final Deque<Long> attacks = new ArrayDeque<>();
        private final Deque<Long> rateStrikes = new ArrayDeque<>();
        private final Deque<Long> multiStrikes = new ArrayDeque<>();
        private final Map<UUID, Long> recentTargets = new HashMap<>();
    }
}
