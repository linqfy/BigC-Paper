package dev.linqfy.bigCasares.modules.netheritequest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who gets a boss soul: the three players that dealt the most damage to the boss. */
public final class BossSoulRanking {

    public static final int WINNERS = 3;

    private BossSoulRanking() {
    }

    /** Players by damage dealt, highest first (ties by id so the result is stable); zero or invalid damage is ignored. */
    public static List<UUID> topThree(Map<UUID, Double> damageByPlayer) {
        return damageByPlayer.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .filter(entry -> Double.isFinite(entry.getValue()) && entry.getValue() > 0.0)
            .sorted(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()))
            .limit(WINNERS)
            .map(Map.Entry::getKey)
            .toList();
    }
}
