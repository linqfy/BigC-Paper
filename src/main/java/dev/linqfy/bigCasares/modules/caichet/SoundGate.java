package dev.linqfy.bigCasares.modules.caichet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Topes de frecuencia por jugador y por tipo de sonido, sin tocar Bukkit: el reloj lo pone quien llama (en ticks), asi
 * se puede probar. Evita que un sonido que se pide muy seguido (una luz que titila, una frase del Sueño) suene en cada
 * tick.
 */
final class SoundGate {

    private final Map<UUID, Map<String, Long>> next = new HashMap<>();

    /**
     * Deja pasar el sonido si ya paso el tope desde la ultima vez que paso para este jugador y esta clave.
     *
     * @param cooldownTicks espera minima hasta la proxima vez
     */
    boolean allow(UUID player, String key, long now, long cooldownTicks) {
        Map<String, Long> keys = next.computeIfAbsent(player, k -> new HashMap<>());
        Long at = keys.get(key);
        if (at != null && now < at) {
            return false;
        }
        keys.put(key, now + Math.max(0L, cooldownTicks));
        return true;
    }

    /**
     * Cadencia al azar: la primera vez solo agenda (el sonido no suena apenas entra el jugador); despues suena cuando
     * llega la hora y agenda la siguiente entre minTicks y maxTicks.
     */
    boolean due(UUID player, String key, long now, long minTicks, long maxTicks, RandomGenerator random) {
        Map<String, Long> keys = next.computeIfAbsent(player, k -> new HashMap<>());
        Long at = keys.get(key);
        if (at == null) {
            keys.put(key, now + between(minTicks, maxTicks, random));
            return false;
        }
        if (now < at) {
            return false;
        }
        keys.put(key, now + between(minTicks, maxTicks, random));
        return true;
    }

    private static long between(long min, long max, RandomGenerator random) {
        if (max <= min) {
            return Math.max(0L, min);
        }
        return min + random.nextLong(max - min + 1);
    }

    void forget(UUID player) {
        next.remove(player);
    }

    void clear() {
        next.clear();
    }

    int players() {
        return next.size();
    }
}
