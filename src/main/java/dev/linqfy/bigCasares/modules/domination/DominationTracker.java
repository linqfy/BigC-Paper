package dev.linqfy.bigCasares.modules.domination;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta muertes consecutivas entre pares de jugadores. Matar a alguien corta la racha que esa
 * persona tenía sobre vos; si esa racha ya era una dominación, la muerte es una Venganza.
 */
public final class DominationTracker {

    private final int threshold;
    private final Map<Pair, Integer> streaks = new HashMap<>();

    public DominationTracker(int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("El umbral de dominación debe ser al menos 1");
        }
        this.threshold = threshold;
    }

    public synchronized DominationOutcome recordKill(UUID killerId, UUID victimId) {
        Objects.requireNonNull(killerId, "killerId");
        Objects.requireNonNull(victimId, "victimId");
        if (killerId.equals(victimId)) {
            return new DominationOutcome(0, false, false);
        }
        Integer reverse = streaks.remove(new Pair(victimId, killerId));
        boolean revenge = reverse != null && reverse >= threshold;
        int streak = streaks.merge(new Pair(killerId, victimId), 1, Integer::sum);
        return new DominationOutcome(streak, streak == threshold, revenge);
    }

    public synchronized boolean isDominating(UUID killerId, UUID victimId) {
        return streaks.getOrDefault(new Pair(killerId, victimId), 0) >= threshold;
    }

    public synchronized int streak(UUID killerId, UUID victimId) {
        return streaks.getOrDefault(new Pair(killerId, victimId), 0);
    }

    public synchronized Map<Pair, Integer> snapshot() {
        return Map.copyOf(streaks);
    }

    public synchronized void restore(UUID killerId, UUID victimId, int streak) {
        if (streak > 0 && !killerId.equals(victimId)) {
            streaks.put(new Pair(killerId, victimId), streak);
        }
    }

    public int threshold() {
        return threshold;
    }

    public record Pair(UUID killerId, UUID victimId) {
    }
}
