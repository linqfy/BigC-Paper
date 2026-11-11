package dev.linqfy.bigCasares.modules.missions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class MissionRotationPolicy {

    private final Random random;

    public MissionRotationPolicy(Random random) {
        this.random = random;
    }

    public MissionPlayerState createFreshState(
        UUID playerId,
        MissionCatalog catalog,
        int dailyCount,
        int weeklyCount,
        Instant now,
        Instant dailyReset,
        Instant weeklyReset
    ) {
        return new MissionPlayerState(
            playerId,
            now,
            dailyReset,
            weeklyReset,
            select(playerId, catalog.byScope(MissionScope.DAILY), dailyCount),
            select(playerId, catalog.byScope(MissionScope.WEEKLY), weeklyCount),
            null
        );
    }

    private Map<String, MissionAssignment> select(UUID playerId, List<MissionDefinition> pool, int count) {
        List<MissionDefinition> shuffled = new ArrayList<>(pool);
        long seed = random.nextLong() ^ playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        Random rng = new Random(seed);
        Collections.shuffle(shuffled, rng);
        // Weighted sampling without replacement: higher weights tend to be drawn first.
        Map<MissionDefinition, Double> keys = new java.util.IdentityHashMap<>();
        for (MissionDefinition definition : shuffled) {
            double weight = Math.max(1, definition.weight());
            keys.put(definition, Math.pow(rng.nextDouble(), 1.0 / weight));
        }
        shuffled.sort(java.util.Comparator.comparingDouble((MissionDefinition definition) -> keys.get(definition)).reversed());

        Map<String, MissionAssignment> assignments = new LinkedHashMap<>();
        int limit = Math.min(count, shuffled.size());
        for (int i = 0; i < limit; i++) {
            MissionDefinition definition = shuffled.get(i);
            assignments.put(definition.id(), new MissionAssignment(definition, MissionProgressSnapshot.fresh()));
        }
        return assignments;
    }
}
