package dev.linqfy.bigCasares.modules.staffevents;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * King of the Hill scoring: a second inside the zone counts only when exactly one
 * player holds it. Two or more players contest the hill and nobody scores.
 */
public final class KothScores {

    private final int captureSeconds;
    private final Map<UUID, Integer> points = new HashMap<>();

    public KothScores(int captureSeconds) {
        if (captureSeconds <= 0) {
            throw new IllegalArgumentException("capture-seconds must be greater than zero");
        }
        this.captureSeconds = captureSeconds;
    }

    public enum HillState { EMPTY, HELD, CONTESTED }

    /** Advances one second with the players currently inside the zone. */
    public HillState tick(Collection<UUID> occupants) {
        if (occupants.isEmpty()) {
            return HillState.EMPTY;
        }
        if (occupants.size() > 1) {
            return HillState.CONTESTED;
        }
        points.merge(occupants.iterator().next(), 1, Integer::sum);
        return HillState.HELD;
    }

    public int points(UUID playerId) {
        return points.getOrDefault(playerId, 0);
    }

    /** The player who reached the capture target, if any. */
    public Optional<UUID> capturedBy() {
        return points.entrySet().stream()
            .filter(entry -> entry.getValue() >= captureSeconds)
            .map(Map.Entry::getKey)
            .findFirst();
    }

    /** The current leader; ties keep no leader so a time-out without a clear winner gives no reward. */
    public Optional<UUID> leader() {
        UUID best = null;
        int bestPoints = 0;
        boolean tied = false;
        for (Map.Entry<UUID, Integer> entry : points.entrySet()) {
            if (entry.getValue() > bestPoints) {
                best = entry.getKey();
                bestPoints = entry.getValue();
                tied = false;
            } else if (entry.getValue() == bestPoints) {
                tied = true;
            }
        }
        return tied ? Optional.empty() : Optional.ofNullable(best);
    }

    public void forget(UUID playerId) {
        points.remove(playerId);
    }

    public int captureSeconds() {
        return captureSeconds;
    }
}
