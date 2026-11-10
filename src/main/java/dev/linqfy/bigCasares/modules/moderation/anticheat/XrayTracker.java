package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * X-ray por estadistica: vetas de minerales raros encontradas contra piedra picada en una ventana de tiempo. Un minero
 * normal pica mucha piedra por cada veta; con x-ray va directo. No prueba nada por si solo: por defecto es una regla de
 * solo auditoria para que el staff revise.
 */
public final class XrayTracker {
    public enum Kind { STONE, RARE_ORE, OTHER }

    static final long WINDOW_MILLIS = 20L * 60L * 1000L;
    static final long VEIN_MILLIS = 30_000;
    static final int VEIN_DISTANCE = 3;
    static final int MIN_VEINS = 5;
    static final int MAX_STONE_PER_VEIN = 40;

    private final Map<UUID, State> states = new HashMap<>();

    public Optional<Detection> mined(UUID playerId, long nowMillis, Kind kind, int x, int y, int z) {
        if (kind == Kind.OTHER) {
            return Optional.empty();
        }
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        purge(state.stone, nowMillis);
        purge(state.veins, nowMillis);
        if (kind == Kind.STONE) {
            state.stone.addLast(nowMillis);
            return Optional.empty();
        }
        boolean sameVein = nowMillis - state.lastOreMillis <= VEIN_MILLIS
            && Math.abs(x - state.lastX) <= VEIN_DISTANCE && Math.abs(y - state.lastY) <= VEIN_DISTANCE
            && Math.abs(z - state.lastZ) <= VEIN_DISTANCE;
        state.lastOreMillis = nowMillis;
        state.lastX = x;
        state.lastY = y;
        state.lastZ = z;
        if (sameVein) {
            return Optional.empty();
        }
        state.veins.addLast(nowMillis);
        int veins = state.veins.size();
        int stone = state.stone.size();
        if (veins >= MIN_VEINS && stone < veins * MAX_STONE_PER_VEIN) {
            state.veins.clear();
            state.stone.clear();
            return Optional.of(new Detection("xray-ratio", veins + " vetas de minerales raros con solo " + stone
                + " bloques de piedra picados en 20 minutos"));
        }
        return Optional.empty();
    }

    private static void purge(Deque<Long> times, long nowMillis) {
        while (!times.isEmpty() && times.peekFirst() < nowMillis - WINDOW_MILLIS) {
            times.removeFirst();
        }
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private final Deque<Long> stone = new ArrayDeque<>();
        private final Deque<Long> veins = new ArrayDeque<>();
        private long lastOreMillis = Long.MIN_VALUE / 2;
        private int lastX;
        private int lastY;
        private int lastZ;
    }
}
