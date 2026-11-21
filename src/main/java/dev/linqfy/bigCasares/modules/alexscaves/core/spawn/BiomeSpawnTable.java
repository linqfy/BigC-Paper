package dev.linqfy.bigCasares.modules.alexscaves.core.spawn;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Weighted spawn entries of one cave biome, as in the mod's biome JSON {@code spawners}. */
public final class BiomeSpawnTable {
    private final List<Entry> entries;
    private final int totalWeight;

    public BiomeSpawnTable(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        int total = 0;
        for (Entry entry : this.entries) {
            total += entry.weight();
        }
        this.totalWeight = total;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return totalWeight == 0;
    }

    public Optional<Entry> pick(RandomGenerator random) {
        if (totalWeight == 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * @param mobId    id of a module mob or a vanilla entity key ({@code minecraft:zombie})
     * @param category mob cap bucket
     */
    public record Entry(String mobId, SpawnCategory category, int weight, int minCount, int maxCount) {
        public Entry {
            Objects.requireNonNull(mobId, "mobId");
            Objects.requireNonNull(category, "category");
            if (weight <= 0 || minCount <= 0 || maxCount < minCount) {
                throw new IllegalArgumentException("Invalid spawn entry for " + mobId);
            }
        }

        public int groupSize(RandomGenerator random) {
            return minCount + random.nextInt(maxCount - minCount + 1);
        }
    }

    /** Mob cap buckets of the mod (two of them are mod categories). */
    public enum SpawnCategory {
        MONSTER,
        AMBIENT,
        CAVE_CREATURE,
        DEEP_SEA_CREATURE,
        WATER_AMBIENT,
        UNDERGROUND_WATER_CREATURE
    }
}
