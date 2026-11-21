package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Killaura sin leer paquetes: cosas que el cliente vanilla no puede hacer al atacar. Golpear algo que queda detras
 * (el cliente solo ataca lo que tiene en la mira), atravesar paredes solidas, atacar con un cofre abierto o mientras
 * usa un item (comiendo, cubriendose con el escudo, tensando el arco: el cliente descarta esos clics).
 *
 * <p>La rotacion que conoce el servidor llega un tick despues del ataque, asi que el angulo tiene margen amplio y cada
 * regla pide repeticion antes de reportar.
 */
public final class AuraCheck {
    /** Angulo maximo entre la mira y el punto mas cercano de la caja del objetivo. */
    static final double MAX_ANGLE_DEGREES = 100;
    /** Mas cerca que esto el angulo y las paredes no significan nada (el objetivo casi esta encima). */
    static final double MIN_MEASURED_DISTANCE = 1.0;

    private final Map<UUID, State> states = new HashMap<>();

    /**
     * @param angleDegrees  angulo entre la mira y el objetivo, o NaN si no se mide
     * @param distance      distancia de los ojos a la caja del objetivo
     * @param throughWall   todos los rayos de los ojos al objetivo chocan con un bloque solido
     * @param containerOpen tiene un contenedor abierto desde hace rato (no recien abierto por un modulo)
     * @param usingItem     esta usando un item desde hace rato
     */
    public List<Detection> attack(
        UUID attacker, double angleDegrees, double distance, boolean throughWall, boolean containerOpen, boolean usingItem
    ) {
        State state = states.computeIfAbsent(attacker, ignored -> new State());
        List<Detection> detections = new ArrayList<>(1);
        boolean measurable = distance >= MIN_MEASURED_DISTANCE;

        if (measurable && !Double.isNaN(angleDegrees) && angleDegrees > MAX_ANGLE_DEGREES) {
            if (state.angle.add(1)) {
                detections.add(new Detection("killaura-angle", String.format(Locale.ROOT,
                    "Golpes repetidos a objetivos fuera de la mira: %.0f grados a %.2f bloques", angleDegrees, distance)));
            }
        } else {
            state.angle.decay();
        }

        if (measurable && throughWall) {
            if (state.wall.add(1)) {
                detections.add(new Detection("killaura-wall", String.format(Locale.ROOT,
                    "Golpes repetidos a traves de bloques solidos (%.2f bloques)", distance)));
            }
        } else {
            state.wall.decay();
        }

        if (containerOpen) {
            if (state.container.add(1)) {
                detections.add(new Detection("killaura-inventory", "Ataques con un contenedor abierto"));
            }
        } else {
            state.container.decay();
        }

        if (usingItem) {
            if (state.using.add(1)) {
                detections.add(new Detection("killaura-use-item",
                    "Ataques mientras usa un item (comer, escudo, arco): el cliente vanilla no los manda"));
            }
        } else {
            state.using.decay();
        }
        return detections;
    }

    /** Angulo en grados entre una direccion de mira (normalizada) y el vector hacia el objetivo. */
    public static double angleDegrees(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        double length = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (length < 1e-9 || lookLength < 1e-9) {
            return 0;
        }
        double cos = (lookX * toX + lookY * toY + lookZ * toZ) / (length * lookLength);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private final ViolationBuffer angle = new ViolationBuffer(3, 0.34, 1);
        private final ViolationBuffer wall = new ViolationBuffer(3, 0.34, 1);
        private final ViolationBuffer container = new ViolationBuffer(3, 0.2, 1);
        private final ViolationBuffer using = new ViolationBuffer(3, 0.2, 1);
    }
}
