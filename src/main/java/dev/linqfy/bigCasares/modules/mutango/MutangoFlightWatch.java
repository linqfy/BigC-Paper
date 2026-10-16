package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cuenta los intentos de volar con las Botas de Neguechen que no dieron un doble salto (sin Bukkit). Un jugador comun
 * que aprieta saltar varias veces genera unos pocos; un cliente que esta intentando volar manda muchos por segundo.
 * Cuando pasan el limite avisa una sola vez: no los resuelve el plugin, se le avisa al sistema de moderacion
 * (MutangoModeration) y ese decide.
 */
final class MutangoFlightWatch {

    /** Cuantos intentos seguidos, dentro de la ventana, se consideran un intento de volar. */
    static final int LIMIT = 8;
    static final long WINDOW_MILLIS = 2_000;
    /** Despues de avisar, hasta cuando no se vuelve a avisar del mismo jugador. */
    static final long COOLDOWN_MILLIS = 30_000;

    private final Map<UUID, Deque<Long>> attempts = new HashMap<>();
    private final Map<UUID, Long> quietUntil = new HashMap<>();

    /** Un intento mas de este jugador en ese momento. Devuelve true si hay que avisar (los LIMIT intentos de la ventana). */
    boolean attempt(UUID player, long nowMillis) {
        if (nowMillis < quietUntil.getOrDefault(player, Long.MIN_VALUE)) {
            return false;
        }
        Deque<Long> times = attempts.computeIfAbsent(player, id -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() < nowMillis - WINDOW_MILLIS) {
            times.removeFirst();
        }
        times.addLast(nowMillis);
        if (times.size() < LIMIT) {
            return false;
        }
        times.clear();
        quietUntil.put(player, nowMillis + COOLDOWN_MILLIS);
        return true;
    }

    /** Cuantos intentos lleva en la ventana (para decirlo en el aviso). */
    int pending(UUID player) {
        Deque<Long> times = attempts.get(player);
        return times == null ? 0 : times.size();
    }

    void forget(UUID player) {
        attempts.remove(player);
        quietUntil.remove(player);
    }
}
