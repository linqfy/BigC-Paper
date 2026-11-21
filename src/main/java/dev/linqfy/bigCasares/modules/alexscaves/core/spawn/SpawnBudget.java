package dev.linqfy.bigCasares.modules.alexscaves.core.spawn;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Caps per category around one player. The mod caps cave and deep sea creatures
 * at the vanilla animal cap times {@code cave_creature_spawn_count_modifier}
 * (1.75); monsters and the rest use their vanilla caps.
 */
public final class SpawnBudget {
    private final Map<SpawnCategory, Integer> caps;

    public SpawnBudget(Map<SpawnCategory, Integer> caps) {
        EnumMap<SpawnCategory, Integer> copy = new EnumMap<>(SpawnCategory.class);
        for (SpawnCategory category : SpawnCategory.values()) {
            copy.put(category, Math.max(0, caps.getOrDefault(category, 0)));
        }
        this.caps = Map.copyOf(copy);
    }

    /** Vanilla per-player caps (monster 70, creature 10, ambient 15, water ambient 20, underground water 5). */
    public static SpawnBudget modDefaults(double caveCreatureMultiplier, double scale) {
        EnumMap<SpawnCategory, Integer> caps = new EnumMap<>(SpawnCategory.class);
        caps.put(SpawnCategory.MONSTER, scaled(70, scale));
        caps.put(SpawnCategory.AMBIENT, scaled(15, scale));
        caps.put(SpawnCategory.CAVE_CREATURE, scaled(10 * caveCreatureMultiplier, scale));
        caps.put(SpawnCategory.DEEP_SEA_CREATURE, scaled(10 * caveCreatureMultiplier, scale));
        caps.put(SpawnCategory.WATER_AMBIENT, scaled(20, scale));
        caps.put(SpawnCategory.UNDERGROUND_WATER_CREATURE, scaled(5, scale));
        return new SpawnBudget(caps);
    }

    public int cap(SpawnCategory category) {
        return caps.get(category);
    }

    /** How many more of this category may spawn, given the current count near the player. */
    public int remaining(SpawnCategory category, int currentCount) {
        return Math.max(0, cap(category) - Math.max(0, currentCount));
    }

    private static int scaled(double value, double scale) {
        return (int) Math.floor(value * scale);
    }
}
