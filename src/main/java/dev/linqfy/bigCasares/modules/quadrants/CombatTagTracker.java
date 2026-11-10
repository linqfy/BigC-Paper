package dev.linqfy.bigCasares.modules.quadrants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Búfer de combate: tras un golpe PvP el jugador queda marcado durante un tiempo fijo. */
public final class CombatTagTracker {

    private final long durationMillis;
    private final LongSupplier clock;
    private final Map<UUID, Long> expirations = new HashMap<>();

    public CombatTagTracker(long durationSeconds, LongSupplier clock) {
        if (durationSeconds < 0L) {
            throw new IllegalArgumentException("durationSeconds no puede ser negativo");
        }
        this.durationMillis = durationSeconds * 1000L;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Marca (o renueva) al jugador. Devuelve true si no estaba en combate. */
    public synchronized boolean tag(UUID playerId) {
        boolean fresh = !isTagged(playerId);
        if (durationMillis > 0L) {
            expirations.put(playerId, clock.getAsLong() + durationMillis);
        }
        return fresh;
    }

    public synchronized boolean isTagged(UUID playerId) {
        return remainingMillis(playerId) > 0L;
    }

    /** Segundos restantes redondeados hacia arriba; 0 si no está en combate. */
    public synchronized long remainingSeconds(UUID playerId) {
        long millis = remainingMillis(playerId);
        return (millis + 999L) / 1000L;
    }

    public synchronized void clear(UUID playerId) {
        expirations.remove(playerId);
    }

    /** Quita las marcas vencidas y devuelve si el jugador acaba de salir de combate. */
    public synchronized boolean expire(UUID playerId) {
        Long expiresAt = expirations.get(playerId);
        if (expiresAt != null && expiresAt <= clock.getAsLong()) {
            expirations.remove(playerId);
            return true;
        }
        return false;
    }

    private long remainingMillis(UUID playerId) {
        Long expiresAt = expirations.get(playerId);
        return expiresAt == null ? 0L : Math.max(0L, expiresAt - clock.getAsLong());
    }
}
