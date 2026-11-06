package dev.linqfy.bigCasares.modules.mutango;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cuando conviene hacer un guardado del mundo que espere a las escrituras (sin Bukkit): los bloques que la grieta y las
 * ondas devolvieron solo se tachan del diario si hay evidencia de que el chunk se guardo asi, y esa evidencia es un guardado
 * con flush hecho DESPUES de devolverlos. Es un guardado pesado: se espera un rato desde que aparecio el primer bloque
 * devuelto (para juntar todos los de la pelea) y no se repite antes de un intervalo minimo por mundo.
 */
final class MutangoSavePolicy {

    private final long settleMillis;
    private final long minIntervalMillis;
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    private final Map<UUID, Long> lastSave = new HashMap<>();

    MutangoSavePolicy(long settleMillis, long minIntervalMillis) {
        this.settleMillis = settleMillis;
        this.minIntervalMillis = minIntervalMillis;
    }

    /** Toca guardar ese mundo ahora (hay bloques devueltos esperando, ya paso el tiempo de espera y el intervalo minimo). */
    boolean due(UUID world, boolean hasAwaiting, long now) {
        if (!hasAwaiting) {
            firstSeen.remove(world);
            return false;
        }
        long since = firstSeen.computeIfAbsent(world, id -> now);
        Long last = lastSave.get(world);
        return now - since >= settleMillis && (last == null || now - last >= minIntervalMillis);
    }

    /** Se guardo el mundo: empieza de nuevo la espera para lo que se devuelva despues. */
    void saved(UUID world, long now) {
        lastSave.put(world, now);
        firstSeen.remove(world);
    }
}
